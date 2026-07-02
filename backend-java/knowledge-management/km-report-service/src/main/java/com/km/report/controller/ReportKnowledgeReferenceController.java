package com.km.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.km.report.common.result.ApiResult;
import com.km.report.entity.ReportKnowledgeReference;
import com.km.report.mapper.ReportKnowledgeReferenceMapper;
import com.km.report.service.ReportAccessService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reports/chapters")
public class ReportKnowledgeReferenceController {

    @Resource
    private ReportKnowledgeReferenceMapper reportKnowledgeReferenceMapper;
    @Resource
    private ReportAccessService reportAccessService;

    @GetMapping("/{chapterId}/references")
    public ApiResult<List<ReportKnowledgeReference>> listReferences(@PathVariable Long chapterId) {
        reportAccessService.requireOwnedChapter(chapterId);
        return ApiResult.ok(reportKnowledgeReferenceMapper.selectList(
                new LambdaQueryWrapper<ReportKnowledgeReference>()
                        .eq(ReportKnowledgeReference::getChapterId, chapterId)
                        .orderByAsc(ReportKnowledgeReference::getSourceOrder)
                        .orderByAsc(ReportKnowledgeReference::getId)
        ));
    }
}
