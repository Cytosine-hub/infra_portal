package com.middleware.manager.service;

import com.middleware.manager.domain.ReleaseAsset;

import java.io.InputStream;

public interface StandardPackageOperations {
    void saveTemplateAndProcessAsync(ReleaseAsset asset, InputStream fileStream);

    void processAsync(ReleaseAsset asset);

    void regenerateByParameterStandard(Long parameterStandardId);
}
