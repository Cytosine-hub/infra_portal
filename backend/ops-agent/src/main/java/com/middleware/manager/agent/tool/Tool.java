package com.middleware.manager.agent.tool;

import java.util.Map;

public interface Tool {
    String name();
    String description();
    String call(Map<String, Object> params);
}
