package com.middleware.manager.repository;

import com.middleware.manager.domain.ApiAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApiAuditLogMapper {
    int insert(ApiAuditLog auditLog);
}
