package com.ourcat.app.fragments;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.ourcat.app.R;
import com.ourcat.app.models.RescueStatsViewModel;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RescueStatisticsFragment extends Fragment {

    private TextView statTotalActivities;
    private TextView statCompletedActivities;
    private TextView statInProgressActivities;
    private RecyclerView contributorsList;
    private ContributorsAdapter contributorsAdapter;
    private final List<Map<String, Object>> contributors = new ArrayList<>();
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

        swipeRefreshLayout.setColorSchemeResources(R.color.primary_variant, R.color.primary, R.color.success);
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.surface);
        swipeRefreshLayout.setOnRefreshListener(this::loadStatistics);

        configureCharts();

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

    private void configureCharts() {
        problemTypeChart.getDescription().setEnabled(false);
        problemTypeChart.setNoDataText("\u6682\u65e0\u7c7b\u578b\u6570\u636e");
        problemTypeChart.setExtraOffsets(0f, 8f, 0f, 8f);
        Legend pieLegend = problemTypeChart.getLegend();
        pieLegend.setTextColor(Color.parseColor("#8A7058"));
        pieLegend.setTextSize(11f);
        pieLegend.setWordWrapEnabled(true);
        pieLegend.setVerticalAlignment(Legend.LegendVerticalAlignment.BOTTOM);
        pieLegend.setHorizontalAlignment(Legend.LegendHorizontalAlignment.CENTER);

        monthlyTrendChart.getDescription().setEnabled(false);
        monthlyTrendChart.setNoDataText("\u6682\u65e0\u8d8b\u52bf\u6570\u636e");
        monthlyTrendChart.setDrawGridBackground(false);
        monthlyTrendChart.setDrawBarShadow(false);
        monthlyTrendChart.setPinchZoom(false);
        monthlyTrendChart.setScaleEnabled(false);
        monthlyTrendChart.setExtraOffsets(0f, 8f, 0f, 8f);

        XAxis xAxis = monthlyTrendChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#8A7058"));
        xAxis.setAxisLineColor(Color.parseColor("#F8E8D8"));
        xAxis.setGranularity(1f);

        YAxis leftAxis = monthlyTrendChart.getAxisLeft();
        leftAxis.setTextColor(Color.parseColor("#8A7058"));
        leftAxis.setAxisLineColor(Color.parseColor("#F8E8D8"));
        leftAxis.setGridColor(Color.parseColor("#F8E8D8"));
        leftAxis.setAxisMinimum(0f);
        leftAxis.setGranularity(1f);

        monthlyTrendChart.getAxisRight().setEnabled(false);
        Legend barLegend = monthlyTrendChart.getLegend();
        barLegend.setTextColor(Color.parseColor("#8A7058"));
        barLegend.setTextSize(11f);
    }

    private void loadStatistics() {
        swipeRefreshLayout.setRefreshing(true);
        RetrofitHelper.get().create(RescueApi.class).getStatistics()
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        swipeRefreshLayout.setRefreshing(false);
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(requireContext(), "\u52a0\u8f7d\u5931\u8d25", Toast.LENGTH_SHORT).show();
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
                            Toast.makeText(requireContext(), "\u7f51\u7edc\u9519\u8bef", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void displayStatistics(Map<String, Object> stats) {
        statTotalActivities.setText(String.valueOf(longValue(stats.get("totalActivities"))));
        statCompletedActivities.setText(String.valueOf(longValue(stats.get("completedActivities"))));
        statInProgressActivities.setText(String.valueOf(longValue(stats.get("inProgressActivities"))));

        Object topContributorsObj = stats.get("topContributors");
        contributors.clear();
        if (topContributorsObj instanceof List) {
            contributors.addAll((List<Map<String, Object>>) topContributorsObj);
        }
        contributorsAdapter.notifyDataSetChanged();

        Object problemTypeObj = stats.get("problemTypeDistribution");
        if (problemTypeObj instanceof List) {
            setupProblemTypeChart((List<Map<String, Object>>) problemTypeObj);
        } else {
            problemTypeChart.clear();
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
        setupMonthlyTrendChart(allList, completedList);
    }

    private void setupProblemTypeChart(List<Map<String, Object>> data) {
        if (data == null || data.isEmpty()) {
            problemTypeChart.clear();
            return;
        }

        List<PieEntry> entries = new ArrayList<>();
        for (Map<String, Object> item : data) {
            entries.add(new PieEntry(longValue(item.get("count")), stringValue(item.get("type"))));
        }

        PieDataSet dataSet = new PieDataSet(entries, "");
        dataSet.setColors(new int[] {
                Color.parseColor("#FF8A24"),
                Color.parseColor("#FFB04A"),
                Color.parseColor("#46BA74"),
                Color.parseColor("#5B98EC"),
                Color.parseColor("#FF6F61")
        });
        dataSet.setSliceSpace(3f);
        dataSet.setValueTextSize(12f);
        dataSet.setValueTextColor(Color.WHITE);

        PieData pieData = new PieData(dataSet);
        problemTypeChart.setData(pieData);
        problemTypeChart.setDrawHoleEnabled(true);
        problemTypeChart.setHoleColor(Color.WHITE);
        problemTypeChart.setHoleRadius(58f);
        problemTypeChart.setTransparentCircleRadius(0f);
        problemTypeChart.setEntryLabelColor(Color.parseColor("#8A7058"));
        problemTypeChart.setEntryLabelTextSize(11f);
        problemTypeChart.setCenterText("\u6551\u52a9\u7c7b\u578b");
        problemTypeChart.setCenterTextColor(Color.parseColor("#30251B"));
        problemTypeChart.setCenterTextSize(15f);
        problemTypeChart.animateY(900);
        problemTypeChart.invalidate();
    }

    private void setupMonthlyTrendChart(List<Map<String, Object>> allList,
            List<Map<String, Object>> completedList) {
        List<String> labels = new ArrayList<>();
        Map<String, Long> allMap = new HashMap<>();
        Map<String, Long> completedMap = new HashMap<>();

        for (Map<String, Object> item : allList) {
            allMap.put(stringValue(item.get("month")), longValue(item.get("count")));
        }
        for (Map<String, Object> item : completedList) {
            completedMap.put(stringValue(item.get("month")), longValue(item.get("count")));
        }

        if (!allList.isEmpty()) {
            for (Map<String, Object> item : allList) {
                labels.add(stringValue(item.get("month")));
            }
        } else {
            for (Map<String, Object> item : completedList) {
                labels.add(stringValue(item.get("month")));
            }
        }

        if (labels.isEmpty()) {
            monthlyTrendChart.clear();
            return;
        }

        List<BarEntry> allEntries = new ArrayList<>();
        List<BarEntry> completedEntries = new ArrayList<>();
        for (int i = 0; i < labels.size(); i++) {
            String month = labels.get(i);
            allEntries.add(new BarEntry(i, allMap.getOrDefault(month, 0L)));
            completedEntries.add(new BarEntry(i, completedMap.getOrDefault(month, 0L)));
        }

        BarDataSet allDataSet = new BarDataSet(allEntries, "\u5168\u90e8\u6d3b\u52a8");
        allDataSet.setColor(Color.parseColor("#FF9E45"));
        allDataSet.setValueTextColor(Color.parseColor("#8A7058"));
        allDataSet.setValueTextSize(10f);
        allDataSet.setHighLightAlpha(0);
        allDataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getBarLabel(BarEntry barEntry) {
                return String.valueOf((long) barEntry.getY());
            }
        });

        BarDataSet completedDataSet = new BarDataSet(completedEntries, "\u5df2\u5b8c\u6210");
        completedDataSet.setColor(Color.parseColor("#46BA74"));
        completedDataSet.setValueTextColor(Color.parseColor("#8A7058"));
        completedDataSet.setValueTextSize(10f);
        completedDataSet.setHighLightAlpha(0);
        completedDataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getBarLabel(BarEntry barEntry) {
                return String.valueOf((long) barEntry.getY());
            }
        });

        BarData barData = new BarData(allDataSet, completedDataSet);
        float groupSpace = 0.22f;
        float barSpace = 0.04f;
        float barWidth = 0.35f;
        barData.setBarWidth(barWidth);
        monthlyTrendChart.setData(barData);

        XAxis xAxis = monthlyTrendChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setCenterAxisLabels(true);
        xAxis.setAxisMinimum(0f);
        xAxis.setAxisMaximum(barData.getGroupWidth(groupSpace, barSpace) * labels.size());

        YAxis leftAxis = monthlyTrendChart.getAxisLeft();
        leftAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((long) value);
            }
        });

        monthlyTrendChart.groupBars(0f, groupSpace, barSpace);
        monthlyTrendChart.animateY(900);
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
            long count = longValue(contributor.get("taskCount"));
            if (count <= 0) {
                count = longValue(contributor.get("count"));
            }

            holder.name.setText((position + 1) + ". " + name);
            holder.stats.setText(count + " \u6b21\u884c\u52a8");

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
                Bundle args = new Bundle();
                args.putLong("userId", userId);
                Navigation.findNavController(v).navigate(R.id.user_profile, args);
            });
        }

        @Override
        public int getItemCount() {
            return contributors.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView avatar;
            TextView name;
            TextView stats;

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
