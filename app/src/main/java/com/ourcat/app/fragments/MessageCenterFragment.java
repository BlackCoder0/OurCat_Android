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
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.ourcat.app.R;
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_message_center, container, false);
        view.findViewById(R.id.msg_back).setOnClickListener(v -> NavHostFragment.findNavController(MessageCenterFragment.this).navigateUp());
        RecyclerView recycler = view.findViewById(R.id.msg_recycler);
        SwipeRefreshLayout swipe = view.findViewById(R.id.msg_swipe);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> item = items.get(position);
                TextView t1 = holder.itemView.findViewById(android.R.id.text1);
                TextView t2 = holder.itemView.findViewById(android.R.id.text2);
                t1.setText(item.get("type") != null ? item.get("type").toString() : "");
                t2.setText(item.get("content") != null ? item.get("content").toString() : "");
            }
            @Override
            public int getItemCount() { return items.size(); }
        };
        recycler.setAdapter(adapter);
        swipe.setOnRefreshListener(this::loadMessages);
        loadMessages();
        return view;
    }

    private void loadMessages() {
        RetrofitHelper.get().create(com.ourcat.app.repositories.MessageApi.class).list(0, 50)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (getView() != null) ((SwipeRefreshLayout) getView().findViewById(R.id.msg_swipe)).setRefreshing(false);
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
                        if (getView() != null) ((SwipeRefreshLayout) getView().findViewById(R.id.msg_swipe)).setRefreshing(false);
                        Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
