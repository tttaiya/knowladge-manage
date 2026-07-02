package com.km.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.km.report.common.context.LoginUserContext;
import com.km.report.common.exception.BizException;
import com.km.report.entity.ReportChapterContent;
import com.km.report.entity.ReportMaterial;
import com.km.report.entity.ReportOutlineItem;
import com.km.report.entity.ReportRecord;
import com.km.report.entity.ReportTemplate;
import com.km.report.mapper.ReportChapterContentMapper;
import com.km.report.mapper.ReportMaterialMapper;
import com.km.report.mapper.ReportOutlineItemMapper;
import com.km.report.mapper.ReportRecordMapper;
import com.km.report.mapper.ReportTemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ReportAccessService {

    private final ReportRecordMapper reportRecordMapper;
    private final ReportTemplateMapper reportTemplateMapper;
    private final ReportMaterialMapper reportMaterialMapper;
    private final ReportOutlineItemMapper reportOutlineItemMapper;
    private final ReportChapterContentMapper reportChapterContentMapper;

    public ReportAccessService(ReportRecordMapper reportRecordMapper,
                               ReportTemplateMapper reportTemplateMapper,
                               ReportMaterialMapper reportMaterialMapper,
                               ReportOutlineItemMapper reportOutlineItemMapper,
                               ReportChapterContentMapper reportChapterContentMapper) {
        this.reportRecordMapper = reportRecordMapper;
        this.reportTemplateMapper = reportTemplateMapper;
        this.reportMaterialMapper = reportMaterialMapper;
        this.reportOutlineItemMapper = reportOutlineItemMapper;
        this.reportChapterContentMapper = reportChapterContentMapper;
    }

    public String currentUserId() {
        String userId = LoginUserContext.getUserId();
        if (!StringUtils.hasText(userId)) {
            throw new BizException("missing user context");
        }
        return userId;
    }

    public ReportRecord requireOwnedRecord(Long reportId) {
        if (reportId == null) {
            throw new BizException("报告不存在");
        }
        ReportRecord record = reportRecordMapper.selectOne(
                new LambdaQueryWrapper<ReportRecord>()
                        .eq(ReportRecord::getId, reportId)
                        .eq(ReportRecord::getUserId, currentUserId())
                        .last("LIMIT 1")
        );
        if (record == null) {
            throw new BizException("报告不存在或无权访问");
        }
        return record;
    }

    public ReportTemplate requireVisibleTemplate(Long templateId) {
        if (templateId == null || templateId <= 0) {
            throw new BizException("模板不存在");
        }
        String userId = currentUserId();
        ReportTemplate template = reportTemplateMapper.selectOne(
                new LambdaQueryWrapper<ReportTemplate>()
                        .eq(ReportTemplate::getId, templateId)
                        .and(wrapper -> wrapper
                                .eq(ReportTemplate::getTemplateScope, "GLOBAL")
                                .or()
                                .eq(ReportTemplate::getCreatorId, userId))
                        .last("LIMIT 1")
        );
        if (template == null) {
            throw new BizException("模板不存在或无权访问");
        }
        return template;
    }

    public ReportTemplate requireOwnedTemplate(Long templateId) {
        if (templateId == null || templateId <= 0) {
            throw new BizException("模板不存在");
        }
        ReportTemplate template = reportTemplateMapper.selectOne(
                new LambdaQueryWrapper<ReportTemplate>()
                        .eq(ReportTemplate::getId, templateId)
                        .eq(ReportTemplate::getCreatorId, currentUserId())
                        .last("LIMIT 1")
        );
        if (template == null) {
            throw new BizException("模板不存在或无权操作");
        }
        return template;
    }

    public ReportMaterial requireOwnedMaterial(Long materialId) {
        if (materialId == null) {
            throw new BizException("素材不存在");
        }
        ReportMaterial material = reportMaterialMapper.selectOne(
                new LambdaQueryWrapper<ReportMaterial>()
                        .eq(ReportMaterial::getId, materialId)
                        .eq(ReportMaterial::getCreatorId, currentUserId())
                        .last("LIMIT 1")
        );
        if (material == null) {
            throw new BizException("素材不存在或无权访问");
        }
        return material;
    }

    public ReportOutlineItem requireOwnedOutlineItem(Long itemId) {
        if (itemId == null) {
            throw new BizException("大纲项不存在");
        }
        ReportOutlineItem item = reportOutlineItemMapper.selectById(itemId);
        if (item == null) {
            throw new BizException("大纲项不存在");
        }
        requireOwnedRecord(item.getReportId());
        return item;
    }

    public ReportChapterContent requireOwnedChapter(Long chapterId) {
        if (chapterId == null) {
            throw new BizException("章节不存在");
        }
        ReportChapterContent chapter = reportChapterContentMapper.selectById(chapterId);
        if (chapter == null) {
            throw new BizException("章节不存在");
        }
        requireOwnedRecord(chapter.getReportId());
        return chapter;
    }
}
