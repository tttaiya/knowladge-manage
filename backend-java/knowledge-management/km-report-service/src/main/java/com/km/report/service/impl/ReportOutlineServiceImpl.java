package com.km.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.km.report.common.exception.BizException;
import com.km.report.dto.AiGenerateRequest;
import com.km.report.dto.AiGenerateResponse;
import com.km.report.dto.GenerateOutlineRequest;
import com.km.report.entity.ReportOutlineItem;
import com.km.report.entity.ReportRecord;
import com.km.report.entity.ReportTemplate;
import com.km.report.entity.ReportTemplateChapter;
import com.km.report.service.ReportAiService;
import com.km.report.service.ReportAccessService;
import com.km.report.service.ReportOutlineItemService;
import com.km.report.service.ReportOutlineService;
import com.km.report.service.ReportRecordService;
import com.km.report.service.ReportTemplateChapterService;
import com.km.report.service.ReportTemplateService;
import com.km.report.utils.ChapterNumberUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ReportOutlineServiceImpl implements ReportOutlineService {

    @Resource
    private ReportRecordService reportRecordService;
    @Resource
    private ReportTemplateChapterService reportTemplateChapterService;
    @Resource
    private ReportOutlineItemService reportOutlineItemService;
    @Resource
    private ReportTemplateService reportTemplateService;
    @Resource
    private ReportAiService reportAiService;
    @Resource
    private ReportAccessService reportAccessService;

    @Override
    public Long createDraft(GenerateOutlineRequest request) {
        if (request == null || !StringUtils.hasText(request.getTheme())) {
            throw new BizException("主题不能为空");
        }

        ReportRecord record = new ReportRecord();
        record.setReportName(request.getTheme());
        record.setReportType(request.getReportType());
        record.setTemplateId(resolveTemplateId(request));
        record.setMajor(request.getMajor());
        record.setPowerPlant(request.getPowerPlant());
        record.setReportYear(request.getReportYear());
        record.setGenerationPrompt(request.getTheme());
        record.setEnableKnowledgeRetrieval(request.getEnableKnowledgeRetrieval() == null ? 1 : request.getEnableKnowledgeRetrieval());
        record.setStatus(0);
        record.setExportStatus(0);
        record.setTotalChapter(0);
        record.setFinishedChapter(0);
        record.setUserId(reportAccessService.currentUserId());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        reportRecordService.save(record);

        List<ReportTemplateChapter> templateChapters = loadTemplateChapters(record.getTemplateId());
        List<ReportOutlineItem> outlineItems;
        if (reportAiService.enabled()) {
            outlineItems = generateOutlineByAi(record, request, templateChapters);
        } else if (!templateChapters.isEmpty()) {
            outlineItems = buildOutlineFromTemplate(record.getId(), templateChapters);
        } else {
            outlineItems = buildFallbackOutline(record.getId(), request);
        }

        reportOutlineItemService.saveBatch(outlineItems);
        renumberAndSave(record.getId());
        return record.getId();
    }

    @Override
    public List<ReportOutlineItem> getOutline(Long reportId) {
        reportAccessService.requireOwnedRecord(reportId);
        return orderOutlineItems(reportOutlineItemService.list(
                new LambdaQueryWrapper<ReportOutlineItem>().eq(ReportOutlineItem::getReportId, reportId)
        ));
    }

    @Override
    public ReportOutlineItem getItem(Long itemId) {
        ReportOutlineItem item = reportAccessService.requireOwnedOutlineItem(itemId);
        return item;
    }

    @Override
    public ReportOutlineItem addItem(Long reportId, ReportOutlineItem item) {
        if (item == null || !StringUtils.hasText(item.getChapterTitle())) {
            throw new BizException("章节标题不能为空");
        }
        reportAccessService.requireOwnedRecord(reportId);
        item.setId(null);
        item.setReportId(reportId);
        item.setEditable(item.getEditable() == null ? 1 : item.getEditable());
        item.setAiGenerated(item.getAiGenerated() == null ? 0 : item.getAiGenerated());
        item.setStatus(item.getStatus() == null ? "DRAFT" : item.getStatus());
        item.setCreateTime(LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        reportOutlineItemService.save(item);
        renumberAndSave(reportId);
        return getItem(item.getId());
    }

    @Override
    public ReportOutlineItem updateItem(Long itemId, ReportOutlineItem item) {
        ReportOutlineItem old = getItem(itemId);
        if (item == null) {
            throw new BizException("更新内容不能为空");
        }
        if (StringUtils.hasText(item.getChapterTitle())) {
            old.setChapterTitle(item.getChapterTitle());
        }
        if (item.getParentId() != null) {
            old.setParentId(item.getParentId());
        }
        if (item.getSort() != null) {
            old.setSort(item.getSort());
        }
        if (item.getLevel() != null) {
            old.setLevel(item.getLevel());
        }
        if (item.getEditable() != null) {
            old.setEditable(item.getEditable());
        }
        if (item.getAiGenerated() != null) {
            old.setAiGenerated(item.getAiGenerated());
        }
        if (StringUtils.hasText(item.getStatus())) {
            old.setStatus(item.getStatus());
        }
        old.setRemark(item.getRemark());
        old.setUpdateTime(LocalDateTime.now());
        reportOutlineItemService.updateById(old);
        renumberAndSave(old.getReportId());
        return getItem(itemId);
    }

    @Override
    public List<ReportOutlineItem> regenerateOutline(Long reportId) {
        ReportRecord record = reportAccessService.requireOwnedRecord(reportId);
        reportOutlineItemService.remove(new LambdaQueryWrapper<ReportOutlineItem>().eq(ReportOutlineItem::getReportId, reportId));
        List<ReportTemplateChapter> templateChapters = loadTemplateChapters(record.getTemplateId());
        List<ReportOutlineItem> outlineItems;
        if (reportAiService.enabled()) {
            outlineItems = generateOutlineByAi(record, buildRequestFromRecord(record), templateChapters);
        } else if (!templateChapters.isEmpty()) {
            outlineItems = buildOutlineFromTemplate(record.getId(), templateChapters);
        } else {
            outlineItems = buildFallbackOutline(record.getId(), buildRequestFromRecord(record));
        }
        reportOutlineItemService.saveBatch(outlineItems);
        renumberAndSave(reportId);
        return getOutline(reportId);
    }

    @Override
    public List<ReportOutlineItem> updateOutline(Long reportId, List<ReportOutlineItem> items) {
        if (items == null) {
            throw new BizException("更新内容不能为空");
        }
        reportAccessService.requireOwnedRecord(reportId);
        reportOutlineItemService.remove(new LambdaQueryWrapper<ReportOutlineItem>().eq(ReportOutlineItem::getReportId, reportId));
        for (ReportOutlineItem item : items) {
            item.setId(null);
            item.setReportId(reportId);
            item.setCreateTime(LocalDateTime.now());
            item.setUpdateTime(LocalDateTime.now());
            if (item.getEditable() == null) {
                item.setEditable(1);
            }
            if (item.getAiGenerated() == null) {
                item.setAiGenerated(0);
            }
            if (!StringUtils.hasText(item.getStatus())) {
                item.setStatus("DRAFT");
            }
        }
        reportOutlineItemService.saveBatch(items);
        renumberAndSave(reportId);
        return getOutline(reportId);
    }

    @Override
    public void deleteOutlineItem(Long itemId) {
        ReportOutlineItem item = getItem(itemId);
        reportOutlineItemService.removeById(itemId);
        renumberAndSave(item.getReportId());
    }

    @Override
    public List<ReportOutlineItem> moveItem(Long reportId, Long itemId, Integer sort, Long parentId) {
        ReportOutlineItem item = getItem(itemId);
        if (!reportId.equals(item.getReportId())) {
            throw new BizException("大纲项不属于该报告");
        }
        item.setSort(sort == null ? item.getSort() : sort);
        item.setParentId(parentId == null ? 0L : parentId);
        item.setUpdateTime(LocalDateTime.now());
        reportOutlineItemService.updateById(item);
        renumberAndSave(reportId);
        return getOutline(reportId);
    }

    private Long resolveTemplateId(GenerateOutlineRequest request) {
        if (request.getTemplateId() != null) {
            reportAccessService.requireVisibleTemplate(request.getTemplateId());
            return request.getTemplateId();
        }
        if (StringUtils.hasText(request.getReportType())) {
            ReportTemplate template = reportTemplateService.getOne(
                    new LambdaQueryWrapper<ReportTemplate>()
                            .eq(ReportTemplate::getReportType, request.getReportType())
                            .and(w -> w.eq(ReportTemplate::getTemplateScope, "GLOBAL")
                                    .or()
                                    .eq(ReportTemplate::getCreatorId, reportAccessService.currentUserId()))
                            .orderByDesc(ReportTemplate::getStatus)
                            .orderByDesc(ReportTemplate::getId)
                            .last("LIMIT 1")
            );
            if (template != null) {
                return template.getId();
            }
        }
        return 0L;
    }

    private List<ReportTemplateChapter> loadTemplateChapters(Long templateId) {
        if (templateId == null || templateId <= 0) {
            return new ArrayList<>();
        }
        return reportTemplateChapterService.list(
                new LambdaQueryWrapper<ReportTemplateChapter>()
                        .eq(ReportTemplateChapter::getTemplateId, templateId)
                        .orderByAsc(ReportTemplateChapter::getSort)
                        .orderByAsc(ReportTemplateChapter::getId)
        );
    }

    private List<ReportOutlineItem> buildOutlineFromTemplate(Long reportId, List<ReportTemplateChapter> templateChapters) {
        List<ReportOutlineItem> items = new ArrayList<>();
        for (ReportTemplateChapter templateChapter : templateChapters) {
            ReportOutlineItem item = new ReportOutlineItem();
            item.setReportId(reportId);
            item.setParentId(templateChapter.getParentId() == null ? 0L : templateChapter.getParentId());
            item.setChapterTitle(templateChapter.getChapterTitle());
            item.setLevel(templateChapter.getLevel());
            item.setSort(templateChapter.getSort());
            item.setEditable(1);
            item.setAiGenerated(0);
            item.setStatus("DRAFT");
            item.setCreateTime(LocalDateTime.now());
            item.setUpdateTime(LocalDateTime.now());
            items.add(item);
        }
        return items;
    }

    private List<ReportOutlineItem> generateOutlineByAi(ReportRecord record, GenerateOutlineRequest request, List<ReportTemplateChapter> templateChapters) {
        AiGenerateRequest aiRequest = new AiGenerateRequest();
        aiRequest.setReportId(record.getId());
        aiRequest.setResponseFormat("json");
        aiRequest.setSystemPrompt("你是电力监督报告生成助手，只输出严格 JSON，不要输出解释。JSON 格式为 {\"outline\":[{\"title\":\"\",\"level\":1,\"order_no\":1,\"children\":[]}]}。章节标题要正式、适合报告使用。");
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("报告主题：").append(nullToEmpty(request.getTheme())).append('\n');
        userPrompt.append("报告类型：").append(nullToEmpty(request.getReportType())).append('\n');
        userPrompt.append("专业：").append(nullToEmpty(request.getMajor())).append('\n');
        userPrompt.append("电厂：").append(nullToEmpty(request.getPowerPlant())).append('\n');
        userPrompt.append("年份：").append(request.getReportYear() == null ? "" : request.getReportYear()).append('\n');
        userPrompt.append("参考模板章节：");
        for (ReportTemplateChapter chapter : templateChapters) {
            userPrompt.append('[')
                    .append(nullToEmpty(chapter.getChapterNo()))
                    .append(' ')
                    .append(nullToEmpty(chapter.getChapterTitle()))
                    .append(']');
        }
        aiRequest.setUserPrompt(userPrompt.toString());
        AiGenerateResponse response = reportAiService.generate(aiRequest);
        List<ReportOutlineItem> parsed = parseAiOutline(record.getId(), response.getContent(), request);
        return parsed.isEmpty() ? buildFallbackOutline(record.getId(), request) : parsed;
    }

    private List<ReportOutlineItem> parseAiOutline(Long reportId, String content, GenerateOutlineRequest fallbackRequest) {
        List<ReportOutlineItem> items = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            return items;
        }
        Pattern pattern = Pattern.compile("\\\"title\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        Matcher matcher = pattern.matcher(content);
        int sort = 0;
        while (matcher.find()) {
            String title = matcher.group(1).trim();
            if (!StringUtils.hasText(title)) {
                continue;
            }
            ReportOutlineItem item = new ReportOutlineItem();
            item.setReportId(reportId);
            item.setParentId(0L);
            item.setChapterTitle(title);
            item.setLevel(1);
            item.setSort(++sort);
            item.setEditable(1);
            item.setAiGenerated(1);
            item.setStatus("DRAFT");
            item.setCreateTime(LocalDateTime.now());
            item.setUpdateTime(LocalDateTime.now());
            items.add(item);
        }
        return items.isEmpty() ? buildFallbackOutline(reportId, fallbackRequest) : items;
    }

    private List<ReportOutlineItem> buildFallbackOutline(Long reportId, GenerateOutlineRequest request) {
        List<ReportOutlineItem> items = new ArrayList<>();
        String[] titles = new String[] {"报告概述", "编制依据", "现场情况", "问题分析", "整改建议", "结论"};
        for (int index = 0; index < titles.length; index++) {
            ReportOutlineItem item = new ReportOutlineItem();
            item.setReportId(reportId);
            item.setParentId(0L);
            item.setChapterTitle(titles[index]);
            item.setLevel(1);
            item.setSort(index + 1);
            item.setEditable(1);
            item.setAiGenerated(1);
            item.setGenerationPrompt(request == null ? null : request.getTheme());
            item.setStatus("DRAFT");
            item.setCreateTime(LocalDateTime.now());
            item.setUpdateTime(LocalDateTime.now());
            item.setDeleted(0);
            items.add(item);
        }
        return items;
    }

    private void renumberAndSave(Long reportId) {
        List<ReportOutlineItem> items = reportOutlineItemService.list(
                new LambdaQueryWrapper<ReportOutlineItem>().eq(ReportOutlineItem::getReportId, reportId)
        );
        List<ReportOutlineItem> ordered = orderOutlineItems(items);
        ChapterNumberUtil.renumberOutline(ordered);
        for (ReportOutlineItem item : ordered) {
            item.setUpdateTime(LocalDateTime.now());
        }
        reportOutlineItemService.updateBatchById(ordered);
    }

    private List<ReportOutlineItem> orderOutlineItems(List<ReportOutlineItem> items) {
        if (items == null || items.isEmpty()) {
            return new ArrayList<>();
        }
        List<ReportOutlineItem> ordered = new ArrayList<>();
        appendOutlineChildren(items, 0L, ordered);
        return ordered;
    }

    private void appendOutlineChildren(List<ReportOutlineItem> source, Long parentId, List<ReportOutlineItem> ordered) {
        List<ReportOutlineItem> children = source.stream()
                .filter(item -> normalizeParentId(item.getParentId()).equals(parentId))
                .sorted(Comparator.comparing((ReportOutlineItem item) -> item.getSort() == null ? 0 : item.getSort())
                        .thenComparing(item -> item.getId() == null ? 0L : item.getId()))
                .collect(Collectors.toList());
        for (ReportOutlineItem child : children) {
            ordered.add(child);
            appendOutlineChildren(source, child.getId(), ordered);
        }
    }

    private Long normalizeParentId(Long parentId) {
        return parentId == null ? 0L : parentId;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private GenerateOutlineRequest buildRequestFromRecord(ReportRecord record) {
        GenerateOutlineRequest request = new GenerateOutlineRequest();
        request.setTheme(record.getReportName());
        request.setReportType(record.getReportType());
        request.setMajor(record.getMajor());
        request.setPowerPlant(record.getPowerPlant());
        request.setReportYear(record.getReportYear());
        request.setTemplateId(record.getTemplateId());
        return request;
    }
}
