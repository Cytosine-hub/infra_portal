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
     * <p>限定 REFERENCES 类型只能挡住 CONTRADICTS / DEPENDS_ON 等其他类型。
     * <b>注意</b>：WikiImportService 导入时的默认类型同样是 REFERENCES，因此导入产生、
     * 而正文中没有对应 [[…]] 的边，会在该页下次保存时被一并清除。这是「正文即出边
     * 唯一真相源」的必然结果——若要保留导入边，需要给它们单独的 link_type。
     */
    int deleteOutgoingReferences(@Param("pageId") Long pageId);

    int countAll();
}
