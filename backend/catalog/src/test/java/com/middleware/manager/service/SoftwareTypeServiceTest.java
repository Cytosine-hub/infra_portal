package com.middleware.manager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.repository.ReleaseAssetMapper;
import com.middleware.manager.repository.SoftwareCategoryMapper;
import com.middleware.manager.repository.SoftwareTypeMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class SoftwareTypeServiceTest {

    @Mock
    private SoftwareTypeMapper softwareTypeMapper;

    @Mock
    private SoftwareCategoryMapper softwareCategoryMapper;

    @Mock
    private ReleaseAssetMapper releaseAssetMapper;

    private SoftwareTypeService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new SoftwareTypeService(
                softwareTypeMapper, softwareCategoryMapper, releaseAssetMapper);
    }

    @Test
    @DisplayName("TC-CATALOG-003 按分类名和类型名解析已有软件类型")
    void resolveOrCreateReturnsExistingType() {
        SoftwareType redis = softwareType(41L, "中间件", "Redis");
        when(softwareTypeMapper.findByCategoryIgnoreCaseAndNameIgnoreCase("中间件", "Redis"))
                .thenReturn(redis);

        SoftwareType result = service.resolveOrCreate(" 中间件 ", " Redis ");

        assertThat(result).isSameAs(redis);
        verify(softwareTypeMapper, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("TC-CATALOG-004 缺少类型时由 catalog 幂等落地分类和软件类型")
    void resolveOrCreatePersistsMissingTypeAndCategory() {
        when(softwareTypeMapper.findByCategoryIgnoreCaseAndNameIgnoreCase("中间件", "Nacos"))
                .thenReturn(null);
        when(softwareCategoryMapper.existsByNameIgnoreCase("中间件")).thenReturn(false);

        SoftwareType result = service.resolveOrCreate("中间件", "Nacos");

        assertThat(result.getCategory()).isEqualTo("中间件");
        assertThat(result.getName()).isEqualTo("Nacos");
        assertThat(result.isActive()).isTrue();
        verify(softwareCategoryMapper).insert(org.mockito.ArgumentMatchers.any());
        verify(softwareTypeMapper).insert(result);
    }

    @Test
    @DisplayName("TC-CATALOG-005 软件类型端口按 ID 批量解析且不依赖调用方数据库")
    void findByIdsDelegatesToCatalogMapper() {
        List<SoftwareType> types = List.of(
                softwareType(8L, "中间件", "Kafka"),
                softwareType(13L, "中间件", "Redis"));
        when(softwareTypeMapper.findByIds(List.of(8L, 13L))).thenReturn(types);

        assertThat(service.findByIds(List.of(8L, 13L))).containsExactlyElementsOf(types);
    }

    @Test
    @DisplayName("TC-CATALOG-006 软件类型端口返回后台全部启用类型")
    void findActiveDelegatesToCatalogMapper() {
        List<SoftwareType> types = List.of(
                softwareType(8L, "中间件", "Kafka"),
                softwareType(13L, "数据库", "MySQL"));
        when(softwareTypeMapper.findByActiveTrueOrderByCategoryAscNameAsc()).thenReturn(types);

        assertThat(service.findActive()).containsExactlyElementsOf(types);
    }

    private SoftwareType softwareType(Long id, String category, String name) {
        SoftwareType type = new SoftwareType();
        type.setId(id);
        type.setCategory(category);
        type.setName(name);
        type.setActive(true);
        return type;
    }
}
