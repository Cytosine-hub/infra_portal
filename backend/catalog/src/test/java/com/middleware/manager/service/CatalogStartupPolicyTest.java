package com.middleware.manager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CatalogStartupPolicyTest {

    @Test
    @DisplayName("TC-TYPE-005 启动时不得注册软件类型默认数据初始化器")
    void startupMustNotRegisterSoftwareTypeSeeder() {
        assertThatThrownBy(() -> Class.forName(
                "com.middleware.manager.service.CatalogStartupInitializer"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("TC-TYPE-006 软件类型服务不得提供默认数据补种方法")
    void softwareTypeServiceMustNotExposeDefaultSeeder() {
        assertThat(Arrays.stream(SoftwareTypeService.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .doesNotContain("initializeDefaults");
    }
}
