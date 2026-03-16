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
                View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
                return new RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> m = members.get(position);
                Object nick = m.get("nickname");
                ((android.widget.TextView) holder.itemView.findViewById(android.R.id.text1)).setText(nick != null ? nick.toString() : "用户");
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
