package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.WikiPage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WikiPageMapper {

    WikiPage findById(@Param("id") Long id);

    WikiPage findByTitleAndType(@Param("title") String title, @Param("pageType") String pageType);

    WikiPage findByCanonicalTitleAndType(@Param("canonicalTitle") String canonicalTitle,
                                         @Param("pageType") String pageType,
                                         @Param("category") String category,
                                         @Param("software") String software);

    List<WikiPage> findAll();

    /** 仅查询 id 和 title，避免 SELECT * 携带大 content 列导致排序溢出 */
    List<WikiPage> findAllIdAndTitle();

    /** 排除 content 大列的列表查询，避免 ORDER BY 时 sort buffer 溢出 */
    List<WikiPage> findAllExcludingContent();

    List<WikiPage> findByCategoryExcludingContent(@Param("category") String category);

    List<WikiPage> findBySoftwareExcludingContent(@Param("software") String software);

    List<WikiPage> findByStatusExcludingContent(@Param("status") String status);

    List<WikiPage> findByCategory(@Param("category") String category);

    List<WikiPage> findBySoftware(@Param("software") String software);

    List<WikiPage> findByStatus(@Param("status") String status);

    List<WikiPage> fulltextSearch(@Param("query") String query, @Param("limit") int limit);

    List<WikiPage> findByTitleContaining(@Param("keyword") String keyword, @Param("limit") int limit);

    int countByStatus(@Param("status") String status);

    int countAll();

    int insert(WikiPage page);

    int update(WikiPage page);

    int deleteById(@Param("id") Long id);

    List<WikiPage> findOrphanPages();

    List<WikiPage> findStalePages(@Param("days") int daysSinceUpdate);

    List<WikiPage> findByIds(@Param("ids") List<Long> ids);

    List<WikiPage> findByCategoryOrSoftware(@Param("category") String category,
                                            @Param("software") String software,
                                            @Param("limit") int limit);

    List<WikiPage> findByCategoryOrSoftwareExcludingContent(@Param("category") String category,
                                                             @Param("software") String software,
                                                             @Param("limit") int limit);
}
