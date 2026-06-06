package com.middleware.manager.service;

import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.domain.StandardParameter;
import com.middleware.manager.exception.BusinessException;
import com.middleware.manager.exception.NotFoundException;
import com.middleware.manager.repository.ReleaseAssetMapper;
import com.middleware.manager.repository.StandardParameterMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
public class StandardPackageService {
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");
    private static final String TEMPLATE_DIR = "templates";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String CONTENT_TYPE_ZIP = "application/zip";
    private static final String CONF_DIR = "conf";

    private final ReleaseAssetMapper releaseAssetMapper;
    private final StandardParameterMapper standardParameterMapper;
    private final StorageService storageService;

    public StandardPackageService(ReleaseAssetMapper releaseAssetMapper,
                                   StandardParameterMapper standardParameterMapper,
                                   StorageService storageService) {
        this.releaseAssetMapper = releaseAssetMapper;
        this.standardParameterMapper = standardParameterMapper;
        this.storageService = storageService;
    }

    /**
     * 保存原始模板并启动异步生成任务
     */
    public void saveTemplateAndProcessAsync(ReleaseAsset asset, InputStream fileStream) {
        try {
            Path templateDir = getTemplateDir(asset.getId());
            Files.createDirectories(templateDir);
            Path templateFile = templateDir.resolve(asset.getOriginalFileName());
            Files.copy(fileStream, templateFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("standard package template saved id={} path={}", asset.getId(), templateFile);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "保存标准包模板失败");
        }
        processAsync(asset);
    }

    /**
     * 异步处理标准包：解压 → 扫描占位符 → 替换 → 压缩
     */
    @Async
    public void processAsync(ReleaseAsset asset) {
        log.info("standard package processing started id={}", asset.getId());
        asset.setPackageStatus(STATUS_PROCESSING);
        asset.setPackageError(null);
        releaseAssetMapper.update(asset);

        try {
            Path templateFile = findTemplateFile(asset.getId());
            if (templateFile == null) {
                throw new NotFoundException(ErrorCode.NOT_FOUND, "模板文件不存在");
            }

            // 解压到临时目录
            Path workDir = Files.createTempDirectory("stdpkg-" + asset.getId() + "-");
            try {
                unzip(templateFile, workDir);

                // 扫描 conf/ 目录中的占位符
                Path confDir = workDir.resolve(CONF_DIR);
                if (!Files.exists(confDir) || !Files.isDirectory(confDir)) {
                    throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, "标准包中缺少 conf 目录");
                }

                // 收集所有占位符
                Map<String, List<PlaceholderLocation>> placeholders = scanPlaceholders(confDir);
                if (placeholders.isEmpty()) {
                    // 没有占位符，直接压缩
                    zipDirectory(workDir, asset);
                    asset.setPackageStatus(STATUS_SUCCESS);
                    asset.setPackageError(null);
                    releaseAssetMapper.update(asset);
                    log.info("standard package no placeholders found, packaged directly id={}", asset.getId());
                    return;
                }

                // 查询参数标准中的参数
                if (asset.getParameterStandardId() == null) {
                    throw new BusinessException(ErrorCode.PARAMETER_BINDING_INVALID, ErrorMessages.PARAMETER_BINDING_INVALID);
                }
                List<StandardParameter> params = standardParameterMapper
                        .findByParameterStandardIdAndActiveTrueOrderByCategoryAscCodeAsc(asset.getParameterStandardId());
                Map<String, String> paramMap = params.stream()
                        .collect(Collectors.toMap(StandardParameter::getCode, StandardParameter::getValue, (a, b) -> b));

                // 检查未匹配的占位符
                List<String> unmatched = new ArrayList<>();
                for (String code : placeholders.keySet()) {
                    if (!paramMap.containsKey(code)) {
                        for (PlaceholderLocation loc : placeholders.get(code)) {
                            unmatched.add("{{" + code + "}} (" + loc.file + ":" + loc.line + ")");
                        }
                    }
                }

                if (!unmatched.isEmpty()) {
                    asset.setPackageStatus(STATUS_FAILED);
                    asset.setPackageError("未匹配占位符: " + String.join(", ", unmatched));
                    releaseAssetMapper.update(asset);
                    log.warn("standard package failed id={} unmatched={}", asset.getId(), unmatched);
                    return;
                }

                // 替换占位符
                replacePlaceholders(confDir, paramMap);

                // 压缩生成包
                zipDirectory(workDir, asset);
                asset.setPackageStatus(STATUS_SUCCESS);
                asset.setPackageError(null);
                releaseAssetMapper.update(asset);
                log.info("standard package generated successfully id={}", asset.getId());

            } finally {
                deleteDirectory(workDir);
            }
        } catch (Exception ex) {
            log.error("standard package processing failed id={}", asset.getId(), ex);
            asset.setPackageStatus(STATUS_FAILED);
            asset.setPackageError("生成失败: " + ex.getMessage());
            try {
                releaseAssetMapper.update(asset);
            } catch (Exception updateEx) {
                log.error("failed to update package status id={}", asset.getId(), updateEx);
            }
        }
    }

    /**
     * 参数标准发布时重新生成所有关联的标准包
     */
    public void regenerateByParameterStandard(Long parameterStandardId) {
        List<ReleaseAsset> assets = releaseAssetMapper.findByParameterStandardId(parameterStandardId);
        for (ReleaseAsset asset : assets) {
            log.info("regenerating standard package id={} for parameterStandard={}", asset.getId(), parameterStandardId);
            processAsync(asset);
        }
    }

    private Path getTemplateDir(Long assetId) {
        return storageService.getRootLocation().resolve(TEMPLATE_DIR).resolve(String.valueOf(assetId));
    }

    private Path findTemplateFile(Long assetId) throws IOException {
        Path dir = getTemplateDir(assetId);
        if (!Files.exists(dir)) return null;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isRegularFile(entry)) return entry;
            }
        }
        return null;
    }

    private void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = targetDir.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(targetDir)) {
                    throw new IOException("Zip entry outside target dir: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private Map<String, List<PlaceholderLocation>> scanPlaceholders(Path confDir) throws IOException {
        Map<String, List<PlaceholderLocation>> result = new HashMap<>();
        Files.walkFileTree(confDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isTextFile(file)) {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    String relativePath = confDir.relativize(file).toString();
                    for (int i = 0; i < lines.size(); i++) {
                        Matcher matcher = PLACEHOLDER_PATTERN.matcher(lines.get(i));
                        while (matcher.find()) {
                            String code = matcher.group(1);
                            result.computeIfAbsent(code, k -> new ArrayList<>())
                                    .add(new PlaceholderLocation(relativePath, i + 1));
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    private void replacePlaceholders(Path confDir, Map<String, String> paramMap) throws IOException {
        Files.walkFileTree(confDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (isTextFile(file)) {
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    Matcher matcher = PLACEHOLDER_PATTERN.matcher(content);
                    StringBuilder sb = new StringBuilder();
                    while (matcher.find()) {
                        String code = matcher.group(1);
                        String value = paramMap.getOrDefault(code, matcher.group(0));
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
                    }
                    matcher.appendTail(sb);
                    Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void zipDirectory(Path sourceDir, ReleaseAsset asset) throws IOException {
        String middlewareName = asset.getMiddlewareName() != null ? asset.getMiddlewareName() : "standard-package";
        StorageService.StoredFile storedFile;
        Path zipFile = Files.createTempFile("stdpkg-", ".zip");
        try {
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
                Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String entryName = sourceDir.relativize(file).toString().replace("\\", "/");
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(file, zos);
                        zos.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            // 删除旧文件
            if (asset.getStoredFileName() != null && !STATUS_PENDING.equals(asset.getStoredFileName())) {
                storageService.deleteIfExists(asset.getStoredFileName());
            }
            // 存储新文件
            storedFile = storageService.store(zipFile, middlewareName);
            String origName = asset.getOriginalFileName();
            asset.setOriginalFileName(origName.endsWith(".zip") ? origName : origName + ".zip");
            asset.setStoredFileName(storedFile.storedFileName());
            asset.setContentType(CONTENT_TYPE_ZIP);
            asset.setFileSize(storedFile.size());
        } finally {
            Files.deleteIfExists(zipFile);
        }
    }

    private boolean isTextFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return name.endsWith(".xml") || name.endsWith(".properties") || name.endsWith(".yml")
                || name.endsWith(".yaml") || name.endsWith(".conf") || name.endsWith(".cfg")
                || name.endsWith(".ini") || name.endsWith(".json") || name.endsWith(".txt")
                || name.endsWith(".sh") || name.endsWith(".bat") || name.endsWith(".env")
                || name.endsWith(".cnf") || name.endsWith(".toml");
    }

    private void deleteDirectory(Path dir) {
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                    Files.delete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ex) {
            log.warn("failed to delete temp dir={}", dir, ex);
        }
    }

    private static class PlaceholderLocation {
        final String file;
        final int line;

        PlaceholderLocation(String file, int line) {
            this.file = file;
            this.line = line;
        }

        @Override
        public String toString() {
            return file + ":" + line;
        }
    }
}
