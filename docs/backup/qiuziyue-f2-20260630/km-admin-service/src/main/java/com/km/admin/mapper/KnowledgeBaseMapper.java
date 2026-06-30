package com.km.admin.mapper;

import com.km.admin.model.dto.KnowledgeBaseQueryDTO;
import com.km.admin.model.entity.KnowledgeBase;
import com.km.admin.model.vo.KnowledgeBaseVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface KnowledgeBaseMapper {

    List<KnowledgeBaseVO> selectPage(@Param("query") KnowledgeBaseQueryDTO query, @Param("offset") Integer offset);

    Long countPage(@Param("query") KnowledgeBaseQueryDTO query);

    KnowledgeBase selectById(@Param("id") Long id);

    KnowledgeBaseVO selectDetailById(@Param("id") Long id);

    KnowledgeBase selectByName(@Param("name") String name);

    Integer insert(KnowledgeBase knowledgeBase);

    Integer updateById(KnowledgeBase knowledgeBase);

    Integer logicDeleteById(@Param("id") Long id);

    Integer logicBatchDelete(@Param("ids") List<Long> ids);

    Integer countReadyDocumentsByKbId(@Param("kbId") Long kbId);
}
