package com.middleware.manager.repository;

import com.middleware.manager.domain.ParameterStandard;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ParameterStandardMapper {

    List<ParameterStandard> findWithFilter(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("category") String category);

    List<ParameterStandard> findByStatusOrderByCreatedAtDesc(String status);

    List<ParameterStandard> findByStatusInOrderByPublishedAtDesc(@Param("statuses") List<String> statuses);

    List<ParameterStandard> findAllByOrderByCreatedAtDesc();

    ParameterStandard findById(Long id);

    int insert(ParameterStandard standard);

    int update(ParameterStandard standard);

    int deleteById(Long id);
}
