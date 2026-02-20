package com.java_download_manager.jdm.download.network;

public final class MetadataRequestHeaders {

    private MetadataRequestHeaders() {}
    public static final String UA_CHROME = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    public static final String[] SIZE_HEADER_NAMES = {
            "Content-Length",
            "X-Content-Length",
            "X-File-Size",
            "X-Uncompressed-Content-Length",
            "X-Full-Content-Length",
            "X-Transfer-Length"
    };
}
