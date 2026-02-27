package com.ourcat.app.repositories;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OssApi {

    @GET("/api/oss/upload-url")
    Call<Map<String, String>> getUploadUrl(
            @Query("filename") String filename,
            @Query("contentType") String contentType);
}
