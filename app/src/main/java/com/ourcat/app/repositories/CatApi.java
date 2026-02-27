package com.ourcat.app.repositories;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public interface CatApi {

    @GET("/api/cat/locations")
    Call<List<Map<String, Object>>> getLocations();

    @GET("/api/cat/heatmap")
    Call<List<Map<String, Object>>> getHeatmap();

    @GET("/api/cat/recommend")
    Call<List<Map<String, Object>>> getRecommend(@retrofit2.http.Query("type") String type);

    @POST("/api/cat/report")
    Call<Map<String, Object>> report(@Body Map<String, Object> body);
}
