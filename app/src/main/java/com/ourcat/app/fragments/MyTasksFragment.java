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
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyTasksFragment extends Fragment {

    private RecyclerView list;
    private TextView emptyText;
    private final List<Map<String, Object>> tasks = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_tasks, container, false);
        list = view.findViewById(R.id.tasks_list);
        emptyText = view.findViewById(R.id.empty_text);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_task, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> m = tasks.get(position);
                Object activityId = m.get("rescueActivityId");
                Object taskId = m.get("id");
                Object status = m.get("status");
                ((TextView) holder.itemView.findViewById(R.id.task_activity_title)).setText("任务 #" + (taskId != null ? taskId.toString() : ""));
                ((TextView) holder.itemView.findViewById(R.id.task_status)).setText(status != null ? status.toString() : "");
                Button btnComplete = holder.itemView.findViewById(R.id.btn_complete);
                boolean isDone = "done".equals(status);
                btnComplete.setVisibility(isDone ? View.GONE : View.VISIBLE);
                holder.itemView.setOnClickListener(v -> {
                    if (activityId != null) {
                        Bundle args = new Bundle();
                        args.putLong("activityId", ((Number) activityId).longValue());
                        Navigation.findNavController(v).navigate(R.id.rescue_detail, args);
                    }
                });
                btnComplete.setOnClickListener(v -> {
                    android.widget.EditText input = new android.widget.EditText(v.getContext());
                    input.setHint("完成汇报（选填）");
                    input.setMinLines(2);
                    new androidx.appcompat.app.AlertDialog.Builder(v.getContext())
                            .setTitle("填写完成汇报").setView(input)
                            .setPositiveButton("提交", (d, w) -> {
                                if (taskId != null) completeTask(((Number) taskId).longValue(), input.getText().toString().trim());
                            })
                            .setNegativeButton("取消", null)
                            .show();
                });
            }
            @Override
            public int getItemCount() { return tasks.size(); }
        });
        load();
        return view;
    }

    private void load() {
        RetrofitHelper.get().create(RescueApi.class).myTasks().enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    tasks.clear();
                    tasks.addAll(response.body());
                    list.getAdapter().notifyDataSetChanged();
                    emptyText.setVisibility(tasks.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                if (isAdded()) emptyText.setVisibility(View.VISIBLE);
            }
        });
    }

    private void completeTask(long taskId, String note) {
        Map<String, String> body = new HashMap<>();
        body.put("status", "done");
        if (note != null && !note.isEmpty()) body.put("completionNote", note);
        RetrofitHelper.get().create(RescueApi.class).updateTask(taskId, body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), response.isSuccessful() ? "已标记完成" : "提交失败", Toast.LENGTH_SHORT).show();
                    load();
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (isAdded()) Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
