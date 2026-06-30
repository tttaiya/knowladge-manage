package com.km.admin.knowledgebase.controller;

import com.km.admin.common.ApiResponse;
import com.km.admin.common.PageResult;
import com.km.admin.knowledgebase.dto.BatchDeleteKnowledgeBaseRequest;
import com.km.admin.knowledgebase.dto.CreateKnowledgeBaseRequest;
import com.km.admin.knowledgebase.dto.QueryKnowledgeBaseRequest;
import com.km.admin.knowledgebase.dto.UpdateKnowledgeBaseRequest;
import com.km.admin.knowledgebase.service.KnowledgeBaseService;
import com.km.admin.knowledgebase.vo.KnowledgeBaseDetailVO;
import com.km.admin.knowledgebase.vo.KnowledgeBaseVO;
import com.km.admin.knowledgebase.vo.ReprocessKnowledgeBaseResultVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

import javax.validation.Valid;

/**
 * 知识库管理接口。
 * F2 v1.0 5.4 节端点清单。
 *
 * <p>端点：
 * <ul>
 *   <li>GET    /api/v1/admin/knowledge-bases?category=&nameKeyword=&isDeleted=&pageNum=&pageSize=  → 列表
 *   <li>GET    /api/v1/admin/knowledge-bases/{id}                                               → 详情
 *   <li>POST   /api/v1/admin/knowledge-bases                                                    → 创建
 *   <li>PUT    /api/v1/admin/knowledge-bases/{id}                                               → 更新
 *   <li>DELETE /api/v1/admin/knowledge-bases/{id}                                               → 单删
 *   <li>POST   /api/v1/admin/knowledge-bases/batch-delete                                       → 批量删除
 *   <li>POST   /api/v1/admin/knowledge-bases/{id}/reprocess                                     → 策略变更
 * </ul>
 *
 * <p>用户上下文：{@code X-User-Id} / {@code X-User-Name} 由 Gateway 注入；匿名兜底 "anonymous"。
 */
@RestController
@RequestMapping("/api/v1/admin/knowledge-bases")
public class KnowledgeBaseController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private static final String ANONYMOUS = "anonymous";

    @Autowired
    private KnowledgeBaseService kbService;

    @GetMapping
    public ApiResponse<PageResult<KnowledgeBaseVO>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String nameKeyword,
            @RequestParam(required = false) Integer isDeleted,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        QueryKnowledgeBaseRequest req = new QueryKnowledgeBaseRequest();
        req.setCategory(category);
        req.setNameKeyword(nameKeyword);
        req.setIsDeleted(isDeleted);
        req.setPageNum(pageNum);
        req.setPageSize(pageSize);
        return ApiResponse.success(kbService.list(req));
    }

    @GetMapping("/{id}")
    public ApiResponse<KnowledgeBaseDetailVO> detail(@PathVariable Long id) {
        return ApiResponse.success(kbService.detail(id));
    }

    @PostMapping
    public ApiResponse<KnowledgeBaseVO> create(
            @Valid @RequestBody CreateKnowledgeBaseRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        String uid = (userId == null || userId.isEmpty()) ? ANONYMOUS : userId;
        String uname = (userName == null || userName.isEmpty()) ? ANONYMOUS : userName;
        return ApiResponse.success(kbService.create(req, uid, uname));
    }

    @PutMapping("/{id}")
    public ApiResponse<KnowledgeBaseVO> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateKnowledgeBaseRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName,
            @RequestHeader(value = "X-Confirmation", required = false) Boolean confirmation) {
        // X-Confirmation 优先；DTO 字段 confirmation 备选
        Boolean confirm = confirmation != null ? confirmation : req.getConfirmation();
        String uid = (userId == null || userId.isEmpty()) ? ANONYMOUS : userId;
        String uname = (userName == null || userName.isEmpty()) ? ANONYMOUS : userName;
        req.setId(id);
        return ApiResponse.success(kbService.update(req, uid, uname, confirm));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = (userId == null || userId.isEmpty()) ? ANONYMOUS : userId;
        kbService.delete(id, uid);
        return ApiResponse.success(null);
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDelete(
            @Valid @RequestBody BatchDeleteKnowledgeBaseRequest req,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = (userId == null || userId.isEmpty()) ? ANONYMOUS : userId;
        kbService.batchDelete(req, uid);
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/reprocess")
    public ApiResponse<ReprocessKnowledgeBaseResultVO> reprocess(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {
        String uid = (userId == null || userId.isEmpty()) ? ANONYMOUS : userId;
        String uname = (userName == null || userName.isEmpty()) ? ANONYMOUS : userName;
        return ApiResponse.success(kbService.reprocess(id, uid, uname));
    }
}
