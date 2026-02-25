package com.java_download_manager.jdm.config;

import java.net.http.HttpClient;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class DownloadConfig {

    @Value("${jdm.download.chunk-count:4}")
    private int chunkCount;

    @Value("${jdm.download.ssl-insecure-skip-verify:false}")
    private boolean sslInsecureSkipVerify;

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public HttpClient downloadHttpClient() throws Exception {
        var builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (sslInsecureSkipVerify) {
            log.warn("jdm.download.ssl-insecure-skip-verify=true: SSL certificate validation disabled (insecure)");
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new java.security.SecureRandom());
            builder.sslContext(sslContext);
        }
        return builder.build();
    }

    @Bean(name = "downloadWorkerPool")
    public ExecutorService downloadWorkerPool() {
        return Executors.newFixedThreadPool(chunkCount);
    }
}
