package com.km.admin.document.service;

import com.km.admin.DocumentTaskFacade;
import com.km.admin.common.PageResult;
import com.km.admin.document.entity.KmDocument;
import com.km.admin.document.infrastructure.MinioClientAdapter;
import com.km.admin.document.mapper.DocumentManageMapper;
import com.km.admin.document.mapper.DocumentTagMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 回收站业务。R1：恢复不改 document_status。
 *
 * <p>R8：物理删除 (permanentDelete) 当前只创建 PURGE 任务，不直接删 MinIO/ChromaDB。
 *     等 Worker 端 MinIO 删除链路补完后，前端"永久删除"按钮再开放。
 */
@Service
public class RecycleBinService {

    private final DocumentManageMapper documentMapper;
    private final DocumentTagMapper documentTagMapper;
    private final MinioClientAdapter minioClientAdapter;
    private final DocumentTaskFacade documentTaskFacade;

    @Value("${km.recycle.retention-days:30}")
    private int retentionDays;

    public RecycleBinService(DocumentManageMapper documentMapper,
                             DocumentTagMapper documentTagMapper,
                             MinioClientAdapter minioClientAdapter,
                             DocumentTaskFacade documentTaskFacade) {
        this.documentMapper = documentMapper;
        this.documentTagMapper = documentTagMapper;
        this.minioClientAdapter = minioClientAdapter;
        this.documentTaskFacade = documentTaskFacade;
    }

    public PageResult<KmDocument> listRecycleBin(Long kbId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<KmDocument> records = documentMapper.selectByKbId(kbId, null, null, 1, offset, pageSize);

        PageResult<KmDocument> result = new PageResult<KmDocument>();
        result.setRecords(records);
        result.setTotal(documentMapper.countByKbId(kbId, null, null, 1));
        result.setPage(page);
        result.setPageSize(pageSize);
        return result;
    }

    /**
     * R1/R3：恢复只清 is_deleted/deleted_at，document_status 保持原值。
     * 恢复后由 MySQL is_deleted=0 控制检索可见性（R3）。
     */
    @Transactional
    public void restore(Long docId) {
        KmDocument doc = requireDeletedDocument(docId);
        int affected = documentMapper.restore(docId);
        if (affected == 0) {
            throw new IllegalStateException("恢复失败，请刷新后重试");
        }
    }

    /**
     * R8：永久删除入口。当前走"创建 PURGE 任务"路径，不直接删 MinIO。
     * 前端按钮 v4 阶段隐藏；保留方法签名以便未来开放。
     */
    @Transactional
    public void permanentDelete(Long docId) {
        KmDocument doc = requireDeletedDocument(docId);
        // 创建 PURGE 任务，由 Worker 删除 MinIO + ChromaDB + 业务表
        documentTaskFacade.createPurgeTask(docId, doc.getDeletedAt() == null
                ? LocalDateTime.now()
                : doc.getDeletedAt());
    }

    /**
     * 定时任务调用：清理超过 retentionDays 的回收站文档。
     * 当前 v4 阶段：PurgeScheduler 已基于 deleted_at 扫描，这里只作为手动批量入口。
     */
    @Transactional
    public int purgeExpired() {
        List<KmDocument> expired = documentMapper.selectExpiredRecycle(LocalDateTime.now());
        int count = 0;
        for (KmDocument doc : expired) {
            try {
                documentTaskFacade.createPurgeTask(doc.getId(),
                        doc.getDeletedAt() == null ? LocalDateTime.now() : doc.getDeletedAt());
                count++;
            } catch (Exception ignored) {
                // 单文档失败不影响其他文档
            }
        }
        return count;
    }

    public LocalDateTime calculatePurgeAt(LocalDateTime deletedAt) {
        return deletedAt.plus(retentionDays, ChronoUnit.DAYS);
    }

    private KmDocument requireDeletedDocument(Long docId) {
        KmDocument doc = documentMapper.selectById(docId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        if (doc.getIsDeleted() == null || doc.getIsDeleted() != 1) {
            throw new IllegalStateException("文档不在回收站中");
        }
        return doc;
    }
}
