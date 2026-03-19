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
 * Upload image via backend presigned URL. Call getUploadUrl from API first,
 * then upload file to uploadUrl.
 * Use the "contentType" returned by the API (not the one you passed in) so the
 * PUT header matches the signature.
 */
public class OssUploader {

    private static final String TAG = "OssUploader";
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder().build();

    /**
     * 1. Call api to get uploadUrl and publicUrl (api must add Authorization
     * header).
     * 2. PUT file to uploadUrl.
     * 3. Return publicUrl for use in post/cat report etc.
     */
    public String uploadWithPresign(String uploadUrl, String publicUrl, File file, String contentType)
            throws IOException {
        if (uploadUrl == null || uploadUrl.isEmpty()) {
            return publicUrl;
        }
        MediaType type = contentType != null ? MediaType.parse(contentType) : MediaType.parse("image/jpeg");
        Request request = new Request.Builder()
                .url(uploadUrl)
                .put(RequestBody.create(file, type))
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Upload failed: " + response.code());
            }
        }
        return publicUrl;
    }

    public interface OnUploadListener {
        void onSuccess(String url);

        void onFailure(String error);
    }

    public void upload(android.content.Context context, File file, String objectKey, OnUploadListener listener) {
        String contentType = "image/jpeg";
        RetrofitHelper.get().create(com.ourcat.app.repositories.OssApi.class)
                .getUploadUrl(objectKey, contentType)
                .enqueue(new retrofit2.Callback<java.util.Map<String, String>>() {
                    @Override
                    public void onResponse(retrofit2.Call<java.util.Map<String, String>> call,
                            retrofit2.Response<java.util.Map<String, String>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String uploadUrl = response.body().get("uploadUrl");
                            String publicUrl = response.body().get("publicUrl");
                            new Thread(() -> {
                                try {
                                    uploadWithPresign(uploadUrl, publicUrl, file, contentType);
                                    new android.os.Handler(android.os.Looper.getMainLooper())
                                            .post(() -> listener.onSuccess(publicUrl));
                                } catch (Exception e) {
                                    new android.os.Handler(android.os.Looper.getMainLooper())
                                            .post(() -> listener.onFailure(e.getMessage()));
                                }
                            }).start();
                        } else {
                            listener.onFailure("Get upload url failed: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(retrofit2.Call<java.util.Map<String, String>> call, Throwable t) {
                        listener.onFailure("Network error: " + t.getMessage());
                    }
                });
    }

    public String uploadWithPresign(String uploadUrl, String publicUrl, byte[] data, String contentType)
            throws IOException {
        if (uploadUrl == null || uploadUrl.isEmpty()) {
            return publicUrl;
        }
        MediaType type = contentType != null ? MediaType.parse(contentType) : MediaType.parse("image/jpeg");
        Request request = new Request.Builder()
                .url(uploadUrl)
                .put(RequestBody.create(data, type))
                .build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Upload failed: " + response.code());
            }
        }
        return publicUrl;
    }
}
