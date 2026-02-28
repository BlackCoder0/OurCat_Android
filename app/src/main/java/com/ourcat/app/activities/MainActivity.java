package com.ourcat.app.activities;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.ourcat.app.R;
import com.ourcat.app.fragments.MapFragment;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private NavController navController;
    private BottomNavigationView bottomNav;
    private View mapContainer;
    private View navHostView;
    private MapFragment mapFragment;
    private boolean isMapVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        fetchWeatherWarning();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mapContainer = findViewById(R.id.map_container);
        navHostView = findViewById(R.id.nav_host_fragment);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        AppBarConfiguration appBarConfig = new AppBarConfiguration.Builder(
                R.id.nav_community, R.id.nav_square, R.id.nav_me
        ).build();
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfig);

        if (savedInstanceState == null) {
            mapFragment = new MapFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.map_container, mapFragment, "persistent_map")
                    .commit();
        } else {
            mapFragment = (MapFragment) getSupportFragmentManager().findFragmentByTag("persistent_map");
            isMapVisible = savedInstanceState.getBoolean("is_map_visible", false);
            if (isMapVisible) {
                mapContainer.setVisibility(View.VISIBLE);
                navHostView.setVisibility(View.GONE);
            }
        }

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_map) {
                showMap();
                return true;
            }
            hideMap();
            try {
                NavOptions options = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(navController.getGraph().getStartDestinationId(), false, true)
                        .build();
                navController.navigate(id, null, options);
                return true;
            } catch (Exception e) {
                return false;
            }
        });

        navController.addOnDestinationChangedListener((controller, dest, args) -> {
            if (!isMapVisible) {
                int destId = dest.getId();
                if (destId == R.id.nav_community || destId == R.id.nav_square || destId == R.id.nav_me) {
                    bottomNav.getMenu().findItem(destId).setChecked(true);
                }
            }
        });

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isMapVisible) {
                    bottomNav.setSelectedItemId(R.id.nav_community);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        setupKeyboardListener();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("is_map_visible", isMapVisible);
    }

    private void showMap() {
        isMapVisible = true;
        mapContainer.setVisibility(View.VISIBLE);
        navHostView.setVisibility(View.GONE);
        toolbar.setTitle(R.string.title_map);
    }

    private void hideMap() {
        isMapVisible = false;
        mapContainer.setVisibility(View.GONE);
        navHostView.setVisibility(View.VISIBLE);
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
