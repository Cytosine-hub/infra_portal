package com.middleware.manager.wiki.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface WikiAuditLogMapper {

    int insert(@Param("action") String action,
                @Param("targetType") String targetType,
                @Param("targetId") Long targetId,
                @Param("actorId") Long actorId,
                @Param("actorRole") String actorRole,
                @Param("actorIp") String actorIp,
                @Param("detail") String detail);
}
