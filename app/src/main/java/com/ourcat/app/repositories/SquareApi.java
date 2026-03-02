package com.ourcat.app.repositories;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SquareApi {

        @GET("/api/square/posts")
        Call<Map<String, Object>> listPosts(
                        @Query("page") int page,
                        @Query("size") int size,
                        @Query("sort") String sort);

        @GET("/api/square/posts/{id}")
        Call<Map<String, Object>> getPost(@Path("id") long id);

        @POST("/api/square/posts")
        Call<Map<String, Object>> createPost(@Body Map<String, Object> body);

        @POST("/api/square/posts/{id}/markSolved")
        Call<Map<String, Object>> markSolved(@Path("id") long id);

        @GET("/api/square/posts/{id}/comments")
        Call<List<Map<String, Object>>> getComments(
                        @Path("id") long id,
                        @Query("page") int page,
                        @Query("size") int size);

        @POST("/api/square/posts/{id}/comments")
        Call<Map<String, Object>> addComment(@Path("id") long id, @Body Map<String, String> body);

        @retrofit2.http.DELETE("/api/square/posts/{id}")
        Call<Void> deletePost(@Path("id") long id);
}
