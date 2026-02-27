package com.ourcat.app.activities;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.ourcat.app.R;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private NavController navController;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fetchWeatherWarning();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        AppBarConfiguration appBarConfig = new AppBarConfiguration.Builder(
                R.id.nav_community, R.id.nav_map, R.id.nav_square, R.id.nav_me
        ).build();
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfig);

        bottomNav = findViewById(R.id.bottom_nav);
        if (navController != null) {
            NavigationUI.setupWithNavController(bottomNav, navController);
        }

        setupKeyboardListener();
    }

    private void setupKeyboardListener() {
        final View rootView = findViewById(android.R.id.content);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;
            if (keypadHeight > screenHeight * 0.15) {
                bottomNav.setVisibility(View.GONE);
            } else {
                bottomNav.setVisibility(View.VISIBLE);
            }
        });
    }

    private void fetchWeatherWarning() {
        String location = "31.23,121.47";
        RetrofitHelper.get().create(com.ourcat.app.repositories.WeatherApi.class).getWarning(location)
                .enqueue(new Callback<java.util.Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<java.util.Map<String, Object>> call, Response<java.util.Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Object w = response.body().get("warnings");
                            if (w instanceof List && !((List<?>) w).isEmpty()) {
                                StringBuilder sb = new StringBuilder();
                                for (Object o : (List<?>) w) sb.append(o).append("\n");
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("天气预警")
                                        .setMessage(sb.toString())
                                        .setPositiveButton("确定", null)
                                        .show();
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {}
                });
    }
}
