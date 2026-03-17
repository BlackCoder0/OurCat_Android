package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RescueListFragment extends Fragment {

    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView emptyText;
    private final List<Map<String, Object>> items = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rescue_list, container, false);
        recycler = view.findViewById(R.id.rescue_list);
        progress = view.findViewById(R.id.progress);
        emptyText = view.findViewById(R.id.empty_text);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rescue_activity, parent, false);
                return new RecyclerView.ViewHolder(v) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> m = items.get(position);
                View v = holder.itemView;
                ((TextView) v.findViewById(R.id.item_rescue_title)).setText(str(m, "title"));
                ((TextView) v.findViewById(R.id.item_rescue_desc)).setText(str(m, "description"));
                ((TextView) v.findViewById(R.id.item_rescue_status)).setText(str(m, "status"));
                ((TextView) v.findViewById(R.id.item_rescue_time)).setText(str(m, "createdAt"));
                Object id = m.get("id");
                v.setOnClickListener(v2 -> {
                    if (id != null) {
                        Bundle args = new Bundle();
                        args.putLong("activityId", ((Number) id).longValue());
                        Navigation.findNavController(v2).navigate(R.id.rescue_detail, args);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        });
        load();
        return view;
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        RetrofitHelper.get().create(RescueApi.class).listActivities(0, 50, null, null)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        progress.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) {
                            Object content = response.body().get("content");
                            items.clear();
                            if (content instanceof List) {
                                for (Object o : (List<?>) content) {
                                    if (o instanceof Map)
                                        items.add((Map<String, Object>) o);
                                }
                            }
                            recycler.getAdapter().notifyDataSetChanged();
                            emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded()) {
                            progress.setVisibility(View.GONE);
                            emptyText.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null ? v.toString() : "";
    }
}
