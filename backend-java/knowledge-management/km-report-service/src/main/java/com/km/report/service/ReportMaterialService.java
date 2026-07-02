package com.km.report.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.km.report.dto.MaterialQueryDTO;
import com.km.report.dto.UploadMaterialRequest;
import com.km.report.entity.ReportMaterial;
import org.springframework.web.multipart.MultipartFile;

public interface ReportMaterialService extends IService<ReportMaterial> {

    Page<ReportMaterial> pageMaterials(MaterialQueryDTO queryDTO);

    ReportMaterial uploadMaterial(MultipartFile file, UploadMaterialRequest request);

    ReportMaterial parseMaterial(Long materialId);
}
