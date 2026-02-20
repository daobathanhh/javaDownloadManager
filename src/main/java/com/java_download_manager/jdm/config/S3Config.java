package com.java_download_manager.jdm.config;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Configuration
@ConditionalOnProperty(name = "jdm.storage.s3.bucket")
@Slf4j
public class S3Config {

    @Value("${jdm.storage.s3.bucket}")
    private String bucket;

    @Value("${jdm.storage.s3.region:us-east-1}")
    private String region;

    @Value("${jdm.storage.s3.endpoint:}")
    private String endpoint;

    @Value("${jdm.storage.s3.access-key:}")
    private String accessKey;

    @Value("${jdm.storage.s3.secret-key:}")
    private String secretKey;

    @Value("${jdm.storage.s3.path-style-access:false}")
    private boolean pathStyleAccess;

    @Bean
    public S3Client s3Client() {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(region));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        if (pathStyleAccess) {
            builder.serviceConfiguration(
                    S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }

        S3Client client = builder.build();
        ensureBucketExists(client, bucket);
        return client;
    }

    private void ensureBucketExists(S3Client client, String bucketName) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
        } catch (Exception e) {
            try {
                client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                log.info("Created S3/MinIO bucket: {}", bucketName);
            } catch (Exception ex) {
                log.warn("Could not create bucket {}: {}", bucketName, ex.getMessage());
            }
        }
    }
}
