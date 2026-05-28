package com.middleware.manager.repository;

import com.middleware.manager.domain.MiddlewareType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MiddlewareTypeMapper {

    List<MiddlewareType> findAllByOrderBySortOrderAsc();

    MiddlewareType findByName(@Param("name") String name);

    MiddlewareType findById(@Param("id") Long id);

    int insert(MiddlewareType type);

    int update(MiddlewareType type);

    int deleteById(@Param("id") Long id);

    long count();
}
