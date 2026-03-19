package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.ourcat.app.R;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RescueHomeFragment extends Fragment {

    private TextView statInProgress;
    private TextView statCompleted;
    private TextView statTotal;
    private LinearLayout recentContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rescue_home, container, false);

        statInProgress = view.findViewById(R.id.stat_in_progress);
        statCompleted = view.findViewById(R.id.stat_completed);
        statTotal = view.findViewById(R.id.stat_total);
        recentContainer = view.findViewById(R.id.recent_rescue_container);

        setupEntry(view, R.id.entry_square, v -> Navigation.findNavController(v).navigate(R.id.nav_square));
        setupEntry(view, R.id.entry_forum, v -> Navigation.findNavController(v).navigate(R.id.forum_list));
        setupEntry(view, R.id.entry_guide, v -> Navigation.findNavController(v).navigate(R.id.rescue_guide));
        setupEntry(view, R.id.entry_statistics, v -> Navigation.findNavController(v).navigate(R.id.rescue_statistics));

        setupEntry(view, R.id.btn_open_dynamic, v -> Navigation.findNavController(v).navigate(R.id.rescue_list));
        setupEntry(view, R.id.btn_open_statistics,
                v -> Navigation.findNavController(v).navigate(R.id.rescue_statistics));

        loadStats();
        loadRecent();

        return view;
    }

    private void setupEntry(@NonNull View root, int id, @NonNull View.OnClickListener listener) {
        View v = root.findViewById(id);
        if (v != null) {
            v.setOnClickListener(listener);
        }
    }

    private void loadStats() {
        RetrofitHelper.get().create(RescueApi.class).getStatistics().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded()) {
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }
                Map<String, Object> stats = response.body();
                statTotal.setText(String.valueOf(longValue(stats.get("totalActivities"))));
                statCompleted.setText(String.valueOf(longValue(stats.get("completedActivities"))));
                statInProgress.setText(String.valueOf(longValue(stats.get("inProgressActivities"))));
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
            }
        });
    }

    private void loadRecent() {
        RetrofitHelper.get().create(RescueApi.class).listActivities(0, 3, null, null)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null) {
                            return;
                        }
                        Object content = response.body().get("content");
                        if (recentContainer != null) {
                            recentContainer.removeAllViews();
                        }
                        if (content instanceof List) {
                            for (Object o : (List<?>) content) {
                                if (o instanceof Map) {
                                    bindRecentItemView((Map<String, Object>) o);
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void bindRecentItemView(@NonNull Map<String, Object> m) {
        if (recentContainer == null) {
            return;
        }
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.item_rescue_activity, recentContainer, false);
        ((TextView) v.findViewById(R.id.item_rescue_title)).setText(str(m, "title"));
        ((TextView) v.findViewById(R.id.item_rescue_desc)).setText(str(m, "description"));
        ((TextView) v.findViewById(R.id.item_rescue_status)).setText(str(m, "status"));
        ((TextView) v.findViewById(R.id.item_rescue_time)).setText(str(m, "createdAt"));
        Object id = m.get("id");
        v.setOnClickListener(v2 -> {
            if (id instanceof Number) {
                Bundle args = new Bundle();
                args.putLong("activityId", ((Number) id).longValue());
                Navigation.findNavController(v2).navigate(R.id.rescue_detail, args);
            }
        });
        recentContainer.addView(v);
    }

    private static String str(@NonNull Map<String, Object> m, @NonNull String k) {
        Object v = m.get(k);
        return v != null ? v.toString() : "";
    }

    private static long longValue(@Nullable Object v) {
        return v instanceof Number ? ((Number) v).longValue() : 0L;
    }
}
