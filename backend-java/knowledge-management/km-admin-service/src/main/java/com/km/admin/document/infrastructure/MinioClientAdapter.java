package com.km.admin.document.infrastructure;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * MinIO 客户端适配。R10：URLEncoder 等不涉及这里，全用 Java 8 兼容 API。
 */
@Component
public class MinioClientAdapter {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    public MinioClientAdapter(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 生成 MinIO 对象路径：kb/{kbId}/{yyyyMM}/{uuid}.{ext}
     */
    public String generateObjectPath(Long kbId, String extension) {
        String month = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String ext = extension.startsWith(".") ? extension.substring(1) : extension;
        return String.format("kb/%d/%s/%s.%s", kbId, month, uuid, ext.toLowerCase());
    }

    public void upload(MultipartFile file, String objectPath, String contentType) throws Exception {
        ensureBucket();
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectPath)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
        }
    }

    public InputStream download(String objectPath) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectPath)
                        .build()
        );
    }

    public void delete(String objectPath) throws Exception {
        minioClient.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectPath)
                        .build()
        );
    }

    private void ensureBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(bucket).build()
        );
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
