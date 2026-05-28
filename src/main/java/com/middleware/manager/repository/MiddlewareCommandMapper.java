package com.middleware.manager.repository;

import com.middleware.manager.domain.MiddlewareCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MiddlewareCommandMapper {

    List<MiddlewareCommand> findByMiddlewareTypeIdOrderBySortOrderAsc(@Param("middlewareTypeId") Long middlewareTypeId);

    List<MiddlewareCommand> findAllByOrderByMiddlewareTypeIdAscSortOrderAsc();

    MiddlewareCommand findById(@Param("id") Long id);

    int insert(MiddlewareCommand cmd);

    int update(MiddlewareCommand cmd);

    int deleteById(@Param("id") Long id);

    long count();
}
