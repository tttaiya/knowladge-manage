package com.km.report.controller;

import com.km.report.common.result.ApiResult;
import com.km.report.dto.AiGenerateRequest;
import com.km.report.dto.AiGenerateResponse;
import com.km.report.dto.InsertImageRequest;
import com.km.report.dto.InsertTableRequest;
import com.km.report.dto.UpdateChapterContentRequest;
import com.km.report.entity.ReportChapterContent;
import com.km.report.entity.ReportMaterial;
import com.km.report.entity.ReportOutlineItem;
import com.km.report.entity.ReportRecord;
import com.km.report.service.ReportAiService;
import com.km.report.service.ReportAccessService;
import com.km.report.service.ReportChapterContentService;
import com.km.report.service.ReportMaterialService;
import com.km.report.service.ReportOutlineItemService;
import com.km.report.service.ReportRecordService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/reports/chapters")
public class ReportChapterContentController {

    @Resource
    private ReportChapterContentService reportChapterContentService;
    @Resource
    private ReportAiService reportAiService;
    @Resource
    private ReportOutlineItemService reportOutlineItemService;
    @Resource
    private ReportRecordService reportRecordService;
    @Resource
    private ReportMaterialService reportMaterialService;
    @Resource
    private ReportAccessService reportAccessService;

    @GetMapping("/report/{reportId}")
    public ApiResult<List<ReportChapterContent>> listByReport(@PathVariable Long reportId) {
        reportAccessService.requireOwnedRecord(reportId);
        return ApiResult.ok(reportChapterContentService.list(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ReportChapterContent>()
                .eq(ReportChapterContent::getReportId, reportId)
                .orderByAsc(ReportChapterContent::getSort)
                .orderByAsc(ReportChapterContent::getId)));
    }

    @GetMapping("/{chapterId}")
    public ApiResult<ReportChapterContent> getById(@PathVariable Long chapterId) {
        return ApiResult.ok(reportAccessService.requireOwnedChapter(chapterId));
    }

    @PutMapping("/{chapterId}")
    public ApiResult<ReportChapterContent> update(@PathVariable Long chapterId, @RequestBody UpdateChapterContentRequest request) {
        ReportChapterContent chapter = reportAccessService.requireOwnedChapter(chapterId);
        if (chapter != null) {
            if (request.getContent() != null) {
                chapter.setContent(request.getContent());
                chapter.setWordCount(request.getContent().length());
            }
            if (request.getContentFormat() != null) {
                chapter.setContentFormat(request.getContentFormat());
            }
            if (request.getRemark() != null) {
                chapter.setRemark(request.getRemark());
            }
            chapter.setStatus(3);
            chapter.setUpdateTime(LocalDateTime.now());
            reportChapterContentService.updateById(chapter);
        }
        return ApiResult.ok(chapter);
    }

    @PostMapping("/{chapterId}/ai-regenerate")
    public ApiResult<ReportChapterContent> aiRegenerate(@PathVariable Long chapterId, @RequestBody UpdateChapterContentRequest request) {
        ReportChapterContent chapter = reportAccessService.requireOwnedChapter(chapterId);
        if (request != null && request.getContent() != null) { chapter.setContent(request.getContent()); }
        String currentContent = chapter.getContent() == null ? "" : chapter.getContent();
        ReportRecord record = reportAccessService.requireOwnedRecord(chapter.getReportId());
        ReportOutlineItem outlineItem = null;
        if (chapter.getTemplateChapterId() != null) { outlineItem = reportOutlineItemService.getById(chapter.getTemplateChapterId()); }
        StringBuilder materialContext = new StringBuilder();
        if (record != null) {
            List<ReportMaterial> materials = reportMaterialService.list(
                new LambdaQueryWrapper<ReportMaterial>().eq(ReportMaterial::getDeleted, 0)
                    .eq(ReportMaterial::getCreatorId, reportAccessService.currentUserId())
                    .and(w -> w.eq(ReportMaterial::getReportType, record.getReportType())
                        .or().eq(ReportMaterial::getMajor, record.getMajor())
                        .or().eq(ReportMaterial::getPowerPlant, record.getPowerPlant()))
                    .orderByDesc(ReportMaterial::getCreateTime));
            for (ReportMaterial m : materials) {
                materialContext.append("【素材】").append(safe(m.getMaterialName())).append(" / ").append(safe(m.getMaterialType())).append(" / ").append(safe(m.getRemark())).append("\n");
                if (m.getStructuredData() != null && m.getStructuredData().trim().length() > 0) { materialContext.append(m.getStructuredData()).append("\n"); }
            }
        }
        if (reportAiService.enabled()) {
            AiGenerateRequest aiReq = new AiGenerateRequest();
            aiReq.setReportId(chapter.getReportId()); aiReq.setChapterId(chapterId); aiReq.setResponseFormat("text");
            aiReq.setSystemPrompt("你是正式报告章节编写助手，请输出规范、正式、可直接入文的章节正文，语言客观完整，不要输出额外说明。");
            aiReq.setUserPrompt("报告名称：" + safe(record != null ? record.getReportName() : null) + "\n报告类型：" + safe(record != null ? record.getReportType() : null)
                + "\n章节编号：" + safe(outlineItem != null ? outlineItem.getChapterNo() : chapter.getChapterNo())
                + "\n章节标题：" + safe(outlineItem != null ? outlineItem.getChapterTitle() : chapter.getChapterTitle())
                + "\n专业：" + safe(record != null ? record.getMajor() : null) + "\n电厂：" + safe(record != null ? record.getPowerPlant() : null)
                + "\n年份：" + (record != null && record.getReportYear() != null ? record.getReportYear() : "")
                + "\n当前内容：" + currentContent + "\n素材上下文：" + materialContext.toString()
                + "\n请重新生成该章节正文，要求内容完整充实、与上下文一致，可直接用于正式报告。");
            AiGenerateResponse resp = reportAiService.generate(aiReq);
            if (resp != null && resp.getContent() != null && resp.getContent().trim().length() > 0) { chapter.setContent(resp.getContent().trim()); }
            else { chapter.setContent(currentContent + "\n\n（AI 重新生成返回为空，请稍后重试）"); }
        } else { chapter.setContent(currentContent + "\n\n（AI 未启用，请在系统配置中开启 report.llm.enabled）"); }
        chapter.setContentFormat("MARKDOWN"); chapter.setWordCount(chapter.getContent().length()); chapter.setStatus(1);
        chapter.setUpdateTime(LocalDateTime.now()); reportChapterContentService.updateById(chapter);
        return ApiResult.ok(chapter);
    }

    @PostMapping("/{chapterId}/table")
    public ApiResult<ReportChapterContent> insertTable(@PathVariable Long chapterId, @RequestBody InsertTableRequest request) {
        ReportChapterContent chapter = reportAccessService.requireOwnedChapter(chapterId);
        if (chapter != null) {
            chapter.setContent((chapter.getContent() == null ? "" : chapter.getContent()) + "\n\n" + renderMarkdownTable(request));
            chapter.setContentFormat("MARKDOWN");
            chapter.setWordCount(chapter.getContent().length());
            chapter.setStatus(3);
            chapter.setUpdateTime(LocalDateTime.now());
            reportChapterContentService.updateById(chapter);
        }
        return ApiResult.ok(chapter);
    }

    @PostMapping("/{chapterId}/image")
    public ApiResult<ReportChapterContent> insertImage(@PathVariable Long chapterId, @RequestBody InsertImageRequest request) {
        ReportChapterContent chapter = reportAccessService.requireOwnedChapter(chapterId);
        if (chapter != null) {
            String title = request == null || request.getTitle() == null ? "图片" : request.getTitle();
            String imageUrl = request == null ? "" : request.getImageUrl();
            chapter.setContent((chapter.getContent() == null ? "" : chapter.getContent()) + "\n\n![" + title + "](" + imageUrl + ")");
            chapter.setContentFormat("MARKDOWN");
            chapter.setWordCount(chapter.getContent().length());
            chapter.setStatus(3);
            chapter.setUpdateTime(LocalDateTime.now());
            reportChapterContentService.updateById(chapter);
        }
        return ApiResult.ok(chapter);
    }

    @DeleteMapping("/{chapterId}")
    public ApiResult<Boolean> delete(@PathVariable Long chapterId) {
        reportAccessService.requireOwnedChapter(chapterId);
        reportChapterContentService.removeById(chapterId);
        return ApiResult.ok(true);
    }

    private String renderMarkdownTable(InsertTableRequest request) {
        if (request == null || request.getHeaders() == null || request.getHeaders().isEmpty()) {
            return "|列1|列2|列1|列2|\n|---|---|\n|数据1|数据2|数据1|数据2|";
        }
        int colCount = request.getHeaders().size();
        StringBuilder builder = new StringBuilder();
        if (request.getTitle() != null && request.getTitle().trim().length() > 0) {
            builder.append("**").append(request.getTitle().trim()).append("**\n\n");
        }
        builder.append("|").append(String.join("|", request.getHeaders())).append("|\n");
        builder.append("|").append(request.getHeaders().stream().map(item -> "---").collect(Collectors.joining("|"))).append("|\n");
        if (request.getRows() != null) {
            for (List<String> row : request.getRows()) {
                List<String> cells = new java.util.ArrayList<>(row);
                while (cells.size() < colCount) {
                    cells.add("");
                }
                if (cells.size() > colCount) {
                    cells = cells.subList(0, colCount);
                }
                builder.append("|").append(String.join("|", cells)).append("|\n");
            }
        }
        return builder.toString();
    }
    private String safe(String value) { return value == null ? "" : value; }
}
