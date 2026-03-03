package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;
import com.ourcat.app.adapters.UserManageAdapter;
import com.ourcat.app.repositories.UserApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UserManagementFragment extends Fragment {

    private UserManageAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_user_management, container, false);
        View btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        }

        RecyclerView rv = view.findViewById(R.id.rv_users);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new UserManageAdapter();
        adapter.setListener(this::toggleRole);
        rv.setAdapter(adapter);

        loadUsers();
        return view;
    }

    private void loadUsers() {
        RetrofitHelper.get().create(UserApi.class).listUsers()
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            adapter.setUsers(response.body());
                        } else {
                            Toast.makeText(requireContext(), "无权限或加载失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void toggleRole(long userId, int currentRole) {
        int newRole = currentRole == 2 ? 1 : 2;
        Map<String, Object> body = new HashMap<>();
        body.put("role", newRole);

        RetrofitHelper.get().create(UserApi.class).setRole(userId, body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                                           Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(),
                                    newRole == 2 ? "已授予志愿者" : "已撤销志愿者",
                                    Toast.LENGTH_SHORT).show();
                            loadUsers();
                        } else {
                            Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
