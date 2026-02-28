package com.ourcat.app;

import android.app.Application;
import android.util.Log;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.maps.MapsInitializer;
import com.ourcat.app.utils.RetrofitHelper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

public class OurCatApp extends Application {

    private static final String TAG = "OurCatApp";

    @Override
    public void onCreate() {
        super.onCreate();
        setupCrashHandler();
        RetrofitHelper.init(this, BuildConfig.BASE_URL);

        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);
        AMapLocationClient.updatePrivacyShow(this, true, true);
        AMapLocationClient.updatePrivacyAgree(this, true);
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
