package com.ourcat.app.fragments;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ourcat.app.R;
import com.ourcat.app.adapters.ImagePickAdapter;
import com.ourcat.app.utils.CatTagUtils;
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

public class CreateEditPostFragment extends Fragment {

    private static final int MAX_IMAGES = 9;

    private TextInputEditText etTitle, etContent;
    private MaterialButton btnSubmit;
    private ImagePickAdapter imageAdapter;
    private Map<String, Object> selectedRefCat = null;

    private long editPostId = -1;
    private boolean isEditMode = false;

    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            editPostId = getArguments().getInt("editPostId", -1);
            isEditMode = editPostId > 0;
        }
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        handleImageResult(result.getData());
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_create_edit_post, container, false);
        etTitle = view.findViewById(R.id.et_title);
        etContent = view.findViewById(R.id.et_content);
        btnSubmit = view.findViewById(R.id.btn_submit);

        view.findViewById(R.id.btn_insert_cat).setOnClickListener(v -> openSelectCatDialog());

        imageAdapter = new ImagePickAdapter();
        imageAdapter.setOnRemoveListener(this::removeImage);
        RecyclerView rvImages = view.findViewById(R.id.rv_images);
        rvImages.setLayoutManager(new GridLayoutManager(requireContext(), 3));
        rvImages.setAdapter(imageAdapter);

        view.findViewById(R.id.btn_add_image).setOnClickListener(v -> pickImages());
        btnSubmit.setOnClickListener(v -> submit());

        if (isEditMode) {
            btnSubmit.setText("保存修改");
            loadExistingPost();
        }
        return view;
    }

    private void openSelectCatDialog() {
        SelectCatDialogFragment dialog = new SelectCatDialogFragment();
        dialog.setOnCatSelectedListener(cat -> {
            CatTagUtils.insertCatTag(requireContext(), etContent, cat);
        });
        dialog.show(requireActivity().getSupportFragmentManager(), "SelectCat");
    }

    private void clearRefCat() {
        selectedRefCat = null;
    }

    private void removeImage(int position) {
        imageAdapter.remove(position);
    }

    private void pickImages() {
        int total = imageAdapter.getItemCount();
        if (total >= MAX_IMAGES) {
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
        } else if (data.getData() != null) {
            if (imageAdapter.getItemCount() < MAX_IMAGES) {
                imageAdapter.addUri(data.getData());
            }
        }
    }

    private void loadExistingPost() {
        RetrofitHelper.get().create(com.ourcat.app.repositories.ForumApi.class).getPost(editPostId)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Map<String, Object> post = response.body();
                            etTitle.setText(post.get("title") != null ? post.get("title").toString() : "");
                            etContent.setText(post.get("content") != null ? post.get("content").toString() : "");
                            Object imgs = post.get("images");
                            if (imgs != null) {
                                try {
                                    List<String> urls = new Gson().fromJson(imgs.toString(),
                                            new TypeToken<List<String>>() {
                                            }.getType());
                                    imageAdapter.setExistingUrls(urls);
                                } catch (Exception ignored) {
                                }
                            }
                            Object refCat = post.get("referencedCat");
                            Object refCatId = post.get("referencedCatId");
                            if (refCat instanceof Map && refCatId instanceof Number) {
                                selectedRefCat = (Map<String, Object>) refCat;
                                selectedRefCat.put("id", ((Number) refCatId).longValue());
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(requireContext(), "加载帖子失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void submit() {
        String title = etTitle.getText() != null ? etTitle.getText().toString().trim() : "";
        String content = etContent.getText() != null ? etContent.getText().toString().trim() : "";
        if (title.isEmpty()) {
            Toast.makeText(requireContext(), "请输入标题", Toast.LENGTH_SHORT).show();
            return;
        }
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "请输入内容", Toast.LENGTH_SHORT).show();
            return;
        }
        btnSubmit.setEnabled(false);
        btnSubmit.setText("上传中...");

        List<Uri> newUris = imageAdapter.getNewUris();
        List<String> existingUrls = imageAdapter.getExistingUrls();

        if (newUris.isEmpty()) {
            doSubmit(title, content, existingUrls);
        } else {
            uploadImages(newUris, 0, existingUrls, title, content);
        }
    }

    private void uploadImages(List<Uri> uris, int index, List<String> urls, String title, String content) {
        if (index >= uris.size()) {
            doSubmit(title, content, urls);
            return;
        }
        Uri uri = uris.get(index);
        String mimeType = requireContext().getContentResolver().getType(uri);
        if (mimeType == null)
            mimeType = "image/jpeg";
        String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        if (ext == null)
            ext = "jpg";
        String filename = "post_img_" + System.currentTimeMillis() + "_" + index + "." + ext;
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
                            doUploadFile(uri, uploadUrl, publicUrl, ct, uris, index, urls, title, content);
                        } else {
                            onUploadFail("获取上传URL失败");
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, String>> call, Throwable t) {
                        onUploadFail("网络错误");
                    }
                });
    }

    private void doUploadFile(Uri uri, String uploadUrl, String publicUrl, String contentType,
            List<Uri> uris, int index, List<String> urls, String title, String content) {
        new Thread(() -> {
            try {
                File tempFile = uriToTempFile(uri);
                OssUploader uploader = new OssUploader();
                uploader.uploadWithPresign(uploadUrl, publicUrl, tempFile, contentType);
                urls.add(publicUrl);
                tempFile.delete();
                requireActivity().runOnUiThread(() -> uploadImages(uris, index + 1, urls, title, content));
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> onUploadFail("上传失败: " + e.getMessage()));
            }
        }).start();
    }

    private File uriToTempFile(Uri uri) throws Exception {
        return com.ourcat.app.utils.ImageUtils.uriToTempFile(requireContext(), uri);
    }

    private void onUploadFail(String msg) {
        btnSubmit.setEnabled(true);
        btnSubmit.setText(isEditMode ? "保存修改" : "发布");
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }

    private void doSubmit(String title, String content, List<String> imageUrls) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("content", content);
        body.put("images", imageUrls);
        // selectedRefCat 不再用于单独字段，而是内嵌在 content 中，这里仅保留兼容性或清理旧逻辑
        // 如果后端还需要 referencedCatId 字段，可以保留，但我们现在主要依靠 content 中的 tags
        // 这里我们依然保留逻辑，如果 selectedRefCat 被设置（比如从编辑旧帖子恢复），则传参
        if (selectedRefCat != null && selectedRefCat.get("id") != null) {
            body.put("referencedCatId", ((Number) selectedRefCat.get("id")).longValue());
        }

        Callback<Map<String, Object>> callback = new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                btnSubmit.setEnabled(true);
                btnSubmit.setText(isEditMode ? "保存修改" : "发布");
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), isEditMode ? "修改成功" : "发布成功", Toast.LENGTH_SHORT).show();
                    Navigation.findNavController(requireView()).navigateUp();
                } else {
                    Toast.makeText(requireContext(), "操作失败: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                btnSubmit.setEnabled(true);
                btnSubmit.setText(isEditMode ? "保存修改" : "发布");
                Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
            }
        };

        if (isEditMode) {
            RetrofitHelper.get().create(com.ourcat.app.repositories.ForumApi.class)
                    .updatePost(editPostId, body).enqueue(callback);
        } else {
            RetrofitHelper.get().create(com.ourcat.app.repositories.ForumApi.class)
                    .createPost(body).enqueue(callback);
        }
    }
}
