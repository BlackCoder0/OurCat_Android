package com.ourcat.app.fragments;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.ourcat.app.R;
import com.ourcat.app.repositories.MessageApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MessageCenterFragment extends Fragment {

    private final List<Map<String, Object>> items = new ArrayList<>();
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_message_center, container, false);
        RecyclerView recycler = view.findViewById(R.id.msg_recycler);
        SwipeRefreshLayout swipe = view.findViewById(R.id.msg_swipe);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent,
                        false);
                return new RecyclerView.ViewHolder(v) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> item = items.get(position);
                TextView t1 = holder.itemView.findViewById(android.R.id.text1);
                TextView t2 = holder.itemView.findViewById(android.R.id.text2);
                String type = item.get("type") != null ? item.get("type").toString() : "";
                String content = item.get("content") != null ? item.get("content").toString() : "";
                boolean read = item.get("read") instanceof Boolean && (Boolean) item.get("read");
                t1.setText(getTypeLabel(type));
                t2.setText(content);
                t1.setTypeface(null, read ? Typeface.NORMAL : Typeface.BOLD);
                t2.setTypeface(null, read ? Typeface.NORMAL : Typeface.BOLD);
                holder.itemView.setAlpha(read ? 0.7f : 1f);
                holder.itemView.setOnClickListener(v -> handleMessageClick(item, position));
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        };
        recycler.setAdapter(adapter);
        swipe.setOnRefreshListener(this::loadMessages);
        loadMessages();
        return view;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_message_center, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_mark_all_read) {
            onClickMarkAllRead();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadMessages() {
        RetrofitHelper.get().create(MessageApi.class).list(0, 50)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (getView() != null)
                            ((SwipeRefreshLayout) getView().findViewById(R.id.msg_swipe)).setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            Object content = response.body().get("content");
                            if (content instanceof List) {
                                items.clear();
                                for (Object o : (List<?>) content) {
                                    if (o instanceof Map)
                                        items.add((Map<String, Object>) o);
                                }
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (getView() != null)
                            ((SwipeRefreshLayout) getView().findViewById(R.id.msg_swipe)).setRefreshing(false);
                        Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void onClickMarkAllRead() {
        int unread = 0;
        for (Map<String, Object> item : items) {
            boolean read = item.get("read") instanceof Boolean && (Boolean) item.get("read");
            if (!read) {
                unread++;
            }
        }
        if (unread <= 0) {
            Toast.makeText(requireContext(), "没有未读消息", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("全部标为已读")
                .setMessage("确定要将全部未读消息标记为已读吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> markAllRead())
                .show();
    }

    private void markAllRead() {
        RetrofitHelper.get().create(MessageApi.class).markAllRead()
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        if (!response.isSuccessful()) {
                            Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        int changed = 0;
                        for (Map<String, Object> item : items) {
                            boolean read = item.get("read") instanceof Boolean && (Boolean) item.get("read");
                            if (!read) {
                                item.put("read", true);
                                changed++;
                            }
                        }
                        adapter.notifyDataSetChanged();
                        Toast.makeText(requireContext(), changed > 0 ? "已全部标为已读" : "没有未读消息", Toast.LENGTH_SHORT)
                                .show();
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void handleMessageClick(Map<String, Object> item, int position) {
        Object idObj = item.get("id");
        if (!(idObj instanceof Number))
            return;
        long messageId = ((Number) idObj).longValue();
        boolean read = item.get("read") instanceof Boolean && (Boolean) item.get("read");
        if (!read) {
            RetrofitHelper.get().create(MessageApi.class).markRead(messageId).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    item.put("read", true);
                    adapter.notifyItemChanged(position);
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                }
            });
        }
        String targetType = item.get("targetType") != null ? item.get("targetType").toString() : "";
        Object targetIdObj = item.get("targetId");
        if ("forum_post".equals(targetType) && targetIdObj instanceof Number) {
            Bundle args = new Bundle();
            args.putLong("postId", ((Number) targetIdObj).longValue());
            NavHostFragment.findNavController(this).navigate(R.id.post_detail, args);
            return;
        }
        if ("square_post".equals(targetType) && targetIdObj instanceof Number) {
            Bundle args = new Bundle();
            args.putLong("squarePostId", ((Number) targetIdObj).longValue());
            NavHostFragment.findNavController(this).navigate(R.id.square_detail, args);
            return;
        }
        if ("rescue_activity".equals(targetType) && targetIdObj instanceof Number) {
            Bundle args = new Bundle();
            args.putLong("activityId", ((Number) targetIdObj).longValue());
            NavHostFragment.findNavController(this).navigate(R.id.rescue_detail, args);
            return;
        }
        if ("weather_warning".equals(targetType)) {
            String content = item.get("content") != null ? item.get("content").toString() : "";
            new AlertDialog.Builder(requireContext())
                    .setTitle("天气预警")
                    .setMessage(content)
                    .setPositiveButton("确定", null)
                    .show();
        }
    }

    private String getTypeLabel(String type) {
        if ("forum_reply".equals(type))
            return "论坛回复";
        if ("square_reply".equals(type))
            return "广场回复";
        if ("rescue_response".equals(type))
            return "救助响应";
        if ("rescue_task_assigned".equals(type))
            return "救助任务指派";
        if ("rescue_task_claimed".equals(type))
            return "救助任务申领";
        if ("weather_warning".equals(type))
            return "天气预警";
        return "消息";
    }
}
