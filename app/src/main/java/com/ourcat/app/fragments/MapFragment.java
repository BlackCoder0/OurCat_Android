package com.ourcat.app.fragments;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ourcat.app.R;
import com.ourcat.app.repositories.CatApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MapFragment extends Fragment implements AMapLocationListener {

    private static final String TAG = "MapFragment";
    private static final double CLUSTER_DISTANCE_DP = 22;

    private TextureMapView mapView;
    private AMap aMap;
    private AMapLocationClient locationClient;
    private TextView catCountText;
    private final List<Map<String, Object>> allLocations = new ArrayList<>();
    private final java.util.Map<String, Object> markerDataMap = new HashMap<>();

    private double currentLat = 0;
    private double currentLng = 0;
    private boolean firstLocate = true;
    private float density;

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean fine = result.get(Manifest.permission.ACCESS_FINE_LOCATION);
                Boolean coarse = result.get(Manifest.permission.ACCESS_COARSE_LOCATION);
                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    enableMyLocation();
                    startLocation();
                } else if (isAdded()) {
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

        initMap();
        checkPermissionAndLocate();

        FloatingActionButton fabLocation = view.findViewById(R.id.fab_location);
        fabLocation.setOnClickListener(v -> moveToCurrentLocation());

        FloatingActionButton fabRefresh = view.findViewById(R.id.fab_refresh);
        fabRefresh.setOnClickListener(v -> {
            if (isAdded()) Toast.makeText(requireContext(), "刷新中...", Toast.LENGTH_SHORT).show();
            loadLocations();
        });

        FloatingActionButton fabReport = view.findViewById(R.id.map_fab_report);
        fabReport.setOnClickListener(v -> {
            ReportCatDialogFragment dialog = ReportCatDialogFragment.newInstance(currentLat, currentLng);
            dialog.setOnReportSuccess(this::loadLocations);
            dialog.show(getChildFragmentManager(), "report");
        });

        loadLocations();
        return view;
    }

    private void initMap() {
        if (aMap != null) return;
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
            public View getInfoContents(Marker marker) { return null; }
        });

        aMap.setOnMarkerClickListener(marker -> {
            Object data = markerDataMap.get(marker.getId());
            if (data instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> cluster = (List<Map<String, Object>>) data;
                zoomToCluster(cluster);
                return true;
            }
            marker.showInfoWindow();
            return true;
        });

        aMap.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {
            @Override
            public void onCameraChange(CameraPosition pos) {}
            @Override
            public void onCameraChangeFinish(CameraPosition pos) {
                if (!allLocations.isEmpty()) clusterAndDisplay();
            }
        });

        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(23.13, 113.26), 14));
    }

    private void zoomToCluster(List<Map<String, Object>> cluster) {
        if (cluster.size() < 2) return;
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

        title.setText(marker.getTitle());
        String snippetText = marker.getSnippet();
        if (snippetText != null && !snippetText.isEmpty()) {
            snippet.setText(snippetText);
            snippet.setVisibility(View.VISIBLE);
        } else {
            snippet.setVisibility(View.GONE);
        }

        Object data = markerDataMap.get(marker.getId());
        if (data instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> catData = (Map<String, Object>) data;
            Object imgUrl = catData.get("imageUrl");
            if (imgUrl != null && !imgUrl.toString().isEmpty()) {
                image.setVisibility(View.VISIBLE);
                Glide.with(requireContext()).load(imgUrl.toString()).centerCrop().into(image);
            } else {
                image.setVisibility(View.GONE);
            }
        } else {
            image.setVisibility(View.GONE);
        }
        return view;
    }

    private void enableMyLocation() {
        if (aMap == null) return;
        MyLocationStyle style = new MyLocationStyle();
        style.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER);
        style.interval(5000);
        style.strokeColor(Color.TRANSPARENT);
        style.radiusFillColor(Color.argb(30, 0, 120, 255));
        aMap.setMyLocationStyle(style);
        aMap.setMyLocationEnabled(true);
    }

    private void checkPermissionAndLocate() {
        if (!isAdded()) return;
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation();
            startLocation();
        } else {
            permissionLauncher.launch(new String[]{
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
        if (loc == null) return;
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
    }

    private void moveToCurrentLocation() {
        if (currentLat != 0 && currentLng != 0) {
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(currentLat, currentLng), 16));
        } else if (isAdded()) {
            Toast.makeText(requireContext(), "定位中，请确认已开启GPS...", Toast.LENGTH_LONG).show();
        }
    }

    private void loadLocations() {
        RetrofitHelper.get().create(CatApi.class).getLocations()
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            allLocations.clear();
                            allLocations.addAll(response.body());
                            clusterAndDisplay();
                            catCountText.setText("共发现 " + allLocations.size() + " 只猫咪");
                        } else {
                            catCountText.setText("暂无记录");
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (isAdded()) catCountText.setText("加载失败");
                    }
                });
    }

    // ────────────────── Clustering ──────────────────

    private void clusterAndDisplay() {
        if (aMap == null || allLocations.isEmpty()) return;
        aMap.clear();
        markerDataMap.clear();

        float zoom = aMap.getCameraPosition().zoom;
        double clusterDistPx = CLUSTER_DISTANCE_DP * density;
        double metersPerPx = 156543.03392 * Math.cos(Math.toRadians(
                aMap.getCameraPosition().target.latitude)) / Math.pow(2, zoom);
        double clusterMeters = clusterDistPx * metersPerPx;

        List<List<Map<String, Object>>> clusters = new ArrayList<>();
        boolean[] used = new boolean[allLocations.size()];

        for (int i = 0; i < allLocations.size(); i++) {
            if (used[i]) continue;
            List<Map<String, Object>> cluster = new ArrayList<>();
            cluster.add(allLocations.get(i));
            used[i] = true;

            double lat1 = ((Number) allLocations.get(i).get("lat")).doubleValue();
            double lng1 = ((Number) allLocations.get(i).get("lng")).doubleValue();

            for (int j = i + 1; j < allLocations.size(); j++) {
                if (used[j]) continue;
                double lat2 = ((Number) allLocations.get(j).get("lat")).doubleValue();
                double lng2 = ((Number) allLocations.get(j).get("lng")).doubleValue();
                if (haversineMeters(lat1, lng1, lat2, lng2) < clusterMeters) {
                    cluster.add(allLocations.get(j));
                    used[j] = true;
                }
            }
            clusters.add(cluster);
        }

        for (List<Map<String, Object>> cluster : clusters) {
            if (cluster.size() == 1) {
                addSingleMarker(cluster.get(0));
            } else {
                addClusterMarker(cluster);
            }
        }
    }

    // ────────────────── Single Marker (pin shape) ──────────────────

    private void addSingleMarker(Map<String, Object> loc) {
        double lat = ((Number) loc.get("lat")).doubleValue();
        double lng = ((Number) loc.get("lng")).doubleValue();
        String color = str(loc.get("color"));
        String desc = str(loc.get("description"));
        String time = str(loc.get("reportTime"));
        String feature = str(loc.get("feature"));

        String title = color.isEmpty() ? "猫咪" : color + "猫";
        StringBuilder snippet = new StringBuilder();
        if (!feature.isEmpty()) snippet.append("特征: ").append(feature);
        if (!desc.isEmpty()) {
            if (snippet.length() > 0) snippet.append("\n");
            snippet.append(desc);
        }
        if (!time.isEmpty()) {
            if (snippet.length() > 0) snippet.append("\n");
            snippet.append("上报: ").append(time);
        }

        BitmapDescriptor icon = createPinMarker(getMarkerColor(color));

        Marker marker = aMap.addMarker(new MarkerOptions()
                .position(new LatLng(lat, lng))
                .title(title)
                .snippet(snippet.toString())
                .icon(icon)
                .anchor(0.5f, 1.0f)
                .zIndex(10));

        markerDataMap.put(marker.getId(), loc);
    }

    private BitmapDescriptor createPinMarker(int fillColor) {
        int w = (int) (36 * density);
        int h = (int) (48 * density);
        int circleR = (int) (14 * density);
        int cx = w / 2;
        int cy = (int) (16 * density);
        float border = 2.5f * density;

        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint shadowP = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowP.setColor(0x30000000);
        Path shadowPath = new Path();
        shadowPath.moveTo(cx - 4 * density, cy + circleR * 0.6f);
        shadowPath.lineTo(cx + 1 * density, h);
        shadowPath.lineTo(cx + 6 * density, cy + circleR * 0.6f);
        shadowPath.close();
        c.drawPath(shadowPath, shadowP);
        c.drawCircle(cx + 1 * density, cy + 1 * density, circleR, shadowP);

        Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillP.setColor(fillColor);
        Path pinPath = new Path();
        pinPath.moveTo(cx - 5 * density, cy + circleR * 0.6f);
        pinPath.lineTo(cx, h - 2 * density);
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

    // ────────────────── Cluster Marker (badge) ──────────────────

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
                .title(cluster.size() + " 只猫咪（点击放大查看）")
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

    // ────────────────── Helpers ──────────────────

    private int getMarkerColor(String color) {
        if (color == null || color.isEmpty()) return 0xFFFF6D00;
        if (color.contains("橘")) return 0xFFFF6D00;
        if (color.contains("黑")) return 0xFF37474F;
        if (color.contains("白")) return 0xFF78909C;
        if (color.contains("灰")) return 0xFF546E7A;
        if (color.contains("花") || color.contains("三花")) return 0xFFD81B60;
        if (color.contains("玳瑁")) return 0xFF6D4C41;
        if (color.contains("狸花")) return 0xFF2E7D32;
        if (color.contains("奶牛")) return 0xFF263238;
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

    // ────────────────── Lifecycle ──────────────────

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (locationClient != null) locationClient.stopLocation();
        if (mapView != null) mapView.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        if (locationClient != null) {
            try { locationClient.stopLocation(); locationClient.onDestroy(); } catch (Exception ignored) {}
            locationClient = null;
        }
        if (aMap != null) {
            try { aMap.setMyLocationEnabled(false); } catch (Exception ignored) {}
            aMap = null;
        }
        if (mapView != null) {
            try { mapView.onDestroy(); } catch (Exception ignored) {}
            mapView = null;
        }
        super.onDestroyView();
    }
}
