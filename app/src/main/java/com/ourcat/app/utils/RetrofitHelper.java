package com.ourcat.app.utils;

import android.content.Context;
import java.io.IOException;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitHelper {

    private static volatile Retrofit retrofit;
    private static volatile String baseUrl;
    private static OkHttpClient client;
    private static volatile int generation;

    public static synchronized void init(Context context, String serverUrl) {
        if (serverUrl == null || serverUrl.trim().isEmpty()) {
            throw new IllegalStateException(
                    "BASE_URL 未配置，请在 local.properties 中设置 OURCAT_BASE_URL_DEBUG/OURCAT_BASE_URL_RELEASE");
        }
        if (client != null) client.dispatcher().cancelAll();
        final int session = ++generation;
        baseUrl = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        final okhttp3.HttpUrl origin = okhttp3.HttpUrl.get(baseUrl);
        TokenStore tokenStore = new TokenStore(context);
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    String token;
                    synchronized (RetrofitHelper.class) {
                        if (session != generation) throw new IOException("Backend changed");
                        token = tokenStore.getToken();
                    }
                    Request.Builder builder = original.newBuilder();
                    if (origin.scheme().equals(original.url().scheme())
                            && origin.host().equals(original.url().host())
                            && origin.port() == original.url().port()
                            && token != null && !token.isEmpty()) {
                        builder.header("Authorization", "Bearer " + token);
                    }
                    Response response = chain.proceed(builder.build());
                    if (session != generation) {
                        response.close();
                        throw new IOException("Backend changed");
                    }
                    return response;
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
        return ServerAddress.normalize(serverUrl);
    }

    public static int getGeneration() {
        return generation;
    }
}
