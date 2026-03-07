package com.ourcat.app.utils;

import android.content.Context;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitHelper {

    private static Retrofit retrofit;
    private static String baseUrl;

    public static void init(Context context, String serverUrl) {
        if (serverUrl == null || serverUrl.isEmpty()) {
            throw new IllegalStateException(
                    "BASE_URL 未配置，请在 local.properties 中设置 OURCAT_BASE_URL_DEBUG/OURCAT_BASE_URL_RELEASE");
        }
        baseUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        TokenStore tokenStore = new TokenStore(context);
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    String token = tokenStore.getToken();
                    Request.Builder builder = original.newBuilder();
                    if (token != null && !token.isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    return chain.proceed(builder.build());
                })
                .build();
        retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
    }

    public static void init(Context context) {
        init(context, null);
    }

    public static Retrofit get() {
        return retrofit;
    }

    public static String getBaseUrl() {
        return baseUrl;
    }
}
