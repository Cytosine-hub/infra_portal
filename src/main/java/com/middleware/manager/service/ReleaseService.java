package com.middleware.manager.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.domain.SoftwareType;
import com.middleware.manager.repository.ReleaseAssetMapper;
import com.middleware.manager.web.form.BatchImportForm;
import com.middleware.manager.web.form.ReleaseForm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ReleaseService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReleaseService.class);
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final ReleaseAssetMapper releaseAssetMapper;
    private final StorageService storageService;
    private final SoftwareTypeService softwareTypeService;

    public ReleaseService(ReleaseAssetMapper releaseAssetMapper,
                          StorageService storageService,
                          SoftwareTypeService softwareTypeService) {
        this.releaseAssetMapper = releaseAssetMapper;
        this.storageService = storageService;
        this.softwareTypeService = softwareTypeService;
    }

    public PageInfo<ReleaseAsset> listAdminReleases(String keyword, String platform, Boolean published, String category, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        PageHelper.startPage(safePage + 1, safeSize);
        List<ReleaseAsset> list = releaseAssetMapper.findWithFilter(published, keyword, category, platform);
        return new PageInfo<>(list);
    }

    public PageInfo<ReleaseAsset> listPublishedReleases(String keyword, String platform, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        PageHelper.startPage(safePage + 1, safeSize);
        List<ReleaseAsset> list = releaseAssetMapper.findWithFilter(true, keyword, null, platform);
        return new PageInfo<>(list);
    }

    public ReleaseAsset getAdminRelease(Long id) {
        ReleaseAsset asset = releaseAssetMapper.findById(id);
        if (asset == null) {
            throw new IllegalArgumentException("版本记录不存在");
        }
        return asset;
    }

    public ReleaseAsset getPublishedRelease(String token) {
        ReleaseAsset asset = releaseAssetMapper.findByDownloadTokenAndPublishedTrue(token);
        if (asset == null) {
            throw new IllegalArgumentException("公开资源不存在或未发布");
        }
        return asset;
    }

    @Transactional
    public BatchImportResult batchImport(BatchImportForm form) {
        Path sourceDirectory = Paths.get(form.getSourceDirectory().trim()).toAbsolutePath().normalize();
        if (!Files.exists(sourceDirectory) || !Files.isDirectory(sourceDirectory)) {
            throw new IllegalArgumentException("导入目录不存在或不是有效目录");
        }

        List<Path> files = collectFiles(sourceDirectory, form.isRecursive());
        BatchImportResult result = new BatchImportResult(sourceDirectory.toString(), files.size());
        boolean sourceInsideStorage = sourceDirectory.startsWith(storageService.getRootLocation());
        SoftwareType selectedType = resolveSoftwareType(form.getSoftwareTypeId());
        LOGGER.info("batch import started directory={} recursive={} files={} softwareTypeId={} published={}",
                sourceDirectory, form.isRecursive(), files.size(), form.getSoftwareTypeId(), form.isPublished());

        for (Path file : files) {
            String middlewareName = selectedType != null
                    ? selectedType.getName()
                    : resolveImportedMiddlewareName(form, sourceDirectory, file);
            String version = resolveImportedVersion(file);
            String originalFileName = file.getFileName().toString();
            String displayPath = relativeDisplayPath(sourceDirectory, file);
            String managedStoredFileName = resolveManagedStoredFileName(file);

            if (managedStoredFileName != null && releaseAssetMapper.existsByStoredFileNameIgnoreCase(managedStoredFileName)) {
                result.addSkipped(displayPath, "管理目录中同名文件已存在，已跳过");
                LOGGER.info("batch import skipped managed duplicate file={} storedFileName={}", file, managedStoredFileName);
                continue;
            }

            if (releaseAssetMapper.existsByMiddlewareNameIgnoreCaseAndVersionIgnoreCaseAndOriginalFileNameIgnoreCase(
                    middlewareName, version, originalFileName)) {
                result.addSkipped(displayPath, "记录已存在，已跳过");
                continue;
            }

            StorageService.StoredFile storedFile = null;
            try {
                storedFile = storageService.importFile(file, middlewareName);

                ReleaseAsset entity = new ReleaseAsset();
                entity.setMiddlewareName(middlewareName);
                entity.setSoftwareTypeId(selectedType != null ? selectedType.getId() : null);
                entity.setVersion(version);
                entity.setPlatform(trimToNull(form.getPlatform()));
                entity.setDescription(trimToNull(form.getDescription()));
                entity.setReleasedAt(resolveReleasedAt(file));
                entity.setPublished(form.isPublished());
                entity.setOriginalFileName(storedFile.originalFileName());
                entity.setStoredFileName(storedFile.storedFileName());
                entity.setContentType(storedFile.contentType());
                entity.setFileSize(storedFile.size());
                entity.setDownloadCount(0);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(LocalDateTime.now());

                releaseAssetMapper.insert(entity);
                result.addImported(displayPath, middlewareName, version);
            } catch (Exception ex) {
                if (!sourceInsideStorage && storedFile != null) {
                    storageService.deleteIfExists(storedFile.storedFileName());
                }
                LOGGER.warn("batch import failed file={} reason={}", file, ex.getMessage(), ex);
                result.addFailed(displayPath, ex.getMessage());
            }
        }

        LOGGER.info("batch import finished directory={} scanned={} imported={} skipped={} failed={}",
                result.getSourceDirectory(), result.getScannedCount(), result.getImportedCount(),
                result.getSkippedCount(), result.getFailedCount());
        return result;
    }

    @Transactional
    public ReleaseAsset create(ReleaseForm form) {
        ReleaseAsset entity = new ReleaseAsset();
        applyForm(entity, form);

        StorageService.StoredFile storedFile = storageService.store(form.getFile(), entity.getMiddlewareName());
        entity.setOriginalFileName(storedFile.originalFileName());
        entity.setStoredFileName(storedFile.storedFileName());
        entity.setContentType(storedFile.contentType());
        entity.setFileSize(storedFile.size());
        entity.setDownloadCount(0);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        releaseAssetMapper.insert(entity);
        LOGGER.info("release created id={} middleware={} version={} published={} file={}",
                entity.getId(), entity.getMiddlewareName(), entity.getVersion(), entity.isPublished(), entity.getOriginalFileName());
        return entity;
    }

    @Transactional
    public ReleaseAsset update(Long id, ReleaseForm form) {
        ReleaseAsset entity = getAdminRelease(id);
        String oldStoredFileName = null;

        if (entity.isPublished()) {
            LOGGER.warn("release update rejected because published id={} middleware={} version={}",
                    entity.getId(), entity.getMiddlewareName(), entity.getVersion());
            throw new IllegalStateException("已发布资源不能编辑，请先下架后再编辑");
        }

        applyForm(entity, form);

        if (form.getFile() != null && !form.getFile().isEmpty()) {
            StorageService.StoredFile storedFile = storageService.store(form.getFile(), entity.getMiddlewareName());
            oldStoredFileName = entity.getStoredFileName();
            entity.setOriginalFileName(storedFile.originalFileName());
            entity.setStoredFileName(storedFile.storedFileName());
            entity.setContentType(storedFile.contentType());
            entity.setFileSize(storedFile.size());
        } else {
            entity.setStoredFileName(storageService.moveToMiddlewareDirectory(entity.getStoredFileName(), entity.getMiddlewareName()));
        }

        entity.setUpdatedAt(LocalDateTime.now());
        releaseAssetMapper.update(entity);

        if (StringUtils.hasText(oldStoredFileName)) {
            storageService.deleteIfExists(oldStoredFileName);
        }
        LOGGER.info("release updated id={} middleware={} version={} published={} fileChanged={}",
                entity.getId(), entity.getMiddlewareName(), entity.getVersion(), entity.isPublished(),
                StringUtils.hasText(oldStoredFileName));
        return entity;
    }

    @Transactional
    public void publish(Long id) {
        ReleaseAsset entity = getAdminRelease(id);
        entity.setPublished(true);
        entity.setUpdatedAt(LocalDateTime.now());
        releaseAssetMapper.update(entity);
        LOGGER.info("release published id={} middleware={} version={}",
                entity.getId(), entity.getMiddlewareName(), entity.getVersion());
    }

    @Transactional
    public void unpublish(Long id) {
        ReleaseAsset entity = getAdminRelease(id);
        entity.setPublished(false);
        entity.setUpdatedAt(LocalDateTime.now());
        releaseAssetMapper.update(entity);
        LOGGER.info("release unpublished id={} middleware={} version={}",
                entity.getId(), entity.getMiddlewareName(), entity.getVersion());
    }

    @Transactional
    public void delete(Long id) {
        ReleaseAsset entity = getAdminRelease(id);
        if (entity.isPublished()) {
            LOGGER.warn("release delete rejected because published id={} middleware={} version={}",
                    entity.getId(), entity.getMiddlewareName(), entity.getVersion());
            throw new IllegalStateException("已发布资源不能删除，请先下架后再删除");
        }
        releaseAssetMapper.deleteById(id);
        storageService.deleteIfExists(entity.getStoredFileName());
        LOGGER.info("release deleted id={} middleware={} version={} file={}",
                entity.getId(), entity.getMiddlewareName(), entity.getVersion(), entity.getOriginalFileName());
    }

    @Transactional
    public void incrementDownloadCount(ReleaseAsset entity) {
        releaseAssetMapper.incrementDownloadCount(entity.getId());
        entity.setDownloadCount(entity.getDownloadCount() + 1);
    }

    private void applyForm(ReleaseAsset entity, ReleaseForm form) {
        SoftwareType selectedType = resolveSoftwareType(form.getSoftwareTypeId());
        entity.setSoftwareTypeId(selectedType != null ? selectedType.getId() : null);
        entity.setMiddlewareName(selectedType != null ? selectedType.getName() : form.getMiddlewareName().trim());
        entity.setVersion(form.getVersion().trim());
        entity.setPlatform(trimToNull(form.getPlatform()));
        entity.setDescription(trimToNull(form.getDescription()));
        entity.setReleasedAt(form.getReleasedAt());
        entity.setPublished(form.isPublished());
        entity.setStandardDocumentId(form.getStandardDocumentId());
    }

    private SoftwareType resolveSoftwareType(Long softwareTypeId) {
        return softwareTypeId == null ? null : softwareTypeService.get(softwareTypeId);
    }

    private List<Path> collectFiles(Path sourceDirectory, boolean recursive) {
        try (Stream<Path> stream = recursive ? Files.walk(sourceDirectory) : Files.list(sourceDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.toString().toLowerCase()))
                    .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new IllegalStateException("扫描导入目录失败", ex);
        }
    }

    private String resolveImportedMiddlewareName(BatchImportForm form, Path sourceDirectory, Path file) {
        if (StringUtils.hasText(form.getMiddlewareName())) {
            return truncate(form.getMiddlewareName().trim(), 120);
        }

        Path relativePath = sourceDirectory.relativize(file);
        if (relativePath.getNameCount() > 1) {
            return truncate(relativePath.getName(0).toString(), 120);
        }

        Path directoryName = sourceDirectory.getFileName();
        if (directoryName != null && StringUtils.hasText(directoryName.toString())) {
            return truncate(directoryName.toString(), 120);
        }

        return "未分类中间件";
    }

    private String resolveImportedVersion(Path file) {
        String fileName = file.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        String baseName = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
        String normalized = baseName.trim();
        return truncate(StringUtils.hasText(normalized) ? normalized : "unknown", 60);
    }

    private LocalDate resolveReleasedAt(Path file) {
        try {
            FileTime lastModifiedTime = Files.getLastModifiedTime(file);
            Instant instant = lastModifiedTime.toInstant();
            return instant.atZone(ZoneId.systemDefault()).toLocalDate();
        } catch (IOException ex) {
            return null;
        }
    }

    private String relativeDisplayPath(Path sourceDirectory, Path file) {
        return sourceDirectory.relativize(file).toString().replace("\\", "/");
    }

    private String resolveManagedStoredFileName(Path file) {
        Path normalizedFile = file.toAbsolutePath().normalize();
        Path storageRoot = storageService.getRootLocation();
        if (!normalizedFile.startsWith(storageRoot)) {
            return null;
        }
        return storageRoot.relativize(normalizedFile).toString().replace("\\", "/");
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    public static class BatchImportResult {
        private final String sourceDirectory;
        private final int scannedCount;
        private final List<ImportEntry> imported = new ArrayList<>();
        private final List<ImportEntry> skipped = new ArrayList<>();
        private final List<ImportEntry> failed = new ArrayList<>();

        public BatchImportResult(String sourceDirectory, int scannedCount) {
            this.sourceDirectory = sourceDirectory;
            this.scannedCount = scannedCount;
        }

        public void addImported(String filePath, String middlewareName, String version) {
            imported.add(new ImportEntry(filePath, "已导入到 " + middlewareName + " / " + version));
        }

        public void addSkipped(String filePath, String reason) {
            skipped.add(new ImportEntry(filePath, reason));
        }

        public void addFailed(String filePath, String reason) {
            failed.add(new ImportEntry(filePath, reason));
        }

        public String getSourceDirectory() {
            return sourceDirectory;
        }

        public int getScannedCount() {
            return scannedCount;
        }

        public int getImportedCount() {
            return imported.size();
        }

        public int getSkippedCount() {
            return skipped.size();
        }

        public int getFailedCount() {
            return failed.size();
        }

        public List<ImportEntry> getImported() {
            return imported;
        }

        public List<ImportEntry> getSkipped() {
            return skipped;
        }

        public List<ImportEntry> getFailed() {
            return failed;
        }
    }

    public static class ImportEntry {
        private final String filePath;
        private final String message;

        public ImportEntry(String filePath, String message) {
            this.filePath = filePath;
            this.message = message;
        }

        public String getFilePath() {
            return filePath;
        }

        public String getMessage() {
            return message;
        }
    }
}
