package com.km.worker.filestaging;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * F4 整合（commit #23）：Worker 从 MinIO 下载任务源文件到 task-files 共享卷，
 * FastAPI 只读挂载同一卷，接收容器内绝对路径。
 *
 * 关键约束（v1.0 文档第六章）：
 * - 不允许把 MinIO object key（如 kb/1/doc/12/a.pdf）直接传给 FastAPI
 * - filePath 在 FastAPI 侧必须位于 ALLOWED_DOCUMENT_ROOT（/data/task-files）下
 * - 每个任务 finally 清理自己的暂存目录
 * - 大小上限 50MB（MAX_FILE_SIZE_MB）；Worker 侧 STAGING 阶段异常 errorStage="STAGING"
 */
@Service
public class TaskFileStagingService {

    private static final Logger log = LoggerFactory.getLogger(TaskFileStagingService.class);
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB

    private final MinioClient minioClient;
    private final String bucket;
    private final Path taskFileRoot;

    public TaskFileStagingService(
            @Value("${minio.endpoint}") String endpoint,
            @Value("${minio.access-key}") String accessKey,
            @Value("${minio.secret-key}") String secretKey,
            @Value("${minio.bucket}") String bucket,
            @Value("${km.task-file-root:/data/task-files}") String taskFileRoot) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
        this.taskFileRoot = Paths.get(taskFileRoot);
    }

    /**
     * 下载任务源文件到 task-file-root/{taskId}/source.{extension}。
     * 返回容器内绝对路径（FastAPI 侧 ALLOWED_DOCUMENT_ROOT 必须挂载到同一目录）。
     *
     * @throws IllegalStateException STAGING 阶段错误（errorStage=STAGING）
     */
    public String stage(Long taskId, String objectKey, String extension) {
        if (objectKey == null || objectKey.isEmpty()) {
            throw new IllegalStateException("STAGING: objectKey is empty");
        }
        if (extension == null || extension.isEmpty()) {
            throw new IllegalStateException("STAGING: extension is empty");
        }
        Path taskDir = taskFileRoot.resolve(String.valueOf(taskId));
        try {
            Files.createDirectories(taskDir);
        } catch (IOException e) {
            throw new IllegalStateException("STAGING: cannot create task dir " + taskDir, e);
        }
        Path target = taskDir.resolve("source." + extension);

        try {
            // 1. 校验大小（先 stat 再下载，避免下载大文件后才发现超限）
            io.minio.StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            if (stat.size() > MAX_FILE_SIZE) {
                throw new IllegalStateException(
                        "STAGING: file too large: " + stat.size() + " bytes (max " + MAX_FILE_SIZE + ")");
            }

            // 2. 下载（try-with-resources 保证 InputStream 关闭）
            try (InputStream in = minioClient.getObject(
                    GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("TaskFileStagingService staged taskId={} objectKey={} -> {} ({} bytes)",
                    taskId, objectKey, target, Files.size(target));
            return target.toAbsolutePath().toString();
        } catch (ErrorResponseException e) {
            String code = e.errorResponse() != null ? e.errorResponse().code() : "unknown";
            throw new IllegalStateException(
                    "STAGING: minio getObject failed for objectKey=" + objectKey + ", code=" + code, e);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "STAGING: failed to write " + target + ": " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "STAGING: unexpected error staging taskId=" + taskId + ": " + e.getMessage(), e);
        }
    }

    /**
     * 清理任务暂存目录（在 finally 块调用，删除失败不抛异常）。
     */
    public void cleanup(Long taskId) {
        if (taskId == null) return;
        Path taskDir = taskFileRoot.resolve(String.valueOf(taskId));
        if (!Files.exists(taskDir)) return;
        try (java.util.stream.Stream<Path> stream = Files.walk(taskDir)) {
            stream.sorted((a, b) -> b.compareTo(a))   // 先删文件再删目录
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                            // cleanup best-effort，不影响主流程
                        }
                    });
            log.debug("TaskFileStagingService cleaned up taskId={}", taskId);
        } catch (IOException e) {
            log.warn("TaskFileStagingService cleanup failed for taskId={}: {}", taskId, e.getMessage());
        }
    }
}