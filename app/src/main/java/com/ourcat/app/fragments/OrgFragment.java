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
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.Chip;
import com.google.android.material.tabs.TabLayout;
import com.ourcat.app.R;
import com.ourcat.app.repositories.AuthApi;
import com.ourcat.app.repositories.OrgApi;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.StatusTextUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrgFragment extends Fragment {

    private TextView orgName, orgDesc, orgStatus;
    private TextView statMemberCount, statCompletedRescues, statOngoingRescues;
    private Button btnJoin, btnLeave;
    private RecyclerView membersList, recentActivitiesList;
    private TabLayout tabLayout;
    private View btnRescueList, btnRescueStatistics, btnRescueGuide;
    private final List<Map<String, Object>> members = new ArrayList<>();
    private final List<Map<String, Object>> recentActivities = new ArrayList<>();
    private MembersAdapter membersAdapter;
    private RecentActivitiesAdapter recentActivitiesAdapter;
    private long currentUserId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_org, container, false);
        orgName = view.findViewById(R.id.org_name);
        orgDesc = view.findViewById(R.id.org_desc);
        orgStatus = view.findViewById(R.id.org_status);
        statMemberCount = view.findViewById(R.id.stat_member_count);
        statCompletedRescues = view.findViewById(R.id.stat_completed_rescues);
        statOngoingRescues = view.findViewById(R.id.stat_ongoing_rescues);
        btnJoin = view.findViewById(R.id.btn_join);
        btnLeave = view.findViewById(R.id.btn_leave);
        membersList = view.findViewById(R.id.members_list);
        recentActivitiesList = view.findViewById(R.id.recent_activities_list);
        tabLayout = view.findViewById(R.id.org_tabs);
        btnRescueList = view.findViewById(R.id.btn_org_rescue_list);
        btnRescueStatistics = view.findViewById(R.id.btn_org_rescue_statistics);
        btnRescueGuide = view.findViewById(R.id.btn_org_rescue_guide);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    recentActivitiesList.setVisibility(View.VISIBLE);
                    membersList.setVisibility(View.GONE);
                } else {
                    recentActivitiesList.setVisibility(View.GONE);
                    membersList.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        membersList.setLayoutManager(new LinearLayoutManager(requireContext()));
        membersAdapter = new MembersAdapter();
        membersList.setAdapter(membersAdapter);
        recentActivitiesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        recentActivitiesAdapter = new RecentActivitiesAdapter();
        recentActivitiesList.setAdapter(recentActivitiesAdapter);

        setupOption(btnRescueList, "救助动态",
                v -> NavHostFragment.findNavController(OrgFragment.this).navigate(R.id.rescue_list));
        setupOption(btnRescueStatistics, "救助数据统计",
                v -> NavHostFragment.findNavController(OrgFragment.this).navigate(R.id.rescue_statistics));
        setupOption(btnRescueGuide, "救助指南",
                v -> NavHostFragment.findNavController(OrgFragment.this).navigate(R.id.rescue_guide));

        btnJoin.setOnClickListener(v -> doJoin());
        btnLeave.setOnClickListener(v -> doLeave());
        loadCurrentUserId();
        return view;
    }

    private void setupOption(View optionView, String text, View.OnClickListener listener) {
        if (optionView == null) {
            return;
        }
        TextView tv = optionView.findViewById(R.id.option_text);
        if (tv != null) {
            tv.setText(text);
        }
        optionView.setOnClickListener(listener);
    }

    private void loadCurrentUserId() {
        RetrofitHelper.get().create(AuthApi.class).getProfile().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded()) {
                    return;
                }
                if (response.isSuccessful() && response.body() != null) {
                    currentUserId = longVal(response.body().get("id"));
                }
                loadOrg();
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (!isAdded()) {
                    return;
                }
                loadOrg();
            }
        });
    }

    private void loadOrg() {
        RetrofitHelper.get().create(OrgApi.class).getOrg().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded())
                    return;
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
                    if (id != null) {
                        long orgId = ((Number) id).longValue();
                        loadMembers(orgId);
                        loadOrgHome(orgId);
                    }
                } else
                    orgName.setText("加载失败");
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (isAdded())
                    orgName.setText("网络错误");
            }
        });
    }

    private void loadMembers(long orgId) {
        RetrofitHelper.get().create(OrgApi.class).getMembers(orgId).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!isAdded())
                    return;
                if (response.isSuccessful() && response.body() != null) {
                    members.clear();
                    members.addAll(response.body());
                    membersAdapter.notifyDataSetChanged();

                    if (currentUserId > 0 && btnJoin.getVisibility() == View.VISIBLE) {
                        boolean isMemberByList = false;
                        for (Map<String, Object> m : members) {
                            long uid = longVal(m.get("userId"));
                            if (uid <= 0) {
                                uid = longVal(m.get("id"));
                            }
                            if (uid == currentUserId) {
                                isMemberByList = true;
                                break;
                            }
                        }
                        if (isMemberByList) {
                            btnJoin.setVisibility(View.GONE);
                            btnLeave.setVisibility(View.VISIBLE);
                            orgStatus.setVisibility(View.GONE);
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
            }
        });
    }

    @SuppressWarnings("unchecked")
    private void loadOrgHome(long orgId) {
        RetrofitHelper.get().create(OrgApi.class).getOrgHome(orgId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded()) {
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }
                Map<String, Object> home = response.body();
                Map<String, Object> stats = (Map<String, Object>) home.get("stats");
                if (stats != null) {
                    statMemberCount.setText(String.valueOf(intVal(stats.get("memberCount"))));
                    statCompletedRescues.setText(String.valueOf(intVal(stats.get("completedRescues"))));
                    statOngoingRescues.setText(String.valueOf(intVal(stats.get("ongoingRescues"))));
                }
                List<Map<String, Object>> activityList = (List<Map<String, Object>>) home.get("recentActivities");
                recentActivities.clear();
                if (activityList != null) {
                    recentActivities.addAll(activityList);
                }
                recentActivitiesAdapter.notifyDataSetChanged();
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
            }
        });
    }

    private void doJoin() {
        RetrofitHelper.get().create(OrgApi.class).join().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded())
                    return;
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
                    } catch (Exception ignored) {
                    }
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (isAdded())
                    Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
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
                        public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static String str(Object o) {
        return o != null ? o.toString() : "";
    }

    private static int intVal(Object o) {
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    private static long longVal(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    private static String mapRescueStatusText(String status) {
        if (System.currentTimeMillis() >= 0) {
            return StatusTextUtils.formatRescueStatus(status);
        }
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case "created":
                return "待处理";
            case "in_progress":
                return "进行中";
            case "completed":
                return "已完成";
            default:
                return status;
        }
    }

    private static String mapOrgRoleText(String role) {
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

    private class MembersAdapter extends RecyclerView.Adapter<MembersAdapter.MemberVH> {

        @NonNull
        @Override
        public MemberVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_org_member, parent, false);
            return new MemberVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull MemberVH holder, int position) {
            Map<String, Object> item = members.get(position);
            String nickname = str(item.get("nickname"));
            String role = str(item.get("role"));
            String avatarUrl = str(item.get("avatarUrl"));
            int participatedRescues = intVal(item.get("participatedRescues"));
            int completedTasks = intVal(item.get("completedTasks"));
            String roleText = mapOrgRoleText(role);
            holder.name.setText(nickname + (roleText.isEmpty() ? "" : " (" + roleText + ")"));
            holder.stats.setText("参与救助: " + participatedRescues + " | 完成任务: " + completedTasks);
            if (!avatarUrl.isEmpty()) {
                Glide.with(OrgFragment.this)
                        .load(avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_default_avatar)
                        .into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.ic_default_avatar);
            }

            long userId = 0L;
            Object userIdObj = item.get("userId");
            if (userIdObj instanceof Number) {
                userId = ((Number) userIdObj).longValue();
            } else if (item.get("id") instanceof Number) {
                userId = ((Number) item.get("id")).longValue();
            }
            long finalUserId = userId;
            holder.itemView.setOnClickListener(v -> {
                if (finalUserId <= 0) {
                    return;
                }
                Bundle args = new Bundle();
                args.putLong("userId", finalUserId);
                NavHostFragment.findNavController(OrgFragment.this).navigate(R.id.user_profile, args);
            });
        }

        @Override
        public int getItemCount() {
            return members.size();
        }

        class MemberVH extends RecyclerView.ViewHolder {
            android.widget.ImageView avatar;
            TextView name;
            TextView stats;

            MemberVH(@NonNull View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.member_avatar);
                name = itemView.findViewById(R.id.member_name);
                stats = itemView.findViewById(R.id.member_stats);
            }
        }
    }

    private class RecentActivitiesAdapter extends RecyclerView.Adapter<RecentActivitiesAdapter.ActivityVH> {

        @NonNull
        @Override
        public ActivityVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_org_activity, parent, false);
            return new ActivityVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ActivityVH holder, int position) {
            Map<String, Object> item = recentActivities.get(position);
            String title = str(item.get("title"));
            String status = str(item.get("status"));
            holder.title.setText(title.isEmpty() ? "未命名活动" : title);

            holder.chipStatus.setText(mapRescueStatusText(status));

            if ("completed".equals(status)) {
                holder.chipStatus.setChipBackgroundColorResource(R.color.green_100);
                holder.chipStatus.setTextColor(getResources().getColor(R.color.green_800, null));
            } else {
                holder.chipStatus.setChipBackgroundColorResource(R.color.background_subtle);
                holder.chipStatus.setTextColor(getResources().getColor(R.color.text_secondary, null));
            }
            holder.itemView.setOnClickListener(v -> {
                long squarePostId = longVal(item.get("squarePostId"));
                if (squarePostId > 0) {
                    Bundle args = new Bundle();
                    args.putLong("squarePostId", squarePostId);
                    NavHostFragment.findNavController(OrgFragment.this).navigate(R.id.square_detail, args);
                    return;
                }
                long activityId = longVal(item.get("activityId"));
                if (activityId > 0) {
                    Bundle args = new Bundle();
                    args.putLong("activityId", activityId);
                    NavHostFragment.findNavController(OrgFragment.this).navigate(R.id.rescue_detail, args);
                }
            });
        }

        @Override
        public int getItemCount() {
            return recentActivities.size();
        }

        class ActivityVH extends RecyclerView.ViewHolder {
            TextView title;
            Chip chipStatus;

            ActivityVH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.activity_title);
                chipStatus = itemView.findViewById(R.id.chip_status);
            }
        }
    }
}
