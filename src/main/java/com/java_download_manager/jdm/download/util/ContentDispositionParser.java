package com.java_download_manager.jdm.download.util;

public final class ContentDispositionParser {

    private ContentDispositionParser() {}

    public static String parseFilename(String disposition) {
        if (disposition == null || !disposition.contains("filename=")) {
            return null;
        }
        int idx = disposition.indexOf("filename=");
        String raw = disposition.substring(idx + 9).trim();
        if (raw.startsWith("\"") && raw.indexOf("\"", 1) > 0) {
            return raw.substring(1, raw.indexOf("\"", 1));
        }
        if (raw.contains(";")) {
            return raw.substring(0, raw.indexOf(";")).trim();
        }
        return raw.isBlank() ? null : raw;
    }
}
