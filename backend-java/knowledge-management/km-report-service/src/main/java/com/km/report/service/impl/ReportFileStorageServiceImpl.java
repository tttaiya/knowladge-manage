package com.km.report.service.impl;

import com.km.report.common.exception.BizException;
import com.km.report.config.ReportExportProperties;
import com.km.report.service.ReportFileStorageService;
import com.km.report.vo.FileUploadVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ReportFileStorageServiceImpl implements ReportFileStorageService {

    @Resource
    private ReportExportProperties reportExportProperties;

    @Override
    public FileUploadVO store(MultipartFile file, String bizDir) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }

        String originalFileName = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFileName)) {
            originalFileName = "unnamed";
        }

        String safeOriginalName = sanitizeFileName(originalFileName);
        String ext = getExt(safeOriginalName);
        String dateDir = LocalDate.now().toString().replace("-", "");
        String safeBizDir = sanitizePathPart(StringUtils.hasText(bizDir) ? bizDir : "common");
        String fileName = UUID.randomUUID().toString().replace("-", "") + (StringUtils.hasText(ext) ? "." + ext : "");
        String relativePath = safeBizDir + "/" + dateDir + "/" + fileName;

        File targetFile = new File(reportExportProperties.getBaseDir(), relativePath);
        File parent = targetFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new BizException("创建上传目录失败");
        }

        try {
            file.transferTo(targetFile);
        } catch (Exception e) {
            throw new BizException("文件保存失败：" + e.getMessage());
        }

        FileUploadVO vo = new FileUploadVO();
        vo.setOriginalFileName(safeOriginalName);
        vo.setFileName(fileName);
        vo.setFileExt(ext);
        vo.setFilePath(relativePath.replace("\\", "/"));
        vo.setFileUrl(reportExportProperties.getUrlPrefix() + "/" + vo.getFilePath());
        vo.setFileSize(file.getSize());
        return vo;
    }

    @Override
    public void download(String relativePath, HttpServletResponse response) {
        if (!StringUtils.hasText(relativePath) || relativePath.contains("..")) {
            throw new BizException("非法文件路径");
        }

        try {
            relativePath = URLDecoder.decode(relativePath, "UTF-8");
        } catch (Exception ignored) {
        }

        File file = resolveFile(relativePath);
        if (!file.exists() || !file.isFile()) {
            throw new BizException("文件不存在");
        }

        try (
                BufferedInputStream inputStream = new BufferedInputStream(new FileInputStream(file));
                OutputStream outputStream = response.getOutputStream()
        ) {
            String encodedFileName = URLEncoder.encode(file.getName(), "UTF-8").replaceAll("\\+", "%20");
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
            response.setHeader("Content-Length", String.valueOf(file.length()));

            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
        } catch (Exception e) {
            throw new BizException("文件下载失败：" + e.getMessage());
        }
    }

    private File resolveFile(String relativePath) {
        File baseDir = new File(reportExportProperties.getBaseDir());
        File primary = new File(baseDir, relativePath);
        if (primary.exists()) {
            return primary;
        }

        File parent = baseDir.getParentFile();
        if (parent != null) {
            File legacyRoot = new File(parent, "report-service/report-files");
            File legacy = new File(legacyRoot, relativePath);
            if (legacy.exists()) {
                return legacy;
            }
        }

        return primary;
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String sanitizePathPart(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String getExt(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1).toLowerCase();
    }
}
