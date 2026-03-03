package com.ourcat.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.ourcat.app.repositories.AuthApi;
import com.ourcat.app.repositories.OssApi;
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

public class EditProfileDialogFragment extends DialogFragment {

    private ImageView ivAvatar;
    private TextInputEditText etNickname, etBio;
    private Button btnSave, btnCancel;
    private Uri selectedImageUri;
    private String currentAvatarUrl;
    private String currentNickname;
    private String currentBio;

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    public interface OnProfileUpdateListener {
        void onProfileUpdated();
    }

    private OnProfileUpdateListener listener;

    public void setListener(OnProfileUpdateListener listener) {
        this.listener = listener;
    }

    public static EditProfileDialogFragment newInstance(String nickname, String bio, String avatarUrl) {
        EditProfileDialogFragment fragment = new EditProfileDialogFragment();
        Bundle args = new Bundle();
        args.putString("nickname", nickname);
        args.putString("bio", bio);
        args.putString("avatarUrl", avatarUrl);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            currentNickname = getArguments().getString("nickname");
            currentBio = getArguments().getString("bio");
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
            getDialog().getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_edit_profile, container, false);
        ivAvatar = view.findViewById(R.id.iv_avatar);
        etNickname = view.findViewById(R.id.et_nickname);
        etBio = view.findViewById(R.id.et_bio);
        btnSave = view.findViewById(R.id.btn_save);
        btnCancel = view.findViewById(R.id.btn_cancel);

        etNickname.setText(currentNickname);
        etBio.setText(currentBio);
        if (currentAvatarUrl != null && !currentAvatarUrl.isEmpty()) {
            Glide.with(this).load(currentAvatarUrl).placeholder(R.drawable.ic_default_avatar).circleCrop()
                    .into(ivAvatar);
        } else {
            ivAvatar.setImageResource(R.drawable.ic_default_avatar);
        }

        view.findViewById(R.id.iv_avatar).setOnClickListener(v -> openGallery());
        btnSave.setOnClickListener(v -> saveProfile());
        btnCancel.setOnClickListener(v -> dismiss());

        return view;
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void saveProfile() {
        String nickname = etNickname.getText().toString().trim();
        String bio = etBio.getText().toString().trim();

        if (nickname.isEmpty()) {
            Toast.makeText(requireContext(), "昵称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSave.setEnabled(false);
        if (selectedImageUri != null) {
            uploadAvatarAndSave(nickname, bio);
        } else {
            doUpdateProfile(nickname, bio, currentAvatarUrl);
        }
    }

    private void uploadAvatarAndSave(String nickname, String bio) {
        String filename = "avatar/" + UUID.randomUUID() + ".jpg";
        RetrofitHelper.get().create(OssApi.class).getUploadUrl(filename, "image/jpeg")
                .enqueue(new Callback<Map<String, String>>() {
                    @Override
                    public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            String uploadUrl = response.body().get("uploadUrl");
                            String publicUrl = response.body().get("publicUrl");
                            String contentType = response.body().get("contentType");
                            new Thread(() -> {
                                try {
                                    File file = ImageUtils.uriToTempFile(requireContext(), selectedImageUri);
                                    if (file == null) {
                                        requireActivity().runOnUiThread(() -> {
                                            Toast.makeText(requireContext(), "图片处理失败", Toast.LENGTH_SHORT).show();
                                            btnSave.setEnabled(true);
                                        });
                                        return;
                                    }
                                    OssUploader uploader = new OssUploader();
                                    uploader.uploadWithPresign(uploadUrl, publicUrl, file, contentType);
                                    file.delete();
                                    requireActivity().runOnUiThread(() -> doUpdateProfile(nickname, bio, publicUrl));
                                } catch (Exception e) {
                                    requireActivity().runOnUiThread(() -> {
                                        Toast.makeText(requireContext(), "头像上传失败: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                        btnSave.setEnabled(true);
                                    });
                                }
                            }).start();
                        } else {
                            Toast.makeText(requireContext(), "获取上传链接失败", Toast.LENGTH_SHORT).show();
                            btnSave.setEnabled(true);
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, String>> call, Throwable t) {
                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        btnSave.setEnabled(true);
                    }
                });
    }

    private void doUpdateProfile(String nickname, String bio, String avatarUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("nickname", nickname);
        body.put("bio", bio);
        if (avatarUrl != null) {
            body.put("avatarUrl", avatarUrl);
        }

        RetrofitHelper.get().create(AuthApi.class).updateProfile(body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        btnSave.setEnabled(true);
                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(), "修改成功", Toast.LENGTH_SHORT).show();
                            if (listener != null)
                                listener.onProfileUpdated();
                            dismiss();
                        } else {
                            Toast.makeText(requireContext(), "修改失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        btnSave.setEnabled(true);
                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
