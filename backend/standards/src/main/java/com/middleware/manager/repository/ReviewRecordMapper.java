package com.middleware.manager.repository;

import com.middleware.manager.domain.ReviewRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReviewRecordMapper {

    List<ReviewRecord> findBySubmitterUsernameOrderBySubmittedAtDesc(String submitterUsername);

    List<ReviewRecord> findAllByOrderBySubmittedAtDesc();

    List<ReviewRecord> findByCategoryOrderBySubmittedAtDesc(String category);

    List<ReviewRecord> findByDocumentIdAndStatus(@Param("documentId") Long documentId, @Param("status") String status);

    ReviewRecord findById(Long id);

    int insert(ReviewRecord record);

    int update(ReviewRecord record);
}
