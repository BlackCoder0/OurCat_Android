package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.card.MaterialCardView;
import com.ourcat.app.R;
import com.ourcat.app.repositories.CatApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CatsListFragment extends Fragment {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private TextView statTotal;
    private TextView statActive;
    private TextView statAdopted;
    private TextView emptyView;
    private ChipGroup filterGroup;
    private final List<Map<String, Object>> allItems = new ArrayList<>();
    private final List<Map<String, Object>> items = new ArrayList<>();
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
    private int currentFilterId = R.id.chip_cats_all;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cats_list, container, false);

        recyclerView = view.findViewById(R.id.cats_recycler);
        refreshLayout = view.findViewById(R.id.cats_swipe);
        statTotal = view.findViewById(R.id.cats_stat_total);
        statActive = view.findViewById(R.id.cats_stat_active);
        statAdopted = view.findViewById(R.id.cats_stat_adopted);
        emptyView = view.findViewById(R.id.cats_empty);
        filterGroup = view.findViewById(R.id.cats_filter_group);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        refreshLayout.setColorSchemeResources(R.color.primary_variant, R.color.primary, R.color.info);

        adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            class ViewHolder extends RecyclerView.ViewHolder {
                MaterialCardView container;
                ImageView avatar;
                TextView name, color, status, summary;

                ViewHolder(View itemView) {
                    super(itemView);
                    container = itemView.findViewById(R.id.item_cat_container);
                    avatar = itemView.findViewById(R.id.item_cat_avatar);
                    name = itemView.findViewById(R.id.item_cat_name);
                    color = itemView.findViewById(R.id.item_cat_color);
                    status = itemView.findViewById(R.id.item_cat_status);
                    summary = itemView.findViewById(R.id.item_cat_summary);
                    container.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_DOWN) {
                            v.animate().scaleX(0.98f).scaleY(0.98f).setDuration(120).start();
                        } else if (event.getAction() == MotionEvent.ACTION_UP
                                || event.getAction() == MotionEvent.ACTION_CANCEL) {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                        }
                        return false;
                    });
                }
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cat_list, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ViewHolder h = (ViewHolder) holder;
                Map<String, Object> cat = items.get(position);

                String catName = cat.get("name") != null ? cat.get("name").toString().trim() : "";
                String catColor = cat.get("color") != null ? cat.get("color").toString() : "";
                String catStatus = cat.get("status") != null ? cat.get("status").toString() : "";
                String avatarUrl = cat.get("primaryImageUrl") != null ? cat.get("primaryImageUrl").toString() : null;

                if (catName.isEmpty()) {
                    h.name.setText("未命名");
                    h.name.setAlpha(0.25f);
                } else {
                    h.name.setText(catName);
                    h.name.setAlpha(1f);
                }
                h.color.setText(catColor.isEmpty() ? "花色未知" : catColor);
                h.status.setText(getStatusText(catStatus));
                h.summary.setText(buildSummary(cat));
                applyStatusStyle(h.status, catStatus);

                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    Glide.with(CatsListFragment.this)
                            .load(avatarUrl)
                            .placeholder(R.drawable.ic_default_avatar)
                            .error(R.drawable.ic_default_avatar)
                            .centerCrop()
                            .into(h.avatar);
                } else {
                    h.avatar.setImageResource(R.drawable.ic_default_avatar);
                }

                long catId = cat.get("id") != null ? ((Number) cat.get("id")).longValue() : 0L;
                h.container.setOnClickListener(v -> {
                    Bundle args = new Bundle();
                    args.putLong("catId", catId);
                    NavHostFragment.findNavController(CatsListFragment.this).navigate(R.id.cat_profile, args);
                });
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        };

        recyclerView.setAdapter(adapter);

        if (filterGroup != null) {
            filterGroup.check(currentFilterId);
            filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
                if (checkedIds == null || checkedIds.isEmpty()) {
                    return;
                }
                currentFilterId = checkedIds.get(0);
                applyFilter();
            });
        }

        refreshLayout.setOnRefreshListener(this::loadCats);
        loadCats();

        return view;
    }

    private void loadCats() {
        refreshLayout.setRefreshing(true);
        RetrofitHelper.get().create(CatApi.class).listCats(0, 50, null, null, null, null, null)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        refreshLayout.setRefreshing(false);
                        if (!isAdded()) {
                            return;
                        }
                        allItems.clear();
                        items.clear();
                        if (response.isSuccessful() && response.body() != null) {
                            Object content = response.body().get("content");
                            if (content instanceof List) {
                                allItems.addAll((List<Map<String, Object>>) content);
                            }
                        }
                        applyFilter();
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        refreshLayout.setRefreshing(false);
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void updateSummary() {
        int total = allItems.size();
        int active = 0;
        int adopted = 0;
        for (Map<String, Object> cat : allItems) {
            String status = cat.get("status") != null ? cat.get("status").toString() : "";
            if ("active".equalsIgnoreCase(status) || "活跃".equals(status)) {
                active++;
            }
            if ("adopted".equalsIgnoreCase(status) || "收养".equals(status) || "已安置".equals(status)) {
                adopted++;
            }
        }
        if (statTotal != null) {
            statTotal.setText(String.valueOf(total));
        }
        if (statActive != null) {
            statActive.setText(String.valueOf(active));
        }
        if (statAdopted != null) {
            statAdopted.setText(String.valueOf(adopted));
        }
        if (emptyView != null) {
            emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            emptyView.setText(getEmptyText());
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(items.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void applyFilter() {
        items.clear();
        for (Map<String, Object> cat : allItems) {
            if (matchesCurrentFilter(cat)) {
                items.add(cat);
            }
        }
        updateSummary();
        adapter.notifyDataSetChanged();
    }

    private boolean matchesCurrentFilter(@NonNull Map<String, Object> cat) {
        String status = normalizedStatus(cat.get("status"));
        if (currentFilterId == R.id.chip_cats_active) {
            return "active".equals(status);
        }
        if (currentFilterId == R.id.chip_cats_adopted) {
            return "adopted".equals(status);
        }
        if (currentFilterId == R.id.chip_cats_abnormal) {
            return "missing".equals(status)
                    || "injured".equals(status)
                    || "disease".equals(status)
                    || "plight".equals(status)
                    || "other".equals(status);
        }
        if (currentFilterId == R.id.chip_cats_deceased) {
            return "deceased".equals(status);
        }
        return true;
    }

    @NonNull
    private String normalizedStatus(@Nullable Object rawStatus) {
        String status = rawStatus != null ? rawStatus.toString() : "";
        if ("活跃".equals(status)) {
            return "active";
        }
        if ("失踪".equals(status)) {
            return "missing";
        }
        if ("收养".equals(status) || "已安置".equals(status)) {
            return "adopted";
        }
        if ("受伤".equals(status)) {
            return "injured";
        }
        if ("疾病".equals(status) || "生病".equals(status)) {
            return "disease";
        }
        if ("困境".equals(status)) {
            return "plight";
        }
        if ("其他".equals(status)) {
            return "other";
        }
        if ("已故".equals(status)) {
            return "deceased";
        }
        if ("injured".equalsIgnoreCase(status)) {
            return "injured";
        }
        if ("disease".equalsIgnoreCase(status) || "sick".equalsIgnoreCase(status)) {
            return "disease";
        }
        if ("plight".equalsIgnoreCase(status) || "distress".equalsIgnoreCase(status)) {
            return "plight";
        }
        if ("deceased".equalsIgnoreCase(status) || "dead".equalsIgnoreCase(status)) {
            return "deceased";
        }
        return status.toLowerCase();
    }

    @NonNull
    private String buildSummary(@NonNull Map<String, Object> cat) {
        List<String> parts = new ArrayList<>();
        String personality = getText(cat.get("personality"));
        String feature = getText(cat.get("feature"));
        Number reportCount = cat.get("reportCount") instanceof Number ? (Number) cat.get("reportCount") : null;

        if (!feature.isEmpty()) {
            parts.add("特征 " + feature);
        }
        if (!personality.isEmpty()) {
            parts.add("性格 " + personality);
        }
        if (reportCount != null && reportCount.intValue() > 0) {
            parts.add("上报 " + reportCount.intValue() + " 次");
        }

        if (parts.size() > 2) {
            parts = new ArrayList<>(parts.subList(0, 2));
        }

        if (parts.isEmpty()) {
            return "查看档案与历史记录";
        }

        return joinParts(parts);
    }

    @NonNull
    private String joinParts(@NonNull List<String> parts) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append(" · ");
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }

    @NonNull
    private String getText(@Nullable Object value) {
        return value != null ? value.toString().trim() : "";
    }

    @NonNull
    private String getEmptyText() {
        if (currentFilterId == R.id.chip_cats_active) {
            return "当前没有活跃中的猫咪档案";
        }
        if (currentFilterId == R.id.chip_cats_adopted) {
            return "当前没有收养状态的猫咪档案";
        }
        if (currentFilterId == R.id.chip_cats_abnormal) {
            return "当前没有异常状态的猫咪档案";
        }
        if (currentFilterId == R.id.chip_cats_deceased) {
            return "当前没有已故状态的猫咪档案";
        }
        return "还没有猫咪档案，稍后再来看看吧";
    }

    private String getStatusText(String status) {
        if (status == null || status.isEmpty()) {
            return "待完善";
        }
        switch (status) {
            case "active":
                return "活跃";
            case "missing":
                return "失踪";
            case "adopted":
                return "收养";
            case "injured":
                return "受伤";
            case "disease":
                return "疾病";
            case "plight":
                return "困境";
            case "other":
                return "其他";
            case "deceased":
                return "已故";
            default:
                return status;
        }
    }

    private void applyStatusStyle(@NonNull TextView statusView, @Nullable String status) {
        if ("adopted".equalsIgnoreCase(status) || "收养".equals(status) || "已安置".equals(status)) {
            statusView.setBackgroundResource(R.drawable.bg_chip_success);
            statusView.setTextColor(ContextCompat.getColor(requireContext(), R.color.success));
        } else if ("missing".equalsIgnoreCase(status) || "失踪".equals(status)) {
            statusView.setBackgroundResource(R.drawable.bg_chip_info);
            statusView.setTextColor(ContextCompat.getColor(requireContext(), R.color.info));
        } else {
            statusView.setBackgroundResource(R.drawable.bg_status_chip);
            statusView.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary_variant));
        }
    }
}
