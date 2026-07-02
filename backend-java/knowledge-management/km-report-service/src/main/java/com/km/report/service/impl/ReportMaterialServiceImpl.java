package com.km.report.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.km.report.common.context.LoginUserContext;
import com.km.report.common.exception.BizException;
import com.km.report.config.ReportExportProperties;
import com.km.report.dto.MaterialQueryDTO;
import com.km.report.dto.UploadMaterialRequest;
import com.km.report.entity.ReportMaterial;
import com.km.report.mapper.ReportMaterialMapper;
import com.km.report.service.ReportFileStorageService;
import com.km.report.service.ReportMaterialService;
import com.km.report.vo.FileUploadVO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportMaterialServiceImpl extends ServiceImpl<ReportMaterialMapper, ReportMaterial> implements ReportMaterialService {

    @Resource
    private ReportFileStorageService reportFileStorageService;

    @Resource
    private ReportExportProperties reportExportProperties;

    @Override
    public Page<ReportMaterial> pageMaterials(MaterialQueryDTO queryDTO) {
        MaterialQueryDTO query = queryDTO == null ? new MaterialQueryDTO() : queryDTO;
        int pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1 : query.getPageNum();
        int pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 10 : Math.min(query.getPageSize(), 100);
        LambdaQueryWrapper<ReportMaterial> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getKeyword()), ReportMaterial::getMaterialName, query.getKeyword())
                .eq(StringUtils.hasText(query.getMaterialType()), ReportMaterial::getMaterialType, query.getMaterialType())
                .eq(StringUtils.hasText(query.getReportType()), ReportMaterial::getReportType, query.getReportType())
                .eq(StringUtils.hasText(query.getMajor()), ReportMaterial::getMajor, query.getMajor())
                .eq(StringUtils.hasText(query.getPowerPlant()), ReportMaterial::getPowerPlant, query.getPowerPlant())
                .eq(query.getReportYear() != null, ReportMaterial::getReportYear, query.getReportYear())
                .orderByDesc(ReportMaterial::getCreateTime);
        return this.page(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public ReportMaterial uploadMaterial(MultipartFile file, UploadMaterialRequest request) {
        UploadMaterialRequest uploadRequest = request == null ? new UploadMaterialRequest() : request;
        FileUploadVO uploaded = reportFileStorageService.store(file, reportExportProperties.getMaterialDir());

        ReportMaterial material = new ReportMaterial();
        material.setMaterialName(StringUtils.hasText(uploadRequest.getMaterialName()) ? uploadRequest.getMaterialName() : uploaded.getOriginalFileName());
        material.setMaterialType(StringUtils.hasText(uploadRequest.getMaterialType()) ? uploadRequest.getMaterialType() : inferMaterialType(uploaded.getFileExt()));
        material.setReportType(uploadRequest.getReportType());
        material.setMajor(uploadRequest.getMajor());
        material.setPowerPlant(uploadRequest.getPowerPlant());
        material.setReportYear(uploadRequest.getReportYear());
        material.setOriginalFileName(uploaded.getOriginalFileName());
        material.setFileUrl(uploaded.getFileUrl());
        material.setFilePath(uploaded.getFilePath());
        material.setFileExt(uploaded.getFileExt());
        material.setFileSize(uploaded.getFileSize());
        material.setParseStatus("PENDING");
        material.setCreatorId(LoginUserContext.getUserId() == null ? 0L : LoginUserContext.getUserId());
        material.setRemark(uploadRequest.getRemark());
        material.setCreateTime(LocalDateTime.now());
        material.setUpdateTime(LocalDateTime.now());
        material.setDeleted(0);
        this.save(material);
        return parseMaterial(material.getId());
    }

    @Override
    public ReportMaterial parseMaterial(Long materialId) {
        ReportMaterial material = this.getById(materialId);
        if (material == null) {
            throw new BizException("素材不存在");
        }
        File file = new File(reportExportProperties.getBaseDir(), material.getFilePath());
        if (!file.exists()) {
            throw new BizException("素材文件不存在");
        }
        try {
            String structuredData;
            if ("csv".equalsIgnoreCase(material.getFileExt())) {
                structuredData = parseCsv(file);
            } else if ("xlsx".equalsIgnoreCase(material.getFileExt())) {
                structuredData = parseXlsx(file);
            } else if ("txt".equalsIgnoreCase(material.getFileExt())) {
                structuredData = parseTxt(file);
            } else if ("docx".equalsIgnoreCase(material.getFileExt())) {
                structuredData = parseDocx(file);
            } else {
                structuredData = "{\"type\":\"file\",\"message\":\"暂不支持该文件类型自动解析\"}";
            }
            material.setStructuredData(structuredData);
            material.setParseStatus("SUCCESS");
        } catch (Exception e) {
            material.setParseStatus("FAILED");
            material.setStructuredData("{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
        material.setUpdateTime(LocalDateTime.now());
        this.updateById(material);
        return material;
    }

    private String parseCsv(File file) throws Exception {
        List<String> rows;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            rows = reader.lines().collect(Collectors.toList());
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{\"type\":\"table\",\"headers\":");
        if (rows.isEmpty()) {
            builder.append("[],\"rows\":[]}");
            return builder.toString();
        }
        builder.append(toJsonArray(splitCsv(rows.get(0)))).append(",\"rows\":[");
        for (int index = 1; index < rows.size(); index++) {
            if (index > 1) {
                builder.append(",");
            }
            builder.append(toJsonArray(splitCsv(rows.get(index))));
        }
        builder.append("]}");
        return builder.toString();
    }

    private String parseXlsx(File file) throws Exception {
        try (Workbook workbook = new XSSFWorkbook(new FileInputStream(file))) {
            DataFormatter formatter = new DataFormatter();
            Sheet sheet = workbook.getSheetAt(0);
            List<List<String>> rows = new ArrayList<>();
            int maxRows = Math.min(sheet.getLastRowNum(), 200);
            for (int rowIndex = 0; rowIndex <= maxRows; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
                    Cell cell = row.getCell(cellIndex);
                    values.add(formatter.formatCellValue(cell));
                }
                rows.add(values);
            }
            StringBuilder builder = new StringBuilder();
            builder.append("{\"type\":\"table\",\"headers\":");
            if (rows.isEmpty()) {
                builder.append("[],\"rows\":[]}");
                return builder.toString();
            }
            builder.append(toJsonArray(rows.get(0))).append(",\"rows\":[");
            for (int index = 1; index < rows.size(); index++) {
                if (index > 1) {
                    builder.append(",");
                }
                builder.append(toJsonArray(rows.get(index)));
            }
            builder.append("]}");
            return builder.toString();
        }
    }

    private String parseTxt(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String text = reader.lines().limit(300).collect(Collectors.joining("\n"));
            return "{\"type\":\"text\",\"content\":\"" + escapeJson(text) + "\"}";
        }
    }

    private String parseDocx(File file) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new FileInputStream(file))) {
            String text = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(StringUtils::hasText)
                    .limit(300)
                    .collect(Collectors.joining("\n"));
            return "{\"type\":\"text\",\"content\":\"" + escapeJson(text) + "\"}";
        }
    }

    private List<String> splitCsv(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                quoted = !quoted;
            } else if (ch == ',' && !quoted) {
                result.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        result.add(current.toString());
        return result;
    }

    private String toJsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + escapeJson(value) + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String inferMaterialType(String ext) {
        if ("csv".equalsIgnoreCase(ext) || "xlsx".equalsIgnoreCase(ext)) {
            return "TABLE";
        }
        if ("png".equalsIgnoreCase(ext) || "jpg".equalsIgnoreCase(ext) || "jpeg".equalsIgnoreCase(ext)) {
            return "IMAGE";
        }
        return "DOCUMENT";
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "").replace("\n", "\\n");
    }
}
