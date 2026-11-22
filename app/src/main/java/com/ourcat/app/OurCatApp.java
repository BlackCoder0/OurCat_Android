package com.ourcat.app;

import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.maps.MapsInitializer;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.ServerConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.security.MessageDigest;

public class OurCatApp extends Application {

    private static final String TAG = "OurCatApp";

    @Override
    public void onCreate() {
        super.onCreate();
        setupCrashHandler();
        String configuredUrl = BuildConfig.ALLOW_SERVER_SWITCH ? ServerConfig.get(this) : null;
        RetrofitHelper.init(this, configuredUrl != null ? configuredUrl : BuildConfig.BASE_URL);

        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);
        AMapLocationClient.updatePrivacyShow(this, true, true);
        AMapLocationClient.updatePrivacyAgree(this, true);
        initAmapApiKey();
        logRuntimePackageSha1();
    }

    private void initAmapApiKey() {
        String apiKey = readAmapApiKeyFromManifest();
        if (TextUtils.isEmpty(apiKey)) {
            Log.e(TAG, "AMap apiKey is empty in manifest");
            return;
        }
        try {
            MapsInitializer.setApiKey(apiKey);
            AMapLocationClient.setApiKey(apiKey);
            Log.i(TAG, "AMap apiKey initialized: " + maskApiKey(apiKey));
        } catch (Exception e) {
            Log.e(TAG, "AMap apiKey init failed", e);
        }
    }

    private String readAmapApiKeyFromManifest() {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.GET_META_DATA);
            Bundle data = info.metaData;
            if (data == null) {
                return null;
            }
            return data.getString("com.amap.api.v2.apikey");
        } catch (Exception e) {
            Log.e(TAG, "Read AMap apiKey failed", e);
            return null;
        }
    }

    private String maskApiKey(String key) {
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private void logRuntimePackageSha1() {
        try {
            Signature[] signatures = getCurrentSignatures();
            if (signatures == null || signatures.length == 0) {
                Log.e(TAG, "Runtime signing SHA1 unavailable");
                return;
            }
            for (int i = 0; i < signatures.length; i++) {
                String sha1 = toSha1(signatures[i].toByteArray());
                // Log.i(TAG, "Runtime signing SHA1[" + i + "]: " + sha1);
            }
        } catch (Exception e) {
            Log.e(TAG, "Log runtime signing SHA1 failed", e);
        }
    }

    private Signature[] getCurrentSignatures() throws Exception {
        PackageManager pm = getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo info = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            if (info.signingInfo == null) {
                return null;
            }
            if (info.signingInfo.hasMultipleSigners()) {
                return info.signingInfo.getApkContentsSigners();
            }
            return info.signingInfo.getSigningCertificateHistory();
        }
        PackageInfo info = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
        return info.signatures;
    }

    private String toSha1(byte[] certBytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA1");
        byte[] digest = md.digest(certBytes);
        StringBuilder sb = new StringBuilder(digest.length * 3 - 1);
        for (int i = 0; i < digest.length; i++) {
            if (i > 0) {
                sb.append(':');
            }
            int b = digest[i] & 0xFF;
            if (b < 0x10) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(b).toUpperCase());
        }
        return sb.toString();
    }

    private void setupCrashHandler() {
        Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, ex) -> {
            try {
                File crashFile = new File(getExternalFilesDir(null), "crash.log");
                PrintWriter pw = new PrintWriter(new FileWriter(crashFile, false));
                pw.println("=== OurCat Crash " + new java.util.Date() + " ===");
                ex.printStackTrace(pw);
                pw.close();
                Log.e(TAG, "Crash saved to: " + crashFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e(TAG, "Failed to save crash log", e);
            }
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, ex);
            }
        });
    }
}
