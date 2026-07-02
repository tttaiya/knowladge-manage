package com.km.report.service.impl;

import com.km.report.common.exception.BizException;
import com.km.report.config.ReportExportProperties;
import com.km.report.config.ReportStorageProperties;
import com.km.report.service.ReportAccessService;
import com.km.report.service.ReportFileStorageService;
import com.km.report.vo.FileUploadVO;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class ReportFileStorageServiceImpl implements ReportFileStorageService {

    @Resource
    private ReportExportProperties reportExportProperties;
    @Resource
    private ReportStorageProperties reportStorageProperties;
    @Resource
    private ReportAccessService reportAccessService;

    private volatile MinioClient minioClient;

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
        String objectKey = buildObjectKey(bizDir, ext);
        String contentType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "application/octet-stream";
        String bucket = reportStorageProperties.getMaterialsBucket();
        try (InputStream inputStream = file.getInputStream()) {
            putObject(bucket, objectKey, inputStream, file.getSize(), contentType);
        } catch (Exception e) {
            throw new BizException("文件保存到 MinIO 失败：" + e.getMessage());
        }
        return buildUploadVO(safeOriginalName, objectKey, bucket, ext, file.getSize());
    }

    @Override
    public FileUploadVO storeBytes(byte[] content, String originalFileName, String contentType, String bucket, String bizDir) {
        if (content == null || content.length == 0) {
            throw new BizException("文件内容不能为空");
        }
        String safeOriginalName = sanitizeFileName(StringUtils.hasText(originalFileName) ? originalFileName : "unnamed");
        String ext = getExt(safeOriginalName);
        String targetBucket = StringUtils.hasText(bucket) ? bucket : reportStorageProperties.getExportsBucket();
        String objectKey = buildObjectKey(bizDir, ext);
        try (InputStream inputStream = new ByteArrayInputStream(content)) {
            putObject(targetBucket, objectKey, inputStream, content.length,
                    StringUtils.hasText(contentType) ? contentType : "application/octet-stream");
        } catch (Exception e) {
            throw new BizException("文件保存到 MinIO 失败：" + e.getMessage());
        }
        return buildUploadVO(safeOriginalName, objectKey, targetBucket, ext, (long) content.length);
    }

    @Override
    public InputStream open(String bucket, String objectKey) {
        if (!StringUtils.hasText(bucket) || !StringUtils.hasText(objectKey) || objectKey.contains("..")) {
            throw new BizException("非法文件路径");
        }
        try {
            return client().getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new BizException("文件读取失败：" + e.getMessage());
        }
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
        String bucket = reportStorageProperties.getExportsBucket();
        String objectKey = relativePath;
        int slash = relativePath.indexOf('/');
        if (slash > 0 && (relativePath.startsWith(reportStorageProperties.getMaterialsBucket() + "/")
                || relativePath.startsWith(reportStorageProperties.getExportsBucket() + "/"))) {
            bucket = relativePath.substring(0, slash);
            objectKey = relativePath.substring(slash + 1);
        }
        String userPrefix = sanitizePathPart(reportAccessService.currentUserId()) + "/";
        if (!objectKey.startsWith(userPrefix)) {
            throw new BizException("无权下载该文件");
        }
        try (InputStream inputStream = open(bucket, objectKey);
             OutputStream outputStream = response.getOutputStream()) {
            String fileName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
            String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replaceAll("\\+", "%20");
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
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

    private void putObject(String bucket, String objectKey, InputStream inputStream, long size, String contentType) throws Exception {
        ensureBucket(bucket);
        client().putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .stream(inputStream, size, -1)
                .contentType(contentType)
                .build());
    }

    private void ensureBucket(String bucket) throws Exception {
        boolean exists = client().bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private MinioClient client() {
        if (minioClient == null) {
            synchronized (this) {
                if (minioClient == null) {
                    minioClient = MinioClient.builder()
                            .endpoint(reportStorageProperties.getEndpoint())
                            .credentials(reportStorageProperties.getAccessKey(), reportStorageProperties.getSecretKey())
                            .build();
                }
            }
        }
        return minioClient;
    }

    private FileUploadVO buildUploadVO(String originalFileName, String objectKey, String bucket, String ext, Long size) {
        FileUploadVO vo = new FileUploadVO();
        vo.setOriginalFileName(originalFileName);
        vo.setFileName(objectKey.substring(objectKey.lastIndexOf('/') + 1));
        vo.setFileExt(ext);
        vo.setFilePath(objectKey);
        vo.setBucket(bucket);
        vo.setObjectKey(objectKey);
        vo.setFileUrl(reportExportProperties.getUrlPrefix() + "/" + bucket + "/" + objectKey);
        vo.setFileSize(size);
        return vo;
    }

    private String buildObjectKey(String bizDir, String ext) {
        String userId = reportAccessService.currentUserId();
        String dateDir = LocalDate.now().toString().replace("-", "");
        String safeBizDir = sanitizePathPart(StringUtils.hasText(bizDir) ? bizDir : "common");
        String fileName = UUID.randomUUID().toString().replace("-", "") + (StringUtils.hasText(ext) ? "." + ext : "");
        return sanitizePathPart(userId) + "/" + safeBizDir + "/" + dateDir + "/" + fileName;
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
