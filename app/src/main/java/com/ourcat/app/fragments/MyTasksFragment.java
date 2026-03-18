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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.ourcat.app.R;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.repositories.SquareApi;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyTasksFragment extends Fragment {

    private RecyclerView list;
    private SwipeRefreshLayout refreshLayout;
    private TextView emptyText;
    private final List<Map<String, Object>> tasks = new ArrayList<>();

    private static class TaskViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;
        final TextView note;
        final Button completeButton;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.task_title);
            subtitle = itemView.findViewById(R.id.task_subtitle);
            note = itemView.findViewById(R.id.task_note);
            completeButton = itemView.findViewById(R.id.btn_complete);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_tasks, container, false);
        list = view.findViewById(R.id.tasks_list);
        refreshLayout = view.findViewById(R.id.tasks_swipe);
        emptyText = view.findViewById(R.id.empty_text);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        refreshLayout.setOnRefreshListener(this::load);
        list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_task, parent, false);
                return new TaskViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                TaskViewHolder vh = (TaskViewHolder) holder;
                Map<String, Object> m = tasks.get(position);
                long activityId = longValue(m.get("rescueActivityId"));
                String assignedAt = stringValue(m.get("assignedAt"), "");
                String completionNote = stringValue(m.get("completionNote"), "");
                String activityTitle = stringValue(m.get("rescueActivityTitle"), "");
                String activityStatus = stringValue(m.get("rescueActivityStatus"), "");
                long squarePostId = longValue(m.get("squarePostId"));
                String assignerName = stringValue(m.get("assignerName"), "");
                String squarePostStatus = stringValue(m.get("squarePostStatus"), "");
                String squarePostSnippet = stringValue(m.get("squarePostSnippet"), "");
                String squarePostLocation = stringValue(m.get("squarePostLocation"), "");
                String catName = stringValue(m.get("catName"), "");

                String baseTitle = !squarePostSnippet.isEmpty() ? squarePostSnippet
                        : (!activityTitle.isEmpty() ? activityTitle : ("救助任务 · 活动 #" + activityId));
                vh.title.setText(!catName.isEmpty() ? (catName + " · " + baseTitle) : baseTitle);

                String eventStatus = mapEventStatus(squarePostStatus, activityStatus);
                vh.subtitle.setText(buildSubtitle(eventStatus, assignedAt, assignerName));

                String locationDisplay = formatLocationForDisplay(squarePostLocation);
                StringBuilder noteBuilder = new StringBuilder();
                if (!locationDisplay.isEmpty()) {
                    noteBuilder.append("位置：").append(locationDisplay);
                }
                if (completionNote != null && !completionNote.trim().isEmpty()) {
                    if (noteBuilder.length() > 0) {
                        noteBuilder.append("\n");
                    }
                    noteBuilder.append("记录：").append(completionNote.trim());
                }
                if (noteBuilder.length() > 0) {
                    vh.note.setVisibility(View.VISIBLE);
                    vh.note.setText(noteBuilder.toString());
                } else {
                    vh.note.setVisibility(View.GONE);
                }

                boolean isResolved = "已解决".equals(eventStatus);
                boolean canMarkSolved = !isResolved && squarePostId > 0;
                vh.completeButton.setVisibility(canMarkSolved ? View.VISIBLE : View.GONE);
                holder.itemView.setOnClickListener(v -> {
                    if (squarePostId > 0) {
                        Bundle args = new Bundle();
                        args.putLong("squarePostId", squarePostId);
                        Navigation.findNavController(v).navigate(R.id.square_detail, args);
                        return;
                    }
                    if (activityId > 0) {
                        Bundle args = new Bundle();
                        args.putLong("activityId", activityId);
                        Navigation.findNavController(v).navigate(R.id.rescue_detail, args);
                    }
                });
                vh.completeButton.setOnClickListener(v -> confirmMarkSolved(squarePostId));
            }

            @Override
            public int getItemCount() {
                return tasks.size();
            }
        });
        load();
        return view;
    }

    private void load() {
        if (!isAdded()) {
            return;
        }
        refreshLayout.setRefreshing(true);
        RetrofitHelper.get().create(RescueApi.class).myTasks().enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!isAdded())
                    return;
                refreshLayout.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null) {
                    tasks.clear();
                    tasks.addAll(response.body());
                    list.getAdapter().notifyDataSetChanged();
                    emptyText.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                if (!isAdded()) {
                    return;
                }
                refreshLayout.setRefreshing(false);
                emptyText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void confirmMarkSolved(long squarePostId) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("标记已解决")
                .setMessage("确认将该事件标记为已解决吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> markSolved(squarePostId))
                .show();
    }

    private void markSolved(long squarePostId) {
        RetrofitHelper.get().create(SquareApi.class).markSolved(squarePostId)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), response.isSuccessful() ? "已标记为已解决" : "操作失败",
                                    Toast.LENGTH_SHORT).show();
                            load();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded())
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String buildSubtitle(String eventStatus, String assignedAt, String assignerName) {
        String timeText = assignedAt == null || assignedAt.trim().isEmpty() ? ""
                : TimeUtils.getRelativeTime(assignedAt);
        String a = assignerName == null || assignerName.trim().isEmpty() ? "" : ("指派：" + assignerName.trim());
        StringBuilder sb = new StringBuilder();
        sb.append("状态：").append(eventStatus.isEmpty() ? "--" : eventStatus);
        if (!timeText.isEmpty()) {
            sb.append(" · ").append(timeText);
        }
        if (!a.isEmpty()) {
            sb.append(" · ").append(a);
        }
        return sb.toString();
    }

    private String mapEventStatus(String squarePostStatus, String activityStatus) {
        String p = squarePostStatus != null ? squarePostStatus.trim() : "";
        String a = activityStatus != null ? activityStatus.trim() : "";
        if ("resolved".equalsIgnoreCase(p) || "completed".equalsIgnoreCase(a)) {
            return "已解决";
        }
        return "进行中";
    }

    private String formatLocationForDisplay(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "";
        }
        int space = t.indexOf(' ');
        if (space > 0) {
            String prefix = t.substring(0, space);
            if (prefix.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
                String rest = t.substring(space + 1).trim();
                return rest.isEmpty() ? "附近" : rest;
            }
        }
        if (t.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
            return "附近";
        }
        return t;
    }

    private long longValue(Object o) {
        return o instanceof Number ? ((Number) o).longValue() : 0L;
    }

    private String stringValue(Object o, String def) {
        return o != null ? String.valueOf(o) : def;
    }
}
