package com.ourcat.app.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenStore {

    private static final String PREFS_NAME = "ourcat_auth";
    private static final String KEY_TOKEN = "token";

    private final SharedPreferences prefs;

    public TokenStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public void clear() {
        prefs.edit().remove(KEY_TOKEN).apply();
    }

    public boolean hasToken() {
        return getToken() != null && !getToken().isEmpty();
    }
}
