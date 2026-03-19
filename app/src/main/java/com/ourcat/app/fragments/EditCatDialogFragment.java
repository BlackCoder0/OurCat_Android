package com.ourcat.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.ourcat.app.R;
import com.ourcat.app.repositories.CatApi;
import com.ourcat.app.utils.ImageUtils;
import com.ourcat.app.utils.OssUploader;
import com.ourcat.app.utils.RetrofitHelper;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditCatDialogFragment extends DialogFragment {

    private ImageView ivAvatar;
    private TextInputEditText etName, etFeature, etPersonality;
    private AutoCompleteTextView spinnerColor, spinnerStatus;
    private Button btnSave, btnCancel;
    private Uri selectedImageUri;

    private long catId;
    private String currentAvatarUrl;
    private String currentName;
    private String currentColor;
    private String currentFeature;
    private String currentPersonality;
    private String currentStatus;

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    public interface OnCatUpdateListener {
        void onCatUpdated();
    }

    private OnCatUpdateListener listener;

    public void setListener(OnCatUpdateListener listener) {
        this.listener = listener;
    }

    public static EditCatDialogFragment newInstance(long catId, String name, String color,
            String feature, String personality, String status, String avatarUrl) {
        EditCatDialogFragment fragment = new EditCatDialogFragment();
        Bundle args = new Bundle();
        args.putLong("catId", catId);
        args.putString("name", name);
        args.putString("color", color);
        args.putString("feature", feature);
        args.putString("personality", personality);
        args.putString("status", status);
        args.putString("avatarUrl", avatarUrl);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            catId = getArguments().getLong("catId");
            currentName = getArguments().getString("name");
            currentColor = getArguments().getString("color");
            currentFeature = getArguments().getString("feature");
            currentPersonality = getArguments().getString("personality");
            currentStatus = getArguments().getString("status");
            currentAvatarUrl = getArguments().getString("avatarUrl");
        }

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        selectedImageUri = result.getData().getData();
                        if (selectedImageUri != null) {
                            Glide.with(this).load(selectedImageUri).circleCrop().into(ivAvatar);
                        }
                    }
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.85f);
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_edit_cat, container, false);
        ivAvatar = view.findViewById(R.id.iv_avatar);
        etName = view.findViewById(R.id.et_name);
        etFeature = view.findViewById(R.id.et_feature);
        etPersonality = view.findViewById(R.id.et_personality);
        spinnerColor = view.findViewById(R.id.spinner_color);
        spinnerStatus = view.findViewById(R.id.spinner_status);
        btnSave = view.findViewById(R.id.btn_save);
        btnCancel = view.findViewById(R.id.btn_cancel);

        etName.setText(currentName);
        etFeature.setText(currentFeature);
        etPersonality.setText(currentPersonality);
        spinnerColor.setText(currentColor, false);
        spinnerStatus.setText(currentStatus, false);

        if (currentAvatarUrl != null && !currentAvatarUrl.isEmpty()) {
            Glide.with(this).load(currentAvatarUrl).placeholder(R.drawable.ic_default_avatar).circleCrop()
                    .into(ivAvatar);
        } else {
            ivAvatar.setImageResource(R.drawable.ic_default_avatar);
        }

        setupSpinners();

        view.findViewById(R.id.iv_avatar).setOnClickListener(v -> openGallery());
        btnSave.setOnClickListener(v -> saveCat());
        btnCancel.setOnClickListener(v -> dismiss());

        return view;
    }

    private void setupSpinners() {
        String[] colors = new String[] {
                "橘色", "黑色", "白色", "灰色", "狸花", "银虎斑", "奶牛", "三花", "玳瑁", "奶油色", "暹罗色"
        };
        ArrayAdapter<String> colorAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, colors);
        spinnerColor.setAdapter(colorAdapter);

        String[] statuses = new String[] { "活跃", "收养", "失踪", "受伤", "疾病", "困境", "其他", "已故" };
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, statuses);
        spinnerStatus.setAdapter(statusAdapter);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void saveCat() {
        String name = etName.getText().toString().trim();
        String color = spinnerColor.getText().toString().trim();
        String feature = etFeature.getText().toString().trim();
        String personality = etPersonality.getText().toString().trim();
        String status = spinnerStatus.getText().toString().trim();

        if (color.isEmpty()) {
            Toast.makeText(requireContext(), "请选择颜色", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedImageUri != null) {
            uploadImageAndSave(name, color, feature, personality, status);
        } else {
            submitUpdate(name, color, feature, personality, status, currentAvatarUrl);
        }
    }

    private void uploadImageAndSave(String name, String color, String feature, String personality, String status) {
        Toast.makeText(requireContext(), "正在上传图片...", Toast.LENGTH_SHORT).show();
        btnSave.setEnabled(false);

        File file = ImageUtils.uriToFile(requireContext(), selectedImageUri);
        if (file == null) {
            Toast.makeText(requireContext(), "图片处理失败", Toast.LENGTH_SHORT).show();
            btnSave.setEnabled(true);
            return;
        }

        String objectKey = "cats/" + UUID.randomUUID().toString() + ".jpg";
        new OssUploader().upload(requireContext(), file, objectKey, new OssUploader.OnUploadListener() {
            @Override
            public void onSuccess(String url) {
                if (!isAdded())
                    return;
                submitUpdate(name, color, feature, personality, status, url);
            }

            @Override
            public void onFailure(String error) {
                if (!isAdded())
                    return;
                Toast.makeText(requireContext(), "上传失败: " + error, Toast.LENGTH_SHORT).show();
                btnSave.setEnabled(true);
            }
        });
    }

    private void submitUpdate(String name, String color, String feature, String personality, String status,
            String avatarUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("color", color);
        body.put("feature", feature);
        body.put("personality", personality);
        body.put("status", status);
        body.put("primaryImageUrl", avatarUrl);

        RetrofitHelper.get().create(CatApi.class).updateCat(catId, body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        btnSave.setEnabled(true);
                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(), "更新成功", Toast.LENGTH_SHORT).show();
                            if (listener != null)
                                listener.onCatUpdated();
                            dismiss();
                        } else {
                            Toast.makeText(requireContext(), "更新失败: " + response.code(), Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (!isAdded())
                            return;
                        btnSave.setEnabled(true);
                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
