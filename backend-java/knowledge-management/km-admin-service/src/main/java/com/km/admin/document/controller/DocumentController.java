package com.km.admin.document.controller;

import com.km.admin.common.ApiResponse;
import com.km.admin.common.PageResult;
import com.km.admin.document.dto.TagUpdateRequest;
import com.km.admin.document.entity.KmDocument;
import com.km.admin.document.service.DocumentService;
import com.km.admin.document.service.RecycleBinService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;

/**
 * 文档模块 REST 接口。
 *
 * <p>用户身份从 HTTP Header 读取：
 * <ul>
 *   <li>X-User-Id  : String UUID（必改 1：R9 Gateway 注入）</li>
 *   <li>X-User-Name: 展示用名</li>
 * </ul>
 *
 * <p>网关已 remove 伪造的 X-User-Id / X-User-Name 后注入真实值；这里直接读。
 *
 * <p>R10：URLEncoder.encode(name, "UTF-8")，不使用 StandardCharsets 常量。
 */
@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documentService;
    private final RecycleBinService recycleBinService;

    public DocumentController(DocumentService documentService, RecycleBinService recycleBinService) {
        this.documentService = documentService;
        this.recycleBinService = recycleBinService;
    }

    @GetMapping("/knowledge-bases/{kbId}/documents")
    public ApiResponse<PageResult<KmDocument>> listDocuments(
            @PathVariable Long kbId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(documentService.listDocuments(kbId, status, keyword, page, pageSize));
    }

    @PostMapping(value = "/knowledge-bases/{kbId}/documents/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<KmDocument>> uploadDocuments(
            @PathVariable Long kbId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) throws Exception {
        // R14：UUID 字符串；前端未传时退化为 "anonymous"，Worker 任务用 null 表示系统任务
        String effectiveUserId = (userId == null || userId.isEmpty()) ? "anonymous" : userId;
        String effectiveUserName = userName == null ? "anonymous" : userName;
        return ApiResponse.success(
                documentService.uploadDocuments(kbId, files, tags, effectiveUserId, effectiveUserName));
    }

    @GetMapping("/documents/{id}")
    public ApiResponse<KmDocument> getDocument(@PathVariable Long id) {
        return ApiResponse.success(documentService.getDocument(id));
    }

    @PutMapping("/documents/{id}/tags")
    public ApiResponse<Void> updateTags(@PathVariable Long id, @RequestBody TagUpdateRequest body) {
        List<String> tags = body.getTags() == null ? java.util.Collections.<String>emptyList() : body.getTags();
        documentService.updateTags(id, tags);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/documents/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/documents/batch-delete")
    public ApiResponse<Void> batchDelete(@RequestBody List<Long> ids) {
        documentService.batchDeleteDocuments(ids);
        return ApiResponse.success(null);
    }

    @GetMapping("/documents/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable Long id) throws Exception {
        KmDocument doc = documentService.getDocument(id);
        InputStream stream = documentService.downloadDocument(id);
        // R10：URLEncoder.encode(name, "UTF-8")，不用 StandardCharsets
        String encodedName = URLEncoder.encode(doc.getOriginalName(), "UTF-8").replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new InputStreamResource(stream));
    }

    @GetMapping("/knowledge-bases/{kbId}/documents/recycle-bin")
    public ApiResponse<PageResult<KmDocument>> listRecycleBin(
            @PathVariable Long kbId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(recycleBinService.listRecycleBin(kbId, page, pageSize));
    }

    @PostMapping("/documents/{id}/restore")
    public ApiResponse<Void> restore(@PathVariable Long id) {
        recycleBinService.restore(id);
        return ApiResponse.success(null);
    }

    /**
     * 永久删除。R8：当前 v4 阶段 Worker MinIO 删除未就绪，前端按钮隐藏。
     * 服务端方法保留，物理删除改为创建 PURGE 任务。
     */
    @DeleteMapping("/documents/{id}/permanent")
    public ApiResponse<Void> permanentDelete(@PathVariable Long id) {
        recycleBinService.permanentDelete(id);
        return ApiResponse.success(null);
    }
}
