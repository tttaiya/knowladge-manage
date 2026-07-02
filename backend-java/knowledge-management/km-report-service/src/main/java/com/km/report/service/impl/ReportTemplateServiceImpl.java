package com.km.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.km.report.common.context.LoginUserContext;
import com.km.report.common.exception.BizException;
import com.km.report.dto.ChapterNodeDTO;
import com.km.report.dto.ChapterTreeSaveDTO;
import com.km.report.dto.TemplateQueryDTO;
import com.km.report.dto.TemplateSaveDTO;
import com.km.report.entity.ReportRecord;
import com.km.report.entity.ReportTemplate;
import com.km.report.entity.ReportTemplateChapter;
import com.km.report.mapper.ReportRecordMapper;
import com.km.report.mapper.ReportTemplateChapterMapper;
import com.km.report.mapper.ReportTemplateMapper;
import com.km.report.config.ReportExportProperties;
import com.km.report.service.ReportFileStorageService;
import com.km.report.service.ReportAccessService;
import com.km.report.service.ReportTemplateService;
import com.km.report.utils.ChapterTreeUtil;
import com.km.report.vo.FileUploadVO;
import com.km.report.vo.ChapterTreeVO;
import com.km.report.vo.TemplateVO;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportTemplateServiceImpl extends ServiceImpl<ReportTemplateMapper, ReportTemplate> implements ReportTemplateService {

    @Resource
    private ReportTemplateChapterMapper chapterMapper;

    @Resource
    private ReportRecordMapper reportRecordMapper;

    @Resource
    private ReportFileStorageService reportFileStorageService;

    @Resource
    private ReportExportProperties reportExportProperties;
    @Resource
    private ReportAccessService reportAccessService;

    @Override
    public Page<TemplateVO> pageTemplates(TemplateQueryDTO queryDTO) {
        TemplateQueryDTO query = queryDTO == null ? new TemplateQueryDTO() : queryDTO;
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        Page<ReportTemplate> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getKeyword()), ReportTemplate::getTemplateName, query.getKeyword())
                .eq(StringUtils.hasText(query.getReportType()), ReportTemplate::getReportType, query.getReportType())
                .eq(query.getStatus() != null, ReportTemplate::getStatus, query.getStatus())
                .and(w -> w.eq(ReportTemplate::getTemplateScope, "GLOBAL")
                        .or()
                        .eq(ReportTemplate::getCreatorId, reportAccessService.currentUserId()))
                .orderByDesc(ReportTemplate::getUpdateTime);

        Page<ReportTemplate> resultPage = this.page(page, wrapper);
        Page<TemplateVO> voPage = new Page<>();
        BeanUtils.copyProperties(resultPage, voPage);
        voPage.setRecords(resultPage.getRecords().stream().map(this::convertToVO).collect(Collectors.toList()));
        return voPage;
    }

    @Override
    public List<TemplateVO> listVisible(String reportType) {
        LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(reportType), ReportTemplate::getReportType, reportType)
                .and(w -> w.eq(ReportTemplate::getTemplateScope, "GLOBAL")
                        .or()
                        .eq(ReportTemplate::getCreatorId, reportAccessService.currentUserId()))
                .orderByDesc(ReportTemplate::getUpdateTime);
        return this.list(wrapper).stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public TemplateVO getTemplateVO(Long id) {
        return convertToVO(getTemplateById(id));
    }

    @Override
    public Long addTemplate(TemplateSaveDTO saveDTO) {
        if (saveDTO == null || !StringUtils.hasText(saveDTO.getTemplateName())) {
            throw new BizException("模板不存在");
        }
        String templateName = saveDTO.getTemplateName().trim();
        String reportType = StringUtils.hasText(saveDTO.getReportType()) ? saveDTO.getReportType().trim() : "";
        String templateScope = StringUtils.hasText(saveDTO.getTemplateScope()) ? saveDTO.getTemplateScope().trim() : "GLOBAL";
        String currentUserId = reportAccessService.currentUserId();

        LambdaQueryWrapper<ReportTemplate> duplicateWrapper = Wrappers.<ReportTemplate>lambdaQuery()
                .eq(ReportTemplate::getTemplateName, templateName)
                .eq(ReportTemplate::getReportType, reportType)
                .eq(ReportTemplate::getTemplateScope, templateScope);
        if (!"GLOBAL".equalsIgnoreCase(templateScope)) {
            duplicateWrapper.eq(ReportTemplate::getCreatorId, currentUserId);
        }
        ReportTemplate exist = this.getOne(duplicateWrapper.last("LIMIT 1"));
        if (exist != null) {
            if (currentUserId.equals(exist.getCreatorId())) {
                updateTemplateMeta(exist, saveDTO);
            }
            return exist.getId();
        }

        ReportTemplate template = new ReportTemplate();
        BeanUtils.copyProperties(saveDTO, template);
        template.setTemplateName(templateName);
        template.setReportType(reportType);
        template.setTemplateScope(templateScope);
        template.setStatus(0);
        template.setChapterCount(0);
        template.setCreatorId(currentUserId);
        template.setCreateTime(LocalDateTime.now());
        template.setUpdateTime(LocalDateTime.now());
        template.setDeleted(0);
        try {
            this.save(template);
        } catch (DuplicateKeyException duplicateKeyException) {
            LambdaQueryWrapper<ReportTemplate> existingWrapper = Wrappers.<ReportTemplate>lambdaQuery()
                    .eq(ReportTemplate::getTemplateName, templateName)
                    .eq(ReportTemplate::getReportType, reportType)
                    .eq(ReportTemplate::getTemplateScope, templateScope);
            if (!"GLOBAL".equalsIgnoreCase(templateScope)) {
                existingWrapper.eq(ReportTemplate::getCreatorId, currentUserId);
            }
            ReportTemplate existing = this.getOne(existingWrapper.last("LIMIT 1"));
            if (existing != null) {
                if (currentUserId.equals(existing.getCreatorId())) {
                    updateTemplateMeta(existing, saveDTO);
                }
                return existing.getId();
            }
            throw duplicateKeyException;
        }
        return template.getId();
    }

    @Override
    public void updateTemplate(TemplateSaveDTO saveDTO) {
        if (saveDTO == null || saveDTO.getId() == null) {
            throw new BizException("模板不存在");
        }
        ReportTemplate template = reportAccessService.requireOwnedTemplate(saveDTO.getId());
        updateTemplateMeta(template, saveDTO);
        this.updateById(template);
    }

    @Override
    public void deleteTemplate(Long id) {
        reportAccessService.requireOwnedTemplate(id);
        Long count = reportRecordMapper.selectCount(new LambdaQueryWrapper<ReportRecord>()
                .eq(ReportRecord::getTemplateId, id)
                .eq(ReportRecord::getUserId, reportAccessService.currentUserId()));
        if (count > 0) {
            throw new BizException("模板不存在");
        }
        this.removeById(id);
        chapterMapper.delete(new LambdaQueryWrapper<ReportTemplateChapter>().eq(ReportTemplateChapter::getTemplateId, id));
    }

    @Override
    public void publishTemplate(Long id) {
        ReportTemplate template = reportAccessService.requireOwnedTemplate(id);
        long chapterCount = chapterMapper.selectCount(new LambdaQueryWrapper<ReportTemplateChapter>().eq(ReportTemplateChapter::getTemplateId, id));
        if (chapterCount == 0) {
            throw new BizException("模板没有章节，无法发布");
        }
        template.setStatus(1);
        template.setChapterCount((int) chapterCount);
        template.setUpdateTime(LocalDateTime.now());
        this.updateById(template);
    }

    @Override
    public void offlineTemplate(Long id) {
        ReportTemplate template = reportAccessService.requireOwnedTemplate(id);
        template.setStatus(2);
        template.setUpdateTime(LocalDateTime.now());
        this.updateById(template);
    }

    @Override
    public List<ChapterTreeVO> getChapterTree(Long templateId) {
        reportAccessService.requireVisibleTemplate(templateId);
        List<ReportTemplateChapter> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<ReportTemplateChapter>()
                        .eq(ReportTemplateChapter::getTemplateId, templateId)
                        .orderByAsc(ReportTemplateChapter::getSort)
        );
        return ChapterTreeUtil.buildTree(chapters);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveChapterTree(ChapterTreeSaveDTO saveDTO) {
        if (saveDTO == null || saveDTO.getTemplateId() == null) {
            throw new BizException("模板不存在");
        }
        ReportTemplate template = reportAccessService.requireOwnedTemplate(saveDTO.getTemplateId());
        chapterMapper.delete(new LambdaQueryWrapper<ReportTemplateChapter>().eq(ReportTemplateChapter::getTemplateId, saveDTO.getTemplateId()));
        if (saveDTO.getChapters() != null && !saveDTO.getChapters().isEmpty()) {
            batchInsertChapters(saveDTO.getTemplateId(), 0L, "", saveDTO.getChapters());
        }
        int count = chapterMapper.selectCount(new LambdaQueryWrapper<ReportTemplateChapter>().eq(ReportTemplateChapter::getTemplateId, saveDTO.getTemplateId())).intValue();
        template.setChapterCount(count);
        template.setUpdateTime(LocalDateTime.now());
        this.updateById(template);
    }

    @Override
    public TemplateVO uploadTemplateFile(Long templateId, MultipartFile file) {
        ReportTemplate template = reportAccessService.requireOwnedTemplate(templateId);
        FileUploadVO uploaded = reportFileStorageService.store(file, reportExportProperties.getTemplateDir());
        template.setOriginalFileName(uploaded.getOriginalFileName());
        template.setFileUrl(uploaded.getFileUrl());
        template.setFileSize(uploaded.getFileSize());
        template.setUpdateTime(LocalDateTime.now());
        this.updateById(template);
        return convertToVO(template);
    }

    private void updateTemplateMeta(ReportTemplate template, TemplateSaveDTO saveDTO) {
        if (StringUtils.hasText(saveDTO.getTemplateName())) {
            template.setTemplateName(saveDTO.getTemplateName().trim());
        }
        if (StringUtils.hasText(saveDTO.getReportType())) {
            template.setReportType(saveDTO.getReportType().trim());
        }
        if (saveDTO.getDescription() != null) {
            template.setDescription(saveDTO.getDescription());
        }
        if (StringUtils.hasText(saveDTO.getTemplateScope())) {
            template.setTemplateScope(saveDTO.getTemplateScope().trim());
        }
        if (saveDTO.getStyleConfig() != null) {
            template.setStyleConfig(saveDTO.getStyleConfig());
        }
        if (StringUtils.hasText(saveDTO.getOriginalFileName())) {
            template.setOriginalFileName(saveDTO.getOriginalFileName().trim());
        }
        if (saveDTO.getFileUrl() != null) {
            template.setFileUrl(saveDTO.getFileUrl());
        }
        if (saveDTO.getFileSize() != null) {
            template.setFileSize(saveDTO.getFileSize());
        }
        template.setUpdateTime(LocalDateTime.now());
    }

    private void batchInsertChapters(Long templateId, Long parentId, String parentNo, List<ChapterNodeDTO> nodes) {
        for (int index = 0; index < nodes.size(); index++) {
            ChapterNodeDTO node = nodes.get(index);
            String chapterNo = parentNo.isEmpty() ? String.valueOf(index + 1) : parentNo + "." + (index + 1);
            ReportTemplateChapter chapter = new ReportTemplateChapter();
            chapter.setTemplateId(templateId);
            chapter.setParentId(parentId);
            chapter.setChapterTitle(node.getChapterTitle());
            chapter.setChapterNo(chapterNo);
            chapter.setChapterType(StringUtils.hasText(node.getChapterType()) ? node.getChapterType() : "TEXT");
            chapter.setLevel(node.getLevel() == null ? 1 : node.getLevel());
            chapter.setSort(node.getSort() == null ? index + 1 : node.getSort());
            chapter.setDefaultContent(node.getDefaultContent());
            chapter.setWritingPrompt(node.getWritingPrompt());
            chapter.setRequiredFlag(1);
            chapter.setCreateTime(LocalDateTime.now());
            chapter.setUpdateTime(LocalDateTime.now());
            chapter.setDeleted(0);
            chapterMapper.insert(chapter);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                batchInsertChapters(templateId, chapter.getId(), chapterNo, node.getChildren());
            }
        }
    }

    private ReportTemplate getTemplateById(Long id) {
        ReportTemplate template = reportAccessService.requireVisibleTemplate(id);
        if (template == null) {
            throw new BizException("模板不存在");
        }
        return template;
    }

    private TemplateVO convertToVO(ReportTemplate entity) {
        TemplateVO vo = new TemplateVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}




