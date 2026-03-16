package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;
import com.ourcat.app.repositories.OrgApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrgFragment extends Fragment {

    private TextView orgName, orgDesc, orgStatus;
    private Button btnJoin, btnLeave;
    private RecyclerView membersList;
    private final List<Map<String, Object>> members = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_org, container, false);
        orgName = view.findViewById(R.id.org_name);
        orgDesc = view.findViewById(R.id.org_desc);
        orgStatus = view.findViewById(R.id.org_status);
        btnJoin = view.findViewById(R.id.btn_join);
        btnLeave = view.findViewById(R.id.btn_leave);
        membersList = view.findViewById(R.id.members_list);
        membersList.setLayoutManager(new LinearLayoutManager(requireContext()));
        membersList.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> m = members.get(position);
                Object nick = m.get("nickname");
                Object role = m.get("role");
                ((TextView) holder.itemView.findViewById(android.R.id.text1)).setText((nick != null ? nick.toString() : "") + (role != null ? " (" + role + ")" : ""));
            }
            @Override
            public int getItemCount() { return members.size(); }
        });

        btnJoin.setOnClickListener(v -> doJoin());
        btnLeave.setOnClickListener(v -> doLeave());
        loadOrg();
        return view;
    }

    private void loadOrg() {
        RetrofitHelper.get().create(OrgApi.class).getOrg().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> org = response.body();
                    orgName.setText(str(org.get("name")));
                    orgDesc.setText(str(org.get("description")));
                    boolean isMember = Boolean.TRUE.equals(org.get("isMember"));
                    boolean hasPending = Boolean.TRUE.equals(org.get("hasPendingRequest"));
                    if (isMember) {
                        btnJoin.setVisibility(View.GONE);
                        btnLeave.setVisibility(View.VISIBLE);
                        orgStatus.setVisibility(View.GONE);
                    } else if (hasPending) {
                        btnJoin.setVisibility(View.GONE);
                        btnLeave.setVisibility(View.GONE);
                        orgStatus.setVisibility(View.VISIBLE);
                        orgStatus.setText("您的加入申请待审核");
                    } else {
                        btnJoin.setVisibility(View.VISIBLE);
                        btnLeave.setVisibility(View.GONE);
                        orgStatus.setVisibility(View.GONE);
                    }
                    Object id = org.get("id");
                    if (id != null) loadMembers(((Number) id).longValue());
                } else orgName.setText("加载失败");
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (isAdded()) orgName.setText("网络错误");
            }
        });
    }

    private void loadMembers(long orgId) {
        RetrofitHelper.get().create(OrgApi.class).getMembers(orgId).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    members.clear();
                    members.addAll(response.body());
                    membersList.getAdapter().notifyDataSetChanged();
                }
            }
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {}
        });
    }

    private void doJoin() {
        RetrofitHelper.get().create(OrgApi.class).join().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    Object msg = response.body().get("message");
                    Toast.makeText(requireContext(),
                            msg != null ? msg.toString() : "申请已提交，请等待管理员审核",
                            Toast.LENGTH_SHORT).show();
                    loadOrg();
                } else {
                    String message = "申请失败";
                    try {
                        if (response.errorBody() != null) {
                            String raw = response.errorBody().string();
                            if (raw.contains("未登录")) {
                                message = "请先登录后再申请加入组织";
                            } else if (raw.contains("您已是组织成员") || raw.contains("您已提交过申请")) {
                                message = raw;
                            }
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (isAdded()) Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void doLeave() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("退出组织").setMessage("退出后您将变为普通用户，确定退出？")
                .setPositiveButton("确定", (d, w) -> {
                    RetrofitHelper.get().create(OrgApi.class).leave().enqueue(new Callback<Map<String, Object>>() {
                        @Override
                        public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), "已退出组织", Toast.LENGTH_SHORT).show();
                                loadOrg();
                            }
                        }
                        @Override
                        public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static String str(Object o) { return o != null ? o.toString() : ""; }
}
