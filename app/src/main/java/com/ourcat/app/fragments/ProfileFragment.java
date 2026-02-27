package com.ourcat.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.ourcat.app.R;
import com.ourcat.app.activities.LoginActivity;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.TokenStore;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private TextView tvNickname, tvRole;
    private View avatar;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_profile, container, false);
        avatar = view.findViewById(R.id.avatar);
        tvNickname = view.findViewById(R.id.tv_nickname);
        tvRole = view.findViewById(R.id.tv_role);
        view.findViewById(R.id.btn_message_center).setOnClickListener(v ->
                NavHostFragment.findNavController(ProfileFragment.this).navigate(R.id.message_center));
        view.findViewById(R.id.btn_edit_profile).setOnClickListener(v -> {
            Toast.makeText(requireContext(), "编辑资料（可后续扩展）", Toast.LENGTH_SHORT).show();
        });
        view.findViewById(R.id.btn_logout).setOnClickListener(v -> {
            new TokenStore(requireContext()).clear();
            startActivity(new Intent(requireContext(), LoginActivity.class));
            requireActivity().finish();
        });
        loadProfile();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadProfile();
    }

    private void loadProfile() {
        RetrofitHelper.get().create(com.ourcat.app.repositories.AuthApi.class).getProfile()
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
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        tvNickname.setText("--");
                        tvRole.setText("");
                    }
                });
    }
}
