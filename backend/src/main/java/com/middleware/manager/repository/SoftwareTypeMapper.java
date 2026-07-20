package com.middleware.manager.repository;

import com.middleware.manager.domain.SoftwareType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SoftwareTypeMapper {

    List<SoftwareType> findByActiveTrueOrderByCategoryAscNameAsc();

    List<SoftwareType> findAllByOrderByCategoryAscNameAsc();

    List<String> findDistinctCategories();

    boolean existsByCategoryIgnoreCaseAndNameIgnoreCase(@Param("category") String category, @Param("name") String name);

    SoftwareType findByCategoryIgnoreCaseAndNameIgnoreCase(@Param("category") String category, @Param("name") String name);

    SoftwareType findById(@Param("id") Long id);

    int insert(SoftwareType type);

    int update(SoftwareType type);

    int deleteById(@Param("id") Long id);
}
