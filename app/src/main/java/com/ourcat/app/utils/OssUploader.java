package com.ourcat.app.utils;

import android.util.Log;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Upload image via backend presigned URL. Call getUploadUrl from API first, then upload file to uploadUrl.
 */
public class OssUploader {

    private static final String TAG = "OssUploader";
    private final OkHttpClient client = new OkHttpClient.Builder().build();

    /**
     * 1. Call api to get uploadUrl and publicUrl (api must add Authorization header).
     * 2. PUT file to uploadUrl.
     * 3. Return publicUrl for use in post/cat report etc.
     */
    public String uploadWithPresign(String uploadUrl, String publicUrl, File file, String contentType) throws IOException {
        if (uploadUrl == null || uploadUrl.isEmpty()) {
            return publicUrl;
        }
        MediaType type = contentType != null ? MediaType.parse(contentType) : MediaType.parse("image/jpeg");
        Request request = new Request.Builder()
                .url(uploadUrl)
                .put(RequestBody.create(file, type))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Upload failed: " + response.code());
            }
        }
        return publicUrl;
    }

    public String uploadWithPresign(String uploadUrl, String publicUrl, byte[] data, String contentType) throws IOException {
        if (uploadUrl == null || uploadUrl.isEmpty()) {
            return publicUrl;
        }
        MediaType type = contentType != null ? MediaType.parse(contentType) : MediaType.parse("image/jpeg");
        Request request = new Request.Builder()
                .url(uploadUrl)
                .put(RequestBody.create(data, type))
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Upload failed: " + response.code());
            }
        }
        return publicUrl;
    }
}
