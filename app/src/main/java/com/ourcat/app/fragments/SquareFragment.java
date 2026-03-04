package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.ChipGroup;
import com.ourcat.app.R;
import com.ourcat.app.repositories.SquareApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 广场列表。对应计划文档「阶段 6 → 前端 → SquareFragment、排序选择 UI」。
 */
public class SquareFragment extends Fragment {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private ChipGroup sortGroup;
    private final List<Map<String, Object>> items = new ArrayList<>();
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_square, container, false);
        recyclerView = view.findViewById(R.id.square_recycler);
        refreshLayout = view.findViewById(R.id.square_swipe);
        sortGroup = view.findViewById(R.id.square_sort_group);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            class ViewHolder extends RecyclerView.ViewHolder {
                TextView type, status, text, location;
                View container;

                ViewHolder(View itemView) {
                    super(itemView);
                    type = itemView.findViewById(R.id.item_square_type);
                    status = itemView.findViewById(R.id.item_square_status);
                    text = itemView.findViewById(R.id.item_square_text);
                    location = itemView.findViewById(R.id.item_square_location);
                    container = itemView.findViewById(R.id.item_square_container);
                }
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_square_post, parent, false);
                return new ViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holderRaw, int position) {
                ViewHolder holder = (ViewHolder) holderRaw;
                Map<String, Object> item = items.get(position);
                String t = (String) item.get("type");
                String s = (String) item.get("status");
                String txt = (String) item.get("text");
                Object loc = item.get("location");

                holder.type.setText("rescue".equals(t) ? "救助" : "询问");
                holder.type.setBackgroundResource(
                        "rescue".equals(t) ? R.drawable.bg_map_mode_chip : R.drawable.bg_chip_inquiry);
                holder.status.setText("resolved".equals(s) ? "已解决" : "进行中");
                holder.status.setTextColor("resolved".equals(s) ? 0xFF4CAF50 : 0xFFFF9800);
                holder.text.setText(txt);

                if (loc != null) {
                    holder.location.setVisibility(View.VISIBLE);
                    holder.location.setText(loc.toString());
                } else {
                    holder.location.setVisibility(View.GONE);
                }

                if ("resolved".equals(s)) {
                    if (holder.container != null)
                        holder.container.setAlpha(0.25f);
                } else {
                    if (holder.container != null)
                        holder.container.setAlpha(1.0f);
                }
                holder.itemView.setOnClickListener(v -> {
                    Object idObj = item.get("id");
                    if (idObj instanceof Number) {
                        long postId = ((Number) idObj).longValue();
                        Bundle args = new Bundle();
                        args.putLong("squarePostId", postId);
                        Navigation.findNavController(v).navigate(R.id.square_detail, args);
                    }
                });
                holder.itemView.setOnLongClickListener(v -> {
                    Object idObj = item.get("id");
                    if (idObj instanceof Number) {
                        long postId = ((Number) idObj).longValue();
                        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                                .setTitle("操作")
                                .setItems(new String[] { "删除" }, (dialog, which) -> {
                                    if (which == 0)
                                        deletePost(postId);
                                })
                                .show();
                        return true;
                    }
                    return false;
                });
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        };
        recyclerView.setAdapter(adapter);

        sortGroup.setOnCheckedStateChangeListener((group, checkedIds) -> loadPosts());
        refreshLayout.setOnRefreshListener(this::loadPosts);
        view.findViewById(R.id.square_fab)
                .setOnClickListener(v -> Navigation.findNavController(v).navigate(R.id.create_square_post));
        loadPosts();
        return view;
    }

    private void deletePost(long postId) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除这条广播吗？")
                .setPositiveButton("删除", (d, w) -> {
                    RetrofitHelper.get().create(SquareApi.class).deletePost(postId)
                            .enqueue(new Callback<Void>() {
                                @Override
                                public void onResponse(Call<Void> call, Response<Void> response) {
                                    if (response.isSuccessful()) {
                                        Toast.makeText(requireContext(), "删除成功", Toast.LENGTH_SHORT).show();
                                        loadPosts();
                                    } else {
                                        Toast.makeText(requireContext(), "删除失败: 无权限", Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onFailure(Call<Void> call, Throwable t) {
                                    Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String getCurrentFilter() {
        if (sortGroup == null)
            return "all";
        int id = sortGroup.getCheckedChipId();
        if (id == R.id.chip_filter_unresolved)
            return "open";
        if (id == R.id.chip_filter_resolved)
            return "resolved";
        return "all";
    }

    private void loadPosts() {
        if (!isAdded())
            return;
        refreshLayout.setRefreshing(true);
        String filter = getCurrentFilter();
        // Use a new API or filter client-side. Backend currently supports
        // sort=hot/latest.
        // For filtering status, we might need to filter locally if backend doesn't
        // support it yet.
        // Assuming we fetch all latest first, then filter locally for now.
        RetrofitHelper.get().create(SquareApi.class).listPosts(0, 50, null)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                            Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        refreshLayout.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            Object content = response.body().get("content");
                            if (content instanceof List) {
                                items.clear();
                                for (Object o : (List<?>) content) {
                                    if (o instanceof Map) {
                                        Map<String, Object> m = (Map<String, Object>) o;
                                        String status = m.get("status") != null ? m.get("status").toString() : "open";
                                        if ("all".equals(filter)) {
                                            items.add(m);
                                        } else if ("open".equals(filter) && !"resolved".equals(status)) {
                                            items.add(m);
                                        } else if ("resolved".equals(filter) && "resolved".equals(status)) {
                                            items.add(m);
                                        }
                                    }
                                }
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (!isAdded())
                            return;
                        refreshLayout.setRefreshing(false);
                        Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
