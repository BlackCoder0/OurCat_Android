package com.ourcat.app.repositories;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ForumApi {

    @GET("/api/forum/posts")
    Call<Map<String, Object>> listPosts(
            @Query("page") int page,
            @Query("size") int size,
            @Query("search") String search);

    @GET("/api/forum/posts/{id}")
    Call<Map<String, Object>> getPost(@Path("id") long id);

    @POST("/api/forum/posts")
    Call<Map<String, Object>> createPost(@Body Map<String, Object> body);

    @PUT("/api/forum/posts/{id}")
    Call<Map<String, Object>> updatePost(@Path("id") long id, @Body Map<String, Object> body);

    @DELETE("/api/forum/posts/{id}")
    Call<Void> deletePost(@Path("id") long id);

    @POST("/api/forum/posts/{id}/pin")
    Call<Map<String, Object>> setPinned(@Path("id") long id, @Query("pinned") boolean pinned);

    @POST("/api/forum/posts/{id}/like")
    Call<Map<String, Object>> like(@Path("id") long id, @Query("like") boolean like);

    @GET("/api/forum/posts/{id}/comments")
    Call<List<Map<String, Object>>> getComments(
            @Path("id") long id,
            @Query("page") int page,
            @Query("size") int size);

    @POST("/api/forum/posts/{id}/comments")
    Call<Map<String, Object>> addComment(@Path("id") long id, @Body Map<String, String> body);
}
