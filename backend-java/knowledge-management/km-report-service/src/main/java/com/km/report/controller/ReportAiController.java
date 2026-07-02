package com.km.report.controller;

import com.km.report.common.result.ApiResult;
import com.km.report.dto.AiGenerateRequest;
import com.km.report.dto.AiGenerateResponse;
import com.km.report.dto.AiRewriteRequest;
import com.km.report.entity.ReportChapterContent;
import com.km.report.service.ReportAccessService;
import com.km.report.service.ReportAiService;
import com.km.report.service.ReportChapterContentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/reports/ai")
public class ReportAiController {

    @Resource
    private ReportAiService reportAiService;
    @Resource
    private ReportChapterContentService reportChapterContentService;
    @Resource
    private ReportAccessService reportAccessService;

    @PostMapping("/outline/generate")
    public ApiResult<AiGenerateResponse> generateOutline(@RequestBody AiGenerateRequest request) {
        if (request != null && request.getReportId() != null) {
            reportAccessService.requireOwnedRecord(request.getReportId());
        }
        return ApiResult.ok(reportAiService.generate(request));
    }

    @PostMapping("/chapter/rewrite")
    public ApiResult<ReportChapterContent> rewriteChapter(@RequestBody AiRewriteRequest request) {
        AiGenerateRequest aiRequest = new AiGenerateRequest();
        aiRequest.setReportId(request.getReportId());
        aiRequest.setChapterId(request.getChapterId());
        aiRequest.setResponseFormat("text");
        aiRequest.setSystemPrompt("你是正式报告章节改写助手，请输出规范、正式、可直接入文的章节正文，不要输出额外说明。");
        aiRequest.setUserPrompt("章节标题：" + safe(request.getChapterTitle())
                + "\n原文：" + safe(request.getCurrentContent())
                + "\n章节上下文：" + safe(request.getChapterContext())
                + "\n素材上下文：" + safe(request.getMaterialContext())
                + "\n改写目标：" + safe(request.getRewriteGoal())
                + "\n目标长度：" + request.getTargetLength());
        AiGenerateResponse response = reportAiService.generate(aiRequest);
        ReportChapterContent chapter = reportAccessService.requireOwnedChapter(request.getChapterId());
        if (chapter != null) {
            chapter.setContent(response.getContent());
            chapter.setContentFormat("MARKDOWN");
            chapter.setStatus(3);
            chapter.setWordCount(response.getContent() == null ? 0 : response.getContent().length());
            chapter.setUpdateTime(LocalDateTime.now());
            reportChapterContentService.updateById(chapter);
        }
        return ApiResult.ok(chapter);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
