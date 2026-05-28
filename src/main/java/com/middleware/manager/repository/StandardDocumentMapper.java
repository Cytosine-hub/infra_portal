package com.middleware.manager.repository;

import com.middleware.manager.domain.StandardDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StandardDocumentMapper {

    List<StandardDocument> findWithFilter(@Param("keyword") String keyword,
                                          @Param("documentType") String documentType,
                                          @Param("status") String status,
                                          @Param("category") String category);

    List<StandardDocument> findByDocumentTypeAndStatusOrderByPublishedAtDescUpdatedAtDesc(
            @Param("documentType") String documentType,
            @Param("status") String status);

    List<StandardDocument> findByRelatedStandardDocumentIdAndStatusOrderByPublishedAtDescUpdatedAtDesc(
            @Param("relatedStandardDocumentId") Long relatedStandardDocumentId,
            @Param("status") String status);

    List<StandardDocument> findByStatusOrderByCategoryAscPublishedAtDesc(@Param("status") String status);

    List<StandardDocument> findByStatusInOrderByUpdatedAtDesc(@Param("statuses") List<String> statuses);

    StandardDocument findById(@Param("id") Long id);

    int insert(StandardDocument doc);

    int update(StandardDocument doc);

    int deleteById(@Param("id") Long id);
}
