package com.km.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.km.report.common.exception.BizException;
import com.km.report.dto.AiGenerateRequest;
import com.km.report.dto.AiGenerateResponse;
import com.km.report.dto.GenerateReportRequest;
import com.km.report.dto.KnowledgeSearchHit;
import com.km.report.entity.ReportChapterContent;
import com.km.report.entity.ReportKnowledgeReference;
import com.km.report.entity.ReportMaterial;
import com.km.report.entity.ReportOutlineItem;
import com.km.report.entity.ReportRecord;
import com.km.report.mapper.ReportKnowledgeReferenceMapper;
import com.km.report.service.ReportAiService;
import com.km.report.service.ReportAccessService;
import com.km.report.service.ReportChapterContentService;
import com.km.report.service.ReportGenerationService;
import com.km.report.service.ReportKnowledgeSearchClient;
import com.km.report.service.ReportMaterialService;
import com.km.report.service.ReportOutlineItemService;
import com.km.report.service.ReportRecordService;
import com.km.report.vo.ReportGenerationProgressVO;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ReportGenerationServiceImpl implements ReportGenerationService {

    @Resource
    private ReportRecordService reportRecordService;
    @Resource
    private ReportOutlineItemService reportOutlineItemService;
    @Resource
    private ReportChapterContentService reportChapterContentService;
    @Resource
    private ReportMaterialService reportMaterialService;
    @Resource
    private ReportAiService reportAiService;
    @Resource
    private ReportAccessService reportAccessService;
    @Resource
    private ReportKnowledgeSearchClient reportKnowledgeSearchClient;
    @Resource
    private ReportKnowledgeReferenceMapper reportKnowledgeReferenceMapper;

    @Override
    public ReportGenerationProgressVO startGenerate(GenerateReportRequest request) {
        GenerateContext context = prepareGeneration(request);
        try {
            ReportGenerationProgressVO latest = null;
            for (int index = 0; index < context.outlineItems.size(); index++) {
                latest = generateOneChapter(context, index);
            }
            context.record.setStatus(1);
            context.record.setFinishedChapter(context.outlineItems.size());
            context.record.setUpdateTime(LocalDateTime.now());
            reportRecordService.updateById(context.record);
            return latest;
        } catch (Exception e) {
            context.record.setStatus(2);
            context.record.setFailReason(e.getMessage());
            context.record.setUpdateTime(LocalDateTime.now());
            reportRecordService.updateById(context.record);
            if (e instanceof BizException) {
                throw (BizException) e;
            }
            throw new BizException("报告生成失败：" + e.getMessage());
        }
    }

    @Override
    public ReportGenerationProgressVO getProgress(Long reportId) {
        ReportRecord record = reportAccessService.requireOwnedRecord(reportId);
        ReportGenerationProgressVO vo = new ReportGenerationProgressVO();
        vo.setReportId(reportId);
        vo.setTotalChapter(record.getTotalChapter());
        vo.setFinishedChapter(record.getFinishedChapter());
        vo.setStatus(record.getStatus());
        vo.setMessage("当前生成进度：" + safeInt(record.getFinishedChapter()) + "/" + safeInt(record.getTotalChapter()));
        return vo;
    }

    private GenerateContext prepareGeneration(GenerateReportRequest request) {
        if (request == null || request.getReportId() == null) {
            throw new BizException("报告ID不能为空");
        }
        ReportRecord record = reportAccessService.requireOwnedRecord(request.getReportId());
        List<ReportOutlineItem> outlineItems = reportOutlineItemService.list(
                new LambdaQueryWrapper<ReportOutlineItem>()
                        .eq(ReportOutlineItem::getReportId, request.getReportId())
                        .orderByAsc(ReportOutlineItem::getSort)
                        .orderByAsc(ReportOutlineItem::getId)
        );
        if (outlineItems.isEmpty()) {
            throw new BizException("请先生成大纲");
        }
        record.setStatus(0);
        record.setTotalChapter(outlineItems.size());
        record.setFinishedChapter(0);
        record.setFailReason("");
        record.setUpdateTime(LocalDateTime.now());
        reportRecordService.updateById(record);
        return new GenerateContext(record, outlineItems, resolveMaterials(record));
    }

    private ReportGenerationProgressVO generateOneChapter(GenerateContext context, int index) {
        ReportOutlineItem outlineItem = context.outlineItems.get(index);
        ChapterDraft draft = buildChapterDraft(context.record, outlineItem, context.materials);
        ReportChapterContent chapter = reportChapterContentService.getOne(
                new LambdaQueryWrapper<ReportChapterContent>()
                        .eq(ReportChapterContent::getReportId, context.record.getId())
                        .eq(ReportChapterContent::getTemplateChapterId, outlineItem.getId())
                        .last("LIMIT 1")
        );
        if (chapter == null) {
            chapter = new ReportChapterContent();
            chapter.setReportId(context.record.getId());
            chapter.setTemplateChapterId(outlineItem.getId());
            chapter.setParentId(outlineItem.getParentId());
            chapter.setChapterTitle(outlineItem.getChapterTitle());
            chapter.setChapterNo(outlineItem.getChapterNo());
            chapter.setLevel(outlineItem.getLevel());
            chapter.setSort(outlineItem.getSort());
            chapter.setContentFormat("MARKDOWN");
            chapter.setCreateTime(LocalDateTime.now());
        }
        chapter.setChapterTitle(outlineItem.getChapterTitle());
        chapter.setChapterNo(outlineItem.getChapterNo());
        chapter.setLevel(outlineItem.getLevel());
        chapter.setSort(outlineItem.getSort());
        chapter.setContent(draft.content);
        chapter.setContentFormat("MARKDOWN");
        chapter.setStatus(1);
        chapter.setWordCount(draft.content == null ? 0 : draft.content.length());
        chapter.setUpdateTime(LocalDateTime.now());
        reportChapterContentService.saveOrUpdate(chapter);
        saveKnowledgeReferences(context.record, chapter, draft.knowledgeHits);

        context.record.setFinishedChapter(index + 1);
        context.record.setUpdateTime(LocalDateTime.now());
        reportRecordService.updateById(context.record);
        return progress(context.record, outlineItem, index + 1, context.outlineItems.size(), "章节已生成");
    }

    private ChapterDraft buildChapterDraft(ReportRecord record, ReportOutlineItem outlineItem, List<ReportMaterial> materials) {
        String materialContext = buildMaterialContext(materials);
        List<KnowledgeSearchHit> knowledgeHits = resolveKnowledgeHits(record, outlineItem);
        String knowledgeContext = buildKnowledgeContext(knowledgeHits);
        String chapterContext = buildChapterContext(record, outlineItem);
        if (reportAiService.enabled()) {
            AiGenerateRequest aiRequest = new AiGenerateRequest();
            aiRequest.setReportId(record.getId());
            aiRequest.setChapterId(outlineItem.getId());
            aiRequest.setResponseFormat("text");
            aiRequest.setSystemPrompt("你是正式报告撰写助手，请输出可直接入稿的章节正文，语言规范、客观、完整。不要输出标题编号以外的解释。");
            aiRequest.setUserPrompt("报告名称：" + safe(record.getReportName())
                    + "\n报告类型：" + safe(record.getReportType())
                    + "\n章节编号：" + safe(outlineItem.getChapterNo())
                    + "\n章节标题：" + safe(outlineItem.getChapterTitle())
                    + "\n章节上下文：" + chapterContext
                    + "\n知识库引用上下文：" + knowledgeContext
                    + "\n素材上下文：" + materialContext
                    + "\n请生成该章节正文，要求与上下文一致、可直接用于正式报告。" );
            AiGenerateResponse response = reportAiService.generate(aiRequest);
            if (response != null && response.getContent() != null && response.getContent().trim().length() > 0) {
                return new ChapterDraft(response.getContent().trim(), knowledgeHits);
            }
        }
        StringBuilder builder = new StringBuilder();
        builder.append(outlineItem.getChapterTitle()).append('\n');
        builder.append("基于已收集的材料对该章节进行正式撰写。\n");
        if (knowledgeContext.length() > 0) {
            builder.append(knowledgeContext);
        }
        if (materialContext.length() > 0) {
            builder.append(materialContext);
        }
        return new ChapterDraft(builder.toString(), knowledgeHits);
    }

    private List<KnowledgeSearchHit> resolveKnowledgeHits(ReportRecord record, ReportOutlineItem outlineItem) {
        if (record.getEnableKnowledgeRetrieval() != null && record.getEnableKnowledgeRetrieval() == 0) {
            return new ArrayList<KnowledgeSearchHit>();
        }
        String query = safe(record.getReportName()) + " " + safe(record.getReportType()) + " "
                + safe(record.getMajor()) + " " + safe(record.getPowerPlant()) + " "
                + safe(outlineItem.getChapterTitle()) + " " + safe(outlineItem.getGenerationPrompt());
        return reportKnowledgeSearchClient.search(query, 5);
    }

    private String buildKnowledgeContext(List<KnowledgeSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        for (KnowledgeSearchHit hit : hits) {
            builder.append("【知识引用").append(++index).append("】");
            builder.append(safe(hit.getKbName())).append(" / ");
            builder.append(safe(hit.getDocName()));
            if (hit.getPageNo() != null) {
                builder.append(" / 第").append(hit.getPageNo()).append("页");
            }
            builder.append('\n');
            builder.append(safe(firstText(hit.getContent(), hit.getSummary()))).append('\n');
        }
        return builder.toString();
    }

    private void saveKnowledgeReferences(ReportRecord record, ReportChapterContent chapter, List<KnowledgeSearchHit> hits) {
        reportKnowledgeReferenceMapper.delete(new LambdaQueryWrapper<ReportKnowledgeReference>()
                .eq(ReportKnowledgeReference::getReportId, record.getId())
                .eq(ReportKnowledgeReference::getChapterId, chapter.getId()));
        if (hits == null || hits.isEmpty()) {
            return;
        }
        int order = 0;
        for (KnowledgeSearchHit hit : hits) {
            ReportKnowledgeReference reference = new ReportKnowledgeReference();
            reference.setReportId(record.getId());
            reference.setChapterId(chapter.getId());
            reference.setKnowledgeBaseId(hit.getKbId());
            reference.setDocumentId(hit.getDocId());
            reference.setChunkId(hit.getChunkId());
            reference.setVectorId(hit.getVectorId());
            Double score = hit.getRerankScore() != null ? hit.getRerankScore() : hit.getSimilarityScore();
            reference.setRetrievalScore(score == null ? null : BigDecimal.valueOf(score));
            reference.setSourceTitle(buildSourceTitle(hit));
            reference.setExcerptSnapshot(firstText(hit.getContent(), hit.getSummary()));
            reference.setSourceOrder(++order);
            reference.setCreateTime(LocalDateTime.now());
            reportKnowledgeReferenceMapper.insert(reference);
        }
    }

    private String buildSourceTitle(KnowledgeSearchHit hit) {
        StringBuilder builder = new StringBuilder();
        builder.append(safe(hit.getKbName()));
        if (builder.length() > 0) {
            builder.append(" / ");
        }
        builder.append(safe(hit.getDocName()));
        if (hit.getChapterPath() != null && hit.getChapterPath().trim().length() > 0) {
            builder.append(" / ").append(hit.getChapterPath());
        }
        return builder.toString();
    }

    private String firstText(String primary, String fallback) {
        return primary != null && primary.trim().length() > 0 ? primary : fallback;
    }

    private List<ReportMaterial> resolveMaterials(ReportRecord record) {
        return reportMaterialService.list(
                new LambdaQueryWrapper<ReportMaterial>()
                        .eq(ReportMaterial::getDeleted, 0)
                        .eq(ReportMaterial::getCreatorId, reportAccessService.currentUserId())
                        .and(wrapper -> wrapper
                                .eq(ReportMaterial::getReportType, record.getReportType())
                                .or()
                                .eq(ReportMaterial::getMajor, record.getMajor())
                                .or()
                                .eq(ReportMaterial::getPowerPlant, record.getPowerPlant()))
                        .orderByDesc(ReportMaterial::getCreateTime)
        );
    }

    private String buildChapterContext(ReportRecord record, ReportOutlineItem outlineItem) {
        StringBuilder builder = new StringBuilder();
        builder.append("报告主题：").append(safe(record.getReportName())).append('\n');
        builder.append("报告类型：").append(safe(record.getReportType())).append('\n');
        builder.append("章节编号：").append(safe(outlineItem.getChapterNo())).append('\n');
        builder.append("章节标题：").append(safe(outlineItem.getChapterTitle())).append('\n');
        builder.append("专业：").append(safe(record.getMajor())).append('\n');
        builder.append("电厂：").append(safe(record.getPowerPlant())).append('\n');
        builder.append("年份：").append(record.getReportYear() == null ? "" : record.getReportYear()).append('\n');
        return builder.toString();
    }

    private String buildMaterialContext(List<ReportMaterial> materials) {
        if (materials == null || materials.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (ReportMaterial material : materials) {
            builder.append("【素材】");
            builder.append(safe(material.getMaterialName())).append(" / ");
            builder.append(safe(material.getMaterialType())).append(" / ");
            builder.append(safe(material.getRemark())).append('\n');
            if (material.getStructuredData() != null && material.getStructuredData().trim().length() > 0) {
                builder.append(material.getStructuredData()).append('\n');
            }
        }
        return builder.toString();
    }

    private ReportGenerationProgressVO progress(ReportRecord record, ReportOutlineItem item, int finished, int total, String message) {
        ReportGenerationProgressVO vo = new ReportGenerationProgressVO();
        vo.setReportId(record.getId());
        vo.setTotalChapter(total);
        vo.setFinishedChapter(finished);
        vo.setStatus(record.getStatus());
        vo.setCurrentChapterTitle(item == null ? null : item.getChapterTitle());
        vo.setCurrentChapterId(item == null ? null : item.getId());
        vo.setMessage(message + " " + finished + "/" + total);
        return vo;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static class GenerateContext {
        private final ReportRecord record;
        private final List<ReportOutlineItem> outlineItems;
        private final List<ReportMaterial> materials;

        private GenerateContext(ReportRecord record, List<ReportOutlineItem> outlineItems, List<ReportMaterial> materials) {
            this.record = record;
            this.outlineItems = outlineItems;
            this.materials = materials;
        }
    }

    private static class ChapterDraft {
        private final String content;
        private final List<KnowledgeSearchHit> knowledgeHits;

        private ChapterDraft(String content, List<KnowledgeSearchHit> knowledgeHits) {
            this.content = content;
            this.knowledgeHits = knowledgeHits;
        }
    }
}
