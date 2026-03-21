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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;

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
import com.amap.api.maps.model.LatLng;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.badge.BadgeDrawable;
import com.google.android.material.badge.BadgeUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ourcat.app.BuildConfig;
import com.ourcat.app.R;
import com.ourcat.app.fragments.MapFragment;
import com.ourcat.app.fragments.ReportCatDialogFragment;
import com.ourcat.app.repositories.AuthApi;
import com.ourcat.app.repositories.MessageApi;
import com.ourcat.app.repositories.WeatherApi;
import com.ourcat.app.utils.PressFeedbackUtils;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private NavController navController;
    private AppBarConfiguration appBarConfiguration;
    private View mapContainer;
    private View navHostView;
    private View bottomBarContainer;
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
    private FloatingActionButton fabQuickCatReport;
    private FloatingActionButton fabQuickRescueSquare;
    private View quickActionOverlay;
    private View quickActionScrim;
    private View quickCatReportAction;
    private View quickRescueSquareAction;
    private AMapLocationClient quickCameraLocationClient;
    private ActivityResultLauncher<String[]> quickCameraPermissionLauncher;
    private ActivityResultLauncher<Uri> quickCameraLauncher;
    private ActivityResultLauncher<String> cameraPermissionLauncher;
    private Uri tempCameraUri;
    private Uri pendingImageUri;
    private QuickCameraAction pendingQuickCameraAction = QuickCameraAction.CAT_REPORT;
    private boolean quickFlowInProgress = false;
    private boolean quickFlowConsumed = false;
    private double lastQuickLat = 0;
    private double lastQuickLng = 0;
    private AlertDialog weatherWarningDialog;
    private Long lastWeatherWarningPopupMessageId = null;
    private static final String ARG_FROM_MAP = "fromMap";

    private interface WeatherLocationAction {
        void run(double lat, double lng, boolean isFallbackLocation);
    }

    private enum QuickCameraAction {
        CAT_REPORT,
        RESCUE_SQUARE
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
                                                    (lat, lng, isFallbackLocation) -> fetchWeatherWarning(lat, lng,
                                                            false, null, true,
                                                            isFallbackLocation));
                                        } else if (which == 1) {
                                            requestWeatherLocation(MainActivity.this::fetchTodayWeatherDebug);
                                        } else if (which == 2) {
                                            requestWeatherLocation(
                                                    (lat, lng, isFallbackLocation) -> fetchWeatherWarning(lat, lng,
                                                            true, "warning",
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
        bottomBarContainer = findViewById(R.id.bottom_bar_container);

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_community, R.id.nav_cats, R.id.nav_me).build();
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);
        toolbar.setNavigationIconTint(ContextCompat.getColor(this, android.R.color.white));
        toolbar.setNavigationOnClickListener(v -> onToolbarNavigateClick());

        if (savedInstanceState == null) {
            mapFragment = new MapFragment();
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.map_container, mapFragment, "persistent_map")
                    .commit();
        } else {
            mapFragment = (MapFragment) getSupportFragmentManager().findFragmentByTag("persistent_map");
            isMapVisible = savedInstanceState.getBoolean("is_map_visible", false);
            if (isMapVisible) {
                showMap();
            }
        }

        // 自定义底部导航栏点击处理
        LinearLayout navItemCommunity = findViewById(R.id.nav_item_community);
        LinearLayout navItemMap = findViewById(R.id.nav_item_map);
        LinearLayout navItemCats = findViewById(R.id.nav_item_cats);
        LinearLayout navItemMe = findViewById(R.id.nav_item_me);

        View.OnClickListener navClickListener = v -> {
            hideQuickActionMenu();
            hideMap();
            int destId = 0;
            int id = v.getId();
            if (id == R.id.nav_item_community) {
                destId = R.id.nav_community;
            } else if (id == R.id.nav_item_map) {
                showMap();
                updateNavSelection(R.id.nav_item_map);
                return;
            } else if (id == R.id.nav_item_cats) {
                destId = R.id.nav_cats;
            } else if (id == R.id.nav_item_me) {
                destId = R.id.nav_me;
            }
            if (destId != 0) {
                navigateToTopLevelDestination(destId, id);
            }
        };

        navItemCommunity.setOnClickListener(navClickListener);
        navItemMap.setOnClickListener(navClickListener);
        navItemCats.setOnClickListener(navClickListener);
        navItemMe.setOnClickListener(navClickListener);
        PressFeedbackUtils.attach(navItemCommunity, 0.96f);
        PressFeedbackUtils.attach(navItemMap, 0.96f);
        PressFeedbackUtils.attach(navItemCats, 0.96f);
        PressFeedbackUtils.attach(navItemMe, 0.96f);

        navController.addOnDestinationChangedListener((controller, dest, args) -> {
            int destId = dest.getId();

            if (!isMapVisible) {
                int selectedId = mapDestinationToNavItem(destId);
                if (selectedId != 0) {
                    updateNavSelection(selectedId);
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
                if (handleUnifiedBack()) {
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });

        setupKeyboardListener();
        // 初始化底部导航选中状态
        updateNavSelection(R.id.nav_item_community);
        initBadgeRefresh();
        setupQuickCameraFab();
    }

    private void setupQuickCameraFab() {
        quickActionOverlay = findViewById(R.id.quick_action_overlay);
        quickActionScrim = findViewById(R.id.quick_action_scrim);
        quickCatReportAction = findViewById(R.id.action_quick_cat_report);
        quickRescueSquareAction = findViewById(R.id.action_quick_rescue_square);
        fabQuickCatReport = findViewById(R.id.fab_quick_cat_report);
        fabQuickRescueSquare = findViewById(R.id.fab_quick_rescue_square);

        quickCameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    if (!quickFlowInProgress) {
                        return;
                    }
                    Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarse = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                    if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                        startQuickCameraLocation(pendingImageUri);
                    } else {
                        openQuickCameraTarget(31.23, 121.47, pendingImageUri);
                    }
                });

        quickCameraLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(), success -> {
                    if (!quickFlowInProgress) {
                        return;
                    }
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
                        resetQuickCameraState();
                    }
                });

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (!quickFlowInProgress) {
                        return;
                    }
                    if (granted) {
                        launchQuickCamera();
                    } else {
                        resetQuickCameraState();
                    }
                });

        fabQuickCamera = findViewById(R.id.fab_quick_camera);
        fabQuickCamera.setOnClickListener(v -> toggleQuickActionMenu());
        if (quickActionScrim != null) {
            quickActionScrim.setOnClickListener(v -> hideQuickActionMenu());
        }
        View.OnClickListener catReportClick = v -> {
            hideQuickActionMenu();
            startQuickFlow(QuickCameraAction.CAT_REPORT);
        };
        View.OnClickListener rescueSquareClick = v -> {
            hideQuickActionMenu();
            startQuickFlow(QuickCameraAction.RESCUE_SQUARE);
        };

        if (quickCatReportAction != null) {
            PressFeedbackUtils.attach(quickCatReportAction);
            quickCatReportAction.setOnClickListener(catReportClick);
        }
        if (fabQuickCatReport != null) {
            fabQuickCatReport.setOnClickListener(catReportClick);
        }
        if (quickRescueSquareAction != null) {
            PressFeedbackUtils.attach(quickRescueSquareAction);
            quickRescueSquareAction.setOnClickListener(rescueSquareClick);
        }
        if (fabQuickRescueSquare != null) {
            fabQuickRescueSquare.setOnClickListener(rescueSquareClick);
        }
    }

    private void toggleQuickActionMenu() {
        if (quickActionOverlay == null) {
            return;
        }
        if (quickActionOverlay.getVisibility() == View.VISIBLE) {
            hideQuickActionMenu();
        } else {
            showQuickActionMenu();
        }
    }

    private void showQuickActionMenu() {
        if (quickActionOverlay == null) {
            return;
        }
        quickActionOverlay.animate().cancel();
        quickActionOverlay.setAlpha(0f);
        quickActionOverlay.setVisibility(View.VISIBLE);
        quickActionOverlay.animate().alpha(1f).setDuration(160).setListener(null).start();
    }

    private void hideQuickActionMenu() {
        if (quickActionOverlay == null) {
            return;
        }
        if (quickActionOverlay.getVisibility() != View.VISIBLE) {
            return;
        }
        quickActionOverlay.animate().cancel();
        quickActionOverlay.animate().alpha(0f).setDuration(140).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                forceHideQuickActionMenu();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                forceHideQuickActionMenu();
            }
        }).start();
    }

    private void forceHideQuickActionMenu() {
        if (quickActionOverlay == null) {
            return;
        }
        quickActionOverlay.animate().cancel();
        quickActionOverlay.setAlpha(0f);
        quickActionOverlay.setVisibility(View.GONE);
    }

    private void startQuickFlow(QuickCameraAction action) {
        if (quickFlowInProgress) {
            return;
        }
        resetQuickCameraState();
        quickFlowInProgress = true;
        quickFlowConsumed = false;
        pendingQuickCameraAction = action;
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchQuickCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
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
        pendingImageUri = imageUri;
        double[] cachedLocation = getCachedQuickLocation();
        if (cachedLocation != null) {
            openQuickCameraTarget(cachedLocation[0], cachedLocation[1], imageUri);
            return;
        }
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startQuickCameraLocation(imageUri);
        } else {
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
                if (!quickFlowInProgress || quickFlowConsumed) {
                    if (quickCameraLocationClient != null) {
                        quickCameraLocationClient.stopLocation();
                    }
                    return;
                }
                Uri targetImageUri = pendingImageUri != null ? pendingImageUri : imageUri;
                if (aMapLocation != null && aMapLocation.getErrorCode() == 0) {
                    lastQuickLat = aMapLocation.getLatitude();
                    lastQuickLng = aMapLocation.getLongitude();
                    openQuickCameraTarget(aMapLocation.getLatitude(), aMapLocation.getLongitude(), targetImageUri);
                } else {
                    openQuickCameraTarget(31.23, 121.47, targetImageUri);
                }
                if (quickCameraLocationClient != null) {
                    quickCameraLocationClient.stopLocation();
                }
            });
            quickCameraLocationClient.startLocation();
        } catch (Exception e) {
            openQuickCameraTarget(31.23, 121.47, imageUri);
        }
    }

    private void startQuickCameraLocation() {
        startQuickCameraLocation(null);
    }

    private void openQuickCameraTarget(double lat, double lng, Uri imageUri) {
        if (!quickFlowInProgress || quickFlowConsumed) {
            return;
        }
        quickFlowConsumed = true;
        QuickCameraAction action = pendingQuickCameraAction;
        resetQuickCameraState();
        if (action == QuickCameraAction.RESCUE_SQUARE) {
            openQuickRescueSquarePost(lat, lng, imageUri);
            return;
        }
        openReportDialogWithCamera(lat, lng, imageUri);
    }

    private void resetQuickCameraState() {
        pendingImageUri = null;
        tempCameraUri = null;
        pendingQuickCameraAction = QuickCameraAction.CAT_REPORT;
        quickFlowInProgress = false;
        quickFlowConsumed = false;
    }

    private double[] getCachedQuickLocation() {
        if (mapFragment != null) {
            LatLng latLng = mapFragment.getCurrentLocationForQuickAction();
            if (latLng != null) {
                lastQuickLat = latLng.latitude;
                lastQuickLng = latLng.longitude;
                return new double[] { latLng.latitude, latLng.longitude };
            }
        }
        if (lastQuickLat != 0 || lastQuickLng != 0) {
            return new double[] { lastQuickLat, lastQuickLng };
        }
        return null;
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

    private void openQuickRescueSquarePost(double lat, double lng, Uri imageUri) {
        hideMap();
        updateNavSelection(R.id.nav_item_community);
        Bundle args = new Bundle();
        args.putString("presetType", "rescue");
        args.putDouble("presetLat", lat);
        args.putDouble("presetLng", lng);
        if (imageUri != null) {
            args.putParcelable("presetImageUri", imageUri);
        }
        if (navController != null) {
            navController.navigate(R.id.create_square_post, args);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("is_map_visible", isMapVisible);
    }

    private void showMap() {
        hideQuickActionMenu();
        isMapVisible = true;
        mapContainer.setVisibility(View.VISIBLE);
        navHostView.setVisibility(View.GONE);
        if (bottomBarContainer != null) {
            bottomBarContainer.setVisibility(View.VISIBLE);
        }
        updateNavSelection(R.id.nav_item_map);
        applyToolbarForMap();
        invalidateOptionsMenu();
    }

    /**
     * 显示地图并移动到指定位置
     * 
     * @param lat 纬度
     * @param lng 经度
     */
    public void showMapAtLocation(double lat, double lng) {
        showMap();
        if (mapFragment != null) {
            mapFragment.navigateTo(lat, lng);
        }
    }

    public void showMapAtRescueLocation(double lat, double lng) {
        showMap();
        if (mapFragment != null) {
            mapFragment.navigateTo(lat, lng);
            mapFragment.showRescueFocusMarker(lat, lng);
        }
    }

    public void showMapAtRescueLocationSolved(double lat, double lng) {
        showMap();
        if (mapFragment != null) {
            mapFragment.navigateTo(lat, lng);
            mapFragment.showRescueFocusMarker(lat, lng, true, true);
        }
    }

    /**
     * 显示地图并跳转到指定猫咪的位置
     */
    public void showMapAtCatLocation(long catId) {
        showMap();
        if (mapFragment != null) {
            mapFragment.navigateToCatLocation(catId);
        }
    }

    public void showMapAtCatLocationNear(long catId, double lat, double lng) {
        showMap();
        if (mapFragment != null) {
            mapFragment.navigateToCatLocationNearForceReload(catId, lat, lng);
        }
    }

    public void showMapAtCatLocationNearSolved(long catId, double lat, double lng) {
        showMap();
        if (mapFragment != null) {
            mapFragment.navigateToCatLocationNearForceReload(catId, lat, lng);
            mapFragment.showRescueFocusMarker(lat, lng, true, true);
        }
    }

    public void showMapAtCatLocationNearestToUser(long catId) {
        showMap();
        if (mapFragment != null) {
            mapFragment.navigateToCatLocationNearestToUser(catId);
        }
    }

    /**
     * 显示地图（不移动到特定位置）
     */
    public void showMapView() {
        showMap();
    }

    /**
     * 通过 Navigation Controller 导航到指定页面
     */
    public void navigateTo(int destinationId, Bundle args) {
        if (navController != null) {
            navController.navigate(destinationId, args);
        }
    }

    public void navigateFromMap(int destinationId, Bundle args, int selectedNavItemId) {
        Bundle navArgs = args != null ? new Bundle(args) : new Bundle();
        navArgs.putBoolean(ARG_FROM_MAP, true);
        hideMap();
        if (selectedNavItemId != 0) {
            updateNavSelection(selectedNavItemId);
        }
        navigateTo(destinationId, navArgs);
    }

    private void hideMap() {
        hideQuickActionMenu();
        isMapVisible = false;
        mapContainer.setVisibility(View.GONE);
        navHostView.setVisibility(View.VISIBLE);
        if (bottomBarContainer != null) {
            bottomBarContainer.setVisibility(View.VISIBLE);
        }
        applyToolbarForNav();
        invalidateOptionsMenu();
    }

    private boolean handleUnifiedBack() {
        if (quickActionOverlay != null && quickActionOverlay.getVisibility() == View.VISIBLE) {
            hideQuickActionMenu();
            return true;
        }
        if (isMapVisible) {
            hideMap();
            if (navController != null && navController.getCurrentDestination() != null) {
                int selectedId = mapDestinationToNavItem(navController.getCurrentDestination().getId());
                if (selectedId != 0) {
                    updateNavSelection(selectedId);
                }
            }
            return true;
        }
        if (navController == null) {
            return false;
        }
        androidx.navigation.NavBackStackEntry entry = navController.getCurrentBackStackEntry();
        Bundle args = entry != null ? entry.getArguments() : null;
        boolean fromMap = args != null && args.getBoolean(ARG_FROM_MAP, false);
        if (fromMap) {
            navController.popBackStack();
            showMap();
            return true;
        }
        return navController.popBackStack();
    }

    private void onToolbarNavigateClick() {
        if (!handleUnifiedBack()) {
            NavigationUI.navigateUp(navController, appBarConfiguration);
        }
    }

    private void navigateToTopLevelDestination(int destId, int selectedNavItemId) {
        if (navController == null) {
            return;
        }
        if (navController.popBackStack(destId, false)) {
            updateNavSelection(selectedNavItemId);
            return;
        }
        NavOptions options = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false, true)
                .build();
        navController.navigate(destId, null, options);
        updateNavSelection(selectedNavItemId);
    }

    private void applyToolbarForMap() {
        toolbar.setTitle(R.string.title_map);
        toolbar.setNavigationIcon(null);
    }

    private void applyToolbarForNav() {
        if (navController == null) {
            return;
        }
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);
        toolbar.setNavigationIconTint(ContextCompat.getColor(this, android.R.color.white));
        toolbar.setNavigationOnClickListener(v -> onToolbarNavigateClick());
    }

    private int mapDestinationToNavItem(int destId) {
        if (destId == R.id.nav_community) {
            return R.id.nav_item_community;
        } else if (destId == R.id.forum_list
                || destId == R.id.nav_square
                || destId == R.id.rescue_list
                || destId == R.id.rescue_statistics
                || destId == R.id.rescue_guide) {
            return R.id.nav_item_community;
        } else if (destId == R.id.nav_me) {
            return R.id.nav_item_me;
        } else if (destId == R.id.nav_cats) {
            return R.id.nav_item_cats;
        } else if (destId == R.id.nav_org
                || destId == R.id.my_posts
                || destId == R.id.my_comments
                || destId == R.id.my_cat_reports
                || destId == R.id.my_tasks
                || destId == R.id.join_requests
                || destId == R.id.user_management) {
            return R.id.nav_item_me;
        }
        return 0;
    }

    private void updateNavSelection(int selectedId) {
        int[] navItemIds = {
                R.id.nav_item_community,
                R.id.nav_item_map,
                R.id.nav_item_cats,
                R.id.nav_item_me
        };
        int[] iconIds = {
                R.id.nav_icon_community,
                R.id.nav_icon_map,
                R.id.nav_icon_cats,
                R.id.nav_icon_me
        };
        int[] textIds = {
                R.id.nav_text_community,
                R.id.nav_text_map,
                R.id.nav_text_cats,
                R.id.nav_text_me
        };

        for (int i = 0; i < navItemIds.length; i++) {
            View item = findViewById(navItemIds[i]);
            ImageView icon = findViewById(iconIds[i]);
            TextView text = findViewById(textIds[i]);
            boolean isSelected = navItemIds[i] == selectedId;
            if (item != null) {
                item.setSelected(isSelected);
                item.setBackgroundResource(R.drawable.bg_bottom_nav_item);
                item.animate()
                        .scaleX(isSelected ? 1.0f : 0.98f)
                        .scaleY(isSelected ? 1.0f : 0.98f)
                        .translationY(isSelected ? -2f : 0f)
                        .setDuration(180)
                        .start();
            }
            if (icon != null) {
                icon.setColorFilter(isSelected ? getColor(R.color.nav_selected) : getColor(R.color.nav_unselected));
                icon.setAlpha(isSelected ? 1f : 0.82f);
            }
            if (text != null) {
                text.setTextColor(isSelected ? getColor(R.color.nav_selected) : getColor(R.color.nav_unselected));
                text.setAlpha(isSelected ? 1f : 0.92f);
            }
        }
    }

    private void setupKeyboardListener() {
        final View rootView = findViewById(android.R.id.content);
        final View bottomBarContainer = findViewById(R.id.bottom_bar_container);
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;
            if (keypadHeight > screenHeight * 0.15) {
                if (bottomBarContainer != null)
                    bottomBarContainer.setVisibility(View.GONE);
                if (fabQuickCamera != null)
                    fabQuickCamera.setVisibility(View.GONE);
                forceHideQuickActionMenu();
            } else {
                if (bottomBarContainer != null)
                    bottomBarContainer.setVisibility(View.VISIBLE);
                if (fabQuickCamera != null)
                    fabQuickCamera.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        forceHideQuickActionMenu();
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
        requestWeatherLocation((lat, lng, isFallbackLocation) -> fetchWeatherWarning(lat, lng, false, null, false,
                isFallbackLocation));
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
