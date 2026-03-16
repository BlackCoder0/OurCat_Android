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

import com.ourcat.app.R;
import com.ourcat.app.repositories.OrgApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class JoinRequestsFragment extends Fragment {

    private RecyclerView list;
    private TextView emptyText;
    private final List<Map<String, Object>> requests = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_join_requests, container, false);
        list = view.findViewById(R.id.requests_list);
        emptyText = view.findViewById(R.id.empty_text);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> m = requests.get(position);
                ((TextView) holder.itemView.findViewById(android.R.id.text1)).setText(m.get("nickname") != null ? m.get("nickname").toString() : "");
                ((TextView) holder.itemView.findViewById(android.R.id.text2)).setText("点击通过/拒绝");
                holder.itemView.setOnClickListener(v -> {
                    Object id = m.get("id");
                    if (id == null) return;
                    long rid = ((Number) id).longValue();
                    new androidx.appcompat.app.AlertDialog.Builder(v.getContext())
                            .setTitle("审核").setMessage("通过后该用户将成为志愿者（2级）")
                            .setPositiveButton("通过", (d, w) -> review(rid, true))
                            .setNegativeButton("拒绝", (d, w) -> review(rid, false))
                            .show();
                });
            }
            @Override
            public int getItemCount() { return requests.size(); }
        });
        load();
        return view;
    }

    private void load() {
        RetrofitHelper.get().create(OrgApi.class).listJoinRequests(1L).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    requests.clear();
                    requests.addAll(response.body());
                    list.getAdapter().notifyDataSetChanged();
                    emptyText.setVisibility(requests.isEmpty() ? View.VISIBLE : View.GONE);
                }
            }
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {}
        });
    }

    private void review(long requestId, boolean approve) {
        Map<String, Boolean> body = new HashMap<>();
        body.put("approve", approve);
        RetrofitHelper.get().create(OrgApi.class).reviewJoinRequest(requestId, body).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (isAdded()) {
                    Toast.makeText(requireContext(), approve ? "已通过" : "已拒绝", Toast.LENGTH_SHORT).show();
                    load();
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (isAdded()) Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
