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
import android.widget.Toast;

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
import com.ourcat.app.BuildConfig;
import com.ourcat.app.R;
import com.ourcat.app.fragments.MapFragment;
import com.ourcat.app.fragments.ReportCatDialogFragment;
import com.ourcat.app.repositories.AuthApi;
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
    private AlertDialog weatherWarningDialog;
    private Long lastWeatherWarningPopupMessageId = null;

    private interface WeatherLocationAction {
        void run(double lat, double lng, boolean isFallbackLocation);
    }

    private WeatherLocationAction pendingWeatherLocationAction;

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
                        WeatherLocationAction action = pendingWeatherLocationAction;
                        pendingWeatherLocationAction = null;
                        if (action != null) {
                            action.run(31.23, 121.47, true);
                        }
                    }
                });
        requestWeatherWarning();

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setOnLongClickListener(v -> {
            RetrofitHelper.get().create(AuthApi.class).getProfile()
                    .enqueue(new Callback<java.util.Map<String, Object>>() {
                        @Override
                        public void onResponse(Call<java.util.Map<String, Object>> call,
                                Response<java.util.Map<String, Object>> response) {
                            if (response.body() == null)
                                return;
                            Object roleObj = response.body().get("role");
                            int role = roleObj instanceof Number ? ((Number) roleObj).intValue() : 0;
                            if (role < 3) {
                                Toast.makeText(MainActivity.this, "仅三级用户可触发", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            String[] items = new String[] { "天气预警（当前位置）", "今日天气（当前位置）", "模拟预警（管理员）" };

                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("选择功能")
                                    .setItems(items, (dialog, which) -> {
                                        if (which == 0) {
                                            requestWeatherLocation(
                                                    (lat, lng, isFallbackLocation) -> fetchWeatherWarning(lat, lng, false, null, true,
                                                            isFallbackLocation));
                                        } else if (which == 1) {
                                            requestWeatherLocation(MainActivity.this::fetchTodayWeatherDebug);
                                        } else if (which == 2) {
                                            requestWeatherLocation(
                                                    (lat, lng, isFallbackLocation) -> fetchWeatherWarning(lat, lng, true, "warning",
                                                            true, isFallbackLocation));
                                        }
                                    })
                                    .setNegativeButton("取消", null)
                                    .show();
                        }

                        @Override
                        public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {
                            Toast.makeText(MainActivity.this, "获取权限失败", Toast.LENGTH_SHORT).show();
                        }
                    });
            return true;
        });

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
        toolbar.setNavigationIconTint(ContextCompat.getColor(this, android.R.color.white));

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
            int destId = dest.getId();

            // 如果是从地图进入猫咪档案页，标记一下
            if (destId == R.id.cat_profile) {
                if (isMapVisible) {
                    hideMap();
                    // 标记是因为导航到 profile 而隐藏的
                    navHostView.setTag(R.id.map_container, true);
                }
            } else {
                // 如果离开猫咪档案页（通过返回或其他方式），检查是否需要恢复地图
                Object tag = navHostView.getTag(R.id.map_container);
                if (tag != null && (Boolean) tag) {
                    // 清除标记
                    navHostView.setTag(R.id.map_container, null);
                    // 只有当回到了社区（首页）时才恢复地图，因为地图逻辑上是在社区之上的覆盖层
                    if (destId == R.id.nav_community) {
                        showMap();
                        // 强制选中"地图"TAB（如果有的话），或者保持当前TAB
                        bottomNav.getMenu().findItem(R.id.nav_map).setChecked(true);
                        return; // 阻止后续逻辑覆盖选中状态
                    }
                }
            }

            if (!isMapVisible) {
                if (destId == R.id.nav_community || destId == R.id.nav_square || destId == R.id.nav_me) {
                    bottomNav.getMenu().findItem(destId).setChecked(true);
                }
            }
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
                // 如果当前在猫咪档案页面，允许默认返回行为（会退栈）
                if (navController.getCurrentDestination() != null
                        && navController.getCurrentDestination().getId() == R.id.cat_profile) {
                    // 检查是否是从地图跳转过来的（通过 arguments 判断或其他标志）
                    // 但由于 Navigation 栈已被正确管理，我们只需要在退栈后检查是否需要显示地图
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);

                    // 如果退栈后回到了 NavHost 的根（或者栈空了），或者我们希望恢复地图状态
                    // 这里比较棘手，因为 Navigation 不知道 "MapFragment" 的状态（它是手动管理的 View）
                    // 我们可以在 popBackStack 后检查
                    return;
                }

                if (isMapVisible) {
                    // 如果地图可见，按返回键回到社区（默认首页）
                    bottomNav.setSelectedItemId(R.id.nav_community);
                    hideMap();
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
        boolean inMessageCenter = navController != null && navController.getCurrentDestination() != null
                && navController.getCurrentDestination().getId() == R.id.message_center;
        RetrofitHelper.get().create(MessageApi.class).list(0, 50)
                .enqueue(new Callback<java.util.Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<java.util.Map<String, Object>> call,
                            Response<java.util.Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Object content = response.body().get("content");
                            int unread = 0;
                            Long weatherMsgId = null;
                            String weatherContent = null;
                            if (content instanceof List) {
                                for (Object o : (List<?>) content) {
                                    if (o instanceof java.util.Map) {
                                        java.util.Map<?, ?> m = (java.util.Map<?, ?>) o;
                                        Object readObj = m.get("read");
                                        boolean read = readObj instanceof Boolean && (Boolean) readObj;
                                        if (!read)
                                            unread++;
                                        if (!read && weatherMsgId == null) {
                                            Object typeObj = m.get("targetType");
                                            if (typeObj != null && "weather_warning".equals(typeObj.toString())) {
                                                Object idObj = m.get("id");
                                                Object cObj = m.get("content");
                                                if (idObj instanceof Number) {
                                                    weatherMsgId = ((Number) idObj).longValue();
                                                    weatherContent = cObj != null ? cObj.toString() : "";
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            if (weatherMsgId != null && weatherMsgId != lastWeatherWarningPopupMessageId) {
                                showGlobalWeatherWarningDialog(weatherContent, weatherMsgId);
                            }
                            if (messageBadge == null) {
                                messageBadge = BadgeDrawable.create(MainActivity.this);
                            }
                            if (!inMessageCenter) {
                                if (unread <= 0) {
                                    messageBadge.setVisible(false);
                                } else {
                                    messageBadge.setVisible(true);
                                    messageBadge.setNumber(Math.min(unread, 99));
                                }
                            } else {
                                messageBadge.setVisible(false);
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
        requestWeatherLocation((lat, lng, isFallbackLocation) -> fetchWeatherWarning(lat, lng, false, null, false, isFallbackLocation));
    }

    private void requestWeatherLocation(WeatherLocationAction action) {
        pendingWeatherLocationAction = action;
        boolean fineGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (fineGranted || coarseGranted) {
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
                    WeatherLocationAction action = pendingWeatherLocationAction;
                    pendingWeatherLocationAction = null;
                    if (action == null) {
                        return;
                    }
                    if (aMapLocation != null && aMapLocation.getErrorCode() == 0) {
                        action.run(aMapLocation.getLatitude(), aMapLocation.getLongitude(), false);
                        return;
                    }
                    action.run(31.23, 121.47, true);
                });
            }
            weatherLocationClient.startLocation();
        } catch (Exception e) {
            WeatherLocationAction action = pendingWeatherLocationAction;
            pendingWeatherLocationAction = null;
            if (action != null) {
                action.run(31.23, 121.47, true);
            }
        }
    }

    private void fetchWeatherWarning(double lat, double lng) {
        fetchWeatherWarning(lat, lng, false, null, false, false);
    }

    private void fetchTodayWeatherDebug(double lat, double lng, boolean isFallbackLocation) {
        String location = lat + "," + lng;
        WeatherApi api = RetrofitHelper.get().create(WeatherApi.class);
        api.getNow(location).enqueue(new Callback<java.util.Map<String, Object>>() {
            @Override
            public void onResponse(Call<java.util.Map<String, Object>> call,
                    Response<java.util.Map<String, Object>> response) {
                if (response.code() == 403) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    Object textObj = response.body().get("text");
                    String text = textObj != null ? textObj.toString() : "暂无实时天气数据";
                    String locationSource = isFallbackLocation ? "默认坐标（上海 31.23,121.47）" : "当前位置";
                    String explain = "位置来源：" + locationSource + "\n时区：由后端 open-meteo.timezone 配置（默认 Asia/Shanghai）";
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("今日天气")
                            .setMessage(explain + "\n\n" + text)
                            .setPositiveButton("确定", null)
                            .show();
                } else {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("调试")
                            .setMessage("请求失败: " + response.code())
                            .setPositiveButton("确定", null)
                            .show();
                }
            }

            @Override
            public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("测试失败")
                        .setMessage("网络请求异常: " + t.getMessage())
                        .setPositiveButton("确定", null)
                        .show();
            }
        });
    }

    private void fetchWeatherWarning(double lat, double lng, boolean mock, String mockMode, boolean userInitiated,
            boolean isFallbackLocation) {
        String location = lat + "," + lng;
        WeatherApi api = RetrofitHelper.get().create(WeatherApi.class);
        Call<java.util.Map<String, Object>> call = mock
                ? api.getWarningMock(location, true, mockMode != null ? mockMode : "warning")
                : api.getWarning(location);
        call.enqueue(new Callback<java.util.Map<String, Object>>() {
            @Override
            public void onResponse(Call<java.util.Map<String, Object>> call,
                    Response<java.util.Map<String, Object>> response) {
                if (response.code() == 403 && mock) {
                    // Toast.makeText(MainActivity.this, "仅管理员可操作", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    Object w = response.body().get("warnings");
                    if (w instanceof List && !((List<?>) w).isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (Object o : (List<?>) w)
                            sb.append(o).append("\n");
                        Object msgIdObj = response.body().get("messageId");
                        Long msgId = null;
                        if (msgIdObj instanceof Number) {
                            msgId = ((Number) msgIdObj).longValue();
                        }
                        if (mock && "weather".equalsIgnoreCase(mockMode)) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("今日天气")
                                    .setMessage(sb.toString().trim())
                                    .setPositiveButton("确定", null)
                                    .show();
                        } else {
                            showGlobalWeatherWarningDialog(sb.toString(), msgId);
                        }
                    } else {
                        if (mock) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("调试")
                                    .setMessage("后端返回为空，请检查和风配置或 JWT 文件")
                                    .setPositiveButton("确定", null)
                                    .show();
                        } else if (userInitiated) {
                            String locationSource = isFallbackLocation ? "默认坐标（上海 31.23,121.47）" : "当前位置";
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("天气预警")
                                    .setMessage("当前暂无天气预警\n位置来源：" + locationSource)
                                    .setPositiveButton("确定", null)
                                    .show();
                        }
                    }
                } else if (mock) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("调试")
                            .setMessage("请求失败: " + response.code())
                            .setPositiveButton("确定", null)
                            .show();
                } else if (userInitiated) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("天气预警")
                            .setMessage("查询失败: " + response.code())
                            .setPositiveButton("确定", null)
                            .show();
                }
            }

            @Override
            public void onFailure(Call<java.util.Map<String, Object>> call, Throwable t) {
                if (mock) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("测试失败")
                            .setMessage("网络请求异常: " + t.getMessage())
                            .setPositiveButton("确定", null)
                            .show();
                } else if (userInitiated) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("天气预警")
                            .setMessage("网络请求异常: " + t.getMessage())
                            .setPositiveButton("确定", null)
                            .show();
                }
            }
        });
    }

    private void showGlobalWeatherWarningDialog(String content, Long messageId) {
        if (weatherWarningDialog != null && weatherWarningDialog.isShowing())
            return;
        if (messageId != null) {
            lastWeatherWarningPopupMessageId = messageId;
        }
        weatherWarningDialog = new AlertDialog.Builder(this)
                .setTitle("天气预警")
                .setMessage(content != null ? content : "")
                .setCancelable(false)
                .setPositiveButton("关闭", (d, which) -> {
                    if (messageId != null) {
                        RetrofitHelper.get().create(MessageApi.class).markRead(messageId)
                                .enqueue(new Callback<Void>() {
                                    @Override
                                    public void onResponse(Call<Void> call, Response<Void> response) {
                                    }

                                    @Override
                                    public void onFailure(Call<Void> call, Throwable t) {
                                    }
                                });
                    }
                })
                .create();
        weatherWarningDialog.setCanceledOnTouchOutside(false);
        weatherWarningDialog.show();
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
