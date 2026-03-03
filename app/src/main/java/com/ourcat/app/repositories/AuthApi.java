package com.ourcat.app.repositories;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;

public interface AuthApi {

    @GET("/api/auth/captcha")
    Call<Map<String, String>> getCaptcha();

    @retrofit2.http.POST("/api/auth/login")
    Call<Map<String, Object>> login(@Body Map<String, String> body);

    @retrofit2.http.POST("/api/auth/register")
    Call<Map<String, Object>> register(@Body Map<String, Object> body);

    @GET("/api/user/profile")
    Call<Map<String, Object>> getProfile();

    @GET("/api/user/{id}/public-profile")
    Call<Map<String, Object>> getPublicProfile(@retrofit2.http.Path("id") long id);

    @PUT("/api/user/profile")
    Call<Map<String, Object>> updateProfile(@Body Map<String, Object> body);

    @PUT("/api/user/password")
    Call<Map<String, Object>> changePassword(@Body Map<String, String> body);
}
