package com.middleware.manager.web.controller;

import com.middleware.manager.domain.ReleaseAsset;
import com.middleware.manager.service.ReleaseService;
import com.middleware.manager.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
public class FileDownloadController {

    private final ReleaseService releaseService;
    private final StorageService storageService;

    public FileDownloadController(ReleaseService releaseService, StorageService storageService) {
        this.releaseService = releaseService;
        this.storageService = storageService;
    }

    @GetMapping("/files/{token}")
    public ResponseEntity<?> download(@PathVariable String token,
                                      @RequestHeader(value = "Range", required = false) String rangeHeader) {
        ReleaseAsset release = findPublishedRelease(token);
        releaseService.incrementDownloadCount(release);

        Resource resource = storageService.loadAsResource(release.getStoredFileName());
        long fileSize = release.getFileSize();
        MediaType mediaType;
        try {
            mediaType = release.getContentType() != null
                    ? MediaType.parseMediaType(release.getContentType())
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String contentDisposition = ContentDisposition.attachment()
                .filename(release.getOriginalFileName(), StandardCharsets.UTF_8)
                .build().toString();

        if (rangeHeader == null) {
            return ResponseEntity.ok()
                    .header("Accept-Ranges", "bytes")
                    .contentType(mediaType)
                    .contentLength(fileSize)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .body(resource);
        }

        long start;
        long end;
        try {
            String rangeSpec = rangeHeader.replace("bytes=", "").trim();
            String[] parts = rangeSpec.split("-");
            start = Long.parseLong(parts[0]);
            end = parts.length > 1 && !parts[1].isEmpty()
                    ? Long.parseLong(parts[1])
                    : fileSize - 1;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + fileSize)
                    .build();
        }

        if (start >= fileSize || end >= fileSize || start > end) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + fileSize)
                    .build();
        }

        long rangeLength = end - start + 1;
        ResourceRegion region = new ResourceRegion(resource, start, rangeLength);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header("Accept-Ranges", "bytes")
                .header("Content-Range", "bytes " + start + "-" + end + "/" + fileSize)
                .contentType(mediaType)
                .contentLength(rangeLength)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(region);
    }

    @GetMapping("/files/{middlewareName}/{fileName}")
    public ResponseEntity<?> downloadByName(@PathVariable String middlewareName,
                                             @PathVariable String fileName,
                                             @RequestHeader(value = "Range", required = false) String rangeHeader) {
        ReleaseAsset release = findPublishedReleaseByName(middlewareName, fileName);
        releaseService.incrementDownloadCount(release);
        return buildResponse(release, rangeHeader);
    }

    private ReleaseAsset findPublishedRelease(String token) {
        try {
            return releaseService.getPublishedRelease(token);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        }
    }

    private ReleaseAsset findPublishedReleaseByName(String middlewareName, String fileName) {
        try {
            return releaseService.getPublishedReleaseByNameAndFile(middlewareName, fileName);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        }
    }

    private ResponseEntity<?> buildResponse(ReleaseAsset release, String rangeHeader) {
        Resource resource = storageService.loadAsResource(release.getStoredFileName());
        long fileSize = release.getFileSize();
        MediaType mediaType;
        try {
            mediaType = release.getContentType() != null
                    ? MediaType.parseMediaType(release.getContentType())
                    : MediaType.APPLICATION_OCTET_STREAM;
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        String contentDisposition = ContentDisposition.attachment()
                .filename(release.getOriginalFileName(), StandardCharsets.UTF_8)
                .build().toString();

        if (rangeHeader == null) {
            return ResponseEntity.ok()
                    .header("Accept-Ranges", "bytes")
                    .contentType(mediaType)
                    .contentLength(fileSize)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .body(resource);
        }

        long start;
        long end;
        try {
            String rangeSpec = rangeHeader.replace("bytes=", "").trim();
            String[] parts = rangeSpec.split("-");
            start = Long.parseLong(parts[0]);
            end = parts.length > 1 && !parts[1].isEmpty()
                    ? Long.parseLong(parts[1])
                    : fileSize - 1;
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + fileSize)
                    .build();
        }

        if (start >= fileSize || end >= fileSize || start > end) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header("Content-Range", "bytes */" + fileSize)
                    .build();
        }

        long rangeLength = end - start + 1;
        ResourceRegion region = new ResourceRegion(resource, start, rangeLength);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header("Accept-Ranges", "bytes")
                .header("Content-Range", "bytes " + start + "-" + end + "/" + fileSize)
                .contentType(mediaType)
                .contentLength(rangeLength)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(region);
    }
}
