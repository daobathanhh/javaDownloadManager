package com.java_download_manager.jdm.controllers.http;

import com.java_download_manager.jdm.entities.DownloadTask;
import com.java_download_manager.jdm.entities.DownloadTask.DownloadTypeEnum;
import com.java_download_manager.jdm.service.DownloadTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.java_download_manager.jdm.download.scheduler.DynamicSegmentPool;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class DownloadTaskRestController {

    private final DownloadTaskService downloadTaskService;

    private Long requireAccountId(HttpServletRequest request) {
        Long id = HttpAuthFilter.getAccountId(request);
        if (id == null) {
            throw new UnauthorizedException();
        }
        return id;
    }

    @PostMapping
    @Operation(summary = "Create download task", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<DownloadTaskDto> create(
            @RequestBody CreateDownloadTaskRequest body,
            HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        DownloadTypeEnum type = parseDownloadType(body.downloadType());
        DownloadTask task = downloadTaskService.createDownloadTask(
                accountId,
                URI.create(body.url()),
                type,
                body.metadata() != null ? body.metadata() : "{}");
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(task));
    }

    @GetMapping
    @Operation(summary = "List download tasks", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<DownloadTaskListResponse> list(
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit,
            HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        int pageSize = Math.min(Math.max(limit, 1), 100);
        int page = offset / pageSize;
        List<DownloadTask> tasks = downloadTaskService.listDownloadTasks(accountId, page, pageSize);
        long total = downloadTaskService.countDownloadTasks(accountId);
        List<DownloadTaskDto> dtos = tasks.stream().map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(new DownloadTaskListResponse(dtos, total));
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "Get download task", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<DownloadTaskDto> get(
            @PathVariable long taskId,
            HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        Optional<DownloadTask> opt = downloadTaskService.getDownloadTask(accountId, taskId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String effectiveStatus = downloadTaskService.getEffectiveStatus(accountId, taskId).orElse(opt.get().getDownloadStatus().name());
        return ResponseEntity.ok(toDto(opt.get(), effectiveStatus));
    }

    @PutMapping("/{taskId}")
    @Operation(summary = "Update download task URL", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<DownloadTaskDto> update(
            @PathVariable long taskId,
            @RequestBody UpdateDownloadTaskRequest body,
            HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        Optional<DownloadTask> opt = downloadTaskService.updateDownloadTask(accountId, taskId, body.url());
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDto(opt.get()));
    }

    @PostMapping("/{taskId}/pause")
    @Operation(summary = "Pause downloading task", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> pause(@PathVariable long taskId, HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        if (!downloadTaskService.pauseTask(accountId, taskId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{taskId}/resume")
    @Operation(summary = "Resume paused download", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> resume(@PathVariable long taskId, HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        if (!downloadTaskService.resumeTask(accountId, taskId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{taskId}")
    @Operation(summary = "Delete download task", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Void> delete(
            @PathVariable long taskId,
            HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        boolean deleted = downloadTaskService.deleteDownloadTask(accountId, taskId);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{taskId}/progress")
    @Operation(summary = "Get in-memory progress (downloading tasks only)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ProgressDto> getProgress(
            @PathVariable long taskId,
            HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        var progress = downloadTaskService.getProgress(accountId, taskId);
        if (progress.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var p = progress.get();
        long downloaded = p.getDownloadedBytes().get();
        long total = p.getTotalBytes();
        double percent = total > 0 ? (downloaded * 100.0 / total) : 0;
        long speed = p.getCurrentSpeedBytesPerSec();
        long remainingSeconds = (speed > 0 && total > downloaded) ? (total - downloaded) / speed : 0;
        return ResponseEntity.ok(new ProgressDto(downloaded, total, percent, speed, remainingSeconds, p.getStatus()));
    }

    @GetMapping("/{taskId}/segments")
    @Operation(summary = "Get runtime worker/segment status", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<SegmentListResponse> getSegments(
            @PathVariable long taskId,
            HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        var segments = downloadTaskService.getSegmentDetails(accountId, taskId);
        if (segments.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new SegmentListResponse(segments.get()));
    }

    @GetMapping(value = "/{taskId}/file", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Download file", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable long taskId,
            HttpServletRequest request) {
        Long accountId = requireAccountId(request);
        Optional<InputStream> opt = downloadTaskService.getDownloadTaskFile(accountId, taskId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        String filename = filenameFromStoredKey(
                downloadTaskService.getDownloadTask(accountId, taskId)
                        .map(DownloadTask::getMetadata)
                        .orElse(null));
        InputStream in = opt.get();
        StreamingResponseBody stream = out -> {
            try (InputStream src = in) {
                src.transferTo(out);
            }
        };
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(stream);
    }

    private static DownloadTypeEnum parseDownloadType(String s) {
        if (s == null || s.isBlank()) return DownloadTypeEnum.HTTP;
        return switch (s.toUpperCase()) {
            case "HTTP" -> DownloadTypeEnum.HTTP;
            default -> DownloadTypeEnum.UNSPECIFIED;
        };
    }

    private DownloadTaskDto toDto(DownloadTask t) {
        return toDto(t, t.getDownloadStatus().name());
    }

    private DownloadTaskDto toDto(DownloadTask t, String downloadStatus) {
        return new DownloadTaskDto(
                t.getId(),
                t.getOfAccountId(),
                t.getDownloadType().name(),
                t.getUrl(),
                downloadStatus,
                t.getMetadata(),
                toInstant(t.getCreatedAt()),
                toInstant(t.getUpdatedAt()),
                toInstant(t.getCompletedAt()));
    }

    private static Instant toInstant(java.time.LocalDateTime ldt) {
        return ldt != null ? ldt.atZone(java.time.ZoneOffset.UTC).toInstant() : null;
    }

    private static String filenameFromStoredKey(String metadata) {
        if (metadata == null) return "download";
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(metadata);
            if (node.has("storedKey")) {
                String key = node.get("storedKey").asText();
                int last = key.lastIndexOf('/');
                return last >= 0 ? key.substring(last + 1) : key;
            }
        } catch (Exception ignored) {
        }
        return "download";
    }

    public record CreateDownloadTaskRequest(String url, String downloadType, String metadata) {}

    public record UpdateDownloadTaskRequest(String url) {}

    public record DownloadTaskDto(
            long id,
            long accountId,
            String downloadType,
            String url,
            String downloadStatus,
            String metadata,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {}

    public record DownloadTaskListResponse(List<DownloadTaskDto> downloadTasks, long total) {}

    public record SegmentListResponse(List<DynamicSegmentPool.SegmentDetail> segments) {}

    public record ProgressDto(long downloadedBytes, long totalBytes, double percent,
                              long speedBytesPerSec, long remainingSeconds, String status) {}

    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public static class UnauthorizedException extends RuntimeException {}
}
