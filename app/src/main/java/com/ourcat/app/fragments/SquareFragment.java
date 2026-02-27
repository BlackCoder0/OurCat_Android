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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ourcat.app.R;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SquareFragment extends Fragment {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private final List<Map<String, Object>> items = new ArrayList<>();
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_square, container, false);
        recyclerView = view.findViewById(R.id.square_recycler);
        refreshLayout = view.findViewById(R.id.square_swipe);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_square_post, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> item = items.get(position);
                TextView text = holder.itemView.findViewById(R.id.item_square_text);
                TextView type = holder.itemView.findViewById(R.id.item_square_type);
                TextView status = holder.itemView.findViewById(R.id.item_square_status);
                text.setText(item.get("text") != null ? item.get("text").toString() : "");
                type.setText("询问".equals(item.get("type")) ? "询问" : "救助");
                status.setText("resolved".equals(item.get("status")) ? "已解决" : "进行中");
            }
            @Override
            public int getItemCount() { return items.size(); }
        };
        recyclerView.setAdapter(adapter);

        refreshLayout.setOnRefreshListener(this::loadPosts);
        view.findViewById(R.id.square_fab).setOnClickListener(v ->
                Toast.makeText(requireContext(), "发布广播（可后续实现）", Toast.LENGTH_SHORT).show());
        loadPosts();
        return view;
    }

    private void loadPosts() {
        refreshLayout.setRefreshing(true);
        RetrofitHelper.get().create(com.ourcat.app.repositories.SquareApi.class).listPosts(0, 20, null)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        refreshLayout.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            Object content = response.body().get("content");
                            if (content instanceof List) {
                                items.clear();
                                for (Object o : (List<?>) content) {
                                    if (o instanceof Map) items.add((Map<String, Object>) o);
                                }
                                adapter.notifyDataSetChanged();
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        refreshLayout.setRefreshing(false);
                        Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
