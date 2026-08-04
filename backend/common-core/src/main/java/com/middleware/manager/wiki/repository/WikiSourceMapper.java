package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.WikiSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WikiSourceMapper {

    /** 按来源类型列出，用于索引对账。 */
    List<WikiSource> findAllByType(@Param("sourceType") String sourceType);

    WikiSource findById(@Param("id") Long id);

    WikiSource findByContentHash(@Param("contentHash") String contentHash);

    WikiSource findByTitleAndType(@Param("title") String title, @Param("sourceType") String sourceType);

    WikiSource findByTypeAndSourceRef(@Param("sourceType") String sourceType,
                                      @Param("sourceRef") String sourceRef);

    List<WikiSource> findAll();

    /** 图谱投影：只返回来源元数据，不加载正文。 */
    List<WikiSource> findAllForGraph();

    /** 健康检查投影：content 只返回是否存在，避免传输 LONGTEXT。 */
    List<WikiSource> findAllForHealth();

    List<WikiSource> findByIngested(@Param("ingested") boolean ingested);

    int insert(WikiSource source);

    int update(WikiSource source);

    int deleteById(@Param("id") Long id);

    int countAll();

    int countByIngested(@Param("ingested") boolean ingested);
}
