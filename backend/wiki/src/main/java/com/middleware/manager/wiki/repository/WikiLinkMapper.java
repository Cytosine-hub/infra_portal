package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.WikiLink;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WikiLinkMapper {

    List<WikiLink> findByFromPageId(@Param("fromPageId") Long fromPageId);

    List<WikiLink> findByToPageId(@Param("toPageId") Long toPageId);

    List<WikiLink> findAllByPageId(@Param("pageId") Long pageId);

    List<WikiLink> findAll();

    int exists(@Param("fromPageId") Long fromPageId, @Param("toPageId") Long toPageId);

    int insertIgnore(WikiLink link);

    int deleteByPageId(@Param("pageId") Long pageId);

    int countAll();
}
