package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.ourcat.app.R;
import com.ourcat.app.repositories.AuthApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserProfileFragment extends Fragment {

    private long userId;
    private ImageView ivAvatar;
    private TextView tvNickname, tvBio, tvJoinedAt, tvStatPosts, tvStatComments, tvStatReports;
    private Chip chipRole;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user_profile, container, false);

        if (getArguments() != null) {
            userId = getArguments().getLong("userId", 0);
        }

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp());

        ivAvatar = view.findViewById(R.id.user_avatar);
        tvNickname = view.findViewById(R.id.user_nickname);
        tvBio = view.findViewById(R.id.user_bio);
        tvJoinedAt = view.findViewById(R.id.user_joined_at);
        tvStatPosts = view.findViewById(R.id.stat_posts);
        tvStatComments = view.findViewById(R.id.stat_comments);
        tvStatReports = view.findViewById(R.id.stat_reports);
        chipRole = view.findViewById(R.id.user_role_chip);

        loadUserProfile();
        return view;
    }

    private void loadUserProfile() {
        if (userId == 0) return;

        RetrofitHelper.get().create(AuthApi.class).getPublicProfile(userId)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Map<String, Object> data = response.body();
                            bindData(data);
                        } else {
                            Toast.makeText(requireContext(), "加载用户失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void bindData(Map<String, Object> data) {
        String nickname = (String) data.get("nickname");
        String username = (String) data.get("username");
        String avatarUrl = (String) data.get("avatarUrl");
        String bio = (String) data.get("bio");
        Object roleObj = data.get("role");
        Object createdAtObj = data.get("createdAt");

        tvNickname.setText(nickname != null ? nickname : username);
        if (bio != null && !bio.isEmpty()) {
            tvBio.setText(bio);
        } else {
            tvBio.setText("这个人很懒，什么都没写。");
        }

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(avatarUrl)
                    .circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .into(ivAvatar);
        } else {
            ivAvatar.setImageResource(R.drawable.ic_default_avatar);
        }

        int role = 1;
        if (roleObj instanceof Number) {
            role = ((Number) roleObj).intValue();
        }
        String roleText = "普通用户";
        int roleColor = 0xFF757575; // grey
        if (role == 2) {
            roleText = "志愿者";
            roleColor = 0xFF2196F3; // blue
        } else if (role >= 3) {
            roleText = "管理员";
            roleColor = 0xFF4CAF50; // green
        }
        chipRole.setText(roleText);
        chipRole.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(roleColor));

        if (createdAtObj != null) {
            // Assuming ISO 8601 string or timestamp
            try {
                String dateStr = createdAtObj.toString();
                // Simple parsing logic, might need adjustment based on backend format
                // If backend returns Instant.toString(), it's like 2026-02-27T05:25:16Z
                if (dateStr.length() >= 10) {
                    tvJoinedAt.setText("加入于 " + dateStr.substring(0, 10));
                }
            } catch (Exception e) {
                tvJoinedAt.setVisibility(View.GONE);
            }
        } else {
            tvJoinedAt.setVisibility(View.GONE);
        }

        setStat(tvStatPosts, data.get("postCount"));
        setStat(tvStatComments, data.get("commentCount"));
        setStat(tvStatReports, data.get("catReportCount"));
    }

    private void setStat(TextView tv, Object val) {
        if (val instanceof Number) {
            tv.setText(String.valueOf(((Number) val).intValue()));
        } else {
            tv.setText("0");
        }
    }
}
