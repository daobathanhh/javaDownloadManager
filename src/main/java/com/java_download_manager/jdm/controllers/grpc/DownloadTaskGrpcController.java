package com.java_download_manager.jdm.controllers.grpc;

import com.java_download_manager.jdm.entities.DownloadTask;
import com.java_download_manager.jdm.service.DownloadTaskService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import jdm.v1.CreateDownloadTaskRequest;
import jdm.v1.CreateDownloadTaskResponse;
import jdm.v1.DeleteDownloadTaskRequest;
import jdm.v1.DeleteDownloadTaskResponse;
import jdm.v1.DownloadTaskServiceGrpc;
import jdm.v1.GetDownloadTaskFileRequest;
import jdm.v1.GetDownloadTaskFileResponse;
import jdm.v1.GetDownloadTaskListRequest;
import jdm.v1.GetDownloadTaskListResponse;
import jdm.v1.GetDownloadTaskProgressRequest;
import jdm.v1.GetDownloadTaskProgressResponse;
import jdm.v1.GetDownloadTaskSegmentsRequest;
import jdm.v1.GetDownloadTaskSegmentsResponse;
import jdm.v1.PauseDownloadTaskRequest;
import jdm.v1.PauseDownloadTaskResponse;
import jdm.v1.ResumeDownloadTaskRequest;
import jdm.v1.ResumeDownloadTaskResponse;
import jdm.v1.SegmentDetail;
import jdm.v1.UpdateDownloadTaskRequest;
import jdm.v1.UpdateDownloadTaskResponse;

import com.java_download_manager.jdm.download.DownloadProgress;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

@GrpcService
@Component
@RequiredArgsConstructor
public class DownloadTaskGrpcController extends DownloadTaskServiceGrpc.DownloadTaskServiceImplBase {

    private static final int DEFAULT_CHUNK_SIZE = 64 * 1024; // 64 KB

    private final DownloadTaskService downloadTaskService;

    @Override
    public void createDownloadTask(CreateDownloadTaskRequest request, StreamObserver<CreateDownloadTaskResponse> responseObserver) {
        Long accountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (accountId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required")
                    .asRuntimeException());
            return;
        }
        try {
            DownloadTask.DownloadTypeEnum type = toEntityDownloadType(request.getDownloadType());
            DownloadTask task = downloadTaskService.createDownloadTask(
                    accountId,
                    URI.create(request.getUrl()),
                    type,
                    "{}");
            responseObserver.onNext(CreateDownloadTaskResponse.newBuilder()
                    .setDownloadTask(GrpcMappers.toProtoDownloadTask(task))
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to create download task")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getDownloadTaskList(GetDownloadTaskListRequest request, StreamObserver<GetDownloadTaskListResponse> responseObserver) {
        Long accountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (accountId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required")
                    .asRuntimeException());
            return;
        }
        try {
            long limit = request.getLimit() > 0 ? request.getLimit() : 20;
            long offset = request.getOffset() >= 0 ? request.getOffset() : 0;
            int page = (int) (offset / limit);
            int pageSize = (int) Math.min(limit, 100);

            List<DownloadTask> tasks = downloadTaskService.listDownloadTasks(accountId, page, pageSize);
            long total = downloadTaskService.countDownloadTasks(accountId);

            var builder = GetDownloadTaskListResponse.newBuilder()
                    .setTotalDownloadTaskCount(total);
            for (DownloadTask task : tasks) {
                builder.addDownloadTaskList(GrpcMappers.toProtoDownloadTask(task));
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to list download tasks")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void updateDownloadTask(UpdateDownloadTaskRequest request, StreamObserver<UpdateDownloadTaskResponse> responseObserver) {
        Long accountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (accountId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required")
                    .asRuntimeException());
            return;
        }
        try {
            var opt = downloadTaskService.updateDownloadTask(
                    accountId,
                    request.getDownloadTaskId(),
                    request.getUrl());
            if (opt.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Download task not found: " + request.getDownloadTaskId())
                        .asRuntimeException());
                return;
            }
            responseObserver.onNext(UpdateDownloadTaskResponse.newBuilder()
                    .setDownloadTask(GrpcMappers.toProtoDownloadTask(opt.get()))
                    .build());
            responseObserver.onCompleted();
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to update download task")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void deleteDownloadTask(DeleteDownloadTaskRequest request, StreamObserver<DeleteDownloadTaskResponse> responseObserver) {
        Long accountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (accountId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required")
                    .asRuntimeException());
            return;
        }
        try {
            boolean deleted = downloadTaskService.deleteDownloadTask(accountId, request.getDownloadTaskId());
            if (!deleted) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Download task not found: " + request.getDownloadTaskId())
                        .asRuntimeException());
                return;
            }
            responseObserver.onNext(DeleteDownloadTaskResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to delete download task")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getDownloadTaskFile(GetDownloadTaskFileRequest request, StreamObserver<GetDownloadTaskFileResponse> responseObserver) {
        Long accountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (accountId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required")
                    .asRuntimeException());
            return;
        }
        try {
            var optStream = downloadTaskService.getDownloadTaskFile(accountId, request.getDownloadTaskId());
            if (optStream.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Download task file not found or not completed")
                        .asRuntimeException());
                return;
            }
            try (InputStream in = optStream.get()) {
                byte[] buf = new byte[DEFAULT_CHUNK_SIZE];
                int n;
                while ((n = in.read(buf)) != -1) {
                    responseObserver.onNext(GetDownloadTaskFileResponse.newBuilder()
                            .setData(com.google.protobuf.ByteString.copyFrom(buf, 0, n))
                            .build());
                }
            }
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to stream download task file")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getDownloadTaskProgress(GetDownloadTaskProgressRequest request, StreamObserver<GetDownloadTaskProgressResponse> responseObserver) {
        Long accountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (accountId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required")
                    .asRuntimeException());
            return;
        }
        try {
            var opt = downloadTaskService.getProgress(accountId, request.getDownloadTaskId());
            if (opt.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Progress not found (task not downloading or not found)")
                        .asRuntimeException());
                return;
            }
            DownloadProgress p = opt.get();
            long downloaded = p.getDownloadedBytes().get();
            long total = p.getTotalBytes();
            double percent = total > 0 ? (downloaded * 100.0 / total) : 0;
            long speed = p.getCurrentSpeedBytesPerSec();
            long remainingSeconds = (speed > 0 && total > downloaded) ? (total - downloaded) / speed : 0;
            responseObserver.onNext(GetDownloadTaskProgressResponse.newBuilder()
                    .setDownloadedBytes(downloaded)
                    .setTotalBytes(total)
                    .setPercent(percent)
                    .setSpeedBytesPerSec(speed)
                    .setRemainingSeconds(remainingSeconds)
                    .setStatus(p.getStatus() != null ? p.getStatus() : "")
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to get progress")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getDownloadTaskSegments(GetDownloadTaskSegmentsRequest request, StreamObserver<GetDownloadTaskSegmentsResponse> responseObserver) {
        Long accountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (accountId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required")
                    .asRuntimeException());
            return;
        }
        try {
            var opt = downloadTaskService.getSegmentDetails(accountId, request.getDownloadTaskId());
            if (opt.isEmpty()) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Segments not found (task not downloading or not found)")
                        .asRuntimeException());
                return;
            }
            var builder = GetDownloadTaskSegmentsResponse.newBuilder();
            for (var sd : opt.get()) {
                builder.addSegments(SegmentDetail.newBuilder()
                        .setId(sd.id())
                        .setStartOffset(sd.startOffset())
                        .setEndOffset(sd.endOffset())
                        .setSize(sd.size())
                        .setDownloaded(sd.downloaded())
                        .setState(sd.state() != null ? sd.state() : "")
                        .build());
            }
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to get segments")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void pauseDownloadTask(PauseDownloadTaskRequest request, StreamObserver<PauseDownloadTaskResponse> responseObserver) {
        Long accountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (accountId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required")
                    .asRuntimeException());
            return;
        }
        try {
            if (!downloadTaskService.pauseTask(accountId, request.getDownloadTaskId())) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Task not found or not downloadable")
                        .asRuntimeException());
                return;
            }
            responseObserver.onNext(PauseDownloadTaskResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to pause task")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void resumeDownloadTask(ResumeDownloadTaskRequest request, StreamObserver<ResumeDownloadTaskResponse> responseObserver) {
        Long accountId = GrpcAuthContext.ACCOUNT_ID.get();
        if (accountId == null) {
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Authentication required")
                    .asRuntimeException());
            return;
        }
        try {
            if (!downloadTaskService.resumeTask(accountId, request.getDownloadTaskId())) {
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("Task not found or not downloadable")
                        .asRuntimeException());
                return;
            }
            responseObserver.onNext(ResumeDownloadTaskResponse.getDefaultInstance());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Failed to resume task")
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    private static DownloadTask.DownloadTypeEnum toEntityDownloadType(jdm.v1.DownloadType proto) {
        return switch (proto) {
            case DOWNLOAD_TYPE_HTTP -> DownloadTask.DownloadTypeEnum.HTTP;
            default -> DownloadTask.DownloadTypeEnum.UNSPECIFIED;
        };
    }
}
