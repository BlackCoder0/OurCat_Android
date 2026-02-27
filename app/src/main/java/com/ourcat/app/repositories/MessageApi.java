package com.ourcat.app.repositories;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface MessageApi {

    @GET("/api/message/list")
    Call<Map<String, Object>> list(
            @Query("page") int page,
            @Query("size") int size);

    @POST("/api/message/{id}/read")
    Call<Void> markRead(@Path("id") long id);
}
