package com.ourcat.app.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.TextureMapView;
import com.amap.api.maps.model.BitmapDescriptor;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ourcat.app.R;
import com.ourcat.app.repositories.CatApi;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import com.amap.api.maps.model.HeatmapTileProvider;
import com.amap.api.maps.model.TileOverlay;
import com.amap.api.maps.model.TileOverlayOptions;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

public class MapFragment extends Fragment implements AMapLocationListener {

    private static final String TAG = "MapFragment";
    private static final double CLUSTER_DISTANCE_DP = 22;
    private static final double RESCUE_CLUSTER_DISTANCE_RATIO = 0.5;
    private static final float MAX_ZOOM = 20f;

    private TextureMapView mapView;
    private AMap aMap;
    private AMapLocationClient locationClient;
    private TextView catCountText;
    private TextView modeIndicator;
    private final List<Map<String, Object>> allLocations = new ArrayList<>();
    private final java.util.Map<String, Object> markerDataMap = new HashMap<>();

    private double currentLat = 0;
    private double currentLng = 0;
    private boolean firstLocate = true;
    private boolean pendingMoveToCurrentLocation = false;
    private LatLng pendingNavigateLatLng;
    private Long pendingNavigateCatId;
    private LatLng pendingNavigateCatReference;
    private boolean pendingNavigateCatUseCurrentLocation = false;
    private float density;

    private boolean isHeatmapMode = false;
    private TileOverlay heatmapOverlay;
    private final List<Map<String, Object>> heatmapData = new ArrayList<>();
    private final List<Map<String, Object>> recommendData = new ArrayList<>();

    private FloatingActionButton fabHeatmap;
    private FloatingActionButton fabRecommend;
    private FloatingActionButton fabHospital;

    private BottomSheetBehavior<View> bottomSheetBehavior;
    private RecyclerView recommendList;
    private TextView recommendEmpty;
    private boolean isHighRecommend = true;

    private boolean lastWasPreciseMode = false;
    private Marker currentMarker;
    private android.os.Handler heatmapHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable heatmapRunnable;
    private static final long HEATMAP_DEBOUNCE_MS = 300;

    private boolean showHospitals = false;
    private final List<Marker> hospitalMarkers = new ArrayList<>();
    private final List<PoiItem> hospitalPoiList = new ArrayList<>();
    private static final String HOSPITAL_MARKER_TYPE = "hospital";
    private static final String NEED_RESCUE_MARKER_TYPE = "need_rescue";
    private static final String RESCUE_CAT_MARKER_TYPE = "rescue_cat";
    private final List<Map<String, Object>> needRescueList = new ArrayList<>();
    private final List<Marker> needRescueMarkers = new ArrayList<>();
    private final java.util.Map<String, Marker> rescuePulseMarkers = new HashMap<>();
    private final java.util.Map<String, Bitmap> rescuePulsePhotos = new HashMap<>();
    private final java.util.Map<String, Integer> rescuePulseColors = new HashMap<>();
    private final java.util.Map<Integer, Bitmap> rescuePlaceholderPhotoCache = new HashMap<>();
    private Runnable rescueIconPulseRunnable;
    private float rescueIconPulseProgress = 0f;
    private static final long RESCUE_ICON_PULSE_FRAME_MS = 90;
    private LatLng pendingRescueFocusLatLng;
    private boolean pendingRescueFocusSolved = false;
    private boolean pendingRescueFocusOneShot = false;
    private boolean pendingRescueFocusAppliedOnce = false;
    private Marker rescueFocusMarker;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                Boolean coarse = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    enableMyLocation();
                    startLocation();
                    if (pendingMoveToCurrentLocation && isAdded()) {
                        Toast.makeText(requireContext(), "正在定位当前位置...", Toast.LENGTH_SHORT).show();
                    }
                } else if (isAdded()) {
                    pendingMoveToCurrentLocation = false;
                    Toast.makeText(requireContext(), "需要定位权限才能使用地图功能", Toast.LENGTH_SHORT).show();
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);
        density = getResources().getDisplayMetrics().density;

        mapView = view.findViewById(R.id.map_view);
        mapView.onCreate(savedInstanceState);
        catCountText = view.findViewById(R.id.map_cat_count);
        modeIndicator = view.findViewById(R.id.map_mode_indicator);

        initMap();
        checkPermissionAndLocate();
        initBottomSheet(view);

        FloatingActionButton fabLocation = view.findViewById(R.id.fab_location);
        fabLocation.setOnClickListener(v -> moveToCurrentLocation());

        FloatingActionButton fabRefresh = view.findViewById(R.id.fab_refresh);
        fabRefresh.setOnClickListener(v -> {
            if (isAdded())
                Toast.makeText(requireContext(), "刷新中...", Toast.LENGTH_SHORT).show();
            if (isHeatmapMode) {
                loadHeatmapData();
            } else {
                loadLocations();
                loadNeedRescue();
            }
        });

        fabHeatmap = view.findViewById(R.id.fab_heatmap);
        fabHeatmap.setOnClickListener(v -> toggleHeatmapMode());

        fabRecommend = view.findViewById(R.id.fab_recommend);
        fabRecommend.setOnClickListener(v -> toggleRecommendSheet());

        fabHospital = view.findViewById(R.id.fab_hospital);
        fabHospital.setOnClickListener(v -> togglePetHospitals());

        FloatingActionButton fabReport = view.findViewById(R.id.map_fab_report);
        fabReport.setOnClickListener(v -> {
            ReportCatDialogFragment dialog = ReportCatDialogFragment.newInstance(currentLat, currentLng);
            dialog.setOnReportSuccess(this::loadLocations);
            dialog.show(getChildFragmentManager(), "report");
        });

        if (bottomSheetBehavior != null) {
            bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                    if (fabRecommend != null) {
                        fabRecommend.setActivated(newState == BottomSheetBehavior.STATE_EXPANDED);
                    }
                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                }
            });
        }
        if (fabHeatmap != null) {
            fabHeatmap.setActivated(isHeatmapMode);
        }
        if (fabHospital != null) {
            fabHospital.setActivated(showHospitals);
        }
        if (fabRecommend != null && bottomSheetBehavior != null) {
            fabRecommend.setActivated(bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED);
        }

        loadLocations();
        loadNeedRescue();
        return view;
    }

    private void initBottomSheet(View view) {
        View bottomSheet = view.findViewById(R.id.bottom_sheet_recommend);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

        recommendList = view.findViewById(R.id.recommend_list);
        recommendList.setLayoutManager(new LinearLayoutManager(requireContext()));
        recommendEmpty = view.findViewById(R.id.recommend_empty);

        Chip chipHigh = view.findViewById(R.id.chip_high);
        Chip chipLow = view.findViewById(R.id.chip_low);
        TextView recommendTitle = view.findViewById(R.id.recommend_title);

        chipHigh.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                isHighRecommend = true;
                recommendTitle.setText("附近猫咪活跃区域");
                loadRecommendData();
            }
        });

        chipLow.setOnCheckedChangeListener((btn, checked) -> {
            if (checked) {
                isHighRecommend = false;
                recommendTitle.setText("附近猫咪活跃区域");
                loadRecommendData();
            }
        });
    }

    private void toggleHeatmapMode() {
        isHeatmapMode = !isHeatmapMode;
        if (fabHeatmap != null) {
            fabHeatmap.setActivated(isHeatmapMode);
        }
        if (isHeatmapMode) {
            modeIndicator.setVisibility(View.VISIBLE);
            modeIndicator.setText(" 热力图模式");
            android.graphics.drawable.Drawable fireIcon = androidx.core.content.ContextCompat
                    .getDrawable(requireContext(), R.drawable.fire_alert);
            if (fireIcon != null) {
                int iconSizePx = (int) (getResources().getDisplayMetrics().density * 14);
                fireIcon.setBounds(0, 0, iconSizePx, iconSizePx);
                fireIcon.setTint(Color.WHITE);
                modeIndicator.setCompoundDrawablesRelative(fireIcon, null, null, null);
            }
            modeIndicator.setCompoundDrawablePadding(8);

            lastWasPreciseMode = false;
            loadHeatmapData();
        } else {
            modeIndicator.setVisibility(View.GONE);
            if (heatmapRunnable != null) {
                heatmapHandler.removeCallbacks(heatmapRunnable);
            }
            removeHeatmap();
            clusterAndDisplay();
        }
        if (isAdded()) {
            Toast.makeText(requireContext(),
                    isHeatmapMode ? "已切换到热力图模式" : "已切换到标记模式",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleRecommendSheet() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        } else {
            loadRecommendData();
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
        if (fabRecommend != null) {
            fabRecommend.setActivated(bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void togglePetHospitals() {
        showHospitals = !showHospitals;
        if (fabHospital != null) {
            fabHospital.setActivated(showHospitals);
        }
        if (showHospitals) {
            searchPetHospitals();
            if (isAdded()) {
                Toast.makeText(requireContext(), "正在搜索附近宠物医院...", Toast.LENGTH_SHORT).show();
            }
        } else {
            removeHospitalMarkers();
            if (isAdded()) {
                Toast.makeText(requireContext(), "已隐藏宠物医院", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void searchPetHospitals() {
        if (aMap == null)
            return;
        double centerLat = currentLat != 0 ? currentLat : aMap.getCameraPosition().target.latitude;
        double centerLng = currentLng != 0 ? currentLng : aMap.getCameraPosition().target.longitude;

        try {
            PoiSearch.Query query = new PoiSearch.Query("宠物医院", "", "");
            query.setPageSize(30);
            query.setPageNum(0);

            PoiSearch poiSearch = new PoiSearch(requireContext(), query);
            poiSearch.setBound(new PoiSearch.SearchBound(new LatLonPoint(centerLat, centerLng), 10000));
            poiSearch.setOnPoiSearchListener(new PoiSearch.OnPoiSearchListener() {
                @Override
                public void onPoiSearched(PoiResult result, int rCode) {
                    if (!isAdded() || aMap == null)
                        return;
                    if (rCode == 1000 && result != null && result.getPois() != null) {
                        requireActivity().runOnUiThread(() -> {
                            hospitalPoiList.clear();
                            hospitalPoiList.addAll(result.getPois());
                            removeHospitalMarkers();
                            addHospitalMarkers();
                            if (isAdded()) {
                                Toast.makeText(requireContext(),
                                        "找到 " + hospitalPoiList.size() + " 家宠物医院",
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else if (isAdded()) {
                        Toast.makeText(requireContext(), "搜索宠物医院失败", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onPoiItemSearched(PoiItem item, int rCode) {
                }
            });
            poiSearch.searchPOIAsyn();
        } catch (Exception e) {
            Log.e(TAG, "Search pet hospitals failed", e);
            if (isAdded()) {
                Toast.makeText(requireContext(), "搜索失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private BitmapDescriptor getVectorBitmapDescriptor(int id) {
        android.graphics.drawable.Drawable vectorDrawable = ContextCompat.getDrawable(requireContext(), id);
        if (vectorDrawable == null)
            return BitmapDescriptorFactory.defaultMarker();
        vectorDrawable = vectorDrawable.mutate();
        int baseSize = (int) (36 * density);
        int outline = Math.max(2, (int) (2.5f * density));
        int w = baseSize + outline * 2;
        int h = baseSize + outline * 2;
        Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bm);

        Bitmap mask = Bitmap.createBitmap(baseSize, baseSize, Bitmap.Config.ARGB_8888);
        Canvas maskCanvas = new Canvas(mask);
        vectorDrawable.setBounds(0, 0, baseSize, baseSize);
        vectorDrawable.setTint(Color.WHITE);
        vectorDrawable.draw(maskCanvas);

        Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outlinePaint.setAlpha(230);
        for (int dx = -outline; dx <= outline; dx++) {
            for (int dy = -outline; dy <= outline; dy++) {
                if (dx * dx + dy * dy > outline * outline)
                    continue;
                canvas.drawBitmap(mask, outline + dx, outline + dy, outlinePaint);
            }
        }

        vectorDrawable.setBounds(outline, outline, outline + baseSize, outline + baseSize);
        vectorDrawable.setTint(ContextCompat.getColor(requireContext(), R.color.primary));
        vectorDrawable.draw(canvas);
        return BitmapDescriptorFactory.fromBitmap(bm);
    }

    private void openNavChooser(double lat, double lng, String name) {
        java.util.ArrayList<android.content.Intent> intents = new java.util.ArrayList<>();
        android.content.pm.PackageManager pm = requireContext().getPackageManager();

        android.net.Uri uriGaode = android.net.Uri.parse("androidamap://route?sourceApplication=OurCat&dlat=" + lat
                + "&dlon=" + lng + "&dname=" + (name != null ? name : "") + "&dev=0&t=0");
        android.content.Intent gaode = new android.content.Intent(android.content.Intent.ACTION_VIEW, uriGaode);
        gaode.setPackage("com.autonavi.minimap");
        if (gaode.resolveActivity(pm) != null)
            intents.add(gaode);

        android.net.Uri uriBaidu = android.net.Uri.parse("baidumap://map/direction?destination=latlng:" + lat + ","
                + lng + "|name:" + (name != null ? name : "") + "&mode=driving&src=OurCat");
        android.content.Intent baidu = new android.content.Intent(android.content.Intent.ACTION_VIEW, uriBaidu);
        baidu.setPackage("com.baidu.BaiduMap");
        if (baidu.resolveActivity(pm) != null)
            intents.add(baidu);

        android.net.Uri uriTencent = android.net.Uri.parse("qqmap://map/routeplan?type=drive&to="
                + (name != null ? name : "") + "&tocoord=" + lat + "," + lng + "&policy=0&referer=OurCat");
        android.content.Intent tencent = new android.content.Intent(android.content.Intent.ACTION_VIEW, uriTencent);
        tencent.setPackage("com.tencent.map");
        if (tencent.resolveActivity(pm) != null)
            intents.add(tencent);

        android.net.Uri uriGoogle = android.net.Uri
                .parse("https://www.google.com/maps/dir/?api=1&destination=" + lat + "," + lng);
        android.content.Intent google = new android.content.Intent(android.content.Intent.ACTION_VIEW, uriGoogle);
        google.setPackage("com.google.android.apps.maps");
        if (google.resolveActivity(pm) != null)
            intents.add(google);

        if (intents.isEmpty()) {
            android.net.Uri fallback = android.net.Uri.parse(
                    "geo:" + lat + "," + lng + "?q=" + lat + "," + lng + "(" + (name != null ? name : "目的地") + ")");
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, fallback);
            startActivity(intent);
            return;
        }
        android.content.Intent base = intents.remove(0);
        android.content.Intent chooser = android.content.Intent.createChooser(base, "选择导航应用");
        chooser.putExtra(android.content.Intent.EXTRA_INITIAL_INTENTS, intents.toArray(new android.os.Parcelable[0]));
        startActivity(chooser);
    }

    private void addHospitalMarkers() {
        if (aMap == null || hospitalPoiList.isEmpty())
            return;
        BitmapDescriptor hospitalIcon = getVectorBitmapDescriptor(R.drawable.hospital_marker);
        for (PoiItem poi : hospitalPoiList) {
            LatLonPoint point = poi.getLatLonPoint();
            if (point == null)
                continue;
            LatLng latLng = new LatLng(point.getLatitude(), point.getLongitude());
            String title = poi.getTitle() != null ? poi.getTitle() : "宠物医院";
            String snippet = poi.getSnippet() != null ? poi.getSnippet() : "";
            if (poi.getTel() != null && !poi.getTel().isEmpty()) {
                snippet = snippet.isEmpty() ? poi.getTel() : snippet + "\n" + poi.getTel();
            }

            Map<String, Object> data = new HashMap<>();
            data.put("type", HOSPITAL_MARKER_TYPE);
            data.put("name", title);
            data.put("address", poi.getSnippet());
            data.put("tel", poi.getTel());
            data.put("lat", point.getLatitude());
            data.put("lng", point.getLongitude());

            Marker marker = aMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title(title)
                    .snippet(snippet)
                    .icon(hospitalIcon)
                    .anchor(0.5f, 1.0f)
                    .zIndex(15));
            markerDataMap.put(marker.getId(), data);
            hospitalMarkers.add(marker);
        }
    }

    private void removeHospitalMarkers() {
        for (Marker m : hospitalMarkers) {
            if (m != null) {
                markerDataMap.remove(m.getId());
                m.remove();
            }
        }
        hospitalMarkers.clear();
    }

    private void scheduleHeatmapUpdate(float zoom) {
        boolean shouldBePrecise = zoom >= 15;

        if (shouldBePrecise == lastWasPreciseMode && heatmapOverlay != null) {
            return;
        }

        if (heatmapRunnable != null) {
            heatmapHandler.removeCallbacks(heatmapRunnable);
        }

        heatmapRunnable = () -> {
            if (!isAdded() || !isHeatmapMode)
                return;
            lastWasPreciseMode = shouldBePrecise;
            renderHeatmap();
        };
        heatmapHandler.postDelayed(heatmapRunnable, HEATMAP_DEBOUNCE_MS);
    }

    private void initMap() {
        if (aMap != null)
            return;
        aMap = mapView.getMap();
        aMap.getUiSettings().setZoomControlsEnabled(false);
        aMap.getUiSettings().setCompassEnabled(true);
        aMap.getUiSettings().setScaleControlsEnabled(true);
        aMap.getUiSettings().setMyLocationButtonEnabled(false);

        aMap.setInfoWindowAdapter(new AMap.InfoWindowAdapter() {
            @Override
            public View getInfoWindow(Marker marker) {
                return createInfoWindowView(marker);
            }

            @Override
            public View getInfoContents(Marker marker) {
                return null;
            }
        });

        aMap.setOnMarkerClickListener(marker -> {
            if (currentMarker != null && currentMarker != marker && currentMarker.isInfoWindowShown()) {
                currentMarker.hideInfoWindow();
                currentMarker = null;
            }
            Object data = markerDataMap.get(marker.getId());
            if (data == null) {
                return false;
            }
            if (data instanceof Map) {
                Object type = ((Map<?, ?>) data).get("type");
                if (HOSPITAL_MARKER_TYPE.equals(type)
                        || NEED_RESCUE_MARKER_TYPE.equals(type)
                        || RESCUE_CAT_MARKER_TYPE.equals(type)) {
                    if (currentMarker != null && currentMarker.isInfoWindowShown()) {
                        currentMarker.hideInfoWindow();
                    }
                    currentMarker = marker;
                    marker.showInfoWindow();
                    return true;
                }
            }
            if (data instanceof List) {
                if (currentMarker != null && currentMarker.isInfoWindowShown()) {
                    currentMarker.hideInfoWindow();
                    currentMarker = null;
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cluster = (List<Map<String, Object>>) data;
                float zoom = aMap.getCameraPosition().zoom;
                if (zoom >= MAX_ZOOM - 0.5f) {
                    if (isRescueCluster(cluster)) {
                        showRescueClusterListDialog(cluster);
                    } else {
                        showClusterListDialog(cluster);
                    }
                } else {
                    zoomToCluster(cluster);
                }
                return true;
            }
            if (currentMarker != null && currentMarker.isInfoWindowShown()) {
                currentMarker.hideInfoWindow();
            }
            currentMarker = marker;
            marker.showInfoWindow();
            return true;
        });

        aMap.setOnInfoWindowClickListener(marker -> {
            Object data = markerDataMap.get(marker.getId());
            if (data instanceof Map && NEED_RESCUE_MARKER_TYPE.equals(((Map<?, ?>) data).get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rescueData = (Map<String, Object>) data;
                Object squarePostIdObj = rescueData.get("squarePostId");
                if (squarePostIdObj instanceof Number && ((Number) squarePostIdObj).longValue() > 0) {
                    navigateToSquarePost(((Number) squarePostIdObj).longValue());
                }
                return;
            }
            if (data instanceof Map && HOSPITAL_MARKER_TYPE.equals(((Map<?, ?>) data).get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> hospitalData = (Map<String, Object>) data;
                Object latObj = hospitalData.get("lat");
                Object lngObj = hospitalData.get("lng");
                if (latObj != null && lngObj != null) {
                    double lat = ((Number) latObj).doubleValue();
                    double lng = ((Number) lngObj).doubleValue();
                    String name = hospitalData.get("name") != null ? hospitalData.get("name").toString() : "";
                    openNavChooser(lat, lng, name);
                }
                return;
            }
            if (data instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> catData = (Map<String, Object>) data;
                if (isTrue(catData.get("hasActiveRescue"))) {
                    Object squarePostIdObj = catData.get("rescueSquarePostId");
                    if (squarePostIdObj instanceof Number && ((Number) squarePostIdObj).longValue() > 0) {
                        navigateToSquarePost(((Number) squarePostIdObj).longValue());
                        return;
                    }
                }
                Object catIdObj = catData.get("catId");
                if (catIdObj instanceof Number && ((Number) catIdObj).longValue() > 0) {
                    long catId = ((Number) catIdObj).longValue();
                    openCatProfile(catId);
                } else if (isAdded()) {
                    Toast.makeText(requireContext(), "该上报尚未关联猫咪档案", Toast.LENGTH_SHORT).show();
                }
            }
        });

        aMap.setOnMapClickListener(latLng -> {
            if (currentMarker != null && currentMarker.isInfoWindowShown()) {
                currentMarker.hideInfoWindow();
                currentMarker = null;
            }
        });

        aMap.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition pos) {
            }

            @Override
            public void onCameraChangeFinish(CameraPosition pos) {
                if (isHeatmapMode) {
                    scheduleHeatmapUpdate(pos.zoom);
                } else if (!allLocations.isEmpty() || !needRescueList.isEmpty()) {
                    clusterAndDisplay();
                    buildRecommendFromLocal();
                }
            }
        });

        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(23.13, 113.26), 14));
        applyPendingNavigation();
    }

    private void showClusterListDialog(List<Map<String, Object>> cluster) {
        if (!isAdded() || cluster.isEmpty())
            return;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_cluster_list, null);
        RecyclerView rv = dialogView.findViewById(R.id.cluster_list);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("附近发现 " + cluster.size() + " 只猫咪")
                .setView(dialogView)
                .setNegativeButton("关闭", null)
                .create();

        rv.setAdapter(new RecyclerView.Adapter<ClusterVH>() {
            @NonNull
            @Override
            public ClusterVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new ClusterVH(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_cat_report, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull ClusterVH h, int position) {
                Map<String, Object> item = cluster.get(position);
                String colorStr = str(item.get("color"));
                h.color.setText(colorStr.isEmpty() ? "猫咪" : colorStr + "猫");

                String featureStr = str(item.get("feature"));
                String personalityStr = str(item.get("personality"));
                String combined = featureStr;
                if (!personalityStr.isEmpty()) {
                    combined = combined.isEmpty() ? personalityStr : combined + " · " + personalityStr;
                }
                h.feature.setText(combined.isEmpty() ? "暂无描述" : combined);

                Object latObj = item.get("lat");
                Object lngObj = item.get("lng");
                if (latObj != null && lngObj != null) {
                    h.location.setText(String.format("📍 %.4f, %.4f",
                            ((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue()));
                }

                String timeStr = str(item.get("reportTime"));
                h.time.setText(timeStr.isEmpty() ? "" : "🕐 " + timeStr);

                String imageUrl = str(item.get("imageUrl"));
                if (!imageUrl.isEmpty()) {
                    Glide.with(h.image).load(imageUrl).centerCrop().into(h.image);
                } else {
                    h.image.setImageResource(android.R.color.darker_gray);
                }

                h.delete.setVisibility(View.GONE);

                // 显示"查看档案"按钮
                Object catIdObj = item.get("catId");
                if (catIdObj != null && h.btnViewProfile != null) {
                    h.btnViewProfile.setVisibility(View.VISIBLE);
                    h.btnViewProfile.setOnClickListener(v -> {
                        dialog.dismiss();
                        long catId = ((Number) catIdObj).longValue();
                        openCatProfile(catId);
                    });
                } else if (h.btnViewProfile != null) {
                    h.btnViewProfile.setVisibility(View.GONE);
                }

                // 整个卡片点击也可以进入档案
                h.itemView.setOnClickListener(v -> {
                    dialog.dismiss();
                    if (catIdObj != null) {
                        long catId = ((Number) catIdObj).longValue();
                        openCatProfile(catId);
                    } else if (latObj != null && lngObj != null) {
                        aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue()),
                                MAX_ZOOM));
                    }
                });
            }

            @Override
            public int getItemCount() {
                return cluster.size();
            }
        });

        dialog.show();
    }

    private void showRescueClusterListDialog(List<Map<String, Object>> cluster) {
        if (!isAdded() || cluster.isEmpty())
            return;

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_cluster_list, null);
        RecyclerView rv = dialogView.findViewById(R.id.cluster_list);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("附近救助信息 " + cluster.size() + " 条")
                .setView(dialogView)
                .setNegativeButton("关闭", null)
                .create();

        rv.setAdapter(new RecyclerView.Adapter<ClusterVH>() {
            @NonNull
            @Override
            public ClusterVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new ClusterVH(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_cat_report, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull ClusterVH h, int position) {
                Map<String, Object> item = cluster.get(position);
                boolean isNeedRescue = NEED_RESCUE_MARKER_TYPE.equals(str(item.get("type")));
                h.color.setText(isNeedRescue ? buildNeedRescueTitle(item) : buildRescueCatTitle(item));
                h.feature.setText(isNeedRescue ? buildNeedRescueSubtitle(item) : buildRescueCatSubtitle(item));

                Object latObj = item.get("lat");
                Object lngObj = item.get("lng");
                if (latObj != null && lngObj != null) {
                    h.location.setText(String.format("📍 %.4f, %.4f",
                            ((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue()));
                }

                String timeStr = str(item.get("reportTime"));
                if (timeStr.isEmpty()) {
                    timeStr = str(item.get("time"));
                }
                h.time.setText(timeStr.isEmpty() ? "" : "🕐 " + timeStr);

                if (isNeedRescue) {
                    h.image.setImageResource(R.drawable.cat_blink);
                    h.image.setColorFilter(ContextCompat.getColor(requireContext(), R.color.error));
                } else {
                    String imageUrl = str(item.get("imageUrl"));
                    if (!imageUrl.isEmpty()) {
                        Glide.with(h.image).load(imageUrl).centerCrop().into(h.image);
                    } else {
                        h.image.setImageResource(android.R.color.darker_gray);
                    }
                    h.image.clearColorFilter();
                }

                h.delete.setVisibility(View.GONE);

                MaterialButton btn = h.btnViewProfile instanceof MaterialButton ? (MaterialButton) h.btnViewProfile
                        : null;
                Object catIdObj = item.get("catId");
                if (btn != null) {
                    if (isNeedRescue) {
                        btn.setVisibility(View.VISIBLE);
                        btn.setText("🆘 查看救助");
                        btn.setOnClickListener(v -> {
                            dialog.dismiss();
                            openRescueClusterItem(item);
                        });
                    } else if (catIdObj instanceof Number && ((Number) catIdObj).longValue() > 0) {
                        btn.setVisibility(View.VISIBLE);
                        btn.setText("📋 查看猫咪档案");
                        btn.setOnClickListener(v -> {
                            dialog.dismiss();
                            openCatProfile(((Number) catIdObj).longValue());
                        });
                    } else {
                        btn.setVisibility(View.GONE);
                    }
                }

                h.itemView.setOnClickListener(v -> {
                    dialog.dismiss();
                    openRescueClusterItem(item);
                });
            }

            @Override
            public int getItemCount() {
                return cluster.size();
            }
        });

        dialog.show();
    }

    private boolean isRescueCluster(List<Map<String, Object>> cluster) {
        for (Map<String, Object> item : cluster) {
            Object type = item.get("type");
            if (NEED_RESCUE_MARKER_TYPE.equals(type) || RESCUE_CAT_MARKER_TYPE.equals(type)) {
                return true;
            }
            if (isTrue(item.get("hasActiveRescue"))) {
                return true;
            }
        }
        return false;
    }

    private void openRescueClusterItem(Map<String, Object> item) {
        boolean isNeedRescue = NEED_RESCUE_MARKER_TYPE.equals(str(item.get("type")));
        if (isNeedRescue) {
            Object squarePostIdObj = item.get("squarePostId");
            if (squarePostIdObj instanceof Number && ((Number) squarePostIdObj).longValue() > 0) {
                navigateToSquarePost(((Number) squarePostIdObj).longValue());
                return;
            }
            Object activityIdObj = item.get("activityId");
            if (activityIdObj instanceof Number) {
                Bundle args = new Bundle();
                args.putLong("activityId", ((Number) activityIdObj).longValue());
                if (getActivity() instanceof com.ourcat.app.activities.MainActivity) {
                    ((com.ourcat.app.activities.MainActivity) getActivity())
                            .navigateFromMap(R.id.rescue_detail, args, R.id.nav_item_community);
                }
                return;
            }
        } else {
            Object squarePostIdObj = item.get("rescueSquarePostId");
            if (squarePostIdObj instanceof Number && ((Number) squarePostIdObj).longValue() > 0) {
                navigateToSquarePost(((Number) squarePostIdObj).longValue());
                return;
            }
            Object catIdObj = item.get("catId");
            if (catIdObj instanceof Number && ((Number) catIdObj).longValue() > 0) {
                openCatProfile(((Number) catIdObj).longValue());
                return;
            }
        }
        Object latObj = item.get("lat");
        Object lngObj = item.get("lng");
        if (latObj instanceof Number && lngObj instanceof Number && aMap != null) {
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue()),
                    MAX_ZOOM));
        }
    }

    private String buildNeedRescueTitle(Map<String, Object> item) {
        String title = str(item.get("title"));
        String catName = str(item.get("catName"));
        if (title.isEmpty()) {
            title = "需救助";
        }
        return catName.isEmpty() ? title : catName + " · " + title;
    }

    private String buildNeedRescueSubtitle(Map<String, Object> item) {
        String description = str(item.get("description"));
        return description.isEmpty() ? "点击查看救助帖" : description;
    }

    private String buildRescueCatTitle(Map<String, Object> item) {
        String colorStr = str(item.get("color"));
        return colorStr.isEmpty() ? "救助中的猫咪" : colorStr + "猫（救助中）";
    }

    private String buildRescueCatSubtitle(Map<String, Object> item) {
        String personalityStr = str(item.get("personality"));
        return personalityStr.isEmpty() ? "点击查看详情" : personalityStr;
    }

    static class ClusterVH extends RecyclerView.ViewHolder {
        ImageView image;
        TextView color, feature, location, time;
        View delete, btnViewProfile;

        ClusterVH(View v) {
            super(v);
            image = v.findViewById(R.id.item_image);
            color = v.findViewById(R.id.item_color);
            feature = v.findViewById(R.id.item_feature);
            location = v.findViewById(R.id.item_location);
            time = v.findViewById(R.id.item_time);
            delete = v.findViewById(R.id.item_delete);
            btnViewProfile = v.findViewById(R.id.btn_view_profile);
        }
    }

    private void zoomToCluster(List<Map<String, Object>> cluster) {
        if (cluster.size() < 2)
            return;
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        for (Map<String, Object> loc : cluster) {
            double lat = ((Number) loc.get("lat")).doubleValue();
            double lng = ((Number) loc.get("lng")).doubleValue();
            builder.include(new LatLng(lat, lng));
        }
        try {
            aMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), (int) (60 * density)));
        } catch (Exception e) {
            Log.e(TAG, "Zoom to cluster failed", e);
        }
    }

    private View createInfoWindowView(Marker marker) {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.map_info_window, null);
        TextView title = view.findViewById(R.id.info_title);
        TextView snippet = view.findViewById(R.id.info_snippet);
        ImageView image = view.findViewById(R.id.info_image);
        TextView actionHint = view.findViewById(R.id.info_action_hint);
        View btnRescue = view.findViewById(R.id.info_btn_start_rescue);

        title.setText(marker.getTitle());
        String snippetText = marker.getSnippet();
        if (snippetText != null && !snippetText.isEmpty()) {
            snippet.setText(snippetText);
            snippet.setVisibility(View.VISIBLE);
        } else {
            snippet.setVisibility(View.GONE);
        }

        if (btnRescue != null) {
            btnRescue.setVisibility(View.GONE);
        }

        Object data = markerDataMap.get(marker.getId());
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> catData = (Map<String, Object>) data;
            if (NEED_RESCUE_MARKER_TYPE.equals(catData.get("type"))) {
                String imageUrl = str(catData.get("imageUrl"));
                if (!imageUrl.isEmpty()) {
                    image.setVisibility(View.VISIBLE);
                    image.setColorFilter(null);
                    Glide.with(requireContext()).load(imageUrl).centerCrop().into(image);
                } else {
                    image.setImageResource(R.drawable.cat_blink);
                    image.setColorFilter(ContextCompat.getColor(requireContext(), R.color.error));
                    image.setVisibility(View.VISIBLE);
                }
                actionHint.setText("🆘 点击进入救助广场帖");
                actionHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.error));
                actionHint.setVisibility(View.VISIBLE);
                return view;
            }
            if (HOSPITAL_MARKER_TYPE.equals(catData.get("type"))) {
                image.setImageResource(R.drawable.hospital_building);
                image.setColorFilter(ContextCompat.getColor(requireContext(), R.color.primary));
                image.setVisibility(View.VISIBLE);
                actionHint.setText("📍 点击导航");
                actionHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary));
                actionHint.setVisibility(View.VISIBLE);
                return view;
            }
            Object imgUrl = catData.get("imageUrl");
            if (imgUrl != null && !imgUrl.toString().isEmpty()) {
                image.setVisibility(View.VISIBLE);
                Glide.with(requireContext()).load(imgUrl.toString()).centerCrop().into(image);
            } else {
                image.setVisibility(View.GONE);
            }

            // 根据是否有 catId 显示不同提示
            Object catIdObj = catData.get("catId");
            if (catIdObj instanceof Number && ((Number) catIdObj).longValue() > 0) {
                if (isTrue(catData.get("hasActiveRescue"))) {
                    actionHint.setText("🆘 当前处于待救助状态，点击查看");
                    actionHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.error));
                } else {
                    actionHint.setText("👆 点击查看猫咪档案");
                    actionHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary));
                    if (btnRescue != null) {
                        btnRescue.setVisibility(View.VISIBLE);
                        long catId = ((Number) catIdObj).longValue();
                        btnRescue.setOnClickListener(v -> showStartRescueFromMapDialog(catId));
                    }
                }
                actionHint.setVisibility(View.VISIBLE);
            } else {
                actionHint.setText("⏳ 匹配中，稍后可查看档案");
                actionHint.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary));
                actionHint.setVisibility(View.VISIBLE);
            }
        } else {
            image.setVisibility(View.GONE);
            actionHint.setVisibility(View.GONE);
        }
        return view;
    }

    private void showStartRescueFromMapDialog(long catId) {
        android.widget.EditText titleInput = new android.widget.EditText(requireContext());
        titleInput.setHint("救助标题");
        titleInput.setMinEms(12);
        android.widget.EditText descInput = new android.widget.EditText(requireContext());
        descInput.setHint("描述(选填)");
        descInput.setMinLines(2);

        android.widget.Spinner problemTypeSpinner = new android.widget.Spinner(requireContext());
        String[] problemTypes = { "受伤", "疾病", "困境", "其他" };
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, problemTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        problemTypeSpinner.setAdapter(adapter);

        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(titleInput);
        layout.addView(descInput);

        android.widget.TextView problemTypeLabel = new android.widget.TextView(requireContext());
        problemTypeLabel.setText("问题类型");
        problemTypeLabel.setPadding(0, 20, 0, 8);
        layout.addView(problemTypeLabel);
        layout.addView(problemTypeSpinner);

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("发起救助")
                .setView(layout)
                .setPositiveButton("创建", (d, w) -> {
                    String title = titleInput.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(requireContext(), "请填写标题", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String problemType = problemTypeSpinner.getSelectedItem().toString();
                    Map<String, Object> body = new HashMap<>();
                    body.put("title", title);
                    body.put("description", descInput.getText().toString().trim());
                    body.put("catId", catId);
                    body.put("problemType", problemType);
                    RetrofitHelper.get().create(RescueApi.class).createActivity(body)
                            .enqueue(new Callback<Map<String, Object>>() {
                                @Override
                                public void onResponse(Call<Map<String, Object>> call,
                                        Response<Map<String, Object>> response) {
                                    if (!isAdded())
                                        return;
                                    if (response.isSuccessful() && response.body() != null) {
                                        if (currentMarker != null && currentMarker.isInfoWindowShown()) {
                                            currentMarker.hideInfoWindow();
                                        }
                                        Toast.makeText(requireContext(), "已创建救助活动", Toast.LENGTH_SHORT).show();
                                        Object squarePostIdObj = response.body().get("squarePostId");
                                        if (squarePostIdObj instanceof Number
                                                && ((Number) squarePostIdObj).longValue() > 0) {
                                            navigateToSquarePost(((Number) squarePostIdObj).longValue());
                                            return;
                                        }
                                        Object activityIdObj = response.body().get("id");
                                        if (activityIdObj instanceof Number) {
                                            Bundle args = new Bundle();
                                            args.putLong("activityId", ((Number) activityIdObj).longValue());
                                            if (getActivity() instanceof com.ourcat.app.activities.MainActivity) {
                                                ((com.ourcat.app.activities.MainActivity) getActivity())
                                                        .navigateFromMap(R.id.rescue_detail, args,
                                                                R.id.nav_item_community);
                                            }
                                        }
                                    } else if (response.code() == 409) {
                                        Toast.makeText(requireContext(), "该猫咪当前已处于待救助状态", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(requireContext(), "创建失败", Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void enableMyLocation() {
        if (aMap == null)
            return;
        MyLocationStyle style = new MyLocationStyle();
        style.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER);
        style.interval(5000);
        style.strokeColor(Color.TRANSPARENT);
        style.radiusFillColor(Color.argb(30, 0, 120, 255));
        aMap.setMyLocationStyle(style);
        aMap.setMyLocationEnabled(true);
    }

    private void checkPermissionAndLocate() {
        if (!isAdded())
            return;
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
            startLocation();
        } else {
            permissionLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void startLocation() {
        try {
            if (locationClient != null) {
                locationClient.stopLocation();
                locationClient.onDestroy();
            }
            locationClient = new AMapLocationClient(requireContext().getApplicationContext());
            AMapLocationClientOption option = new AMapLocationClientOption();
            option.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            option.setOnceLocation(false);
            option.setInterval(5000);
            option.setNeedAddress(true);
            option.setHttpTimeOut(15000);
            locationClient.setLocationOption(option);
            locationClient.setLocationListener(this);
            locationClient.startLocation();
            Log.d(TAG, "Location started");
        } catch (Exception e) {
            Log.e(TAG, "Location init failed", e);
            if (isAdded()) {
                Toast.makeText(requireContext(), "定位失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onLocationChanged(AMapLocation loc) {
        if (loc == null)
            return;
        if (loc.getErrorCode() != 0) {
            Log.e(TAG, "定位错误 code=" + loc.getErrorCode() + " info=" + loc.getErrorInfo());
            if (isAdded() && firstLocate) {
                requireActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(),
                                "定位失败[" + loc.getErrorCode() + "]: " + loc.getErrorInfo(),
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
            return;
        }
        currentLat = loc.getLatitude();
        currentLng = loc.getLongitude();
        Log.d(TAG, "定位成功: " + currentLat + "," + currentLng);

        if (firstLocate && isAdded()) {
            firstLocate = false;
            requireActivity().runOnUiThread(() -> {
                if (aMap != null) {
                    aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(currentLat, currentLng), 16));
                }
            });
        }
        if (pendingMoveToCurrentLocation && isAdded()) {
            pendingMoveToCurrentLocation = false;
            requireActivity().runOnUiThread(() -> {
                if (aMap != null) {
                    aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(currentLat, currentLng), 16));
                }
            });
        }
        if (pendingNavigateCatUseCurrentLocation && pendingNavigateCatId != null) {
            tryNavigateToCatLocation(pendingNavigateCatId, new LatLng(currentLat, currentLng), false);
        }
    }

    private void moveToCurrentLocation() {
        if (currentLat != 0 && currentLng != 0) {
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(currentLat, currentLng), 16));
            return;
        }
        pendingMoveToCurrentLocation = true;
        if (!isAdded()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocation();
            Toast.makeText(requireContext(), "正在定位当前位置...", Toast.LENGTH_SHORT).show();
        } else {
            permissionLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void loadLocations() {
        RetrofitHelper.get().create(CatApi.class).getLocations()
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                            Response<List<Map<String, Object>>> response) {
                        if (!isAdded())
                            return;
                        if (response.isSuccessful() && response.body() != null) {
                            allLocations.clear();
                            allLocations.addAll(response.body());
                            if (!isHeatmapMode) {
                                clusterAndDisplay();
                            }
                            catCountText.setText("共发现 " + allLocations.size() + " 只猫咪");
                            Long pendingCatId = pendingNavigateCatId;
                            if (pendingCatId != null) {
                                tryNavigateToCatLocation(pendingCatId, pendingNavigateCatReference, false);
                            }
                        } else {
                            catCountText.setText("暂无记录");
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (isAdded())
                            catCountText.setText("加载失败");
                    }
                });
    }

    private void loadNeedRescue() {
        RetrofitHelper.get().create(RescueApi.class).rescueLocations()
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                            Response<List<Map<String, Object>>> response) {
                        if (!isAdded())
                            return;
                        needRescueList.clear();
                        if (response.isSuccessful() && response.body() != null) {
                            needRescueList.addAll(response.body());
                        }
                        if (!isHeatmapMode && aMap != null) {
                            clusterAndDisplay();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (!isAdded())
                            return;
                        needRescueList.clear();
                        if (!isHeatmapMode && aMap != null) {
                            clusterAndDisplay();
                        } else {
                            removeNeedRescueMarkers();
                        }
                    }
                });
    }

    private void addNeedRescueMarkers() {
        if (aMap == null)
            return;
        removeNeedRescueMarkers();
        int rescueColor = 0xFFE53935;
        for (Map<String, Object> item : needRescueList) {
            addNeedRescueMarker(item, rescueColor);
        }
    }

    private void addNeedRescueMarker(Map<String, Object> item, int rescueColor) {
        if (aMap == null) {
            return;
        }
        Object latObj = item.get("lat");
        Object lngObj = item.get("lng");
        Object activityIdObj = item.get("activityId");
        if (latObj == null || lngObj == null || activityIdObj == null) {
            return;
        }
        Object catIdObj = item.get("catId");
        if (catIdObj instanceof Number && ((Number) catIdObj).longValue() > 0) {
            return;
        }
        double lat = ((Number) latObj).doubleValue();
        double lng = ((Number) lngObj).doubleValue();
        String title = buildNeedRescueTitle(item);
        Map<String, Object> data = new HashMap<>(item);
        data.put("type", NEED_RESCUE_MARKER_TYPE);
        String imageUrl = str(item.get("imageUrl"));
        if (!imageUrl.isEmpty()) {
            Glide.with(this)
                    .asBitmap()
                    .load(imageUrl)
                    .circleCrop()
                    .override((int) (44 * density), (int) (44 * density))
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource,
                                @Nullable Transition<? super Bitmap> transition) {
                            if (!isAdded() || aMap == null) {
                                return;
                            }
                            Marker marker = aMap.addMarker(new MarkerOptions()
                                    .position(new LatLng(lat, lng))
                                    .title(title)
                                    .snippet("点击进入救助广场帖")
                                    .icon(createImagePinMarker(resource, rescueColor))
                                    .anchor(0.5f, 1.0f)
                                    .zIndex(25));
                            if (marker == null) {
                                return;
                            }
                            markerDataMap.put(marker.getId(), data);
                            needRescueMarkers.add(marker);
                            registerRescueIconPulseMarker(marker, resource, rescueColor);
                        }
                    });
            return;
        }
        Bitmap placeholderPhoto = getRescuePlaceholderPhoto(rescueColor);
        BitmapDescriptor icon = createImagePinMarker(placeholderPhoto, rescueColor);
        Marker marker = aMap.addMarker(new MarkerOptions()
                .position(new LatLng(lat, lng))
                .title(title)
                .snippet("点击进入救助广场帖")
                .icon(icon)
                .anchor(0.5f, 1.0f)
                .zIndex(25));
        if (marker == null) {
            return;
        }
        markerDataMap.put(marker.getId(), data);
        needRescueMarkers.add(marker);
        registerRescueIconPulseMarker(marker, placeholderPhoto, rescueColor);
    }

    private void removeNeedRescueMarkers() {
        for (Marker marker : needRescueMarkers) {
            if (marker != null) {
                markerDataMap.remove(marker.getId());
                rescuePulseMarkers.remove(marker.getId());
                rescuePulsePhotos.remove(marker.getId());
                rescuePulseColors.remove(marker.getId());
                marker.remove();
            }
        }
        needRescueMarkers.clear();
    }

    private void loadHeatmapData() {
        if (allLocations.isEmpty()) {
            RetrofitHelper.get().create(CatApi.class).getLocations()
                    .enqueue(new Callback<List<Map<String, Object>>>() {
                        @Override
                        public void onResponse(Call<List<Map<String, Object>>> call,
                                Response<List<Map<String, Object>>> response) {
                            if (!isAdded())
                                return;
                            if (response.isSuccessful() && response.body() != null) {
                                allLocations.clear();
                                allLocations.addAll(response.body());
                            }
                            loadHeatmapAggregated();
                        }

                        @Override
                        public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                            loadHeatmapAggregated();
                        }
                    });
        } else {
            loadHeatmapAggregated();
        }
    }

    private void loadHeatmapAggregated() {
        RetrofitHelper.get().create(CatApi.class).getHeatmap()
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                            Response<List<Map<String, Object>>> response) {
                        if (!isAdded())
                            return;
                        if (response.isSuccessful() && response.body() != null) {
                            heatmapData.clear();
                            heatmapData.addAll(response.body());
                            renderHeatmap();
                        } else {
                            catCountText.setText("热力数据为空");
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (isAdded()) {
                            catCountText.setText("热力数据加载失败");
                            Toast.makeText(requireContext(), "加载热力图失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void renderHeatmap() {
        if (aMap == null)
            return;

        removeHeatmap();
        clearRescueIconPulseMarkers();
        aMap.clear();
        markerDataMap.clear();
        hospitalMarkers.clear();
        needRescueMarkers.clear();
        rescueFocusMarker = null;

        float zoom = aMap.getCameraPosition().zoom;

        if (zoom >= 15) {
            renderHeatmapFromLocations();
        } else {
            renderHeatmapFromAggregated();
        }
    }

    private void renderHeatmapFromLocations() {
        if (allLocations.isEmpty()) {
            loadLocations();
            return;
        }

        List<LatLng> latLngList = new ArrayList<>();
        for (Map<String, Object> loc : allLocations) {
            Object latObj = loc.get("lat");
            Object lngObj = loc.get("lng");
            if (latObj != null && lngObj != null) {
                double lat = ((Number) latObj).doubleValue();
                double lng = ((Number) lngObj).doubleValue();
                latLngList.add(new LatLng(lat, lng));
            }
        }

        if (latLngList.isEmpty())
            return;

        try {
            int radius = calculateHeatmapRadius(aMap.getCameraPosition().zoom);

            HeatmapTileProvider.Builder builder = new HeatmapTileProvider.Builder();
            builder.data(latLngList);

            int[] colors = {
                    Color.argb(0, 102, 255, 0),
                    Color.argb(120, 147, 255, 0),
                    Color.argb(180, 255, 215, 0),
                    Color.argb(220, 255, 140, 0),
                    Color.argb(255, 255, 69, 0)
            };
            float[] startPoints = { 0f, 0.15f, 0.4f, 0.7f, 1.0f };
            builder.gradient(new com.amap.api.maps.model.Gradient(colors, startPoints));
            builder.radius(radius);
            builder.transparency(0.5);

            HeatmapTileProvider provider = builder.build();

            TileOverlayOptions options = new TileOverlayOptions();
            options.tileProvider(provider);
            options.zIndex(5);

            heatmapOverlay = aMap.addTileOverlay(options);

            catCountText.setText("热力图（精确）: " + latLngList.size() + " 个点");
            if (showHospitals && !hospitalPoiList.isEmpty()) {
                addHospitalMarkers();
            }
        } catch (Exception e) {
            Log.e(TAG, "Render precise heatmap failed", e);
        }
    }

    private void renderHeatmapFromAggregated() {
        if (heatmapData.isEmpty())
            return;

        List<LatLng> latLngList = new ArrayList<>();
        java.util.Map<LatLng, Float> weightMap = new HashMap<>();

        for (Map<String, Object> point : heatmapData) {
            Object latObj = point.get("lat");
            Object lngObj = point.get("lng");
            Object weightObj = point.get("weight");
            if (latObj != null && lngObj != null) {
                double lat = ((Number) latObj).doubleValue();
                double lng = ((Number) lngObj).doubleValue();
                float weight = weightObj != null ? ((Number) weightObj).floatValue() : 1f;
                LatLng ll = new LatLng(lat, lng);
                latLngList.add(ll);
                weightMap.put(ll, weight);
            }
        }

        if (latLngList.isEmpty())
            return;

        try {
            int radius = calculateHeatmapRadius(aMap.getCameraPosition().zoom);

            HeatmapTileProvider.Builder builder = new HeatmapTileProvider.Builder();
            builder.weightedData(createWeightedData(latLngList, weightMap));

            int[] colors = {
                    Color.argb(0, 102, 255, 0),
                    Color.argb(120, 147, 255, 0),
                    Color.argb(180, 255, 215, 0),
                    Color.argb(220, 255, 140, 0),
                    Color.argb(255, 255, 69, 0)
            };
            float[] startPoints = { 0f, 0.15f, 0.4f, 0.7f, 1.0f };
            builder.gradient(new com.amap.api.maps.model.Gradient(colors, startPoints));
            builder.radius(radius);
            builder.transparency(0.5);

            HeatmapTileProvider provider = builder.build();

            TileOverlayOptions options = new TileOverlayOptions();
            options.tileProvider(provider);
            options.zIndex(5);

            heatmapOverlay = aMap.addTileOverlay(options);

            int total = 0;
            for (Map<String, Object> p : heatmapData) {
                Object w = p.get("weight");
                if (w instanceof Number)
                    total += ((Number) w).intValue();
            }
            catCountText.setText("热力图: " + heatmapData.size() + " 区域, " + total + " 次上报");
            if (showHospitals && !hospitalPoiList.isEmpty()) {
                addHospitalMarkers();
            }
        } catch (Exception e) {
            Log.e(TAG, "Render aggregated heatmap failed", e);
            if (isAdded()) {
                Toast.makeText(requireContext(), "热力图渲染失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private int calculateHeatmapRadius(float zoom) {
        if (zoom >= 18)
            return 20;
        if (zoom >= 16)
            return 28;
        if (zoom >= 14)
            return 38;
        if (zoom >= 12)
            return 48;
        return 55;
    }

    private List<com.amap.api.maps.model.WeightedLatLng> createWeightedData(
            List<LatLng> latLngList, java.util.Map<LatLng, Float> weightMap) {
        List<com.amap.api.maps.model.WeightedLatLng> result = new ArrayList<>();
        for (LatLng ll : latLngList) {
            float weight = weightMap.getOrDefault(ll, 1f);
            result.add(new com.amap.api.maps.model.WeightedLatLng(ll, weight));
        }
        return result;
    }

    private void removeHeatmap() {
        if (heatmapOverlay != null) {
            heatmapOverlay.remove();
            heatmapOverlay = null;
        }
    }

    private void loadRecommendData() {
        if (allLocations.isEmpty()) {
            RetrofitHelper.get().create(CatApi.class).getLocations()
                    .enqueue(new Callback<List<Map<String, Object>>>() {
                        @Override
                        public void onResponse(Call<List<Map<String, Object>>> call,
                                Response<List<Map<String, Object>>> response) {
                            if (!isAdded())
                                return;
                            if (response.isSuccessful() && response.body() != null) {
                                allLocations.clear();
                                allLocations.addAll(response.body());
                            }
                            buildRecommendFromLocal();
                        }

                        @Override
                        public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                            if (isAdded()) {
                                recommendList.setVisibility(View.GONE);
                                recommendEmpty.setVisibility(View.VISIBLE);
                                recommendEmpty.setText("加载失败");
                            }
                        }
                    });
        } else {
            buildRecommendFromLocal();
        }
    }

    private void buildRecommendFromLocal() {
        if (aMap == null || allLocations.isEmpty()) {
            recommendList.setVisibility(View.GONE);
            recommendEmpty.setVisibility(View.VISIBLE);
            recommendEmpty.setText("暂无数据");
            return;
        }

        LatLngBounds bounds = aMap.getProjection().getVisibleRegion().latLngBounds;
        float zoom = aMap.getCameraPosition().zoom;
        int precision = getRecommendGridPrecision(zoom);

        java.util.Map<String, List<Map<String, Object>>> gridMap = new HashMap<>();
        for (Map<String, Object> loc : allLocations) {
            Object latObj = loc.get("lat");
            Object lngObj = loc.get("lng");
            if (latObj == null || lngObj == null)
                continue;

            double lat = ((Number) latObj).doubleValue();
            double lng = ((Number) lngObj).doubleValue();
            LatLng ll = new LatLng(lat, lng);

            if (!bounds.contains(ll))
                continue;

            double gridLat = roundTo(lat, precision);
            double gridLng = roundTo(lng, precision);
            String gridKey = String.format(Locale.US, "%." + precision + "f,%." + precision + "f", gridLat, gridLng);
            if (!gridMap.containsKey(gridKey)) {
                gridMap.put(gridKey, new ArrayList<>());
            }
            gridMap.get(gridKey).add(loc);
        }

        if (gridMap.isEmpty()) {
            recommendList.setVisibility(View.GONE);
            recommendEmpty.setVisibility(View.VISIBLE);
            recommendEmpty.setText("当前视野内暂无数据");
            return;
        }

        List<Map<String, Object>> aggregated = new ArrayList<>();
        for (java.util.Map.Entry<String, List<Map<String, Object>>> entry : gridMap.entrySet()) {
            String[] parts = entry.getKey().split(",");
            double lat = Double.parseDouble(parts[0]);
            double lng = Double.parseDouble(parts[1]);
            int count = entry.getValue().size();

            Map<String, Object> item = new HashMap<>();
            item.put("lat", lat);
            item.put("lng", lng);
            item.put("count", count);
            item.put("name", "");
            aggregated.add(item);
        }

        if (isHighRecommend) {
            aggregated.sort((a, b) -> ((Integer) b.get("count")).compareTo((Integer) a.get("count")));
        } else {
            aggregated.sort((a, b) -> ((Integer) a.get("count")).compareTo((Integer) b.get("count")));
        }

        recommendData.clear();
        recommendData.addAll(aggregated.subList(0, Math.min(5, aggregated.size())));

        recommendList.setVisibility(View.VISIBLE);
        recommendEmpty.setVisibility(View.GONE);
        recommendList.setAdapter(new RecommendAdapter());

        loadAddressesForRecommend();
    }

    private int getRecommendGridPrecision(float zoom) {
        if (zoom < 9f)
            return 1;
        if (zoom < 13f)
            return 2;
        return 3;
    }

    private double roundTo(double value, int precision) {
        double p = Math.pow(10, precision);
        return Math.round(value * p) / p;
    }

    private void loadAddressesForRecommend() {
        if (aMap == null)
            return;
        float zoom = aMap.getCameraPosition().zoom;
        int precision = getRecommendGridPrecision(zoom);
        float radius = (precision == 1) ? 5000f : (precision == 2 ? 1000f : 200f);

        for (int i = 0; i < recommendData.size(); i++) {
            final int index = i;
            Map<String, Object> item = recommendData.get(i);
            double lat = ((Number) item.get("lat")).doubleValue();
            double lng = ((Number) item.get("lng")).doubleValue();

            try {
                com.amap.api.services.geocoder.GeocodeSearch geocodeSearch = new com.amap.api.services.geocoder.GeocodeSearch(
                        requireContext());
                geocodeSearch.setOnGeocodeSearchListener(
                        new com.amap.api.services.geocoder.GeocodeSearch.OnGeocodeSearchListener() {
                            @Override
                            public void onRegeocodeSearched(com.amap.api.services.geocoder.RegeocodeResult result,
                                    int rCode) {
                                if (!isAdded())
                                    return;
                                if (rCode == 1000 && result != null && result.getRegeocodeAddress() != null) {
                                    String shortAddr = getAddressByPrecision(result.getRegeocodeAddress(), precision);
                                    if (shortAddr != null && !shortAddr.isEmpty()) {
                                        if (index < recommendData.size()) {
                                            recommendData.get(index).put("name", shortAddr);
                                            if (recommendList.getAdapter() != null) {
                                                recommendList.getAdapter().notifyItemChanged(index);
                                            }
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onGeocodeSearched(com.amap.api.services.geocoder.GeocodeResult result,
                                    int rCode) {
                            }
                        });

                com.amap.api.services.geocoder.RegeocodeQuery query = new com.amap.api.services.geocoder.RegeocodeQuery(
                        new com.amap.api.services.core.LatLonPoint(lat, lng), radius,
                        com.amap.api.services.geocoder.GeocodeSearch.AMAP);
                geocodeSearch.getFromLocationAsyn(query);
            } catch (Exception e) {
                Log.e(TAG, "Geocode failed for index " + index, e);
            }
        }
    }

    private String getAddressByPrecision(com.amap.api.services.geocoder.RegeocodeAddress address, int precision) {
        if (address == null)
            return "";

        // precision 1: Zoom < 9 (10km+), prefer District/City
        if (precision == 1) {
            String district = address.getDistrict();
            if (district != null && !district.isEmpty())
                return district + "附近";
            return address.getCity();
        }

        // precision 2: Zoom < 13 (1km+), prefer Township/Neighborhood/AOI
        if (precision == 2) {
            // Try AOI first (e.g. University, Park)
            if (address.getAois() != null && !address.getAois().isEmpty()) {
                return address.getAois().get(0).getAoiName() + "附近";
            }
            // Fallback to township/neighborhood
            String township = address.getTownship();
            String neighborhood = address.getNeighborhood();
            if (neighborhood != null && !neighborhood.isEmpty())
                return neighborhood;
            if (township != null && !township.isEmpty())
                return township;
            // Last resort: format address but simplified
            return simplifyAddress(address.getFormatAddress());
        }

        // precision 3: Zoom >= 13 (100m), prefer specific building or POI
        // Try POI first if available (not directly in RegeocodeAddress root, usually
        // need POI search,
        // but let's use building/format address)
        String building = address.getBuilding();
        if (building != null && !building.isEmpty())
            return building;

        return simplifyAddress(address.getFormatAddress());
    }

    private String simplifyAddress(String fullAddress) {
        if (fullAddress == null)
            return "";
        String result = fullAddress
                .replaceAll("^中国", "")
                .replaceAll("^广东省|^北京市|^上海市|^天津市|^重庆市", "");
        if (result.length() > 20) {
            int districtIdx = result.indexOf("区");
            if (districtIdx > 0 && districtIdx < result.length() - 1) {
                result = result.substring(districtIdx + 1);
            }
        }
        if (result.length() > 25) {
            result = result.substring(0, 22) + "...";
        }
        return result;
    }

    private class RecommendAdapter extends RecyclerView.Adapter<RecommendAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_recommend, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Map<String, Object> item = recommendData.get(position);

            h.rank.setText(String.valueOf(position + 1));

            int bgColor;
            if (position == 0)
                bgColor = 0xFFFF6D00;
            else if (position == 1)
                bgColor = 0xFFFF9800;
            else if (position == 2)
                bgColor = 0xFFFFC107;
            else
                bgColor = 0xFFBDBDBD;
            h.rank.getBackground().setTint(bgColor);

            String name = str(item.get("name"));
            h.name.setText(name.isEmpty() ? "📍 获取地址中..." : name);

            Object latObj = item.get("lat");
            Object lngObj = item.get("lng");
            if (latObj != null && lngObj != null) {
                double lat = ((Number) latObj).doubleValue();
                double lng = ((Number) lngObj).doubleValue();
                if (name.isEmpty()) {
                    h.location.setText(String.format("%.5f, %.5f", lat, lng));
                } else {
                    h.location.setText(String.format("%.5f, %.5f", lat, lng));
                }
            }

            Object countObj = item.get("count");
            h.count.setText(countObj != null ? String.valueOf(((Number) countObj).intValue()) : "0");

            h.itemView.setOnClickListener(v -> {
                if (latObj != null && lngObj != null) {
                    double lat = ((Number) latObj).doubleValue();
                    double lng = ((Number) lngObj).doubleValue();
                    bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);

                    float currentZoom = aMap.getCameraPosition().zoom;
                    int precision = getRecommendGridPrecision(currentZoom);
                    float targetZoom = (precision == 1) ? 10f : (precision == 2 ? 14f : 17f);

                    aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), targetZoom));
                }
            });
        }

        @Override
        public int getItemCount() {
            return recommendData.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView rank, name, location, count;

            VH(View v) {
                super(v);
                rank = v.findViewById(R.id.item_rank);
                name = v.findViewById(R.id.item_name);
                location = v.findViewById(R.id.item_location);
                count = v.findViewById(R.id.item_count);
            }
        }
    }

    private void clusterAndDisplay() {
        if (aMap == null)
            return;
        if (allLocations.isEmpty() && needRescueList.isEmpty())
            return;

        new Thread(() -> {
            List<Map<String, Object>> locationsSnapshot = new ArrayList<>();
            List<Map<String, Object>> rescueLocationsSnapshot = new ArrayList<>();
            for (Map<String, Object> item : allLocations) {
                if (isTrue(item.get("hasActiveRescue"))) {
                    Map<String, Object> rescueItem = new HashMap<>(item);
                    rescueItem.put("type", RESCUE_CAT_MARKER_TYPE);
                    rescueLocationsSnapshot.add(rescueItem);
                } else {
                    locationsSnapshot.add(item);
                }
            }

            for (Map<String, Object> item : needRescueList) {
                Object latObj = item.get("lat");
                Object lngObj = item.get("lng");
                Object activityIdObj = item.get("activityId");
                if (latObj == null || lngObj == null || activityIdObj == null) {
                    continue;
                }
                Object catIdObj = item.get("catId");
                if (catIdObj instanceof Number && ((Number) catIdObj).longValue() > 0) {
                    continue;
                }
                Map<String, Object> rescueItem = new HashMap<>(item);
                rescueItem.put("type", NEED_RESCUE_MARKER_TYPE);
                rescueLocationsSnapshot.add(rescueItem);
            }

            float zoom = aMap.getCameraPosition().zoom;
            double clusterDistPx = CLUSTER_DISTANCE_DP * density;
            double metersPerPx = 156543.03392 * Math.cos(Math.toRadians(
                    aMap.getCameraPosition().target.latitude)) / Math.pow(2, zoom);
            double clusterMeters = clusterDistPx * metersPerPx;
            double rescueClusterMeters = clusterMeters * RESCUE_CLUSTER_DISTANCE_RATIO;

            List<List<Map<String, Object>>> clusters = clusterLocations(locationsSnapshot, clusterMeters);
            List<List<Map<String, Object>>> rescueClusters = clusterLocations(rescueLocationsSnapshot,
                    rescueClusterMeters);

            // 切回主线程更新 UI
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (aMap == null || isHeatmapMode)
                        return;
                    clearRescueIconPulseMarkers();
                    aMap.clear();
                    markerDataMap.clear();
                    hospitalMarkers.clear();
                    needRescueMarkers.clear();
                    rescueFocusMarker = null;

                    for (List<Map<String, Object>> cluster : clusters) {
                        if (cluster.size() == 1) {
                            addSingleMarker(cluster.get(0));
                        } else {
                            addClusterMarker(cluster);
                        }
                    }
                    for (List<Map<String, Object>> cluster : rescueClusters) {
                        if (cluster.size() == 1) {
                            addRescueMarker(cluster.get(0));
                        } else {
                            addRescueClusterMarker(cluster);
                        }
                    }
                    if (showHospitals && !hospitalPoiList.isEmpty()) {
                        addHospitalMarkers();
                    }
                    applyPendingRescueFocusMarker();
                });
            }
        }).start();
    }

    private List<List<Map<String, Object>>> clusterLocations(List<Map<String, Object>> items, double clusterMeters) {
        List<List<Map<String, Object>>> clusters = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return clusters;
        }
        boolean[] used = new boolean[items.size()];

        for (int i = 0; i < items.size(); i++) {
            if (used[i]) {
                continue;
            }
            List<Map<String, Object>> cluster = new ArrayList<>();
            cluster.add(items.get(i));
            used[i] = true;

            double lat1 = ((Number) items.get(i).get("lat")).doubleValue();
            double lng1 = ((Number) items.get(i).get("lng")).doubleValue();

            for (int j = i + 1; j < items.size(); j++) {
                if (used[j]) {
                    continue;
                }
                double lat2 = ((Number) items.get(j).get("lat")).doubleValue();
                double lng2 = ((Number) items.get(j).get("lng")).doubleValue();
                if (haversineMeters(lat1, lng1, lat2, lng2) < clusterMeters) {
                    cluster.add(items.get(j));
                    used[j] = true;
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    private void addRescueMarker(Map<String, Object> loc) {
        if (loc == null || aMap == null) {
            return;
        }
        Object type = loc.get("type");
        if (RESCUE_CAT_MARKER_TYPE.equals(type) || isTrue(loc.get("hasActiveRescue"))) {
            addRescueCatMarker(loc);
            return;
        }
        if (NEED_RESCUE_MARKER_TYPE.equals(type)) {
            int rescueColor = 0xFFE53935;
            addNeedRescueMarker(loc, rescueColor);
        }
    }

    private void addRescueClusterMarker(List<Map<String, Object>> cluster) {
        if (aMap == null || cluster == null || cluster.isEmpty()) {
            return;
        }
        double sumLat = 0;
        double sumLng = 0;
        for (Map<String, Object> loc : cluster) {
            sumLat += ((Number) loc.get("lat")).doubleValue();
            sumLng += ((Number) loc.get("lng")).doubleValue();
        }
        double avgLat = sumLat / cluster.size();
        double avgLng = sumLng / cluster.size();

        BitmapDescriptor icon = createRescueClusterBadge(cluster.size());
        Marker marker = aMap.addMarker(new MarkerOptions()
                .position(new LatLng(avgLat, avgLng))
                .title(cluster.size() + " 条救助")
                .icon(icon)
                .anchor(0.5f, 0.5f)
                .zIndex(25));
        if (marker != null) {
            markerDataMap.put(marker.getId(), (Object) cluster);
        }
    }

    private void addSingleMarker(Map<String, Object> loc) {
        if (isTrue(loc.get("hasActiveRescue"))) {
            return;
        }
        double lat = ((Number) loc.get("lat")).doubleValue();
        double lng = ((Number) loc.get("lng")).doubleValue();
        String color = str(loc.get("color"));
        String desc = str(loc.get("description"));
        String time = str(loc.get("reportTime"));
        String feature = str(loc.get("feature"));
        String imageUrl = str(loc.get("imageUrl"));

        String title = color.isEmpty() ? "猫咪" : color + "猫";
        StringBuilder snippet = new StringBuilder();
        if (!feature.isEmpty())
            snippet.append("特征: ").append(feature);
        if (!desc.isEmpty()) {
            if (snippet.length() > 0)
                snippet.append("\n");
            snippet.append(desc);
        }
        if (!time.isEmpty()) {
            if (snippet.length() > 0)
                snippet.append("\n");
            snippet.append("上报: ").append(time);
        }

        LatLng pos = new LatLng(lat, lng);
        int markerColor = getMarkerColor(color);

        if (!imageUrl.isEmpty()) {
            Glide.with(this)
                    .asBitmap()
                    .load(imageUrl)
                    .circleCrop()
                    .override((int) (44 * density), (int) (44 * density))
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource,
                                @Nullable Transition<? super Bitmap> transition) {
                            if (!isAdded() || aMap == null)
                                return;
                            BitmapDescriptor icon = createImagePinMarker(resource, markerColor);
                            Marker marker = aMap.addMarker(new MarkerOptions()
                                    .position(pos)
                                    .title(title)
                                    .snippet(snippet.toString())
                                    .icon(icon)
                                    .anchor(0.5f, 1.0f)
                                    .zIndex(10));
                            markerDataMap.put(marker.getId(), loc);
                        }
                    });
        } else {
            BitmapDescriptor icon = createDefaultPinMarker(markerColor);
            Marker marker = aMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(title)
                    .snippet(snippet.toString())
                    .icon(icon)
                    .anchor(0.5f, 1.0f)
                    .zIndex(10));
            markerDataMap.put(marker.getId(), loc);
        }
    }

    private void addRescueCatMarker(Map<String, Object> loc) {
        double lat = ((Number) loc.get("lat")).doubleValue();
        double lng = ((Number) loc.get("lng")).doubleValue();
        String color = str(loc.get("color"));
        String desc = str(loc.get("description"));
        String time = str(loc.get("reportTime"));
        String feature = str(loc.get("feature"));
        String imageUrl = str(loc.get("imageUrl"));

        String title = color.isEmpty() ? "猫咪" : color + "猫";
        StringBuilder snippet = new StringBuilder();
        if (!feature.isEmpty())
            snippet.append("特征: ").append(feature);
        if (!desc.isEmpty()) {
            if (snippet.length() > 0)
                snippet.append("\n");
            snippet.append(desc);
        }
        if (!time.isEmpty()) {
            if (snippet.length() > 0)
                snippet.append("\n");
            snippet.append("上报: ").append(time);
        }

        Map<String, Object> markerData = new HashMap<>(loc);
        markerData.put("type", RESCUE_CAT_MARKER_TYPE);
        LatLng pos = new LatLng(lat, lng);
        int rescueColor = 0xFFE53935;

        if (!imageUrl.isEmpty()) {
            Glide.with(this)
                    .asBitmap()
                    .load(imageUrl)
                    .circleCrop()
                    .override((int) (44 * density), (int) (44 * density))
                    .into(new SimpleTarget<Bitmap>() {
                        @Override
                        public void onResourceReady(@NonNull Bitmap resource,
                                @Nullable Transition<? super Bitmap> transition) {
                            if (!isAdded() || aMap == null)
                                return;
                            BitmapDescriptor icon = createImagePinMarker(resource, rescueColor);
                            Marker marker = aMap.addMarker(new MarkerOptions()
                                    .position(pos)
                                    .title(title)
                                    .snippet(snippet.toString())
                                    .icon(icon)
                                    .anchor(0.5f, 1.0f)
                                    .zIndex(20));
                            markerDataMap.put(marker.getId(), markerData);
                            registerRescueIconPulseMarker(marker, resource, rescueColor);
                        }
                    });
        } else {
            BitmapDescriptor icon = createDefaultPinMarker(rescueColor);
            Marker marker = aMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title(title)
                    .snippet(snippet.toString())
                    .icon(icon)
                    .anchor(0.5f, 1.0f)
                    .zIndex(20));
            markerDataMap.put(marker.getId(), markerData);
            registerRescueIconPulseMarker(marker, null, rescueColor);
        }
    }

    private void registerRescueIconPulseMarker(Marker marker, Bitmap photo, int color) {
        if (marker == null) {
            return;
        }
        String markerId = marker.getId();
        rescuePulseMarkers.put(markerId, marker);
        rescuePulseColors.put(markerId, color);
        if (photo != null) {
            rescuePulsePhotos.put(markerId, photo);
        } else {
            rescuePulsePhotos.remove(markerId);
        }
        startRescueIconPulseAnimation();
    }

    private void clearRescueIconPulseMarkers() {
        rescuePulseMarkers.clear();
        rescuePulsePhotos.clear();
        rescuePulseColors.clear();
        stopRescueIconPulseAnimation();
    }

    private void startRescueIconPulseAnimation() {
        if (rescueIconPulseRunnable != null || rescuePulseMarkers.isEmpty()) {
            return;
        }
        rescueIconPulseRunnable = new Runnable() {
            @Override
            public void run() {
                if (aMap == null || rescuePulseMarkers.isEmpty()) {
                    stopRescueIconPulseAnimation();
                    return;
                }
                rescueIconPulseProgress += 0.08f;
                if (rescueIconPulseProgress >= 1f) {
                    rescueIconPulseProgress -= 1f;
                }
                int index = 0;
                List<String> removedIds = new ArrayList<>();
                for (Map.Entry<String, Marker> entry : rescuePulseMarkers.entrySet()) {
                    String markerId = entry.getKey();
                    Marker marker = entry.getValue();
                    if (marker == null || !marker.isVisible()) {
                        removedIds.add(markerId);
                        continue;
                    }
                    float phase = rescueIconPulseProgress + index * 0.21f;
                    phase = phase - (float) Math.floor(phase);
                    int color = rescuePulseColors.containsKey(markerId) ? rescuePulseColors.get(markerId) : 0xFFE53935;
                    Bitmap photo = rescuePulsePhotos.get(markerId);
                    BitmapDescriptor icon = photo != null
                            ? createImagePinMarker(photo, color, phase)
                            : createDefaultPinMarker(color, phase);
                    marker.setIcon(icon);
                    index++;
                }
                if (!removedIds.isEmpty()) {
                    for (String markerId : removedIds) {
                        rescuePulseMarkers.remove(markerId);
                        rescuePulsePhotos.remove(markerId);
                        rescuePulseColors.remove(markerId);
                    }
                }
                if (rescuePulseMarkers.isEmpty()) {
                    stopRescueIconPulseAnimation();
                    return;
                }
                heatmapHandler.postDelayed(this, RESCUE_ICON_PULSE_FRAME_MS);
            }
        };
        heatmapHandler.post(rescueIconPulseRunnable);
    }

    private void stopRescueIconPulseAnimation() {
        if (rescueIconPulseRunnable != null) {
            heatmapHandler.removeCallbacks(rescueIconPulseRunnable);
            rescueIconPulseRunnable = null;
        }
    }

    public void showRescueFocusMarker(double lat, double lng) {
        showRescueFocusMarker(lat, lng, false, false);
    }

    public void showRescueFocusMarker(double lat, double lng, boolean solved, boolean oneShot) {
        if (lat == 0 && lng == 0) {
            return;
        }
        pendingRescueFocusLatLng = new LatLng(lat, lng);
        pendingRescueFocusSolved = solved;
        pendingRescueFocusOneShot = oneShot;
        if (!oneShot) {
            pendingRescueFocusAppliedOnce = false;
        }
        applyPendingRescueFocusMarker();
    }

    private void applyPendingRescueFocusMarker() {
        if (aMap == null || pendingRescueFocusLatLng == null) {
            return;
        }
        if (rescueFocusMarker != null) {
            markerDataMap.remove(rescueFocusMarker.getId());
            rescuePulseMarkers.remove(rescueFocusMarker.getId());
            rescuePulsePhotos.remove(rescueFocusMarker.getId());
            rescuePulseColors.remove(rescueFocusMarker.getId());
            rescueFocusMarker.remove();
            rescueFocusMarker = null;
        }
        int rescueColor = pendingRescueFocusSolved ? 0xFF2E7D32 : 0xFFE53935;
        Bitmap placeholderPhoto = getRescuePlaceholderPhoto(rescueColor);
        BitmapDescriptor icon = createImagePinMarker(placeholderPhoto, rescueColor);
        String title = pendingRescueFocusSolved ? "已解决救助点位" : "救助点位";
        String snippet = pendingRescueFocusSolved ? "该事件已解决，刷新地图后该标点将消失" : "正在跟踪该救助位置";
        rescueFocusMarker = aMap.addMarker(new MarkerOptions()
                .position(pendingRescueFocusLatLng)
                .title(title)
                .snippet(snippet)
                .icon(icon)
                .anchor(0.5f, 1.0f)
                .zIndex(30));
        if (rescueFocusMarker != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("type", NEED_RESCUE_MARKER_TYPE);
            data.put("isFocus", true);
            data.put("lat", pendingRescueFocusLatLng.latitude);
            data.put("lng", pendingRescueFocusLatLng.longitude);
            markerDataMap.put(rescueFocusMarker.getId(), data);
            if (!pendingRescueFocusSolved) {
                registerRescueIconPulseMarker(rescueFocusMarker, placeholderPhoto, rescueColor);
            }
        }

        if (pendingRescueFocusOneShot) {
            if (!pendingRescueFocusAppliedOnce) {
                pendingRescueFocusAppliedOnce = true;
            } else {
                pendingRescueFocusLatLng = null;
                pendingRescueFocusSolved = false;
                pendingRescueFocusOneShot = false;
                pendingRescueFocusAppliedOnce = false;
            }
        }
    }

    private Bitmap getRescuePlaceholderPhoto(int accentColor) {
        Bitmap cached = rescuePlaceholderPhotoCache.get(accentColor);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        int size = (int) (40 * density);
        if (size <= 0) {
            size = 40;
        }
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.WHITE);
        Drawable drawable = AppCompatResources.getDrawable(requireContext(), R.drawable.cat_blink);
        if (drawable != null) {
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
        }
        rescuePlaceholderPhotoCache.put(accentColor, bmp);
        return bmp;
    }

    private BitmapDescriptor createImagePinMarker(Bitmap photo, int borderColor) {
        return createImagePinMarker(photo, borderColor, -1f);
    }

    private BitmapDescriptor createImagePinMarker(Bitmap photo, int borderColor, float pulsePhase) {
        int baseW = (int) (48 * density);
        int baseH = (int) (58 * density);
        int circleR = (int) (20 * density);
        int baseCx = baseW / 2;
        int baseCy = (int) (22 * density);
        float border = 3f * density;
        int ripplePad = (int) (14 * density);
        int w = baseW + ripplePad * 2;
        int h = baseH + ripplePad;
        int offsetX = ripplePad;
        int offsetY = ripplePad;
        int cx = offsetX + baseCx;
        int cy = offsetY + baseCy;
        int tipY = offsetY + baseH - (int) (2 * density);

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint shadowP = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowP.setColor(0x40000000);
        Path shadowPath = new Path();
        shadowPath.moveTo(cx - 6 * density, cy + circleR * 0.5f);
        shadowPath.lineTo(cx + 1 * density, tipY + 2 * density);
        shadowPath.lineTo(cx + 8 * density, cy + circleR * 0.5f);
        shadowPath.close();
        c.drawPath(shadowPath, shadowP);
        c.drawCircle(cx + 1 * density, cy + 1 * density, circleR + border / 2, shadowP);

        if (pulsePhase >= 0f) {
            drawRippleRings(c, cx, cy, circleR + border, borderColor, pulsePhase, 10f * density);
        }

        Paint borderP = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderP.setColor(borderColor);
        Path pinPath = new Path();
        pinPath.moveTo(cx - 6 * density, cy + circleR * 0.5f);
        pinPath.lineTo(cx, tipY);
        pinPath.lineTo(cx + 6 * density, cy + circleR * 0.5f);
        pinPath.close();
        c.drawPath(pinPath, borderP);
        c.drawCircle(cx, cy, circleR + border / 2, borderP);

        Bitmap scaled = Bitmap.createScaledBitmap(photo, circleR * 2, circleR * 2, true);
        BitmapShader shader = new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Paint imgP = new Paint(Paint.ANTI_ALIAS_FLAG);
        imgP.setShader(shader);
        c.save();
        c.translate(cx - circleR, cy - circleR);
        c.drawCircle(circleR, circleR, circleR - 1, imgP);
        c.restore();

        return BitmapDescriptorFactory.fromBitmap(bmp);
    }

    private BitmapDescriptor createDefaultPinMarker(int fillColor) {
        return createDefaultPinMarker(fillColor, -1f);
    }

    private BitmapDescriptor createDefaultPinMarker(int fillColor, float pulsePhase) {
        int baseW = (int) (36 * density);
        int baseH = (int) (48 * density);
        int circleR = (int) (14 * density);
        int baseCx = baseW / 2;
        int baseCy = (int) (16 * density);
        float border = 2.5f * density;
        int ripplePad = (int) (10 * density);
        int w = baseW + ripplePad * 2;
        int h = baseH + ripplePad;
        int offsetX = ripplePad;
        int offsetY = ripplePad;
        int cx = offsetX + baseCx;
        int cy = offsetY + baseCy;
        int tipY = offsetY + baseH - (int) (2 * density);

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint shadowP = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowP.setColor(0x30000000);
        Path shadowPath = new Path();
        shadowPath.moveTo(cx - 4 * density, cy + circleR * 0.6f);
        shadowPath.lineTo(cx + 1 * density, tipY + 2 * density);
        shadowPath.lineTo(cx + 6 * density, cy + circleR * 0.6f);
        shadowPath.close();
        c.drawPath(shadowPath, shadowP);
        c.drawCircle(cx + 1 * density, cy + 1 * density, circleR, shadowP);

        if (pulsePhase >= 0f) {
            drawRippleRings(c, cx, cy, circleR, fillColor, pulsePhase, 8f * density);
        }

        Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillP.setColor(fillColor);
        Path pinPath = new Path();
        pinPath.moveTo(cx - 5 * density, cy + circleR * 0.6f);
        pinPath.lineTo(cx, tipY);
        pinPath.lineTo(cx + 5 * density, cy + circleR * 0.6f);
        pinPath.close();
        c.drawPath(pinPath, fillP);
        c.drawCircle(cx, cy, circleR, fillP);

        Paint borderP = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderP.setColor(Color.WHITE);
        borderP.setStyle(Paint.Style.STROKE);
        borderP.setStrokeWidth(border);
        c.drawCircle(cx, cy, circleR - border / 2, borderP);

        Paint innerP = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerP.setColor(Color.WHITE);
        c.drawCircle(cx, cy, circleR * 0.42f, innerP);

        Paint dotP = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotP.setColor(fillColor);
        float dotR = 2.2f * density;
        c.drawCircle(cx - 3.5f * density, cy - 2 * density, dotR, dotP);
        c.drawCircle(cx + 3.5f * density, cy - 2 * density, dotR, dotP);
        RectF mouth = new RectF(cx - 2.5f * density, cy + 0.5f * density,
                cx + 2.5f * density, cy + 3.5f * density);
        dotP.setStyle(Paint.Style.STROKE);
        dotP.setStrokeWidth(1.2f * density);
        dotP.setStrokeCap(Paint.Cap.ROUND);
        c.drawArc(mouth, 0, 180, false, dotP);

        return BitmapDescriptorFactory.fromBitmap(bmp);
    }

    private void drawRippleRings(Canvas canvas, int cx, int cy, float baseRadius, int color, float pulsePhase,
            float maxExpand) {
        drawRippleRing(canvas, cx, cy, baseRadius, color, pulsePhase, maxExpand);
        float phase2 = pulsePhase + 0.5f;
        if (phase2 >= 1f) {
            phase2 -= 1f;
        }
        drawRippleRing(canvas, cx, cy, baseRadius, color, phase2, maxExpand);
    }

    private void drawRippleRing(Canvas canvas, int cx, int cy, float baseRadius, int color, float phase,
            float maxExpand) {
        float radius = baseRadius + maxExpand * phase;
        float fade = 1f - phase;
        int alpha = Math.max(0, Math.min(255, (int) (140f * fade * fade)));
        if (alpha <= 0) {
            return;
        }
        Paint ripplePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        ripplePaint.setStyle(Paint.Style.STROKE);
        ripplePaint.setColor(color);
        ripplePaint.setAlpha(alpha);
        ripplePaint.setStrokeWidth(Math.max(1f, 2.4f * density * fade));
        canvas.drawCircle(cx, cy, radius, ripplePaint);
    }

    private void addClusterMarker(List<Map<String, Object>> cluster) {
        double sumLat = 0, sumLng = 0;
        for (Map<String, Object> loc : cluster) {
            sumLat += ((Number) loc.get("lat")).doubleValue();
            sumLng += ((Number) loc.get("lng")).doubleValue();
        }
        double avgLat = sumLat / cluster.size();
        double avgLng = sumLng / cluster.size();

        BitmapDescriptor icon = createClusterBadge(cluster.size());

        Marker marker = aMap.addMarker(new MarkerOptions()
                .position(new LatLng(avgLat, avgLng))
                .title(cluster.size() + " 只猫咪")
                .icon(icon)
                .anchor(0.5f, 0.5f)
                .zIndex(20));

        markerDataMap.put(marker.getId(), (Object) cluster);
    }

    private BitmapDescriptor createClusterBadge(int count) {
        int baseSize = count <= 5 ? 40 : count <= 15 ? 46 : 52;
        int size = (int) (baseSize * density);

        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float r = size / 2f;

        Paint outerP = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerP.setColor(0x30FF6D00);
        c.drawCircle(r, r, r, outerP);

        Paint midP = new Paint(Paint.ANTI_ALIAS_FLAG);
        midP.setColor(0x60FF6D00);
        c.drawCircle(r, r, r * 0.78f, midP);

        Paint innerP = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerP.setColor(0xFFFF6D00);
        c.drawCircle(r, r, r * 0.58f, innerP);

        Paint borderP = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderP.setColor(Color.WHITE);
        borderP.setStyle(Paint.Style.STROKE);
        borderP.setStrokeWidth(2 * density);
        c.drawCircle(r, r, r * 0.58f, borderP);

        Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);
        textP.setColor(Color.WHITE);
        textP.setTextSize(14 * density);
        textP.setTextAlign(Paint.Align.CENTER);
        textP.setTypeface(Typeface.DEFAULT_BOLD);
        Paint.FontMetrics fm = textP.getFontMetrics();
        float baseline = r - (fm.top + fm.bottom) / 2f;
        c.drawText(String.valueOf(count), r, baseline, textP);

        return BitmapDescriptorFactory.fromBitmap(bmp);
    }

    private BitmapDescriptor createRescueClusterBadge(int count) {
        int baseSize = count <= 5 ? 40 : count <= 15 ? 46 : 52;
        int size = (int) (baseSize * density);

        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        float r = size / 2f;

        Paint outerP = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerP.setColor(0x30E53935);
        c.drawCircle(r, r, r, outerP);

        Paint midP = new Paint(Paint.ANTI_ALIAS_FLAG);
        midP.setColor(0x60E53935);
        c.drawCircle(r, r, r * 0.78f, midP);

        Paint innerP = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerP.setColor(0xFFE53935);
        c.drawCircle(r, r, r * 0.58f, innerP);

        Paint borderP = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderP.setColor(Color.WHITE);
        borderP.setStyle(Paint.Style.STROKE);
        borderP.setStrokeWidth(2 * density);
        c.drawCircle(r, r, r * 0.58f, borderP);

        Paint textP = new Paint(Paint.ANTI_ALIAS_FLAG);
        textP.setColor(Color.WHITE);
        textP.setTextSize(14 * density);
        textP.setTextAlign(Paint.Align.CENTER);
        textP.setTypeface(Typeface.DEFAULT_BOLD);
        Paint.FontMetrics fm = textP.getFontMetrics();
        float baseline = r - (fm.top + fm.bottom) / 2f;
        c.drawText(String.valueOf(count), r, baseline, textP);

        return BitmapDescriptorFactory.fromBitmap(bmp);
    }

    private int getMarkerColor(String color) {
        if (color == null || color.isEmpty())
            return 0xFFFF6D00;
        // 纯色
        if (color.contains("橘"))
            return 0xFFFF6D00; // 橘色
        if (color.contains("黑"))
            return 0xFF37474F; // 深灰黑
        if (color.contains("白"))
            return 0xFF78909C; // 浅灰白
        if (color.contains("灰"))
            return 0xFF546E7A; // 蓝灰
        // 虎斑
        if (color.contains("狸花"))
            return 0xFF2E7D32; // 深绿
        if (color.contains("银虎斑"))
            return 0xFF607D8B; // 银灰蓝
        // 双色/多色
        if (color.contains("奶牛"))
            return 0xFF263238; // 深蓝黑
        if (color.contains("三花"))
            return 0xFFD81B60; // 粉红
        if (color.contains("玳瑁"))
            return 0xFF6D4C41; // 棕色
        // 特殊色
        if (color.contains("奶油"))
            return 0xFFFFA726; // 浅橙奶油色
        if (color.contains("暹罗"))
            return 0xFF8D6E63; // 浅棕暹罗色
        return 0xFFFF6D00;
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private String str(Object o) {
        return o != null ? o.toString() : "";
    }

    public void refreshCatReports() {
        allLocations.clear();
        loadHeatmapData();
    }

    public void navigateTo(double lat, double lng) {
        if (lat == 0 && lng == 0) {
            return;
        }
        pendingNavigateLatLng = new LatLng(lat, lng);
        applyPendingNavigation();
    }

    public void navigateToCatLocation(long catId) {
        if (catId <= 0) {
            return;
        }
        pendingNavigateCatUseCurrentLocation = false;
        tryNavigateToCatLocation(catId, null, true);
    }

    public void navigateToCatLocationNear(long catId, double lat, double lng) {
        if (catId <= 0 || (lat == 0 && lng == 0)) {
            return;
        }
        pendingNavigateCatUseCurrentLocation = false;
        tryNavigateToCatLocation(catId, new LatLng(lat, lng), true);
    }

    public void navigateToCatLocationNearForceReload(long catId, double lat, double lng) {
        if (catId <= 0 || (lat == 0 && lng == 0)) {
            return;
        }
        pendingNavigateCatUseCurrentLocation = false;
        pendingNavigateCatId = catId;
        pendingNavigateCatReference = new LatLng(lat, lng);
        loadLocations();
    }

    public void navigateToCatLocationNearestToUser(long catId) {
        if (catId <= 0) {
            return;
        }
        LatLng current = getCurrentLocationLatLng();
        if (current != null) {
            pendingNavigateCatUseCurrentLocation = false;
            tryNavigateToCatLocation(catId, current, true);
            return;
        }

        pendingNavigateCatId = catId;
        pendingNavigateCatReference = null;
        pendingNavigateCatUseCurrentLocation = true;
        requestLocationForPendingCatNavigation();
        if (isAdded()) {
            Toast.makeText(requireContext(), "正在定位后跳转到最近猫咪标点", Toast.LENGTH_SHORT).show();
        }
    }

    private void tryNavigateToCatLocation(long catId, LatLng reference, boolean allowReload) {
        LatLng latLng = findCatLatLng(catId, reference);
        if (latLng != null) {
            pendingNavigateCatId = null;
            pendingNavigateCatReference = null;
            pendingNavigateCatUseCurrentLocation = false;
            navigateTo(latLng.latitude, latLng.longitude);
            return;
        }

        if (allowReload) {
            pendingNavigateCatId = catId;
            pendingNavigateCatReference = reference;
            loadLocations();
        }
    }

    private LatLng findCatLatLng(long catId, LatLng reference) {
        LatLng target = null;
        double bestDistance = Double.MAX_VALUE;
        for (Map<String, Object> item : allLocations) {
            Object idObj = item.get("catId");
            if (!(idObj instanceof Number) || ((Number) idObj).longValue() != catId) {
                continue;
            }
            Object latObj = item.get("lat");
            Object lngObj = item.get("lng");
            if (!(latObj instanceof Number) || !(lngObj instanceof Number)) {
                continue;
            }
            LatLng candidate = new LatLng(((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue());
            if (reference == null) {
                return candidate;
            }
            double dist = haversineMeters(reference.latitude, reference.longitude,
                    candidate.latitude, candidate.longitude);
            if (dist < bestDistance) {
                bestDistance = dist;
                target = candidate;
            }
        }
        return target;
    }

    private LatLng getCurrentLocationLatLng() {
        if (currentLat == 0 && currentLng == 0) {
            return null;
        }
        return new LatLng(currentLat, currentLng);
    }

    public LatLng getCurrentLocationForQuickAction() {
        return getCurrentLocationLatLng();
    }

    private void requestLocationForPendingCatNavigation() {
        if (!isAdded()) {
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocation();
        } else {
            permissionLauncher.launch(new String[] {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    private void applyPendingNavigation() {
        if (!isAdded() || aMap == null) {
            return;
        }
        LatLng latLng = pendingNavigateLatLng;
        pendingNavigateLatLng = null;
        if (latLng != null) {
            requireActivity().runOnUiThread(() -> {
                if (aMap != null) {
                    aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, MAX_ZOOM));
                }
            });
        }
    }

    private void navigateToSquarePost(long squarePostId) {
        if (!isAdded())
            return;
        Bundle args = new Bundle();
        args.putLong("squarePostId", squarePostId);
        if (getActivity() instanceof com.ourcat.app.activities.MainActivity) {
            ((com.ourcat.app.activities.MainActivity) getActivity())
                    .navigateFromMap(R.id.square_detail, args, R.id.nav_item_community);
        }
    }

    private boolean isTrue(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null)
            mapView.onResume();
        if (!isHeatmapMode) {
            loadNeedRescue();
            loadLocations();
        }
        if (isAdded()
                && ContextCompat.checkSelfPermission(requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
            startLocation();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (locationClient != null)
            locationClient.stopLocation();
        if (mapView != null)
            mapView.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null)
            mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (heatmapRunnable != null) {
            heatmapHandler.removeCallbacks(heatmapRunnable);
            heatmapRunnable = null;
        }
        if (locationClient != null) {
            try {
                locationClient.stopLocation();
                locationClient.onDestroy();
            } catch (Exception ignored) {
            }
            locationClient = null;
        }
        removeHeatmap();
        removeHospitalMarkers();
        removeNeedRescueMarkers();
        rescueFocusMarker = null;
        pendingRescueFocusLatLng = null;
        clearRescueIconPulseMarkers();
        hospitalPoiList.clear();
        if (aMap != null) {
            try {
                aMap.setMyLocationEnabled(false);
            } catch (Exception ignored) {
            }
            aMap = null;
        }
        if (mapView != null) {
            try {
                mapView.onDestroy();
            } catch (Exception ignored) {
            }
            mapView = null;
        }
        super.onDestroyView();
    }

    /**
     * 打开猫咪档案详情页面
     */
    private void openCatProfile(long catId) {
        if (!isAdded())
            return;

        Bundle args = new Bundle();
        args.putLong("catId", catId);
        try {
            if (getActivity() instanceof com.ourcat.app.activities.MainActivity) {
                ((com.ourcat.app.activities.MainActivity) getActivity())
                        .navigateFromMap(R.id.cat_profile, args, R.id.nav_item_cats);
            } else {
                Toast.makeText(requireContext(), "无法打开档案", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Open cat profile failed", e);
            Toast.makeText(requireContext(), "无法打开档案", Toast.LENGTH_SHORT).show();
        }
    }
}
