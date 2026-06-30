package com.km.admin.document.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface DocumentTagMapper {

    int insert(@Param("docId") Long docId, @Param("tagName") String tagName);

    int batchInsert(@Param("docId") Long docId, @Param("tagNames") List<String> tagNames);

    List<String> selectTagNamesByDocId(@Param("docId") Long docId);

    int deleteByDocId(@Param("docId") Long docId);
}
