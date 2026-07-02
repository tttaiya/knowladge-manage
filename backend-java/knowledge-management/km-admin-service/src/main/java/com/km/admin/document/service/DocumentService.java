package com.km.admin.document.service;

import com.km.admin.DocumentTaskFacade;
import com.km.admin.common.PageResult;
import com.km.admin.document.entity.KmDocument;
import com.km.admin.document.infrastructure.MinioClientAdapter;
import com.km.admin.document.mapper.DocumentManageMapper;
import com.km.admin.document.mapper.DocumentTagMapper;
import com.km.admin.knowledgebase.mapper.KnowledgeBaseMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文档业务服务。
 *
 * <p>R4：不拆接口 + 实现类，保持程雨彤原始风格。
 * <p>R6：上传完成后调 DocumentTaskFacade.createProcessTask()（同进程，跨子包）。
 * <p>R14：uploaderUserId 是 String UUID。
 * <p>R10：Java 8 兼容（无 List.of / isBlank / var）。
 *
 * <p>不再使用 JdbcTemplate，全部走 MyBatis（review 模块已统一）。
 */
@Service
public class DocumentService {

    private static final String STATUS_UPLOADED = "UPLOADED";

    private final DocumentManageMapper documentMapper;
    private final DocumentTagMapper documentTagMapper;
    private final MinioClientAdapter minioClientAdapter;
    private final RecycleBinService recycleBinService;
    private final DocumentTaskFacade documentTaskFacade;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Value("${km.upload.max-file-size-mb:50}")
    private long maxFileSizeMb;

    @Value("${km.upload.max-batch-count:10}")
    private int maxBatchCount;

    @Value("${km.upload.allowed-extensions}")
    private String allowedExtensions;

    public DocumentService(DocumentManageMapper documentMapper,
                           DocumentTagMapper documentTagMapper,
                           MinioClientAdapter minioClientAdapter,
                           RecycleBinService recycleBinService,
                           DocumentTaskFacade documentTaskFacade,
                           KnowledgeBaseMapper knowledgeBaseMapper) {
        this.documentMapper = documentMapper;
        this.documentTagMapper = documentTagMapper;
        this.minioClientAdapter = minioClientAdapter;
        this.recycleBinService = recycleBinService;
        this.documentTaskFacade = documentTaskFacade;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    public PageResult<KmDocument> listDocuments(Long kbId, String status, String keyword,
                                                int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<KmDocument> records = documentMapper.selectByKbId(kbId, status, keyword, 0, offset, pageSize);
        enrichTags(records);

        PageResult<KmDocument> result = new PageResult<KmDocument>();
        result.setRecords(records);
        result.setTotal(documentMapper.countByKbId(kbId, status, keyword, 0));
        result.setPage(page);
        result.setPageSize(pageSize);
        return result;
    }

    public KmDocument getDocument(Long id) {
        KmDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        List<String> tags = documentTagMapper.selectTagNamesByDocId(id);
        doc.setTags(tags);
        return doc;
    }

    /**
     * 上传文档。R6：上传成功后调 DocumentTaskFacade 创建 PROCESS 任务。
     * R10：Java 8 兼容，List.of / isBlank 全部替换。
     */
    @Transactional
    public List<KmDocument> uploadDocuments(Long kbId, MultipartFile[] files, List<String> tags,
                                            String uploaderUserId, String uploaderName) throws Exception {
        validateBatch(files);

        List<KmDocument> uploaded = new ArrayList<KmDocument>();
        List<String> normalizedTags = normalizeTags(tags);
        validateTags(normalizedTags);

        for (MultipartFile file : files) {
            validateFile(file);

            String originalName = file.getOriginalFilename();
            String extension = extractExtension(originalName);
            String objectPath = minioClientAdapter.generateObjectPath(kbId, extension);
            String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

            minioClientAdapter.upload(file, objectPath, contentType);

            KmDocument document = new KmDocument();
            document.setKbId(kbId);
            document.setOriginalName(originalName);
            document.setExtension(extension);
            document.setMimeType(contentType);
            document.setFilePath(objectPath);
            document.setFileSize(file.getSize());
            document.setFileHash(calculateSha256(file));
            document.setStatus(STATUS_UPLOADED);
            document.setUploaderUserId(uploaderUserId);
            document.setUploaderName(uploaderName);

            documentMapper.insert(document);

            if (!normalizedTags.isEmpty()) {
                documentTagMapper.batchInsert(document.getId(), normalizedTags);
            }

            uploaded.add(document);

            documentCountOnCreated(kbId);

            // R6：上传成功后创建 PROCESS 任务（同进程跨子包调 facade）
            if (document.getId() != null && uploaderUserId != null) {
                documentTaskFacade.createProcessTask(document.getId(), uploaderUserId);
            }
        }

        return uploaded;
    }

    @Transactional
    public void updateTags(Long docId, List<String> tags) {
        KmDocument doc = getDocument(docId);
        if (doc.getIsDeleted() != null && doc.getIsDeleted() == 1) {
            throw new IllegalStateException("回收站文档不可编辑标签");
        }

        List<String> normalizedTags = normalizeTags(tags);
        validateTags(normalizedTags);
        documentTagMapper.deleteByDocId(docId);
        if (!normalizedTags.isEmpty()) {
            documentTagMapper.batchInsert(docId, normalizedTags);
        }
        documentMapper.updateTagsRelatedTimestamp(docId);
    }

    /**
     * R1：逻辑删除只改 is_deleted=1 + deleted_at，不动 document_status。
     * 状态机检查：禁止删除 UPLOADED/PARSING/CHUNKING/VECTORIZING 状态的文档。
     */
    @Transactional
    public void deleteDocument(Long docId) {
        KmDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        if (doc.getIsDeleted() != null && doc.getIsDeleted() == 1) {
            throw new IllegalStateException("文档已在回收站");
        }
        rejectIfProcessing(doc.getStatus());
        LocalDateTime now = LocalDateTime.now();
        int affected = documentMapper.logicDelete(docId, now, recycleBinService.calculatePurgeAt(now));
        if (affected == 0) {
            throw new IllegalStateException("文档删除失败，请刷新后重试");
        }
        documentCountOnLogicDeleted(doc.getKbId());
    }

    /**
     * 批量逻辑删除。R1：状态机检查同上。
     */
    @Transactional
    public void batchDeleteDocuments(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择要删除的文档");
        }
        List<Long> uniqueIds = ids.stream().distinct().collect(Collectors.toList());
        for (Long docId : uniqueIds) {
            KmDocument doc = documentMapper.selectById(docId);
            if (doc == null) {
                throw new IllegalArgumentException("文档不存在");
            }
            if (doc.getIsDeleted() != null && doc.getIsDeleted() == 1) {
                throw new IllegalStateException("文档「" + doc.getOriginalName() + "」已在回收站");
            }
            rejectIfProcessing(doc.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime purgeAt = recycleBinService.calculatePurgeAt(now);
        int affected = documentMapper.batchLogicDelete(uniqueIds, now, purgeAt);
        if (affected != uniqueIds.size()) {
            throw new IllegalStateException("部分文档删除失败，请刷新后重试");
        }
        for (Long docId : uniqueIds) {
            KmDocument doc = documentMapper.selectById(docId);
            if (doc != null) {
                documentCountOnLogicDeleted(doc.getKbId());
            }
        }
    }

    public InputStream downloadDocument(Long docId) throws Exception {
        KmDocument doc = getDocument(docId);
        return minioClientAdapter.download(doc.getFilePath());
    }

    /** P1-2：按需查询文档处理任务，避免列表 N+1。 */
    public List<Map<String, Object>> listDocumentTasks(Long docId) {
        getDocument(docId);
        List<Map<String, Object>> rows = documentMapper.selectTasksByDocId(docId);
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (rows == null) {
            return result;
        }
        for (Map<String, Object> row : rows) {
            result.add(toTaskPayload(row));
        }
        return result;
    }

    /**
     * R8：物理删除入口当前关闭（Worker MinIO 删除还没补完）。
     * 保留方法签名 + 抛异常，前端永久删除按钮在 v4 阶段隐藏。
     */
    public void permanentDeleteDisabled(Long docId) {
        throw new UnsupportedOperationException("物理删除功能暂未开放，等 Worker 端 MinIO 删除链路就绪后再启用");
    }

    private void rejectIfProcessing(String status) {
        if (status == null) return;
        if ("UPLOADED".equals(status)
                || "PARSING".equals(status)
                || "CHUNKING".equals(status)
                || "VECTORIZING".equals(status)) {
            throw new IllegalStateException("处理中的文档不允许删除（状态：" + status + "）");
        }
    }

    private void validateBatch(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请选择至少一个文件");
        }
        if (files.length > maxBatchCount) {
            throw new IllegalArgumentException("单次上传数量超过 " + maxBatchCount);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        long maxBytes = maxFileSizeMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("文件超过 " + maxFileSizeMb + "MB");
        }

        String extension = extractExtension(file.getOriginalFilename());
        Set<String> allowed = new HashSet<String>();
        for (String s : allowedExtensions.split(",")) {
            allowed.add(s.trim().toLowerCase(Locale.ROOT));
        }
        if (!allowed.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("暂不支持该格式");
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("无法识别文件扩展名");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return Collections.<String>emptyList();
        }
        List<String> result = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        for (String raw : tags) {
            if (raw == null) continue;
            for (String part : raw.split("[,，]")) {
                String tag = part == null ? "" : part.trim();
                if (tag.isEmpty()) continue;
                if (tag.length() > 64) tag = tag.substring(0, 64);
                if (seen.add(tag)) {
                    result.add(tag);
                }
            }
        }
        return result;
    }

    private void validateTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        if (tags.size() > 20) {
            throw new IllegalArgumentException("单个文档最多 20 个标签");
        }
        for (String tag : tags) {
            // R10：Java 8 兼容 isBlank
            if (tag == null || tag.trim().isEmpty()) {
                throw new IllegalArgumentException("标签不能为空");
            }
            if (tag.length() > 64) {
                throw new IllegalArgumentException("单个标签不能超过 64 个字符");
            }
        }
    }

    private String calculateSha256(MultipartFile file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(file.getBytes());
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void enrichTags(List<KmDocument> records) {
        for (KmDocument doc : records) {
            if (doc != null && doc.getId() != null) {
                doc.setTags(documentTagMapper.selectTagNamesByDocId(doc.getId()));
            }
        }
    }

    private void documentCountOnCreated(Long kbId) {
        if (kbId != null) {
            knowledgeBaseMapper.incrementDocumentCount(kbId);
        }
    }

    private void documentCountOnLogicDeleted(Long kbId) {
        if (kbId != null) {
            knowledgeBaseMapper.decrementDocumentCount(kbId);
        }
    }

    private Map<String, Object> toTaskPayload(Map<String, Object> row) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", toLong(row.get("id")));
        payload.put("docId", toLong(firstNonNull(row.get("doc_id"), row.get("docId"))));
        payload.put("taskType", toString(firstNonNull(row.get("task_type"), row.get("taskType"))));
        payload.put("triggerSource", toString(firstNonNull(row.get("trigger_source"), row.get("triggerSource"))));
        payload.put("taskStatus", toString(firstNonNull(row.get("task_status"), row.get("taskStatus"))));
        payload.put("progress", toInteger(row.get("progress")));
        payload.put("errorStage", toString(firstNonNull(row.get("error_stage"), row.get("errorStage"))));
        payload.put("errorMessage", toString(firstNonNull(row.get("error_message"), row.get("errorMessage"))));
        payload.put("retryCount", toInteger(firstNonNull(row.get("retry_count"), row.get("retryCount"))));
        payload.put("createdAt", toString(firstNonNull(row.get("created_at"), row.get("createdAt"))));
        payload.put("startedAt", toString(firstNonNull(row.get("started_at"), row.get("startedAt"))));
        payload.put("finishedAt", toString(firstNonNull(row.get("finished_at"), row.get("finishedAt"))));
        return payload;
    }

    private Object firstNonNull(Object primary, Object fallback) {
        return primary != null ? primary : fallback;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String toString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
