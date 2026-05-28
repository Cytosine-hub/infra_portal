package com.middleware.manager.repository;

import com.middleware.manager.domain.SoftwareCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SoftwareCategoryMapper {

    List<SoftwareCategory> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(@Param("name") String name);

    SoftwareCategory findById(@Param("id") Long id);

    int insert(SoftwareCategory cat);

    int deleteById(@Param("id") Long id);
}
