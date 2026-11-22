package com.ourcat.app.utils;

import android.content.Context;
import android.net.Uri;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitHelper {

    private static Retrofit retrofit;
    private static String baseUrl;

    public static void init(Context context, String serverUrl) {
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalStateException(
                    "BASE_URL 未配置，请在 local.properties 中设置 OURCAT_BASE_URL_DEBUG/OURCAT_BASE_URL_RELEASE");
        }
        baseUrl = normalizeBaseUrl(serverUrl);
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

    public static String normalizeBaseUrl(String serverUrl) {
        String value = serverUrl == null ? "" : serverUrl.trim();
        Uri uri = Uri.parse(value);
        String scheme = uri.getScheme();
        String path = uri.getPath();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || (path != null && !path.isEmpty() && !"/".equals(path))) {
            throw new IllegalArgumentException("请输入完整的 HTTP/HTTPS 后端地址，例如 http://192.168.1.20:8080/");
        }
        return value.endsWith("/") ? value : value + "/";
    }
}
