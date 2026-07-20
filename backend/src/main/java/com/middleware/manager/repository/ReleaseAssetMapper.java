package com.middleware.manager.repository;

import com.middleware.manager.domain.ReleaseAsset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReleaseAssetMapper {

    List<ReleaseAsset> findWithFilter(@Param("published") Boolean published,
                                      @Param("keyword") String keyword,
                                      @Param("category") String category,
                                      @Param("platform") String platform);

    ReleaseAsset findById(@Param("id") Long id);

    ReleaseAsset findByDownloadTokenAndPublishedTrue(@Param("downloadToken") String downloadToken);

    boolean existsByStoredFileNameIgnoreCase(@Param("storedFileName") String storedFileName);

    boolean existsByMiddlewareNameIgnoreCaseAndVersionIgnoreCaseAndOriginalFileNameIgnoreCase(
            @Param("middlewareName") String middlewareName,
            @Param("version") String version,
            @Param("originalFileName") String originalFileName);

    boolean existsBySoftwareTypeId(@Param("softwareTypeId") Long softwareTypeId);

    int insert(ReleaseAsset asset);

    int update(ReleaseAsset asset);

    int deleteById(@Param("id") Long id);

    int incrementDownloadCount(@Param("id") Long id);

    List<ReleaseAsset> findByParameterStandardId(@Param("parameterStandardId") Long parameterStandardId);

    ReleaseAsset findByMiddlewareNameAndOriginalFileNameAndPublishedTrue(@Param("middlewareName") String middlewareName,
                                                                        @Param("originalFileName") String originalFileName);
}
