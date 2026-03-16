package com.ourcat.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.ourcat.app.R;
import com.ourcat.app.activities.LoginActivity;
import com.ourcat.app.repositories.AuthApi;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.TokenStore;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private TextView tvNickname, tvRole;
    private ImageView avatar;
    private View btnUserManage, btnJoinRequests;
    private String currentAvatarUrl;
    private String currentNicknameStr;
    private String currentBio;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        avatar = view.findViewById(R.id.avatar);
        tvNickname = view.findViewById(R.id.tv_nickname);
        tvRole = view.findViewById(R.id.tv_role);
        btnUserManage = view.findViewById(R.id.btn_user_manage);
        btnJoinRequests = view.findViewById(R.id.btn_join_requests);

        setupOption(view, R.id.btn_my_posts, "我的帖子",
                v -> NavHostFragment.findNavController(ProfileFragment.this).navigate(R.id.my_posts));
        setupOption(view, R.id.btn_my_comments, "我的评论",
                v -> NavHostFragment.findNavController(ProfileFragment.this).navigate(R.id.my_comments));
        setupOption(view, R.id.btn_my_cat_reports, "我的上报",
                v -> NavHostFragment.findNavController(ProfileFragment.this).navigate(R.id.my_cat_reports));
        // 救助动态已移至广场页面
        setupOption(view, R.id.btn_my_tasks, "我的任务",
                v -> NavHostFragment.findNavController(ProfileFragment.this).navigate(R.id.my_tasks));
        setupOption(view, R.id.btn_rescue_guide, "救助指南",
                v -> NavHostFragment.findNavController(ProfileFragment.this).navigate(R.id.rescue_guide));
        setupOption(view, R.id.btn_join_requests, "加入申请审核",
                v -> NavHostFragment.findNavController(ProfileFragment.this).navigate(R.id.join_requests));

        // 点击头像编辑资料
        avatar.setOnClickListener(v -> {
            EditProfileDialogFragment dialog = EditProfileDialogFragment.newInstance(currentNicknameStr, currentBio,
                    currentAvatarUrl);
            dialog.setListener(this::loadProfile);
            dialog.show(getParentFragmentManager(), "EditProfile");
        });

        setupOption(view, R.id.btn_change_password, "修改密码", v -> showChangePasswordDialog());
        setupOption(view, R.id.btn_user_manage, "用户管理",
                v -> NavHostFragment.findNavController(ProfileFragment.this).navigate(R.id.user_management));

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            new TokenStore(requireContext()).clear();
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finish();
        });
        loadProfile();
        return view;
    }

    private void setupOption(View root, int id, String text, View.OnClickListener listener) {
        View optionView = root.findViewById(id);
        TextView tv = optionView.findViewById(R.id.option_text);
        tv.setText(text);
        optionView.setOnClickListener(listener);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfile();
    }

    private void loadProfile() {
        RetrofitHelper.get().create(AuthApi.class).getProfile()
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Map<String, Object> user = response.body();
                            Object nickname = user.get("nickname");
                            Object role = user.get("role");
                            tvNickname.setText(nickname != null ? nickname.toString() : "");
                            int r = role != null ? ((Number) role).intValue() : 1;
                            tvRole.setText(r == 1 ? "普通用户" : r == 2 ? "志愿者" : "管理员");
                            btnUserManage.setVisibility(r >= 3 ? View.VISIBLE : View.GONE);
                            if (btnJoinRequests != null) btnJoinRequests.setVisibility(r >= 3 ? View.VISIBLE : View.GONE);

                            currentNicknameStr = nickname != null ? nickname.toString() : "";
                            currentAvatarUrl = (String) user.get("avatarUrl");
                            currentBio = (String) user.get("bio");

                            if (currentAvatarUrl != null && !currentAvatarUrl.isEmpty()) {
                                Glide.with(ProfileFragment.this)
                                        .load(currentAvatarUrl)
                                        .placeholder(R.drawable.ic_default_avatar)
                                        .error(R.drawable.ic_default_avatar)
                                        .circleCrop()
                                        .into(avatar);
                            } else {
                                avatar.setImageResource(R.drawable.ic_default_avatar);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        tvNickname.setText("--");
                        tvRole.setText("");
                    }
                });
    }

    private void showChangePasswordDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int px = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(px, px, px, 0);

        EditText etOld = new EditText(requireContext());
        etOld.setHint("原密码");
        etOld.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etOld);

        EditText etNew = new EditText(requireContext());
        etNew.setHint("新密码（至少6位）");
        etNew.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etNew);

        EditText etConfirm = new EditText(requireContext());
        etConfirm.setHint("确认新密码");
        etConfirm.setInputType(
                android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etConfirm);

        new AlertDialog.Builder(requireContext())
                .setTitle("修改密码")
                .setView(layout)
                .setPositiveButton("确定", (dialog, which) -> {
                    String oldPwd = etOld.getText().toString().trim();
                    String newPwd = etNew.getText().toString().trim();
                    String confirm = etConfirm.getText().toString().trim();
                    if (oldPwd.isEmpty() || newPwd.isEmpty()) {
                        Toast.makeText(requireContext(), "请填写完整", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newPwd.length() < 6) {
                        Toast.makeText(requireContext(), "新密码至少6位", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!newPwd.equals(confirm)) {
                        Toast.makeText(requireContext(), "两次输入的新密码不一致", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    doChangePassword(oldPwd, newPwd);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doChangePassword(String oldPwd, String newPwd) {
        Map<String, String> body = new HashMap<>();
        body.put("oldPassword", oldPwd);
        body.put("newPassword", newPwd);
        RetrofitHelper.get().create(AuthApi.class).changePassword(body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(), "密码修改成功", Toast.LENGTH_SHORT).show();
                        } else {
                            String msg = "修改失败";
                            try {
                                if (response.errorBody() != null) {
                                    String err = response.errorBody().string();
                                    if (err.contains("原密码错误"))
                                        msg = "原密码错误";
                                }
                            } catch (Exception ignored) {
                            }
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (!isAdded())
                            return;
                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
