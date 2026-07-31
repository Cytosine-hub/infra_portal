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

    /**
     * 删除该页由 LinkResolver 产生的出边。
     * <p>刻意不复用 deleteByPageId：那个删的是双向边（from 或 to），会连带删掉别的
     * 页面指向本页的入边，而那些边归属对方页面，不该由本页保存决定去留。
     * <p>限定 REFERENCES 类型，避免误删导入等其他来源写入的关系。
     */
    int deleteOutgoingReferences(@Param("pageId") Long pageId);

    int countAll();
}
