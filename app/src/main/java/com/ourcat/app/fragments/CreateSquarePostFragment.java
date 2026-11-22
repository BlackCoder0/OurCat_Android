package com.ourcat.app.fragments;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.core.LatLonPoint;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ourcat.app.R;
import com.ourcat.app.BuildConfig;
import com.ourcat.app.adapters.ImagePickAdapter;
import com.ourcat.app.utils.CatTagUtils;
import com.ourcat.app.utils.ImageUtils;
import com.ourcat.app.utils.LoadingDialogHelper;
import com.ourcat.app.utils.OssUploader;
import com.ourcat.app.utils.RetrofitHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 广场发布广播页面。对应计划文档「阶段 6 → 前端 sh→ 发布界面」。
 */
public class CreateSquarePostFragment extends Fragment {

    private static final int MAX_IMAGES = 9;
    private static final Pattern CAT_TAG_PATTERN = Pattern.compile("\\[cat:(\\d+):([^\\]]+)\\]");
    private static final String ARG_PRESET_TYPE = "presetType";
    private static final String ARG_PRESET_LAT = "presetLat";
    private static final String ARG_PRESET_LNG = "presetLng";
    private static final String ARG_PRESET_IMAGE_URI = "presetImageUri";

    private TextInputEditText etContent, etAnnotation;
    private TextInputEditText etLocation; // Use TextInputEditText for location display
    private TextInputLayout tilLocation;
    private ChipGroup typeGroup;
    private Spinner problemTypeSpinner;
    private View problemTypeLabel;
    private View rescueTypeHintCard;
    private MaterialButton btnSubmit;
    private RecyclerView rvImages;
    private ImagePickAdapter imageAdapter;
    private Map<String, Object> selectedRefCat = null;

    private AMapLocationClient locationClient;
    private AMapLocationClientOption locationOption;
    private double currentLat = 0;
    private double currentLng = 0;
    private String presetType = null;
    private Uri presetImageUri = null;
    private Dialog loadingDialog;

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            presetType = getArguments().getString(ARG_PRESET_TYPE);
            currentLat = getArguments().getDouble(ARG_PRESET_LAT, 0);
            currentLng = getArguments().getDouble(ARG_PRESET_LNG, 0);
            presetImageUri = getArguments().getParcelable(ARG_PRESET_IMAGE_URI);
        }
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        handleImageResult(result.getData());
                    }
                });
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    Boolean fine = result.get(android.Manifest.permission.ACCESS_FINE_LOCATION);
                    Boolean coarse = result.get(android.Manifest.permission.ACCESS_COARSE_LOCATION);
                    if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                        startLocation();
                    } else {
                        Toast.makeText(requireContext(), "需要定位权限才能获取当前位置", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_square_post, container, false);
        etContent = view.findViewById(R.id.et_content);
        typeGroup = view.findViewById(R.id.square_type_group);
        problemTypeSpinner = view.findViewById(R.id.sp_square_problem_type);
        problemTypeLabel = view.findViewById(R.id.tv_square_problem_type);
        rescueTypeHintCard = view.findViewById(R.id.card_rescue_type_hint);
        rvImages = view.findViewById(R.id.rv_square_images);

        tilLocation = view.findViewById(R.id.til_square_location);
        etLocation = view.findViewById(R.id.et_square_location);
        etAnnotation = view.findViewById(R.id.et_square_annotation);

        btnSubmit = view.findViewById(R.id.btn_square_submit);

        view.findViewById(R.id.btn_insert_cat).setOnClickListener(v -> openSelectCatDialog());

        imageAdapter = new ImagePickAdapter();
        imageAdapter.setOnRemoveListener(this::removeImage);
        rvImages.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        rvImages.setAdapter(imageAdapter);

        view.findViewById(R.id.btn_add_square_image).setOnClickListener(v -> pickImages());

        // Location click listener on TextInputEditText or TextInputLayout end icon
        tilLocation.setEndIconOnClickListener(v -> {
            if (hasLocationValue()) {
                clearLocationValue();
            } else {
                checkLocationPermission();
            }
        });

        setupProblemTypeSpinner();
        typeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> updateProblemTypeVisibility());
        applyQuickPreset();
        updateLocationEndIcon();
        updateProblemTypeVisibility();

        btnSubmit.setOnClickListener(v -> submit());
        return view;
    }

    private boolean hasLocationValue() {
        String loc = etLocation != null && etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        return (currentLat != 0 && currentLng != 0) || !loc.isEmpty();
    }

    private void clearLocationValue() {
        currentLat = 0;
        currentLng = 0;
        if (etLocation != null) {
            etLocation.setText("");
        }
        updateLocationEndIcon();
    }

    private void updateLocationEndIcon() {
        if (tilLocation == null) {
            return;
        }
        if (hasLocationValue()) {
            tilLocation.setEndIconDrawable(android.R.drawable.ic_menu_close_clear_cancel);
            tilLocation.setEndIconContentDescription("清除定位");
        } else {
            tilLocation.setEndIconDrawable(android.R.drawable.ic_menu_mylocation);
            tilLocation.setEndIconContentDescription("获取当前位置");
        }
    }

    private void applyQuickPreset() {
        if ("rescue".equalsIgnoreCase(presetType) && typeGroup != null) {
            typeGroup.check(R.id.chip_rescue);
        }
        if (presetImageUri != null && imageAdapter != null) {
            imageAdapter.addUri(presetImageUri);
            presetImageUri = null;
        }
        if (currentLat != 0 && currentLng != 0) {
            fillPresetLocation(currentLat, currentLng);
        }
    }

    private void setupProblemTypeSpinner() {
        String[] problemTypes = new String[] { "受伤", "疾病", "困境", "其他" };
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, problemTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        problemTypeSpinner.setAdapter(adapter);
    }

    private void updateProblemTypeVisibility() {
        int checkedId = typeGroup.getCheckedChipId();
        boolean isRescue = checkedId == R.id.chip_rescue;
        if (problemTypeSpinner != null) {
            problemTypeSpinner.setVisibility(isRescue ? View.VISIBLE : View.GONE);
        }
        if (problemTypeLabel != null) {
            problemTypeLabel.setVisibility(isRescue ? View.VISIBLE : View.GONE);
        }
        if (rescueTypeHintCard != null) {
            rescueTypeHintCard.setVisibility(isRescue ? View.VISIBLE : View.GONE);
        }
    }

    private void openSelectCatDialog() {
        SelectCatDialogFragment dialog = new SelectCatDialogFragment();
        dialog.setOnCatSelectedListener(cat -> {
            selectedRefCat = cat; // 保存选中的猫咪
            CatTagUtils.insertCatTag(requireContext(), etContent, cat);
        });
        dialog.show(requireActivity().getSupportFragmentManager(), "SelectCat");
    }

    private void clearRefCat() {
        selectedRefCat = null;
    }

    private void checkLocationPermission() {
        if (BuildConfig.ALLOW_SERVER_SWITCH) {
            fillPresetLocation(31.23, 121.47);
            return;
        }
        locationPermissionLauncher.launch(new String[] {
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void startLocation() {
        try {
            // Check if client is already created
            if (locationClient == null) {
                AMapLocationClient.updatePrivacyShow(requireContext(), true, true);
                AMapLocationClient.updatePrivacyAgree(requireContext(), true);
                locationClient = new AMapLocationClient(requireContext().getApplicationContext());
                locationOption = new AMapLocationClientOption();
                locationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                locationOption.setOnceLocation(true);
                locationOption.setNeedAddress(true);
                locationClient.setLocationOption(locationOption);

                locationClient.setLocationListener(aMapLocation -> {
                    if (aMapLocation != null) {
                        if (aMapLocation.getErrorCode() == 0) {
                            // 保存坐标
                            currentLat = aMapLocation.getLatitude();
                            currentLng = aMapLocation.getLongitude();
                            // Priority: Address > POI > City+District
                            String address = aMapLocation.getAddress();
                            if (address == null || address.trim().isEmpty()) {
                                address = aMapLocation.getPoiName();
                            }
                            if (address == null || address.trim().isEmpty()) {
                                address = aMapLocation.getCity() + aMapLocation.getDistrict()
                                        + aMapLocation.getStreet();
                            }

                            if (address != null && !address.trim().isEmpty()) {
                                etLocation.setText(address);
                                etLocation.setSelection(address.length());
                                updateLocationEndIcon();
                                Toast.makeText(requireContext(), "定位成功", Toast.LENGTH_SHORT).show();
                            } else {
                                etLocation.setText("附近");
                                updateLocationEndIcon();
                                Toast.makeText(requireContext(), "定位成功，但无法获取详细地址", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.e("AMap", "Location Error, ErrCode:"
                                    + aMapLocation.getErrorCode() + ", errInfo:"
                                    + aMapLocation.getErrorInfo());
                            Toast.makeText(requireContext(), "定位失败: " + aMapLocation.getErrorInfo(), Toast.LENGTH_SHORT)
                                    .show();
                        }
                    } else {
                        Toast.makeText(requireContext(), "定位结果为空", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            Toast.makeText(requireContext(), "正在获取位置...", Toast.LENGTH_SHORT).show();
            locationClient.startLocation();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(requireContext(), "定位服务启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fillPresetLocation(double lat, double lng) {
        if (!isAdded()) {
            return;
        }
        currentLat = lat;
        currentLng = lng;
        if (BuildConfig.ALLOW_SERVER_SWITCH) {
            etLocation.setText("示例位置：上海（31.23, 121.47）");
            updateLocationEndIcon();
            return;
        }
        etLocation.setText("定位中...");
        updateLocationEndIcon();
        try {
            GeocodeSearch geocodeSearch = new GeocodeSearch(requireContext());
            geocodeSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
                @Override
                public void onRegeocodeSearched(com.amap.api.services.geocoder.RegeocodeResult result, int rCode) {
                    if (!isAdded()) {
                        return;
                    }
                    String addressName = "附近";
                    if (rCode == 1000 && result != null && result.getRegeocodeAddress() != null) {
                        com.amap.api.services.geocoder.RegeocodeAddress address = result.getRegeocodeAddress();
                        String formatted = address.getFormatAddress();
                        if (formatted != null && !formatted.trim().isEmpty()) {
                            addressName = formatted;
                        } else if (address.getPois() != null && !address.getPois().isEmpty()
                                && address.getPois().get(0) != null
                                && address.getPois().get(0).getTitle() != null
                                && !address.getPois().get(0).getTitle().trim().isEmpty()) {
                            addressName = address.getPois().get(0).getTitle();
                        }
                    }
                    etLocation.setText(addressName);
                    updateLocationEndIcon();
                }

                @Override
                public void onGeocodeSearched(com.amap.api.services.geocoder.GeocodeResult result, int rCode) {
                }
            });
            RegeocodeQuery query = new RegeocodeQuery(new LatLonPoint(lat, lng), 200, GeocodeSearch.AMAP);
            geocodeSearch.getFromLocationAsyn(query);
        } catch (Exception e) {
            etLocation.setText("附近");
            updateLocationEndIcon();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        hideLoadingDialog();
        if (locationClient != null) {
            locationClient.onDestroy();
        }
    }

    private void reverseGeocode(double lat, double lng, String text, String anno, String type, long referencedCatId) {
        try {
            GeocodeSearch geocodeSearch = new GeocodeSearch(requireContext());
            geocodeSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
                @Override
                public void onRegeocodeSearched(com.amap.api.services.geocoder.RegeocodeResult result, int rCode) {
                    if (!isAdded())
                        return;

                    String addressName = "附近";
                    if (rCode == 1000 && result != null && result.getRegeocodeAddress() != null) {
                        com.amap.api.services.geocoder.RegeocodeAddress address = result.getRegeocodeAddress();
                        String formatted = address.getFormatAddress();
                        if (formatted != null && !formatted.isEmpty()) {
                            addressName = formatted;
                        } else {
                            String poi = address.getPois() != null && !address.getPois().isEmpty()
                                    ? address.getPois().get(0).getTitle()
                                    : null;
                            if (poi != null && !poi.isEmpty()) {
                                addressName = poi;
                            }
                        }
                    }

                    etLocation.setText(addressName);
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("发布");
                    submit();
                }

                @Override
                public void onGeocodeSearched(com.amap.api.services.geocoder.GeocodeResult result, int rCode) {
                }
            });

            RegeocodeQuery query = new RegeocodeQuery(new LatLonPoint(lat, lng), 200, GeocodeSearch.AMAP);
            geocodeSearch.getFromLocationAsyn(query);
        } catch (Exception e) {
            Log.e("CreateSquare", "逆地理编码失败", e);
            etLocation.setText("附近");
            btnSubmit.setEnabled(true);
            btnSubmit.setText("发布");
            submit();
        }
    }

    private void pickImages() {
        if (imageAdapter.getItemCount() >= MAX_IMAGES) {
            Toast.makeText(requireContext(), "最多" + MAX_IMAGES + "张图片", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        imagePickerLauncher.launch(Intent.createChooser(intent, "选择图片"));
    }

    private void handleImageResult(Intent data) {
        if (data.getClipData() != null) {
            int count = data.getClipData().getItemCount();
            for (int i = 0; i < count && imageAdapter.getItemCount() < MAX_IMAGES; i++) {
                imageAdapter.addUri(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null && imageAdapter.getItemCount() < MAX_IMAGES) {
            imageAdapter.addUri(data.getData());
        }
    }

    private void removeImage(int position) {
        imageAdapter.remove(position);
    }

    private void submit() {
        String text = etContent.getText() != null ? etContent.getText().toString().trim() : "";
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), "请输入广播内容", Toast.LENGTH_SHORT).show();
            return;
        }
        long referencedCatIdInText = extractFirstCatId(text);
        int checkedId = typeGroup.getCheckedChipId();
        String type = "inquiry";
        if (checkedId == R.id.chip_rescue)
            type = "rescue";
        String problemType = null;
        if ("rescue".equals(type) && problemTypeSpinner != null && problemTypeSpinner.getSelectedItem() != null) {
            problemType = String.valueOf(problemTypeSpinner.getSelectedItem()).trim();
        }

        String loc = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        String anno = etAnnotation.getText() != null ? etAnnotation.getText().toString().trim() : "";

        Log.d("CreateSquarePost",
                "提交时 - loc: [" + loc + "], anno: [" + anno + "], lat: " + currentLat + ", lng: " + currentLng);

        // 如果有坐标但地名为空,先进行逆地理编码
        if (currentLat != 0 && currentLng != 0 && loc.isEmpty()) {
            btnSubmit.setEnabled(false);
            btnSubmit.setText("获取地址中...");
            reverseGeocode(currentLat, currentLng, text, anno, type, referencedCatIdInText);
            return;
        }

        String finalLocation = null;
        // 如果有坐标，优先使用坐标格式
        if (currentLat != 0 && currentLng != 0) {
            String displayName = loc.isEmpty() ? "附近" : loc;
            finalLocation = currentLat + "," + currentLng + " " + displayName;
            if (!anno.isEmpty()) {
                finalLocation = finalLocation + " (" + anno + ")";
            }
            Log.d("CreateSquarePost", "最终位置信息: [" + finalLocation + "]");
        } else if (!loc.isEmpty() && !anno.isEmpty()) {
            finalLocation = loc + " (" + anno + ")";
        } else if (!loc.isEmpty()) {
            finalLocation = loc;
        } else if (!anno.isEmpty()) {
            finalLocation = anno;
        }

        // 救助类型校验
        if ("rescue".equals(type)) {
            List<Uri> newUris = imageAdapter.getNewUris();
            boolean hasLocation = finalLocation != null && !finalLocation.isEmpty();
            boolean hasImages = !newUris.isEmpty();
            boolean hasCatTag = referencedCatIdInText > 0;
            if (!(hasCatTag || (hasLocation && hasImages))) {
                Toast.makeText(requireContext(), "发布救助帖需要：正文插入猫咪档案，或同时提供定位和照片", Toast.LENGTH_LONG).show();
                return;
            }
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("发布中...");

        showLoadingDialog();
        LoadingDialogHelper.updateInfo(loadingDialog, "正在准备上传图片...");
        List<Uri> newUris = imageAdapter.getNewUris();
        long finalReferencedCatId = referencedCatIdInText > 0 ? referencedCatIdInText
                : (selectedRefCat != null && selectedRefCat.get("id") instanceof Number
                        ? ((Number) selectedRefCat.get("id")).longValue()
                        : 0L);
        if (newUris.isEmpty()) {
            LoadingDialogHelper.updateInfo(loadingDialog, "正在提交到服务器...");
            doCreatePost(text, new ArrayList<>(), finalLocation, type, finalReferencedCatId, problemType);
        } else {
            uploadImagesParallel(newUris, text, finalLocation, type, finalReferencedCatId, problemType);
        }
    }

    private void uploadImagesParallel(List<Uri> uris,
            String text, String location, String type, long referencedCatId, String problemType) {
        int total = uris.size();
        String[] uploadedUrls = new String[total];
        AtomicInteger completed = new AtomicInteger(0);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (int i = 0; i < total; i++) {
            Uri uri = uris.get(i);
            String mimeType = requireContext().getContentResolver().getType(uri);
            if (mimeType == null)
                mimeType = "image/jpeg";
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (ext == null)
                ext = "jpg";
            String filename = "square_img_" + System.currentTimeMillis() + "_" + i + "." + ext;
            String finalMimeType = mimeType;
            int index = i;

            RetrofitHelper.get().create(com.ourcat.app.repositories.OssApi.class)
                    .getUploadUrl(filename, mimeType)
                    .enqueue(new Callback<Map<String, String>>() {
                        @Override
                        public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                            if (failed.get() || !isAdded()) {
                                return;
                            }
                            if (!response.isSuccessful() || response.body() == null) {
                                failParallelUpload(failed, "获取上传URL失败");
                                return;
                            }
                            String uploadUrl = response.body().get("uploadUrl");
                            String publicUrl = response.body().get("publicUrl");
                            String ct = response.body().get("contentType");
                            if (ct == null)
                                ct = finalMimeType;
                            uploadSingleFileParallel(uri, uploadUrl, publicUrl, ct, index, uploadedUrls,
                                    completed, total, failed, text, location, type, referencedCatId, problemType);
                        }

                        @Override
                        public void onFailure(Call<Map<String, String>> call, Throwable t) {
                            failParallelUpload(failed, "网络错误");
                        }
                    });
        }
    }

    private void uploadSingleFileParallel(Uri uri, String uploadUrl, String publicUrl, String contentType, int index,
            String[] uploadedUrls, AtomicInteger completed, int total, AtomicBoolean failed,
            String text, String location, String type, long referencedCatId, String problemType) {
        new Thread(() -> {
            File tempFile = null;
            try {
                tempFile = uriToTempFile(uri);
                new OssUploader().uploadWithPresign(uploadUrl, publicUrl, tempFile, contentType);
                uploadedUrls[index] = publicUrl;
                int count = completed.incrementAndGet();
                requireActivity().runOnUiThread(() -> {
                    LoadingDialogHelper.updateInfo(loadingDialog, "正在上传图片 (" + count + "/" + total + ")...");
                });
                if (count == total && !failed.get() && isAdded()) {
                    requireActivity().runOnUiThread(() -> {
                        LoadingDialogHelper.updateInfo(loadingDialog, "正在提交到服务器...");
                        doCreatePost(text, Arrays.asList(uploadedUrls), location,
                                type, referencedCatId, problemType);
                    });
                }
            } catch (Exception e) {
                failParallelUpload(failed, "上传失败: " + e.getMessage());
            } finally {
                if (tempFile != null && tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }).start();
    }

    private void failParallelUpload(AtomicBoolean failed, String msg) {
        if (!failed.compareAndSet(false, true) || !isAdded()) {
            return;
        }
        requireActivity().runOnUiThread(() -> onFail(msg));
    }

    private File uriToTempFile(Uri uri) throws Exception {
        return com.ourcat.app.utils.ImageUtils.uriToTempFile(requireContext(), uri);
    }

    private void onFail(String msg) {
        hideLoadingDialog();
        btnSubmit.setEnabled(true);
        btnSubmit.setText("发布");
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void doCreatePost(String text, List<String> imageUrls, String location, String type, long referencedCatId,
            String problemType) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("images", imageUrls);
        body.put("type", type);
        if (location != null)
            body.put("location", location);
        if (referencedCatId > 0)
            body.put("referencedCatId", referencedCatId);
        if ("rescue".equals(type) && problemType != null && !problemType.isEmpty()) {
            body.put("problemType", problemType);
        }

        doCreatePost(body);
    }

    private void createRescueActivityAndPost(String text, List<String> imageUrls, String location,
            String type, String finalLocation, long referencedCatId) {
        // 先创建救助活动
        Map<String, Object> activityBody = new HashMap<>();
        activityBody.put("title", "救助广播");
        activityBody.put("description", text);

        // 解析坐标
        if (finalLocation != null && finalLocation.contains(",")) {
            String[] parts = finalLocation.split(" ");
            if (parts.length > 0) {
                String[] coords = parts[0].split(",");
                if (coords.length >= 2) {
                    try {
                        String a = coords[0].trim();
                        String b = coords[1].trim();
                        if (a.matches("-?\\d+(\\.\\d+)?") && b.matches("-?\\d+(\\.\\d+)?")) {
                            activityBody.put("latitude", Double.parseDouble(a));
                            activityBody.put("longitude", Double.parseDouble(b));
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        String locationName = extractLocationName(location);
        if (locationName != null && !locationName.isEmpty()) {
            activityBody.put("locationName", locationName);
        }

        // 关联猫咪
        if (referencedCatId > 0)
            activityBody.put("catId", referencedCatId);

        RetrofitHelper.get().create(com.ourcat.app.repositories.RescueApi.class)
                .createActivity(activityBody)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                            Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            // 救助活动创建成功，获取活动ID
                            Object activityIdObj = response.body().get("id");
                            if (activityIdObj != null) {
                                long activityId = ((Number) activityIdObj).longValue();
                                Log.d("CreateSquarePost", "救助活动创建成功: " + activityId);

                                // 创建广播并关联救助活动
                                Map<String, Object> postBody = new HashMap<>();
                                postBody.put("text", text);
                                postBody.put("images", imageUrls);
                                postBody.put("type", type);
                                if (location != null)
                                    postBody.put("location", location);
                                if (referencedCatId > 0)
                                    postBody.put("referencedCatId", referencedCatId);
                                postBody.put("rescueActivityId", activityId);

                                doCreatePost(postBody);
                                return;
                            }
                        }
                        // 如果创建救助活动失败，仍然创建广播
                        Log.d("CreateSquarePost", "救助活动创建失败，创建普通广播");
                        Map<String, Object> postBody = new HashMap<>();
                        postBody.put("text", text);
                        postBody.put("images", imageUrls);
                        postBody.put("type", type);
                        if (location != null)
                            postBody.put("location", location);
                        if (referencedCatId > 0)
                            postBody.put("referencedCatId", referencedCatId);
                        doCreatePost(postBody);
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        // 如果创建救助活动失败，仍然创建广播
                        Log.d("CreateSquarePost", "救助活动创建失败: " + t.getMessage());
                        Map<String, Object> postBody = new HashMap<>();
                        postBody.put("text", text);
                        postBody.put("images", imageUrls);
                        postBody.put("type", type);
                        if (location != null)
                            postBody.put("location", location);
                        if (referencedCatId > 0)
                            postBody.put("referencedCatId", referencedCatId);
                        doCreatePost(postBody);
                    }
                });
    }

    private String extractLocationName(String location) {
        if (location == null || location.trim().isEmpty()) {
            return "";
        }
        String t = location.trim();
        int annoIdx = t.lastIndexOf(" (");
        if (annoIdx > 0 && t.endsWith(")")) {
            t = t.substring(0, annoIdx).trim();
        }
        int space = t.indexOf(' ');
        if (space > 0) {
            String prefix = t.substring(0, space);
            if (prefix.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
                return t.substring(space + 1).trim();
            }
        }
        if (t.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
            return "附近";
        }
        return t;
    }

    private long extractFirstCatId(String text) {
        if (text == null || text.isEmpty())
            return 0L;
        Matcher matcher = CAT_TAG_PATTERN.matcher(text);
        if (!matcher.find())
            return 0L;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void doCreatePost(Map<String, Object> body) {
        RetrofitHelper.get().create(com.ourcat.app.repositories.SquareApi.class)
                .createPost(body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("发布");
                        if (response.isSuccessful()) {
                            hideLoadingDialog();
                            Toast.makeText(requireContext(), "发布成功", Toast.LENGTH_SHORT).show();
                            Navigation.findNavController(requireView()).navigateUp();
                        } else {
                            if (response.code() == 500) {
                                btnSubmit.setEnabled(false);
                                btnSubmit.setText("确认中...");
                                confirmCreatedAfterServerError(body);
                                return;
                            }
                            String errorMsg = "发布失败: " + response.code();
                            try {
                                if (response.errorBody() != null) {
                                    String json = response.errorBody().string();
                                    // 尝试解析后端返回的错误消息
                                    if (json.contains("message")) {
                                        int start = json.indexOf("\"message\":\"") + 10;
                                        int end = json.indexOf("\"", start);
                                        if (start > 9 && end > start) {
                                            errorMsg = json.substring(start, end);
                                        }
                                    }
                                }
                            } catch (Exception ignored) {
                            }
                            Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        hideLoadingDialog();
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("发布");
                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void confirmCreatedAfterServerError(Map<String, Object> originalBody) {
        confirmCreatedAfterServerError(originalBody, 0);
    }

    @SuppressWarnings("unchecked")
    private void confirmCreatedAfterServerError(Map<String, Object> originalBody, int attempt) {
        RetrofitHelper.get().create(com.ourcat.app.repositories.SquareApi.class)
                .listPosts(0, 50, "createdAt,desc")
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            onConfirmFailed(attempt, originalBody);
                            return;
                        }
                        Object contentObj = response.body().get("content");
                        if (!(contentObj instanceof List)) {
                            onConfirmFailed(attempt, originalBody);
                            return;
                        }
                        for (Object o : (List<?>) contentObj) {
                            if (!(o instanceof Map)) {
                                continue;
                            }
                            Map<String, Object> post = (Map<String, Object>) o;
                            if (matchesCreatedPost(originalBody, post)) {
                                hideLoadingDialog();
                                btnSubmit.setEnabled(true);
                                btnSubmit.setText("发布");
                                Toast.makeText(requireContext(), "发布成功", Toast.LENGTH_SHORT).show();
                                Navigation.findNavController(requireView()).navigateUp();
                                return;
                            }
                        }
                        onConfirmFailed(attempt, originalBody);
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        onConfirmFailed(attempt, originalBody);
                    }
                });
    }

    private void onConfirmFailed(int attempt, Map<String, Object> originalBody) {
        if (!isAdded()) {
            return;
        }
        if (attempt < 2) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (!isAdded()) {
                    return;
                }
                confirmCreatedAfterServerError(originalBody, attempt + 1);
            }, 900L);
            return;
        }
        btnSubmit.setEnabled(true);
        btnSubmit.setText("发布");
        hideLoadingDialog();
        Toast.makeText(requireContext(), "服务器返回异常，请到广场确认是否已发布", Toast.LENGTH_SHORT).show();
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

    private boolean matchesCreatedPost(Map<String, Object> originalBody, Map<String, Object> post) {
        if (originalBody == null || post == null) {
            return false;
        }

        long rescueActivityId = originalBody.get("rescueActivityId") instanceof Number
                ? ((Number) originalBody.get("rescueActivityId")).longValue()
                : 0L;
        if (rescueActivityId > 0) {
            long pActivityId = post.get("rescueActivityId") instanceof Number
                    ? ((Number) post.get("rescueActivityId")).longValue()
                    : 0L;
            return pActivityId == rescueActivityId;
        }

        String text = originalBody.get("text") != null ? String.valueOf(originalBody.get("text")) : "";
        String type = originalBody.get("type") != null ? String.valueOf(originalBody.get("type")) : "";
        long referencedCatId = originalBody.get("referencedCatId") instanceof Number
                ? ((Number) originalBody.get("referencedCatId")).longValue()
                : 0L;

        String pText = post.get("text") != null ? String.valueOf(post.get("text")) : "";
        String pType = post.get("type") != null ? String.valueOf(post.get("type")) : "";
        long pRefCatId = post.get("referencedCatId") instanceof Number
                ? ((Number) post.get("referencedCatId")).longValue()
                : 0L;

        if (!text.equals(pText) || !type.equals(pType)) {
            return false;
        }
        if (referencedCatId > 0 && referencedCatId != pRefCatId) {
            return false;
        }
        return true;
    }
}
