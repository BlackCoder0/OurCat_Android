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

import com.bumptech.glide.Glide;
import com.ourcat.app.R;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.widget.ScrollView;
import androidx.lifecycle.ViewModelProvider;
import com.ourcat.app.models.RescueStatsViewModel;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class RescueStatisticsFragment extends Fragment {

    private TextView statTotalActivities;
    private TextView statCompletedActivities;
    private TextView statInProgressActivities;
    private RecyclerView contributorsList;
    private ContributorsAdapter contributorsAdapter;
    private List<Map<String, Object>> contributors = new ArrayList<>();
    private PieChart problemTypeChart;
    private BarChart monthlyTrendChart;
    private ScrollView scrollView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private RescueStatsViewModel viewModel;
    private static final String KEY_SCROLL_Y = "rescue_stats_scroll_y";
    private Integer pendingScrollY;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(RescueStatsViewModel.class);
        View view = inflater.inflate(R.layout.fragment_rescue_statistics, container, false);
        if (savedInstanceState != null) {
            pendingScrollY = savedInstanceState.getInt(KEY_SCROLL_Y, 0);
        }

        scrollView = view.findViewById(R.id.stats_scroll_view);
        swipeRefreshLayout = view.findViewById(R.id.stats_swipe_refresh);
        statTotalActivities = view.findViewById(R.id.stat_total_activities);
        statCompletedActivities = view.findViewById(R.id.stat_completed_activities);
        statInProgressActivities = view.findViewById(R.id.stat_in_progress_activities);
        contributorsList = view.findViewById(R.id.contributors_list);
        problemTypeChart = view.findViewById(R.id.problem_type_chart);
        monthlyTrendChart = view.findViewById(R.id.monthly_trend_chart);

        contributorsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        contributorsAdapter = new ContributorsAdapter();
        contributorsList.setAdapter(contributorsAdapter);

        swipeRefreshLayout.setOnRefreshListener(this::loadStatistics);

        if (viewModel.getStats().getValue() != null && !viewModel.getStats().getValue().isEmpty()) {
            displayStatistics(viewModel.getStats().getValue());
        } else {
            loadStatistics();
        }

        scrollView.post(() -> {
            if (pendingScrollY != null) {
                scrollView.scrollTo(0, pendingScrollY);
                pendingScrollY = null;
            } else {
                scrollView.scrollTo(0, viewModel.getScrollY());
            }
        });

        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (scrollView != null) {
                viewModel.setScrollY(scrollView.getScrollY());
            }
        });

        return view;
    }

    private void loadStatistics() {
        swipeRefreshLayout.setRefreshing(true);
        RetrofitHelper.get().create(RescueApi.class).getStatistics()
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        swipeRefreshLayout.setRefreshing(false);
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Map<String, Object> stats = response.body();
                        viewModel.getStats().setValue(stats);
                        displayStatistics(stats);
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded()) {
                            swipeRefreshLayout.setRefreshing(false);
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void displayStatistics(Map<String, Object> stats) {
        statTotalActivities.setText(String.valueOf(longValue(stats.get("totalActivities"))));
        statCompletedActivities.setText(String.valueOf(longValue(stats.get("completedActivities"))));
        statInProgressActivities.setText(String.valueOf(longValue(stats.get("inProgressActivities"))));

        Object topContributorsObj = stats.get("topContributors");
        if (topContributorsObj instanceof List) {
            contributors.clear();
            contributors.addAll((List<Map<String, Object>>) topContributorsObj);
            contributorsAdapter.notifyDataSetChanged();
        }

        Object problemTypeObj = stats.get("problemTypeDistribution");
        if (problemTypeObj instanceof List) {
            setupProblemTypeChart((List<Map<String, Object>>) problemTypeObj);
        }

        Object monthlyTrendObj = stats.get("monthlyTrend");
        Object monthlyTrendAllObj = stats.get("monthlyTrendAll");
        Object monthlyTrendCompletedObj = stats.get("monthlyTrendCompleted");
        List<Map<String, Object>> allList = monthlyTrendAllObj instanceof List
                ? (List<Map<String, Object>>) monthlyTrendAllObj
                : monthlyTrendObj instanceof List ? (List<Map<String, Object>>) monthlyTrendObj : new ArrayList<>();
        List<Map<String, Object>> completedList = monthlyTrendCompletedObj instanceof List
                ? (List<Map<String, Object>>) monthlyTrendCompletedObj
                : new ArrayList<>();
        if (!allList.isEmpty() || !completedList.isEmpty()) {
            setupMonthlyTrendChart(allList, completedList);
        }
    }

    private void setupProblemTypeChart(List<Map<String, Object>> data) {
        List<PieEntry> entries = new ArrayList<>();
        for (Map<String, Object> m : data) {
            entries.add(new PieEntry(longValue(m.get("count")), stringValue(m.get("type"))));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(android.graphics.Color.WHITE);

        PieData pieData = new PieData(dataSet);
        problemTypeChart.setData(pieData);
        problemTypeChart.getDescription().setEnabled(false);
        problemTypeChart.setCenterText("救助类型");
        problemTypeChart.animateY(1000);
        problemTypeChart.invalidate();
    }

    private void setupMonthlyTrendChart(List<Map<String, Object>> allList,
            List<Map<String, Object>> completedList) {
        List<String> labels = new ArrayList<>();
        java.util.Map<String, Long> allMap = new java.util.HashMap<>();
        java.util.Map<String, Long> completedMap = new java.util.HashMap<>();
        for (Map<String, Object> m : allList) {
            allMap.put(stringValue(m.get("month")), longValue(m.get("count")));
        }
        for (Map<String, Object> m : completedList) {
            completedMap.put(stringValue(m.get("month")), longValue(m.get("count")));
        }
        if (!allList.isEmpty()) {
            for (Map<String, Object> m : allList) {
                labels.add(stringValue(m.get("month")));
            }
        } else {
            for (Map<String, Object> m : completedList) {
                labels.add(stringValue(m.get("month")));
            }
        }

        List<BarEntry> allEntries = new ArrayList<>();
        List<BarEntry> completedEntries = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String month = labels.get(i);
            allEntries.add(new BarEntry(i, allMap.getOrDefault(month, 0L)));
            completedEntries.add(new BarEntry(i, completedMap.getOrDefault(month, 0L)));
        }

        BarDataSet allDataSet = new BarDataSet(allEntries, "全部");
        allDataSet.setColor(ColorTemplate.MATERIAL_COLORS[0]);
        allDataSet.setValueTextSize(10f);
        allDataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getBarLabel(BarEntry barEntry) {
                return String.valueOf((long) barEntry.getY());
            }
        });

        BarDataSet completedDataSet = new BarDataSet(completedEntries, "已完成");
        completedDataSet.setColor(ColorTemplate.MATERIAL_COLORS[2]);
        completedDataSet.setValueTextSize(10f);
        completedDataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getBarLabel(BarEntry barEntry) {
                return String.valueOf((long) barEntry.getY());
            }
        });

        BarData barData = new BarData(allDataSet, completedDataSet);
        float groupSpace = 0.2f;
        float barSpace = 0.02f;
        float barWidth = 0.38f;
        barData.setBarWidth(barWidth);
        monthlyTrendChart.setData(barData);
        monthlyTrendChart.getDescription().setEnabled(false);
        monthlyTrendChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        monthlyTrendChart.getXAxis().setGranularity(1f);
        monthlyTrendChart.getXAxis().setCenterAxisLabels(true);
        monthlyTrendChart.getXAxis().setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
        monthlyTrendChart.getXAxis().setAxisMinimum(0f);
        monthlyTrendChart.getXAxis()
                .setAxisMaximum(0f + barData.getGroupWidth(groupSpace, barSpace) * labels.size());
        monthlyTrendChart.groupBars(0f, groupSpace, barSpace);
        monthlyTrendChart.getAxisLeft().setGranularity(1f);
        monthlyTrendChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((long) value);
            }
        });
        monthlyTrendChart.getAxisRight().setEnabled(false);
        monthlyTrendChart.animateY(1000);
        monthlyTrendChart.invalidate();
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    class ContributorsAdapter extends RecyclerView.Adapter<ContributorsAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_org_member, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> contributor = contributors.get(position);
            long userId = longValue(contributor.get("userId"));
            String nickname = stringValue(contributor.get("nickname"));
            String username = stringValue(contributor.get("username"));
            String name = !nickname.isEmpty() ? nickname : username;
            long c = longValue(contributor.get("taskCount"));
            if (c <= 0) {
                c = longValue(contributor.get("count"));
            }
            holder.name.setText((position + 1) + ". " + name);
            holder.stats.setText(c + " 次");

            String avatarUrl = stringValue(contributor.get("avatarUrl"));
            if (!avatarUrl.isEmpty()) {
                Glide.with(RescueStatisticsFragment.this)
                        .load(avatarUrl)
                        .circleCrop()
                        .placeholder(R.drawable.ic_default_avatar)
                        .error(R.drawable.ic_default_avatar)
                        .into(holder.avatar);
            } else {
                holder.avatar.setImageResource(R.drawable.ic_default_avatar);
            }

            holder.itemView.setOnClickListener(v -> {
                android.os.Bundle args = new android.os.Bundle();
                args.putLong("userId", userId);
                Navigation.findNavController(v).navigate(R.id.user_profile, args);
            });
        }

        @Override
        public int getItemCount() {
            return contributors.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            android.widget.ImageView avatar;
            TextView name, stats;

            ViewHolder(View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.member_avatar);
                name = itemView.findViewById(R.id.member_name);
                stats = itemView.findViewById(R.id.member_stats);
            }
        }
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (scrollView != null) {
            outState.putInt(KEY_SCROLL_Y, scrollView.getScrollY());
        }
    }

    @Override
    public void onPause() {
        if (scrollView != null) {
            viewModel.setScrollY(scrollView.getScrollY());
        }
        super.onPause();
    }
}
