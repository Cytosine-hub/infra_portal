package com.middleware.manager.repository;

import com.middleware.manager.domain.DocumentRevision;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentRevisionMapper {

    List<DocumentRevision> findByDocumentIdAndType(@Param("documentId") Long documentId,
                                                   @Param("documentType") String documentType);

    int insert(DocumentRevision revision);
}
