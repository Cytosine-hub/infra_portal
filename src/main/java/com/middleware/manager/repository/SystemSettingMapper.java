package com.middleware.manager.repository;

import com.middleware.manager.domain.SystemSetting;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SystemSettingMapper {
    SystemSetting findByKey(String key);
    List<SystemSetting> findAll();
    int upsert(SystemSetting setting);
}
