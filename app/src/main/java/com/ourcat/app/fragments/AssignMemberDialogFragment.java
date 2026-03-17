package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ourcat.app.R;
import com.ourcat.app.repositories.OrgApi;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AssignMemberDialogFragment extends DialogFragment {

    private static final String ARG_ACTIVITY_ID = "activityId";
    private long activityId;
    private Runnable onAssigned;

    private String mapOrgRoleText(String role) {
        if (role == null) {
            return "";
        }
        switch (role) {
            case "admin":
                return "管理员";
            case "manager":
                return "负责人";
            case "member":
                return "成员";
            default:
                return role;
        }
    }

    public void setListener(Runnable onAssigned) {
        this.onAssigned = onAssigned;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) activityId = getArguments().getLong(ARG_ACTIVITY_ID, 0L);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_assign_member, container, false);
        RecyclerView rv = root.findViewById(R.id.members_recycler);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        List<Map<String, Object>> members = new ArrayList<>();
        rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_search, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> m = members.get(position);
                String nickname = m.get("nickname") != null ? String.valueOf(m.get("nickname")) : "用户";
                String avatarUrl = m.get("avatarUrl") != null ? String.valueOf(m.get("avatarUrl")) : "";
                String role = m.get("role") != null ? String.valueOf(m.get("role")) : "";
                int participatedRescues = m.get("participatedRescues") instanceof Number ? ((Number) m.get("participatedRescues")).intValue() : 0;
                int completedTasks = m.get("completedTasks") instanceof Number ? ((Number) m.get("completedTasks")).intValue() : 0;

                android.widget.ImageView ivAvatar = holder.itemView.findViewById(R.id.iv_avatar);
                android.widget.TextView tvNickname = holder.itemView.findViewById(R.id.tv_nickname);
                android.widget.TextView tvUsername = holder.itemView.findViewById(R.id.tv_username);
                android.widget.TextView tvRoleBadge = holder.itemView.findViewById(R.id.tv_role_badge);

                tvNickname.setText(nickname);
                tvUsername.setText("参与救助: " + participatedRescues + " | 完成任务: " + completedTasks);
                String roleText = mapOrgRoleText(role);
                tvRoleBadge.setText(roleText.isEmpty() ? "成员" : roleText);

                if (!avatarUrl.isEmpty()) {
                    Glide.with(AssignMemberDialogFragment.this)
                            .load(avatarUrl)
                            .circleCrop()
                            .placeholder(R.drawable.ic_default_avatar)
                            .into(ivAvatar);
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_default_avatar);
                }

                Object userId = m.get("userId");
                holder.itemView.setOnClickListener(v -> {
                    if (userId == null) return;
                    long uid = ((Number) userId).longValue();
                    Map<String, Long> body = new HashMap<>();
                    body.put("assigneeUserId", uid);
                    RetrofitHelper.get().create(RescueApi.class).assignTask(activityId, body).enqueue(new Callback<Map<String, Object>>() {
                        @Override
                        public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                            if (getActivity() == null) return;
                            if (response.isSuccessful()) {
                                Toast.makeText(getContext(), "指派成功", Toast.LENGTH_SHORT).show();
                                if (onAssigned != null) onAssigned.run();
                                dismiss();
                            } else Toast.makeText(getContext(), "指派失败", Toast.LENGTH_SHORT).show();
                        }
                        @Override
                        public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                            if (getContext() != null) Toast.makeText(getContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }
            @Override
            public int getItemCount() { return members.size(); }
        });

        RetrofitHelper.get().create(OrgApi.class).getMembers(1L).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    members.clear();
                    members.addAll(response.body());
                    rv.getAdapter().notifyDataSetChanged();
                }
            }
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {}
        });
        return root;
    }
}
