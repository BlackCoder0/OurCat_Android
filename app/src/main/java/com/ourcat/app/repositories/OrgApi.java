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

public interface OrgApi {

    @GET("/api/org")
    Call<Map<String, Object>> getOrg();

    @GET("/api/org/{id}/members")
    Call<List<Map<String, Object>>> getMembers(@Path("id") long orgId);

    @GET("/api/org/{id}/home")
    Call<Map<String, Object>> getOrgHome(@Path("id") long orgId);

    @POST("/api/org/join")
    Call<Map<String, Object>> join();

    @POST("/api/org/leave")
    Call<Map<String, Object>> leave();

    @GET("/api/org/join-requests")
    Call<List<Map<String, Object>>> listJoinRequests(@Query("orgId") long orgId);

    @PATCH("/api/org/join-requests/{requestId}")
    Call<Map<String, Object>> reviewJoinRequest(@Path("requestId") long requestId, @Body Map<String, Boolean> body);
}
