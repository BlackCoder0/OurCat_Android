package com.ourcat.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ourcat.app.R;
import com.ourcat.app.adapters.ImagePickAdapter;
import com.ourcat.app.utils.OssUploader;
import com.ourcat.app.utils.RetrofitHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 广场发布广播页面。对应计划文档「阶段 6 → 前端 → 发布界面」。
 */
public class CreateSquarePostFragment extends Fragment {

    private static final int MAX_IMAGES = 9;

    private TextInputEditText etContent, etLocation, etAnnotation;
    private TextInputLayout tilLocation;
    private ChipGroup typeGroup;
    private MaterialButton btnSubmit;
    private ImagePickAdapter imageAdapter;

    private AMapLocationClient locationClient;
    private AMapLocationClientOption locationOption;

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        etContent = view.findViewById(R.id.et_square_content);
        etLocation = view.findViewById(R.id.et_square_location);
        etAnnotation = view.findViewById(R.id.et_square_annotation);
        tilLocation = view.findViewById(R.id.til_square_location);
        typeGroup = view.findViewById(R.id.square_type_group);
        btnSubmit = view.findViewById(R.id.btn_square_submit);

        tilLocation.setEndIconOnClickListener(v -> checkLocationPermission());

        RecyclerView rvImages = view.findViewById(R.id.rv_square_images);
        rvImages.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        imageAdapter = new ImagePickAdapter();
        rvImages.setAdapter(imageAdapter);

        view.findViewById(R.id.btn_add_square_image).setOnClickListener(v -> pickImages());
        btnSubmit.setOnClickListener(v -> submit());
        return view;
    }

    private void checkLocationPermission() {
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
                                Toast.makeText(requireContext(), "定位成功", Toast.LENGTH_SHORT).show();
                            } else {
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (locationClient != null) {
            locationClient.onDestroy();
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

    private void submit() {
        String text = etContent.getText() != null ? etContent.getText().toString().trim() : "";
        if (text.isEmpty()) {
            Toast.makeText(requireContext(), "请输入广播内容", Toast.LENGTH_SHORT).show();
            return;
        }
        int checkedId = typeGroup.getCheckedChipId();
        String type = "inquiry";
        if (checkedId == R.id.chip_rescue)
            type = "rescue";

        String loc = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        String anno = etAnnotation.getText() != null ? etAnnotation.getText().toString().trim() : "";

        String finalLocation = null;
        if (!loc.isEmpty() && !anno.isEmpty()) {
            finalLocation = loc + " (" + anno + ")";
        } else if (!loc.isEmpty()) {
            finalLocation = loc;
        } else if (!anno.isEmpty()) {
            finalLocation = anno;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("发布中...");

        List<Uri> newUris = imageAdapter.getNewUris();
        if (newUris.isEmpty()) {
            doCreatePost(text, new ArrayList<>(), finalLocation, type);
        } else {
            uploadImages(newUris, 0, new ArrayList<>(), text, finalLocation, type);
        }
    }

    private void uploadImages(List<Uri> uris, int index, List<String> urls,
            String text, String location, String type) {
        if (index >= uris.size()) {
            doCreatePost(text, urls, location, type);
            return;
        }
        Uri uri = uris.get(index);
        String mimeType = requireContext().getContentResolver().getType(uri);
        if (mimeType == null)
            mimeType = "image/jpeg";
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (ext == null)
            ext = "jpg";
        String filename = "square_img_" + System.currentTimeMillis() + "_" + index + "." + ext;
        String finalMimeType = mimeType;

        RetrofitHelper.get().create(com.ourcat.app.repositories.OssApi.class)
                .getUploadUrl(filename, mimeType)
                .enqueue(new Callback<Map<String, String>>() {
                    @Override
                    public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String uploadUrl = response.body().get("uploadUrl");
                            String publicUrl = response.body().get("publicUrl");
                            String ct = response.body().get("contentType");
                            if (ct == null)
                                ct = finalMimeType;
                            doUploadFile(uri, uploadUrl, publicUrl, ct, uris, index, urls, text, location, type);
                        } else {
                            onFail("获取上传URL失败");
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, String>> call, Throwable t) {
                        onFail("网络错误");
                    }
                });
    }

    private void doUploadFile(Uri uri, String uploadUrl, String publicUrl, String contentType,
            List<Uri> uris, int index, List<String> urls,
            String text, String location, String type) {
        new Thread(() -> {
            try {
                File tempFile = uriToTempFile(uri);
                OssUploader uploader = new OssUploader();
                uploader.uploadWithPresign(uploadUrl, publicUrl, tempFile, contentType);
                urls.add(publicUrl);
                tempFile.delete();
                requireActivity().runOnUiThread(() -> uploadImages(uris, index + 1, urls, text, location, type));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> onFail("上传失败: " + e.getMessage()));
            }
        }).start();
    }

    private File uriToTempFile(Uri uri) throws Exception {
        InputStream is = requireContext().getContentResolver().openInputStream(uri);
        File temp = File.createTempFile("upload_", ".tmp", requireContext().getCacheDir());
        FileOutputStream fos = new FileOutputStream(temp);
        byte[] buf = new byte[8192];
        int len;
        while ((len = is.read(buf)) > 0)
            fos.write(buf, 0, len);
        fos.close();
        is.close();
        return temp;
    }

    private void onFail(String msg) {
        btnSubmit.setEnabled(true);
        btnSubmit.setText("发布");
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void doCreatePost(String text, List<String> imageUrls, String location, String type) {
        Map<String, Object> body = new HashMap<>();
        body.put("text", text);
        body.put("images", imageUrls);
        body.put("type", type);
        if (location != null)
            body.put("location", location);

        RetrofitHelper.get().create(com.ourcat.app.repositories.SquareApi.class)
                .createPost(body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("发布");
                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(), "发布成功", Toast.LENGTH_SHORT).show();
                            Navigation.findNavController(requireView()).navigateUp();
                        } else {
                            Toast.makeText(requireContext(), "发布失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("发布");
                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
