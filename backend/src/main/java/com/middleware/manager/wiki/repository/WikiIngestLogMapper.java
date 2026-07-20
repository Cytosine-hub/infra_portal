package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.WikiIngestLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WikiIngestLogMapper {

    List<WikiIngestLog> findBySourceId(@Param("sourceId") Long sourceId);

    List<WikiIngestLog> findRecent(@Param("limit") int limit);

    int insert(WikiIngestLog log);

    int deleteBySourceId(@Param("sourceId") Long sourceId);
}
