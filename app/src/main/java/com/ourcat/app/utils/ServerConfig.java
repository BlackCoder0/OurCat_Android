package com.ourcat.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public final class ServerConfig {

    private static final String PREFS_NAME = "ourcat_server";
    private static final String KEY_BASE_URL = "base_url";

    private ServerConfig() {
    }

    public static String get(Context context) {
        return prefs(context).getString(KEY_BASE_URL, null);
    }

    public static void save(Context context, String baseUrl) {
        prefs(context).edit().putString(KEY_BASE_URL, baseUrl).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
