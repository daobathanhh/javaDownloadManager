package com.java_download_manager.jdm.storage;

import java.io.InputStream;

/**
 * Interface for uploading downloaded files to S3, MinIO, or compatible storage.
 */
public interface FileStorageService {

    /**
     * Upload bytes to storage.
     *
     * @param key  Object key (path) in the bucket, e.g. "downloads/1/123/file.zip"
     * @param data Bytes to upload
     * @return The stored object key/path, for saving in task metadata
     */
    String upload(String key, byte[] data);

    /**
     * Upload input stream to storage.
     *
     * @param key         Object key in the bucket
     * @param inputStream Stream to upload (caller closes it)
     * @param contentLength Size in bytes (for multipart, -1 if unknown)
     * @return The stored object key/path
     */
    String upload(String key, InputStream inputStream, long contentLength);

    /**
     * Download object from storage. Caller must close the stream.
     *
     * @param key Object key (path) in the bucket
     * @return InputStream of the object contents, or empty if not found
     */
    java.util.Optional<InputStream> download(String key);
}
