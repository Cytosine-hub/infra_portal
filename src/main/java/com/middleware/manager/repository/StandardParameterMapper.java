package com.middleware.manager.repository;

import com.middleware.manager.domain.StandardParameter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface StandardParameterMapper {

    List<StandardParameter> findWithFilter(
            @Param("standardDocumentId") Long standardDocumentId,
            @Param("parameterStandardId") Long parameterStandardId,
            @Param("keyword") String keyword,
            @Param("category") String category,
            @Param("active") Boolean active);

    List<StandardParameter> findByActiveTrueOrderByCategoryAscCodeAsc();

    List<StandardParameter> findByStandardDocumentIdAndActiveTrueOrderByCategoryAscCodeAsc(Long standardDocumentId);

    List<StandardParameter> findByParameterStandardIdAndActiveTrueOrderByCategoryAscCodeAsc(Long parameterStandardId);

    StandardParameter findById(Long id);

    boolean existsByStandardDocumentIdAndCodeIgnoreCase(
            @Param("standardDocumentId") Long standardDocumentId,
            @Param("code") String code);

    boolean existsByParameterStandardIdAndCodeIgnoreCase(
            @Param("parameterStandardId") Long parameterStandardId,
            @Param("code") String code);

    StandardParameter findByStandardDocumentIdAndCodeIgnoreCase(
            @Param("standardDocumentId") Long standardDocumentId,
            @Param("code") String code);

    StandardParameter findByParameterStandardIdAndCodeIgnoreCase(
            @Param("parameterStandardId") Long parameterStandardId,
            @Param("code") String code);

    int insert(StandardParameter param);

    int update(StandardParameter param);

    int deleteById(Long id);
}
