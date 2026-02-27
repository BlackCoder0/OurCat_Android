package com.ourcat.app;

import android.app.Application;
import com.ourcat.app.utils.RetrofitHelper;

public class OurCatApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RetrofitHelper.init(this);
    }
}
