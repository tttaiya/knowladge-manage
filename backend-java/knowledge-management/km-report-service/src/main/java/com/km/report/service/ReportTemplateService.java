package com.km.report.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.km.report.dto.ChapterTreeSaveDTO;
import com.km.report.dto.TemplateQueryDTO;
import com.km.report.dto.TemplateSaveDTO;
import com.km.report.entity.ReportTemplate;
import com.km.report.vo.ChapterTreeVO;
import com.km.report.vo.TemplateVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReportTemplateService extends IService<ReportTemplate> {
    Page<TemplateVO> pageTemplates(TemplateQueryDTO queryDTO);

    TemplateVO getTemplateVO(Long id);

    List<TemplateVO> listVisible(String reportType);

    Long addTemplate(TemplateSaveDTO saveDTO);

    void updateTemplate(TemplateSaveDTO saveDTO);

    void deleteTemplate(Long id);

    void publishTemplate(Long id);

    void offlineTemplate(Long id);

    List<ChapterTreeVO> getChapterTree(Long templateId);

    void saveChapterTree(ChapterTreeSaveDTO saveDTO);

    TemplateVO uploadTemplateFile(Long templateId, MultipartFile file);
}
