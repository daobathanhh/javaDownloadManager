package com.java_download_manager.jdm.storage;

import java.io.InputStream;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 / MinIO implementation of FileStorageService.
 */
@Service
@ConditionalOnProperty(name = "jdm.storage.s3.bucket")
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final String bucket;

    public S3FileStorageService(S3Client s3Client, @Value("${jdm.storage.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public String upload(String key, byte[] data) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build(),
                RequestBody.fromBytes(data));
        return key;
    }

    @Override
    public String upload(String key, InputStream inputStream, long contentLength) {
        RequestBody body = contentLength >= 0
                ? RequestBody.fromInputStream(inputStream, contentLength)
                : RequestBody.fromInputStream(inputStream, -1);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build(),
                body);
        return key;
    }

    @Override
    public Optional<InputStream> download(String key) {
        try {
            var response = s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(response);
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }
}
