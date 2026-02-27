package com.ourcat.app.repositories;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApi {

    @GET("/api/weather/warning")
    Call<Map<String, Object>> getWarning(@Query("location") String location);
}
