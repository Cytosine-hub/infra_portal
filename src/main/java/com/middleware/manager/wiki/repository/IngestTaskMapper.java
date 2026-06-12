package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.IngestTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface IngestTaskMapper {
    IngestTask findById(@Param("id") Long id);
    List<IngestTask> findAll();
    List<IngestTask> findByStatus(@Param("status") String status);
    int insert(IngestTask task);
    int update(IngestTask task);
    int updateProgress(@Param("id") Long id, @Param("progress") int progress,
                       @Param("step") String step, @Param("completedChunks") int completedChunks);
    int updateProgressWithTotal(@Param("id") Long id, @Param("progress") int progress,
                                @Param("step") String step, @Param("completedChunks") int completedChunks,
                                @Param("totalChunks") int totalChunks);
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("errorMessage") String errorMessage);
    int updateResult(@Param("id") Long id, @Param("pagesCreated") int pagesCreated,
                     @Param("pagesUpdated") int pagesUpdated);
    int updateQualityReport(@Param("id") Long id, @Param("qualityReport") String qualityReport);

    int deleteBySourceId(@Param("sourceId") Long sourceId);
}
