package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.WikiSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WikiSourceMapper {

    WikiSource findById(@Param("id") Long id);

    WikiSource findByContentHash(@Param("contentHash") String contentHash);

    WikiSource findByTitleAndType(@Param("title") String title, @Param("sourceType") String sourceType);

    List<WikiSource> findAll();

    List<WikiSource> findByIngested(@Param("ingested") boolean ingested);

    int insert(WikiSource source);

    int update(WikiSource source);

    int deleteById(@Param("id") Long id);

    int countAll();

    int countByIngested(@Param("ingested") boolean ingested);
}
