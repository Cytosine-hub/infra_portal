package com.middleware.manager.wiki.repository;

import com.middleware.manager.wiki.entity.WikiPagePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WikiPagePermissionMapper {
    WikiPagePermission findByPageId(@Param("pageId") Long pageId);
    int insert(WikiPagePermission permission);
    int update(WikiPagePermission permission);
    int deleteByPageId(@Param("pageId") Long pageId);
}
