package com.km.worker.purge;

import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * F4 整合（commit #24）：MinIO 对象删除客户端（抽出独立文件，硬规则要求）。
 *
 * R8 必改 4：Worker PURGE 链路必须删 MinIO。
 * - 404 / NoSuchKey 视为幂等成功（可能之前已删过）
 * - 网络超时、5xx、鉴权失败等其它异常视为失败
 */
@Service
public class MinioPurgeClient {
    private static final Logger log = LoggerFactory.getLogger(MinioPurgeClient.class);

    private final MinioClient minioClient;
    @Value("${minio.bucket}")
    private String bucket;

    public MinioPurgeClient(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    public boolean deleteObject(String objectKey) {
        if (objectKey == null || objectKey.isEmpty()) {
            return true;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if (e.errorResponse() != null && "NoSuchKey".equals(e.errorResponse().code())) {
                return true;
            }
            log.warn("MinioPurgeClient deleteObject failed for {}: {}", objectKey, e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("MinioPurgeClient deleteObject failed for {}: {}", objectKey, e.getMessage());
            return false;
        }
    }
}