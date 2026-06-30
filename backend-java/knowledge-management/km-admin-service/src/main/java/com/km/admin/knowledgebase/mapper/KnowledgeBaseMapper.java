package com.km.admin.knowledgebase.mapper;

import com.km.admin.knowledgebase.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库 Mapper。
 * F2 v1.0 文档 5.3 节 SQL 集合。
 *
 * <p>命名规范：{@code List<KB>} 方法命名 {@code listXxxByYyy}，结果用 {@code query_xxxByYyy.xml} 中 id 对应。
 * <p>单行操作命名 {@code getXxxByYyy} / {@code updateXxx}。
 */
@Mapper
public interface KnowledgeBaseMapper {

    /** 列表分页查询（按 is_deleted 过滤） */
    List<KnowledgeBase> listByQuery(@Param("category") String category,
                                    @Param("nameKeyword") String nameKeyword,
                                    @Param("isDeleted") Integer isDeleted,
                                    @Param("offset") int offset,
                                    @Param("pageSize") int pageSize);

    /** 列表总数（按 is_deleted 过滤） */
    int countByQuery(@Param("category") String category,
                     @Param("nameKeyword") String nameKeyword,
                     @Param("isDeleted") Integer isDeleted);

    /** 按 ID 查询（任意 is_deleted 状态） */
    KnowledgeBase getById(@Param("id") Long id);

    /** 按 ID 列表批量查询（用于批量删除前置校验） */
    List<KnowledgeBase> listByIds(@Param("ids") List<Long> ids);

    /** 插入一条，返回主键 id */
    int insert(KnowledgeBase kb);

    /** 按 ID 更新基本信息（不更新 strategy_version/document_count/is_deleted/deleted_at） */
    int updateById(KnowledgeBase kb);

    /** 逻辑删除：is_deleted=1、deleted_at=NOW() */
    int softDeleteById(@Param("id") Long id);

    /** 文档计数 +1（由 DocumentTaskFacade 在文档入库成功后调用） */
    int incrementDocumentCount(@Param("id") Long id);

    /** 文档计数 -1（由 DocumentTaskFacade 在文档逻辑删除后调用） */
    int decrementDocumentCount(@Param("id") Long id);
}
