package com.km.admin.document.mapper;

import com.km.admin.document.entity.KmDocument;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DocumentManageMapper {

    int insert(KmDocument document);

    KmDocument selectById(@Param("id") Long id);

    List<KmDocument> selectByKbId(@Param("kbId") Long kbId,
                                  @Param("status") String status,
                                  @Param("keyword") String keyword,
                                  @Param("isDeleted") Integer isDeleted,
                                  @Param("offset") int offset,
                                  @Param("pageSize") int pageSize);

    long countByKbId(@Param("kbId") Long kbId,
                     @Param("status") String status,
                     @Param("keyword") String keyword,
                     @Param("isDeleted") Integer isDeleted);

    int updateTagsRelatedTimestamp(@Param("id") Long id);

    int logicDelete(@Param("id") Long id,
                    @Param("deletedAt") java.time.LocalDateTime deletedAt,
                    @Param("purgeAt") java.time.LocalDateTime purgeAt);

    int batchLogicDelete(@Param("ids") List<Long> ids,
                         @Param("deletedAt") java.time.LocalDateTime deletedAt,
                         @Param("purgeAt") java.time.LocalDateTime purgeAt);

    int restore(@Param("id") Long id);

    /**
     * 物理删除主记录。由 Worker PURGE 链路调用。
     */
    int permanentDelete(@Param("id") Long id);

    List<KmDocument> selectExpiredRecycle(@Param("now") java.time.LocalDateTime now);

    /**
     * 当前最新任务（用于重试场景），可由 DocumentService 透出。
     */
    java.util.Map<String, Object> selectLatestTaskByDocId(@Param("docId") Long docId);

    int updateUploaderSnapshot(@Param("id") Long id,
                               @Param("uploaderUserId") String uploaderUserId,
                               @Param("uploaderName") String uploaderName);

    int updateFileMetadata(@Param("id") Long id,
                           @Param("mimeType") String mimeType,
                           @Param("fileSize") Long fileSize,
                           @Param("fileHash") String fileHash);
}
