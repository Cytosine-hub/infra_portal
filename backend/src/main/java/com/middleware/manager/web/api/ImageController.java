package com.middleware.manager.web.api;

import com.middleware.manager.config.StorageProperties;
import com.middleware.manager.constant.ErrorCode;
import com.middleware.manager.constant.ErrorMessages;
import com.middleware.manager.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@RestController
@Slf4j
public class ImageController {

    private static final String IMAGES_SUBDIR = "images";
    private static final String IMAGE_URL_PREFIX = "/files/images/";
    private static final String IMAGE_CONTENT_TYPE_PREFIX = "image/";
    private static final String DEFAULT_EXTENSION = ".png";

    private final Path imageStoragePath;

    public ImageController(StorageProperties storageProperties) throws IOException {
        this.imageStoragePath = Paths.get(storageProperties.getLocation(), IMAGES_SUBDIR).toAbsolutePath().normalize();
        Files.createDirectories(this.imageStoragePath);
    }

    @PostMapping("/api/admin/images/upload")
    public Map<String, String> upload(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.IMAGE_FILE_REQUIRED);
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith(IMAGE_CONTENT_TYPE_PREFIX)) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, ErrorMessages.IMAGE_TYPE_NOT_SUPPORTED);
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.'));
        }
        if (ext.isEmpty()) ext = DEFAULT_EXTENSION;

        String storedName = UUID.randomUUID() + ext;

        try (InputStream is = file.getInputStream()) {
            Files.copy(is, imageStoragePath.resolve(storedName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_FAILED, ErrorMessages.FILE_UPLOAD_FAILED);
        }

        log.info("图片已上传 originalName={}, storedName={}", originalName, storedName);
        return Collections.singletonMap("url", IMAGE_URL_PREFIX + storedName);
    }

    @GetMapping("/files/images/{filename:.+}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) {
        try {
            Path file = imageStoragePath.resolve(filename).normalize();
            if (!file.startsWith(imageStoragePath)) {
                log.warn("非法图片路径: {}", filename);
                return ResponseEntity.badRequest().build();
            }
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() && resource.isReadable()) {
                MediaType mediaType = MediaType.IMAGE_PNG;
                try {
                    String probe = Files.probeContentType(file);
                    if (probe != null) mediaType = MediaType.parseMediaType(probe);
                } catch (Exception e) {
                    log.debug("探测文件类型失败: {}", filename, e);
                }
                return ResponseEntity.ok().contentType(mediaType).body(resource);
            }
        } catch (MalformedURLException e) {
            log.warn("图片路径解析失败: {}", filename, e);
        }
        return ResponseEntity.notFound().build();
    }
}
