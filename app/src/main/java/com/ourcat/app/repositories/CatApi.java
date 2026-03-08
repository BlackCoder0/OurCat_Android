package com.ourcat.app.repositories;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.DELETE;

public interface CatApi {

    @GET("/api/cat/locations")
    Call<List<Map<String, Object>>> getLocations();

    @GET("/api/cat/heatmap")
    Call<List<Map<String, Object>>> getHeatmap();

    @GET("/api/cat/recommend")
    Call<List<Map<String, Object>>> getRecommend(@Query("type") String type);

    @POST("/api/cat/report")
    Call<Map<String, Object>> report(@Body Map<String, Object> body);

    @GET("/api/cat/my-reports")
    Call<List<Map<String, Object>>> getMyReports();

    @DELETE("/api/cat/reports/{id}")
    Call<Map<String, Object>> deleteReport(@Path("id") long id);

    // ==================== 猫咪档案管理 API ====================

    @GET("/api/cat/list")
    Call<Map<String, Object>> listCats(
            @Query("page") int page,
            @Query("size") int size,
            @Query("keyword") String keyword,
            @Query("status") String status,
            @Query("lat") Double lat,
            @Query("lng") Double lng,
            @Query("sortBy") String sortBy);

    @GET("/api/cat/{id}")
    Call<Map<String, Object>> getCatDetail(@Path("id") long id);

    @PUT("/api/cat/{id}")
    Call<Map<String, Object>> updateCat(@Path("id") long id, @Body Map<String, Object> body);

    @POST("/api/cat/report/{reportId}/confirm")
    Call<Map<String, Object>> confirmReport(@Path("reportId") long reportId, @Body Map<String, Object> body);

    @POST("/api/cat/merge")
    Call<Map<String, Object>> mergeCats(@Body Map<String, Object> body);

    // ==================== 上报详情与异议 API ====================

    @GET("/api/cat/report/{reportId}")
    Call<Map<String, Object>> getReportDetail(@Path("reportId") long reportId);

    @POST("/api/cat/report/{reportId}/reassign")
    Call<Map<String, Object>> reassignReport(@Path("reportId") long reportId, @Body Map<String, Object> body);

    @GET("/api/cat/nearby-cats")
    Call<List<Map<String, Object>>> getNearbyCats(
            @Query("lat") double lat,
            @Query("lng") double lng,
            @Query("radiusKm") double radiusKm);
}
