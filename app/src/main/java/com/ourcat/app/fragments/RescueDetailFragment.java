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
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;
import com.ourcat.app.repositories.AuthApi;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RescueDetailFragment extends Fragment {

    private long activityId;
    private long currentUserId;
    private int currentUserRole;
    private TextView detailTitle;
    private TextView detailUrgencyStatus;
    private TextView detailDescription;
    private Button linkSquarePost;
    private RecyclerView rescueTimeline;
    private TimelineAdapter timelineAdapter;
    private List<TimelineItem> timelineItems = new ArrayList<>();
    private RecyclerView tasksList;
    private TasksAdapter tasksAdapter;
    private final List<Map<String, Object>> taskItems = new ArrayList<>();
    private Button btnClaim;
    private Button btnAssign;
    private Button btnMarkComplete;
    private Button linkMap;
    private Map<String, Object> activityData;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            activityId = getArguments().getLong("activityId", 0L);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rescue_detail, container, false);
        detailTitle = view.findViewById(R.id.detail_title);
        detailUrgencyStatus = view.findViewById(R.id.detail_urgency_status);
        detailDescription = view.findViewById(R.id.detail_description);
        linkSquarePost = view.findViewById(R.id.link_square_post);
        rescueTimeline = view.findViewById(R.id.rescue_timeline);

        rescueTimeline.setLayoutManager(new LinearLayoutManager(requireContext()));
        timelineAdapter = new TimelineAdapter();
        rescueTimeline.setAdapter(timelineAdapter);

        btnClaim = view.findViewById(R.id.btn_claim);
        btnAssign = view.findViewById(R.id.btn_assign);
        btnMarkComplete = view.findViewById(R.id.btn_mark_complete);
        tasksList = view.findViewById(R.id.tasks_list);
        linkMap = view.findViewById(R.id.link_map);

        tasksList.setLayoutManager(new LinearLayoutManager(requireContext()));
        tasksAdapter = new TasksAdapter();
        tasksList.setAdapter(tasksAdapter);

        btnClaim.setOnClickListener(v -> claimTask());
        btnAssign.setOnClickListener(v -> openAssignDialog());
        btnMarkComplete.setOnClickListener(v -> markActivityCompleted());
        linkMap.setOnClickListener(v -> jumpToMap());

        loadProfileThenActivity();
        return view;
    }

    private void loadProfileThenActivity() {
        RetrofitHelper.get().create(AuthApi.class).getProfile().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object id = response.body().get("id");
                    Object role = response.body().get("role");
                    currentUserId = id instanceof Number ? ((Number) id).longValue() : 0L;
                    currentUserRole = role instanceof Number ? ((Number) role).intValue() : 1;
                }
                loadActivity();
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                loadActivity();
            }
        });
    }

    private void loadActivity() {
        RetrofitHelper.get().create(RescueApi.class).getActivity(activityId)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        activityData = response.body();
                        detailTitle.setText(stringValue(activityData.get("title")));
                        String status = stringValue(activityData.get("status"));
                        String urgency = stringValue(activityData.get("urgency"));
                        String problemType = stringValue(activityData.get("problemType"));
                        StringBuilder statusLine = new StringBuilder();
                        statusLine.append("状态: ").append(mapRescueStatusText(status));
                        if (!problemType.isEmpty()) {
                            statusLine.append(" · 类型: ").append(problemType);
                        }
                        detailUrgencyStatus.setText(statusLine.toString());
                        detailDescription.setText(stringValue(activityData.get("description")));

                        bindTasks(activityData);
                        buildTimeline(activityData);
                        updateButtons(activityData);

                        long squarePostId = longValue(activityData.get("squarePostId"));
                        if (squarePostId > 0) {
                            linkSquarePost.setVisibility(View.VISIBLE);
                            linkSquarePost.setOnClickListener(v -> {
                                Bundle args = new Bundle();
                                args.putLong("squarePostId", squarePostId);
                                Navigation.findNavController(v).navigate(R.id.square_detail, args);
                            });
                        } else {
                            linkSquarePost.setVisibility(View.GONE);
                        }

                        linkMap.setVisibility(View.VISIBLE);
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : "";
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private void buildTimeline(Map<String, Object> activity) {
        timelineItems.clear();

        String createdAt = stringValue(activity.get("createdAt"));
        String createdBy = stringValue(activity.get("creatorName"));
        timelineItems.add(new TimelineItem("发现问题", createdAt, createdBy.isEmpty() ? "" : ("操作人: " + createdBy)));
        timelineItems.add(new TimelineItem("创建活动", createdAt, createdBy.isEmpty() ? "" : ("操作人: " + createdBy)));

        if (!taskItems.isEmpty()) {
            String firstAssignedAt = "";
            for (Map<String, Object> t : taskItems) {
                String at = stringValue(t.get("assignedAt"));
                if (!at.isEmpty()) {
                    firstAssignedAt = at;
                    break;
                }
            }
            String members = buildTaskMembersText(taskItems);
            timelineItems.add(new TimelineItem("分配/申领任务", firstAssignedAt, members));
        }

        int doneCount = 0;
        for (Map<String, Object> t : taskItems) {
            if ("done".equalsIgnoreCase(stringValue(t.get("status")))) {
                doneCount++;
            }
        }
        if (!taskItems.isEmpty()) {
            timelineItems.add(new TimelineItem("执行救助", "", "进度: " + doneCount + "/" + taskItems.size() + " 已完成"));
        }

        String status = stringValue(activity.get("status"));
        if ("completed".equals(status)) {
            String completedAt = stringValue(activity.get("completedAt"));
            timelineItems.add(new TimelineItem("完成救助", completedAt, ""));
        }

        timelineAdapter.notifyDataSetChanged();
    }

    private String buildTaskMembersText(List<Map<String, Object>> tasks) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> t : tasks) {
            String name = stringValue(t.get("assigneeName"));
            if (name.isEmpty()) {
                name = "未分配";
            }
            names.add(name);
        }
        String joined = joinDistinct(names, 4);
        return joined.isEmpty() ? "" : ("成员: " + joined);
    }

    private String joinDistinct(List<String> list, int maxItems) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s == null || s.trim().isEmpty()) {
                continue;
            }
            boolean exists = false;
            for (String r : result) {
                if (r.equals(s)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                result.add(s);
            }
            if (result.size() >= maxItems) {
                break;
            }
        }
        if (result.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(result.get(i));
        }
        if (list.size() > result.size()) {
            sb.append("…");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private void bindTasks(Map<String, Object> activity) {
        taskItems.clear();
        Object tasksObj = activity.get("tasks");
        if (tasksObj instanceof List) {
            for (Object o : (List<?>) tasksObj) {
                if (o instanceof Map) {
                    taskItems.add((Map<String, Object>) o);
                }
            }
        }
        tasksAdapter.notifyDataSetChanged();
    }

    private void updateButtons(Map<String, Object> activity) {
        String status = stringValue(activity.get("status"));
        boolean isCompleted = "completed".equalsIgnoreCase(status);
        long createdBy = longValue(activity.get("createdBy"));

        btnClaim.setVisibility(!isCompleted && currentUserRole >= 2 ? View.VISIBLE : View.GONE);
        btnAssign.setVisibility(
                !isCompleted && currentUserId > 0 && currentUserId == createdBy ? View.VISIBLE : View.GONE);
        btnMarkComplete.setVisibility(
                !isCompleted && currentUserId > 0 && currentUserId == createdBy ? View.VISIBLE : View.GONE);
        tasksList.setVisibility(View.VISIBLE);
    }

    private void claimTask() {
        RetrofitHelper.get().create(RescueApi.class).claimTask(activityId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded()) {
                    return;
                }
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), "申领成功", Toast.LENGTH_SHORT).show();
                    loadActivity();
                } else {
                    Toast.makeText(requireContext(), "申领失败", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void openAssignDialog() {
        AssignMemberDialogFragment dialog = new AssignMemberDialogFragment();
        Bundle args = new Bundle();
        args.putLong("activityId", activityId);
        dialog.setArguments(args);
        dialog.setListener(this::loadActivity);
        dialog.show(getParentFragmentManager(), "AssignMember");
    }

    private void markActivityCompleted() {
        Map<String, String> body = new HashMap<>();
        body.put("status", "completed");
        RetrofitHelper.get().create(RescueApi.class).updateActivityStatus(activityId, body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(), "已标记完成", Toast.LENGTH_SHORT).show();
                            loadActivity();
                        } else {
                            Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void jumpToMap() {
        if (!(getActivity() instanceof com.ourcat.app.activities.MainActivity) || activityData == null) {
            return;
        }
        com.ourcat.app.activities.MainActivity mainActivity = (com.ourcat.app.activities.MainActivity) getActivity();
        long catId = longValue(activityData.get("catId"));
        if (catId > 0) {
            mainActivity.showMapAtCatLocation(catId);
            return;
        }
        Object latObj = activityData.get("lat");
        Object lngObj = activityData.get("lng");
        if (latObj instanceof Number && lngObj instanceof Number) {
            mainActivity.showMapAtLocation(((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue());
        } else {
            Toast.makeText(requireContext(), "暂无可用定位", Toast.LENGTH_SHORT).show();
        }
    }

    private static String mapRescueStatusText(String status) {
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

    static class TimelineItem {
        String title;
        String time;
        String operator;

        TimelineItem(String title, String time, String operator) {
            this.title = title;
            this.time = time;
            this.operator = operator;
        }
    }

    class TimelineAdapter extends RecyclerView.Adapter<TimelineAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_rescue_timeline, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TimelineItem item = timelineItems.get(position);
            holder.title.setText(item.title);
            holder.time.setText(item.time.isEmpty() ? "" : TimeUtils.getRelativeTime(item.time));
            holder.operator.setText(item.operator.isEmpty() ? "" : item.operator);

            holder.topLine.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
            holder.bottomLine.setVisibility(position == timelineItems.size() - 1 ? View.INVISIBLE : View.VISIBLE);
        }

        @Override
        public int getItemCount() {
            return timelineItems.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, time, operator;
            View topLine, bottomLine;

            ViewHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.timeline_title);
                time = itemView.findViewById(R.id.timeline_time);
                operator = itemView.findViewById(R.id.timeline_operator);
                topLine = itemView.findViewById(R.id.timeline_top_line);
                bottomLine = itemView.findViewById(R.id.timeline_bottom_line);
            }
        }
    }

    class TasksAdapter extends RecyclerView.Adapter<TasksAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            android.widget.ImageView avatar;
            TextView name;
            TextView status;

            VH(View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.task_avatar);
                name = itemView.findViewById(R.id.task_name);
                status = itemView.findViewById(R.id.task_status);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rescue_task, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> t = taskItems.get(position);
            long taskId = longValue(t.get("id"));
            String assigneeName = stringValue(t.get("assigneeName"));
            String taskStatus = stringValue(t.get("status"));
            String completionNote = stringValue(t.get("completionNote"));
            String assignedAt = stringValue(t.get("assignedAt"));
            String assigneeAvatar = stringValue(t.get("assigneeAvatar"));

            holder.name.setText(!assigneeName.isEmpty() ? assigneeName : ("任务 #" + taskId));
            StringBuilder sb = new StringBuilder();
            sb.append("状态: ").append(mapTaskStatusText(taskStatus));
            if (!assignedAt.isEmpty()) {
                sb.append(" · ").append(TimeUtils.getRelativeTime(assignedAt));
            }
            if (!completionNote.isEmpty()) {
                sb.append("\n记录: ").append(completionNote);
            }
            holder.status.setText(sb.toString());

            if (!assigneeAvatar.isEmpty()) {
                com.bumptech.glide.Glide.with(RescueDetailFragment.this)
                        .load(assigneeAvatar)
                        .circleCrop()
                        .placeholder(R.drawable.ic_default_avatar)
                        .into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.ic_default_avatar);
            }

            holder.itemView.setOnClickListener(v -> showTaskLogDialog(t));
        }

        @Override
        public int getItemCount() {
            return taskItems.size();
        }
    }

    private void showTaskLogDialog(Map<String, Object> task) {
        long taskId = longValue(task.get("id"));
        RetrofitHelper.get().create(RescueApi.class).listTaskLogs(taskId)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                            Response<List<Map<String, Object>>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        List<Map<String, Object>> logs = response.isSuccessful() && response.body() != null
                                ? response.body()
                                : new ArrayList<>();
                        showTaskLogDialogWithData(task, logs);
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (!isAdded()) {
                            return;
                        }
                        showTaskLogDialogWithData(task, new ArrayList<>());
                    }
                });
    }

    private void showTaskLogDialogWithData(Map<String, Object> task, List<Map<String, Object>> logs) {
        long taskId = longValue(task.get("id"));
        long assigneeUserId = longValue(task.get("assigneeUserId"));
        boolean canEdit = currentUserId > 0 && currentUserId == assigneeUserId;
        String taskStatus = stringValue(task.get("status"));

        StringBuilder msg = new StringBuilder();
        if (logs.isEmpty()) {
            msg.append("暂无记录");
        } else {
            int limit = Math.min(12, logs.size());
            for (int i = Math.max(0, logs.size() - limit); i < logs.size(); i++) {
                Map<String, Object> l = logs.get(i);
                String time = stringValue(l.get("createdAt"));
                String content = stringValue(l.get("content"));
                if (msg.length() > 0) {
                    msg.append("\n\n");
                }
                msg.append(TimeUtils.getRelativeTime(time)).append("\n").append(content);
            }
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(
                requireContext())
                .setTitle("任务记录")
                .setMessage(msg.toString())
                .setNegativeButton("关闭", null);

        if (canEdit) {
            builder.setNeutralButton("添加记录", (d, w) -> openAddLogDialog(taskId));
            if (!"done".equalsIgnoreCase(taskStatus)) {
                builder.setPositiveButton("提交完成汇报", (d, w) -> openCompleteTaskDialog(taskId));
            }
        }
        builder.show();
    }

    private void openAddLogDialog(long taskId) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("记录内容");
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("添加记录")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("提交", (d, w) -> {
                    String text = input.getText() != null ? input.getText().toString().trim() : "";
                    if (text.isEmpty()) {
                        Toast.makeText(requireContext(), "请输入内容", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Map<String, String> body = new HashMap<>();
                    body.put("content", text);
                    body.put("logType", "progress");
                    RetrofitHelper.get().create(RescueApi.class).addTaskLog(taskId, body)
                            .enqueue(new Callback<Map<String, Object>>() {
                                @Override
                                public void onResponse(Call<Map<String, Object>> call,
                                        Response<Map<String, Object>> response) {
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(), response.isSuccessful() ? "已提交" : "提交失败",
                                                Toast.LENGTH_SHORT).show();
                                        loadActivity();
                                    }
                                }

                                @Override
                                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                })
                .show();
    }

    private void openCompleteTaskDialog(long taskId) {
        android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setHint("完成汇报(选填)");
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("提交完成汇报")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("提交", (d, w) -> {
                    Map<String, String> body = new HashMap<>();
                    body.put("status", "done");
                    String note = input.getText() != null ? input.getText().toString().trim() : "";
                    if (!note.isEmpty()) {
                        body.put("completionNote", note);
                    }
                    RetrofitHelper.get().create(RescueApi.class).updateTask(taskId, body)
                            .enqueue(new Callback<Map<String, Object>>() {
                                @Override
                                public void onResponse(Call<Map<String, Object>> call,
                                        Response<Map<String, Object>> response) {
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(), response.isSuccessful() ? "已完成" : "操作失败",
                                                Toast.LENGTH_SHORT).show();
                                        loadActivity();
                                    }
                                }

                                @Override
                                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                    if (isAdded()) {
                                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            });
                })
                .show();
    }

    private static String mapTaskStatusText(String status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case "assigned":
                return "待执行";
            case "in_progress":
                return "进行中";
            case "done":
                return "已完成";
            default:
                return status;
        }
    }
}
