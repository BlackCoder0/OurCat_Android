package com.ourcat.app.repositories;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface RescueApi {

    @POST("/api/rescue/activities")
    Call<Map<String, Object>> createActivity(@Body Map<String, Object> body);

    @GET("/api/rescue/activities")
    Call<Map<String, Object>> listActivities(
            @Query("page") int page,
            @Query("size") int size,
            @Query("status") String status,
            @Query("urgency") String urgency);

    @GET("/api/rescue/activities/{id}")
    Call<Map<String, Object>> getActivity(@Path("id") long id);

    @PATCH("/api/rescue/activities/{id}")
    Call<Map<String, Object>> updateActivityStatus(@Path("id") long id, @Body Map<String, String> body);

    @GET("/api/rescue/cats-need-rescue")
    Call<List<Map<String, Object>>> catsNeedRescue();

    @GET("/api/rescue/rescue-locations")
    Call<List<Map<String, Object>>> rescueLocations();

    @GET("/api/rescue/activities/{activityId}/tasks")
    Call<List<Map<String, Object>>> listActivityTasks(@Path("activityId") long activityId);

    @POST("/api/rescue/activities/{activityId}/tasks")
    Call<Map<String, Object>> assignTask(@Path("activityId") long activityId, @Body Map<String, Long> body);

    @POST("/api/rescue/activities/{activityId}/tasks/claim")
    Call<Map<String, Object>> claimTask(@Path("activityId") long activityId);

    @GET("/api/rescue/my-tasks")
    Call<List<Map<String, Object>>> myTasks();

    @PATCH("/api/rescue/tasks/{taskId}")
    Call<Map<String, Object>> updateTask(@Path("taskId") long taskId, @Body Map<String, String> body);

    @GET("/api/rescue/tasks/{taskId}/logs")
    Call<List<Map<String, Object>>> listTaskLogs(@Path("taskId") long taskId);

    @POST("/api/rescue/tasks/{taskId}/logs")
    Call<Map<String, Object>> addTaskLog(@Path("taskId") long taskId, @Body Map<String, String> body);

    @GET("/api/rescue/guide")
    Call<List<Map<String, Object>>> getGuide();

    @GET("/api/rescue/contacts")
    Call<List<Map<String, Object>>> getContacts();
}
