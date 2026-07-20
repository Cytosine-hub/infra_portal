package com.middleware.manager.repository;

import com.middleware.manager.domain.RoleEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface RoleMapper {
    RoleEntity findById(Long id);
    RoleEntity findByDisplayName(String displayName);
    RoleEntity findByAuthority(String authority);
    List<RoleEntity> findAll();
    int insert(RoleEntity role);
    int update(RoleEntity role);
    int deleteById(Long id);
    long count();
}
