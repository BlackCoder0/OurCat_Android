package com.ourcat.app.repositories;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface UserApi {

    @GET("/api/user/list")
    Call<List<Map<String, Object>>> listUsers();

    @PUT("/api/user/{id}/role")
    Call<Map<String, Object>> setRole(@Path("id") long id, @Body Map<String, Object> body);
}
