package com.ourcat.app.activities;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ourcat.app.R;
import com.ourcat.app.fragments.MapFragment;
import com.ourcat.app.fragments.ReportCatDialogFragment;
import com.ourcat.app.repositories.MessageApi;
import com.ourcat.app.repositories.WeatherApi;
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
    private AMapLocationClient weatherLocationClient;
    private AMapLocationClientOption weatherLocationOption;
    private ActivityResultLauncher<String[]> weatherPermissionLauncher;
    private BadgeDrawable messageBadge;
    private Handler badgeHandler;
    private Runnable badgeRunnable;
    private boolean badgeMenuReady = false;
    private int lastDestId = 0;
    private FloatingActionButton fabQuickCamera;
    private AMapLocationClient quickCameraLocationClient;
    private ActivityResultLauncher<String[]> quickCameraPermissionLauncher;
    private ActivityResultLauncher<Uri> quickCameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private Uri tempCameraUri;
    private Uri pendingImageUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        weatherPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarse = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                    if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                        startWeatherLocation();
                    } else {
                        fetchWeatherWarning(31.23, 121.47);
                    }
                });
        requestWeatherWarning();

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
                R.id.nav_community, R.id.nav_square, R.id.nav_me).build();
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
        bottomNav.getMenu().findItem(R.id.nav_placeholder).setEnabled(false);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_placeholder) {
                return false;
            }
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
            int destId = dest.getId();
            if (lastDestId == R.id.message_center && destId != R.id.message_center) {
                updateMessageBadge();
            } else {
                updateMessageBadge();
            }
            lastDestId = destId;
            invalidateOptionsMenu();
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
        initBadgeRefresh();
        setupQuickCameraFab();
    }

    private void setupQuickCameraFab() {
        quickCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarse = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                    if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                        startQuickCameraLocation(pendingImageUri);
                    } else {
                        openReportDialogWithCamera(31.23, 121.47, pendingImageUri);
                    }
                });

        quickCameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(), success -> {
                    if (success) {
                        checkLocationAndOpenDialog(tempCameraUri);
                    } else {
                        // 如果用户取消拍照，清理临时文件并不做任何操作
                        try {
                            if (tempCameraUri != null) {
                                getContentResolver().delete(tempCameraUri, null, null);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        launchQuickCamera();
                    } else {
                        checkLocationAndOpenDialog(null);
                    }
                });

        fabQuickCamera = findViewById(R.id.fab_quick_camera);
        fabQuickCamera.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchQuickCamera();
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });
    }

    private void launchQuickCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "cat_quick_" + System.currentTimeMillis());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        tempCameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (tempCameraUri != null) {
            quickCameraLauncher.launch(tempCameraUri);
        } else {
            checkLocationAndOpenDialog(null);
        }
    }

    private void checkLocationAndOpenDialog(Uri imageUri) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startQuickCameraLocation(imageUri);
        } else {
            this.pendingImageUri = imageUri;
            quickCameraPermissionLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void startQuickCameraLocation(Uri imageUri) {
        try {
            if (quickCameraLocationClient == null) {
                AMapLocationClient.updatePrivacyShow(this, true, true);
                AMapLocationClient.updatePrivacyAgree(this, true);
                quickCameraLocationClient = new AMapLocationClient(getApplicationContext());
                AMapLocationClientOption option = new AMapLocationClientOption();
                option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                option.setOnceLocation(true);
                option.setNeedAddress(false);
                quickCameraLocationClient.setLocationOption(option);
            }
            // Update listener to use the current imageUri
            quickCameraLocationClient.setLocationListener(aMapLocation -> {
                if (aMapLocation != null && aMapLocation.getErrorCode() == 0) {
                    openReportDialogWithCamera(aMapLocation.getLatitude(), aMapLocation.getLongitude(), imageUri);
                } else {
                    openReportDialogWithCamera(31.23, 121.47, imageUri);
                }
            });
            quickCameraLocationClient.startLocation();
        } catch (Exception e) {
            openReportDialogWithCamera(31.23, 121.47, imageUri);
        }
    }

    private void startQuickCameraLocation() {
        startQuickCameraLocation(null);
    }

    private void openReportDialogWithCamera(double lat, double lng, Uri imageUri) {
        ReportCatDialogFragment dialog;
        if (imageUri != null) {
            dialog = ReportCatDialogFragment.newInstance(lat, lng, imageUri);
        } else {
            dialog = ReportCatDialogFragment.newInstance(lat, lng, true);
        }
        dialog.setOnReportSuccess(() -> {
            if (mapFragment != null) {
                mapFragment.refreshCatReports();
            }
        });
        dialog.show(getSupportFragmentManager(), "quick_report_cat");
    }

    private void openReportDialogWithCamera(double lat, double lng) {
        openReportDialogWithCamera(lat, lng, null);
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
                if (fabQuickCamera != null)
                    fabQuickCamera.setVisibility(View.GONE);
            } else {
                bottomNav.setVisibility(View.VISIBLE);
                if (fabQuickCamera != null)
                    fabQuickCamera.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_top_bar, menu);
        badgeMenuReady = true;
        ensureBadge(menu);
        updateMessageBadge();
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_message_center);
        boolean show = true;
        if (navController != null && navController.getCurrentDestination() != null) {
            show = navController.getCurrentDestination().getId() != R.id.message_center;
        }
        if (item != null) {
            item.setVisible(show);
        }
        if (!show && messageBadge != null) {
            messageBadge.setVisible(false);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_message_center) {
            hideMap();
            if (navController != null) {
                navController.navigate(R.id.message_center);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void ensureBadge(Menu menu) {
        if (messageBadge == null) {
            messageBadge = BadgeDrawable.create(this);
            messageBadge.setVisible(false);
            messageBadge.setBadgeGravity(BadgeDrawable.BOTTOM_END);
            messageBadge.setHorizontalOffset(getResources().getDimensionPixelOffset(R.dimen.badge_offset_x));
            messageBadge.setVerticalOffset(getResources().getDimensionPixelOffset(R.dimen.badge_offset_y));
        }
        if (toolbar != null && menu.findItem(R.id.action_message_center) != null) {
            BadgeUtils.attachBadgeDrawable(messageBadge, toolbar, R.id.action_message_center);
        }
    }

    private void initBadgeRefresh() {
        badgeHandler = new Handler(Looper.getMainLooper());
        badgeRunnable = new Runnable() {
            @Override
            public void run() {
                updateMessageBadge();
                badgeHandler.postDelayed(this, 60000);
            }
        };
    }

    private void updateMessageBadge() {
        if (!badgeMenuReady)
            return;
        if (navController != null && navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.message_center) {
            return;
        }
        RetrofitHelper.get().create(MessageApi.class).list(0, 50)
                .enqueue(new Callback<java.util.Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<java.util.Map<String, Object>> call,
                            Response<java.util.Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Object content = response.body().get("content");
                            int unread = 0;
                            if (content instanceof List) {
                                for (Object o : (List<?>) content) {
                                    if (o instanceof java.util.Map) {
                                        Object readObj = ((java.util.Map<?, ?>) o).get("read");
                                        boolean read = readObj instanceof Boolean && (Boolean) readObj;
                                        if (!read)
                                            unread++;
                                    }
                                }
                            }
                            if (messageBadge == null) {
                                messageBadge = BadgeDrawable.create(MainActivity.this);
                            }
                            if (unread <= 0) {
                                messageBadge.setVisible(false);
                            } else {
                                messageBadge.setVisible(true);
                                messageBadge.setNumber(Math.min(unread, 99));
                            }
                            if (toolbar != null) {
                                BadgeUtils.attachBadgeDrawable(messageBadge, toolbar, R.id.action_message_center);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {
                    }
                });
    }

    private void requestWeatherWarning() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            startWeatherLocation();
        } else {
            weatherPermissionLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void startWeatherLocation() {
        try {
            if (weatherLocationClient == null) {
                AMapLocationClient.updatePrivacyShow(this, true, true);
                AMapLocationClient.updatePrivacyAgree(this, true);
                weatherLocationClient = new AMapLocationClient(getApplicationContext());
                weatherLocationOption = new AMapLocationClientOption();
                weatherLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                weatherLocationOption.setOnceLocation(true);
                weatherLocationOption.setNeedAddress(false);
                weatherLocationClient.setLocationOption(weatherLocationOption);
                weatherLocationClient.setLocationListener(aMapLocation -> {
                    if (aMapLocation != null && aMapLocation.getErrorCode() == 0) {
                        fetchWeatherWarning(aMapLocation.getLatitude(), aMapLocation.getLongitude());
                    } else {
                        fetchWeatherWarning(31.23, 121.47);
                    }
                });
            }
            weatherLocationClient.startLocation();
        } catch (Exception e) {
            fetchWeatherWarning(31.23, 121.47);
        }
    }

    private void fetchWeatherWarning(double lat, double lng) {
        String location = lat + "," + lng;
        RetrofitHelper.get().create(WeatherApi.class).getWarning(location)
                .enqueue(new Callback<java.util.Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<java.util.Map<String, Object>> call,
                            Response<java.util.Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Object w = response.body().get("warnings");
                            if (w instanceof List && !((List<?>) w).isEmpty()) {
                                StringBuilder sb = new StringBuilder();
                                for (Object o : (List<?>) w)
                                    sb.append(o).append("\n");
                                Object msgIdObj = response.body().get("messageId");
                                new AlertDialog.Builder(MainActivity.this)
                                        .setTitle("天气预警")
                                        .setMessage(sb.toString())
                                        .setPositiveButton("确定", (d, which) -> {
                                            if (msgIdObj instanceof Number) {
                                                long msgId = ((Number) msgIdObj).longValue();
                                                RetrofitHelper.get().create(MessageApi.class).markRead(msgId)
                                                        .enqueue(new Callback<Void>() {
                                                            @Override
                                                            public void onResponse(Call<Void> call,
                                                                    Response<Void> response) {
                                                            }

                                                            @Override
                                                            public void onFailure(Call<Void> call, Throwable t) {
                                                            }
                                                        });
                                            }
                                        })
                                        .show();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (weatherLocationClient != null) {
            weatherLocationClient.onDestroy();
        }
        if (quickCameraLocationClient != null) {
            quickCameraLocationClient.onDestroy();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (badgeHandler != null && badgeRunnable != null) {
            badgeHandler.removeCallbacks(badgeRunnable);
            badgeHandler.post(badgeRunnable);
        } else {
            updateMessageBadge();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (badgeHandler != null && badgeRunnable != null) {
            badgeHandler.removeCallbacks(badgeRunnable);
        }
    }
}
