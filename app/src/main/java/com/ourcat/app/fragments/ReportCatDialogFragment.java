package com.ourcat.app.fragments;

import android.Manifest;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.TextureMapView;
import com.amap.api.maps.model.CameraPosition;
import com.amap.api.maps.model.LatLng;
import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputLayout;
import com.ourcat.app.R;
import com.ourcat.app.repositories.CatApi;
import com.ourcat.app.repositories.OssApi;
import com.ourcat.app.utils.ImageUtils;
import com.ourcat.app.utils.LoadingDialogHelper;
import com.ourcat.app.utils.OssUploader;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.TokenStore;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportCatDialogFragment extends DialogFragment {

    private static final String TAG = "ReportCatDialog";
    private static final String ARG_LAT = "lat";
    private static final String ARG_LNG = "lng";
    private static final String ARG_AUTO_CAMERA = "auto_camera";
    private static final String ARG_INITIAL_IMAGE_URI = "initial_image_uri";
    private static final int UPLOAD_MAX_LONG_SIDE = 2560;
    private static final int UPLOAD_JPEG_QUALITY = 92;

    private Runnable onReportSuccess;
    private double currentLat = 0;
    private double currentLng = 0;
    private boolean autoCamera = false;
    private boolean autoCameraTriggered = false;
    // 新增：记录初始定位，用于“回到我的位置”
    private double initialLat = 0;
    private double initialLng = 0;
    private Uri initialImageUri;

    private TextureMapView mapView;
    private AMap aMap;
    private TextView locationText;
    private String lastRegeoKey;

    private final List<Uri> selectedImages = new ArrayList<>();
    private ImageAdapter imageAdapter;
    private Uri cameraUri;
    private Dialog loadingDialog;

    public static ReportCatDialogFragment newInstance(double lat, double lng) {
        return newInstance(lat, lng, false);
    }

    public static ReportCatDialogFragment newInstance(double lat, double lng, boolean autoCamera) {
        ReportCatDialogFragment f = new ReportCatDialogFragment();
        Bundle args = new Bundle();
        args.putDouble(ARG_LAT, lat);
        args.putDouble(ARG_LNG, lng);
        args.putBoolean(ARG_AUTO_CAMERA, autoCamera);
        f.setArguments(args);
        return f;
    }

    public static ReportCatDialogFragment newInstance(double lat, double lng, Uri initialImageUri) {
        ReportCatDialogFragment f = new ReportCatDialogFragment();
        Bundle args = new Bundle();
        args.putDouble(ARG_LAT, lat);
        args.putDouble(ARG_LNG, lng);
        args.putBoolean(ARG_AUTO_CAMERA, false);
        args.putParcelable(ARG_INITIAL_IMAGE_URI, initialImageUri);
        f.setArguments(args);
        return f;
    }

    public void setOnReportSuccess(Runnable r) {
        onReportSuccess = r;
    }

    private final ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.PickMultipleVisualMedia(), uris -> {
                if (uris != null) {
                    for (Uri uri : uris) {
                        requireContext().getContentResolver()
                                .takePersistableUriPermission(uri,
                                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        selectedImages.add(uri);
                    }
                    imageAdapter.notifyDataSetChanged();
                }
            });

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), success -> {
                if (success && cameraUri != null) {
                    selectedImages.add(cameraUri);
                    imageAdapter.notifyDataSetChanged();
                }
            });

    private final ActivityResultLauncher<String> cameraPermLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted)
                    launchCamera();
                else if (isAdded())
                    Toast.makeText(requireContext(), "需要相机权限", Toast.LENGTH_SHORT).show();
            });

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.FullScreenDialog);
        if (getArguments() != null) {
            currentLat = getArguments().getDouble(ARG_LAT, 0);
            currentLng = getArguments().getDouble(ARG_LNG, 0);
            initialLat = currentLat;
            initialLng = currentLng;
            autoCamera = getArguments().getBoolean(ARG_AUTO_CAMERA, false);
            initialImageUri = getArguments().getParcelable(ARG_INITIAL_IMAGE_URI);
            if (initialImageUri != null) {
                selectedImages.add(initialImageUri);
            }
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_report_cat, container, false);

        EditText etDesc = view.findViewById(R.id.report_description);

        // 初始化地图
        mapView = view.findViewById(R.id.report_map_view);
        locationText = view.findViewById(R.id.report_location_text);
        if (com.ourcat.app.BuildConfig.ALLOW_SERVER_SWITCH) {
            ((TextView) view.findViewById(R.id.report_location_title)).setText("本地示例位置");
            currentLat = 31.23;
            currentLng = 121.47;
            ((View) mapView.getParent()).setVisibility(View.GONE);
            mapView = null;
            view.findViewById(R.id.report_demo_location).setVisibility(View.VISIBLE);
        } else {
            mapView.onCreate(savedInstanceState);
        }

        if (mapView != null && aMap == null) {
            aMap = mapView.getMap();
            aMap.getUiSettings().setZoomControlsEnabled(false);
            aMap.getUiSettings().setMyLocationButtonEnabled(false);

            if (currentLat != 0 && currentLng != 0) {
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(currentLat, currentLng), 17));
                updateLocationText(currentLat, currentLng);
            }

            aMap.setOnCameraChangeListener(new AMap.OnCameraChangeListener() {
                @Override
                public void onCameraChange(CameraPosition cameraPosition) {
                }

                @Override
                public void onCameraChangeFinish(CameraPosition cameraPosition) {
                    currentLat = cameraPosition.target.latitude;
                    currentLng = cameraPosition.target.longitude;
                    updateLocationText(currentLat, currentLng);
                }
            });
        }

        // 绑定“我的位置”按钮
        view.findViewById(R.id.report_fab_my_location).setOnClickListener(v -> {
            if (aMap != null && currentLat != 0 && currentLng != 0) {
                // 如果已有初始定位，直接回到该位置（假设初始位置即为用户当前位置）
                // 更好的做法是重新请求一次定位，但为了简化逻辑且避免再次请求权限，这里先复用传入的经纬度或当前中心点
                // 实际上，这个 Dialog 启动时传入的 lat/lng 就是用户当前定位
                // 所以我们这里回到初始传入的坐标，或者如果地图移动了，我们希望它能回到“我”的位置
                // 由于 ReportCatDialogFragment 启动时是接收了当前定位的，我们可以认为 initialLat/Lng (这里被 currentLat
                // 复用了，需要修正逻辑) 是准的
                // 但因为 currentLat 会随地图移动而改变，我们需要一个 final 或 member 变量存储“初始定位”

                // 修正：我们需要单独存一下进入页面时的初始定位
                if (initialLat != 0 && initialLng != 0) {
                    aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(initialLat, initialLng), 17));
                }
            }
        });

        RecyclerView imageList = view.findViewById(R.id.report_image_list);
        imageList.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        imageAdapter = new ImageAdapter();
        imageList.setAdapter(imageAdapter);

        view.findViewById(R.id.report_btn_gallery)
                .setOnClickListener(v -> galleryLauncher.launch(
                        new androidx.activity.result.PickVisualMediaRequest.Builder()
                                .setMediaType(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                                .build()));

        view.findViewById(R.id.report_btn_camera).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                launchCamera();
            } else {
                cameraPermLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        setupChipGroup(view, R.id.chip_group_color,
                new String[] {
                        "橘色", "黑色", "白色", "灰色", // 纯色
                        "狸花", "银虎斑", // 虎斑
                        "奶牛", "三花", "玳瑁", // 双色/多色
                        "奶油色", "暹罗色" // 特殊色
                },
                R.id.report_color_input_layout, true);
        setupChipGroup(view, R.id.chip_group_feature,
                new String[] {
                        "短毛", "长毛", // 毛发长度
                        "立耳", "折耳", // 耳朵
                        "长尾", "短尾", "断尾", // 尾巴
                        "偏瘦", "偏胖", // 体型
                        "有项圈", "有耳标", "剪耳", // 人工标记
                        "绝育", "未绝育"
                },
                R.id.report_feature_input_layout, false);
        setupChipGroup(view, R.id.chip_group_personality,
                new String[] {
                        "亲人", "独立", "警惕", "怕人", // 社交性
                        "活泼", "安静", // 活跃度
                        "好奇", "胆小", "有攻击性" // 特殊
                },
                R.id.report_personality_input_layout, false);

        view.findViewById(R.id.report_submit).setOnClickListener(v -> {
            if (currentLat == 0 || currentLng == 0) {
                Toast.makeText(requireContext(), "请拖动地图选择有效位置", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> body = new HashMap<>();
            body.put("lat", currentLat);
            body.put("lng", currentLng);
            body.put("description", etDesc.getText().toString().trim());

            String color = getChipValues(view, R.id.chip_group_color, R.id.report_color_input, true);
            String feature = getChipValues(view, R.id.chip_group_feature, R.id.report_feature_input, false);
            String personality = getChipValues(view, R.id.chip_group_personality, R.id.report_personality_input, false);
            if (!color.isEmpty())
                body.put("color", color);
            if (!feature.isEmpty())
                body.put("feature", feature);
            if (!personality.isEmpty())
                body.put("personality", personality);

            showLoadingDialog();
            LoadingDialogHelper.updateInfo(loadingDialog, "正在准备上报...");
            if (!selectedImages.isEmpty()) {
                uploadImagesThenReport(body);
            } else {
                submitReport(body);
            }
        });

        view.findViewById(R.id.report_cancel).setOnClickListener(v -> dismiss());
        return view;
    }

    private void updateLocationText(double lat, double lng) {
        if (locationText != null) {
            String key = String.format(java.util.Locale.US, "%.5f,%.5f", lat, lng);
            if (key.equals(lastRegeoKey)) {
                return;
            }
            lastRegeoKey = key;
            locationText.setText("已选: 获取中...");
            try {
                com.amap.api.services.geocoder.GeocodeSearch geocodeSearch = new com.amap.api.services.geocoder.GeocodeSearch(
                        requireContext());
                geocodeSearch.setOnGeocodeSearchListener(
                        new com.amap.api.services.geocoder.GeocodeSearch.OnGeocodeSearchListener() {
                            @Override
                            public void onRegeocodeSearched(com.amap.api.services.geocoder.RegeocodeResult result,
                                    int rCode) {
                                if (!isAdded() || locationText == null) {
                                    return;
                                }
                                if (rCode == 1000 && result != null && result.getRegeocodeAddress() != null) {
                                    com.amap.api.services.geocoder.RegeocodeAddress addr = result.getRegeocodeAddress();
                                    String name = "";
                                    if (addr.getAois() != null && !addr.getAois().isEmpty()
                                            && addr.getAois().get(0) != null
                                            && addr.getAois().get(0).getAoiName() != null
                                            && !addr.getAois().get(0).getAoiName().isEmpty()) {
                                        name = addr.getAois().get(0).getAoiName() + "附近";
                                    } else if (addr.getDistrict() != null && !addr.getDistrict().isEmpty()) {
                                        name = addr.getDistrict() + "附近";
                                    } else if (addr.getCity() != null && !addr.getCity().isEmpty()) {
                                        name = addr.getCity();
                                    } else if (addr.getProvince() != null && !addr.getProvince().isEmpty()) {
                                        name = addr.getProvince();
                                    }
                                    if (name.isEmpty()) {
                                        name = "附近";
                                    }
                                    locationText.setText("已选: " + name);
                                } else {
                                    locationText.setText("已选: 附近");
                                }
                            }

                            @Override
                            public void onGeocodeSearched(com.amap.api.services.geocoder.GeocodeResult result,
                                    int rCode) {
                            }
                        });
                com.amap.api.services.geocoder.RegeocodeQuery query = new com.amap.api.services.geocoder.RegeocodeQuery(
                        new com.amap.api.services.core.LatLonPoint(lat, lng), 200,
                        com.amap.api.services.geocoder.GeocodeSearch.AMAP);
                geocodeSearch.getFromLocationAsyn(query);
            } catch (Exception e) {
                locationText.setText("已选: 附近");
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null)
            mapView.onResume();
        if (autoCamera && !autoCameraTriggered) {
            autoCameraTriggered = true;
            if (getView() != null) getView().postDelayed(this::triggerAutoCamera, 300);
        }
    }

    private void triggerAutoCamera() {
        if (!isAdded())
            return;
        if (ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera();
        } else {
            cameraPermLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null)
            mapView.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideLoadingDialog();
        if (mapView != null)
            mapView.onDestroy();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null)
            mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d != null && d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private void setupChipGroup(View root, int chipGroupId, String[] options,
            int inputLayoutId, boolean singleSelection) {
        ChipGroup group = root.findViewById(chipGroupId);
        group.setSingleSelection(singleSelection);
        TextInputLayout inputLayout = root.findViewById(inputLayoutId);

        for (String option : options) {
            Chip chip = new Chip(requireContext());
            chip.setText(option);
            chip.setCheckable(true);
            chip.setChipBackgroundColorResource(android.R.color.white);
            chip.setChipStrokeWidth(2f);
            chip.setChipStrokeColorResource(R.color.text_secondary);
            chip.setTextSize(13);
            chip.setCheckedIconVisible(true);
            group.addView(chip);
        }

        Chip otherChip = new Chip(requireContext());
        otherChip.setText("其他");
        otherChip.setCheckable(true);
        otherChip.setChipBackgroundColorResource(android.R.color.white);
        otherChip.setChipStrokeWidth(2f);
        otherChip.setChipStrokeColorResource(R.color.text_secondary);
        otherChip.setTextSize(13);
        otherChip.setCheckedIconVisible(true);
        otherChip.setOnCheckedChangeListener(
                (btn, checked) -> inputLayout.setVisibility(checked ? View.VISIBLE : View.GONE));
        group.addView(otherChip);
    }

    private String getChipValues(View root, int chipGroupId, int inputEditId, boolean singleMode) {
        ChipGroup group = root.findViewById(chipGroupId);
        List<String> values = new ArrayList<>();

        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.isChecked()) {
                    if ("其他".equals(chip.getText().toString())) {
                        EditText input = root.findViewById(inputEditId);
                        String custom = input != null ? input.getText().toString().trim() : "";
                        if (!custom.isEmpty())
                            values.add(custom);
                    } else {
                        values.add(chip.getText().toString());
                    }
                }
            }
        }
        return String.join("、", values);
    }

    private void launchCamera() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "cat_" + System.currentTimeMillis());
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        cameraUri = requireContext().getContentResolver()
                .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (cameraUri != null)
            cameraLauncher.launch(cameraUri);
    }

    private void uploadImagesThenReport(Map<String, Object> body) {
        Uri firstUri = selectedImages.get(0);
        String filename = "cat_report/" + UUID.randomUUID() + ".jpg";

        RetrofitHelper.get().create(OssApi.class)
                .getUploadUrl(filename, "image/jpeg")
                .enqueue(new Callback<Map<String, String>>() {
                    @Override
                    public void onResponse(Call<Map<String, String>> call,
                            Response<Map<String, String>> response) {
                        if (!isAdded())
                            return;
                        if (!response.isSuccessful() || response.body() == null) {
                            hideLoadingDialog();
                            Toast.makeText(requireContext(), "获取上传链接失败", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        LoadingDialogHelper.updateInfo(loadingDialog, "正在上传图片...");
                        String uploadUrl = response.body().get("uploadUrl");
                        String publicUrl = response.body().get("publicUrl");

                        new Thread(() -> {
                            File temp = null;
                            try {
                                temp = ImageUtils.compressUriForUpload(
                                        requireContext(), firstUri, UPLOAD_MAX_LONG_SIDE, UPLOAD_JPEG_QUALITY);
                                new OssUploader().uploadWithPresign(uploadUrl, publicUrl, temp, "image/jpeg");
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        if (isAdded()) {
                                            body.put("imageUrl", publicUrl);
                                            submitReport(body);
                                        }
                                    });
                                }
                            } catch (Exception e) {
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> {
                                        if (isAdded()) {
                                            hideLoadingDialog();
                                            Toast.makeText(requireContext(), "上传失败", Toast.LENGTH_SHORT).show();
                                        }
                                    });
                                }
                            } finally {
                                if (temp != null && temp.exists()) {
                                    temp.delete();
                                }
                            }
                        }).start();
                    }

                    @Override
                    public void onFailure(Call<Map<String, String>> call, Throwable t) {
                        if (isAdded()) {
                            hideLoadingDialog();
                            Log.e(TAG, "getUploadUrl failed baseUrl=" + RetrofitHelper.getBaseUrl(), t);
                            Toast.makeText(requireContext(),
                                    "网络错误: " + formatNetworkError(t) + "\n" + safeUrl(RetrofitHelper.getBaseUrl()),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void submitReport(Map<String, Object> body) {
        LoadingDialogHelper.updateInfo(loadingDialog, "正在提交上报...");
        RetrofitHelper.get().create(CatApi.class).report(body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                            Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        if (response.isSuccessful()) {
                            hideLoadingDialog();
                            Toast.makeText(requireContext(), "上报成功!", Toast.LENGTH_SHORT).show();
                            dismiss();
                            if (onReportSuccess != null)
                                onReportSuccess.run();
                        } else {
                            hideLoadingDialog();
                            int code = response.code();
                            if (code == 401) {
                                new TokenStore(requireContext()).clear();
                                Toast.makeText(requireContext(), "登录已失效，请重新登录", Toast.LENGTH_LONG).show();
                                dismiss();
                            } else if (code == 404) {
                                Toast.makeText(requireContext(), "接口不存在，请更新后端", Toast.LENGTH_LONG).show();
                            } else if (code == 400) {
                                Toast.makeText(requireContext(), "请求参数错误: 400", Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(requireContext(), "上报失败: " + code, Toast.LENGTH_LONG).show();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded()) {
                            hideLoadingDialog();
                            Log.e(TAG, "report failed baseUrl=" + RetrofitHelper.getBaseUrl(), t);
                            Toast.makeText(requireContext(),
                                    "网络错误: " + formatNetworkError(t) + "\n" + safeUrl(RetrofitHelper.getBaseUrl()),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private static String formatNetworkError(@Nullable Throwable t) {
        if (t == null) {
            return "unknown";
        }
        String name = t.getClass().getSimpleName();
        String msg = t.getMessage();
        if (msg == null || msg.trim().isEmpty()) {
            return name;
        }
        return name + ": " + msg;
    }

    private static String safeUrl(@Nullable String url) {
        if (url == null || url.trim().isEmpty()) {
            return "BASE_URL 未配置";
        }
        return "BASE_URL=" + url;
    }

    private void showLoadingDialog() {
        if (!isAdded()) {
            return;
        }
        if (loadingDialog == null) {
            loadingDialog = LoadingDialogHelper.create(requireContext(), "少女折寿中");
        }
        if (!loadingDialog.isShowing()) {
            loadingDialog.show();
        }
    }

    private void hideLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            ImageView preview, delete;

            VH(View v) {
                super(v);
                preview = v.findViewById(R.id.report_img_preview);
                delete = v.findViewById(R.id.report_img_delete);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_report_image, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Glide.with(h.preview).load(selectedImages.get(position)).centerCrop().into(h.preview);
            h.delete.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos >= 0 && pos < selectedImages.size()) {
                    selectedImages.remove(pos);
                    notifyItemRemoved(pos);
                }
            });
        }

        @Override
        public int getItemCount() {
            return selectedImages.size();
        }
    }
}
