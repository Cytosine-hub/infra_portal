package com.middleware.manager.service;

import com.middleware.manager.domain.SystemSetting;
import com.middleware.manager.repository.SystemSettingMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemSettingService {
    private final SystemSettingMapper mapper;

    public SystemSettingService(SystemSettingMapper mapper) {
        this.mapper = mapper;
    }

    public Map<String, String> getAllSettings() {
        List<SystemSetting> settings = mapper.findAll();
        Map<String, String> map = new LinkedHashMap<>();
        for (SystemSetting s : settings) {
            map.put(s.getSettingKey(), s.getSettingValue());
        }
        return map;
    }

    public String getValue(String key, String defaultValue) {
        SystemSetting setting = mapper.findByKey(key);
        return setting != null ? setting.getSettingValue() : defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String val = getValue(key, null);
        if (val == null) return defaultValue;
        return "true".equalsIgnoreCase(val) || "1".equals(val);
    }

    public void updateSettings(Map<String, String> settings) {
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            SystemSetting s = new SystemSetting();
            s.setSettingKey(entry.getKey());
            s.setSettingValue(entry.getValue());
            mapper.upsert(s);
        }
    }
}
