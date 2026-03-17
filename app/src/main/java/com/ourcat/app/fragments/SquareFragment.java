package com.ourcat.app.fragments;

import android.os.Bundle;
import android.os.Parcelable;
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

import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.geocoder.GeocodeSearch;
import com.amap.api.services.geocoder.RegeocodeAddress;
import com.amap.api.services.geocoder.RegeocodeQuery;
import com.amap.api.services.geocoder.RegeocodeResult;
import com.google.android.material.chip.ChipGroup;
import com.ourcat.app.R;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.repositories.SquareApi;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
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
    private RecyclerView.Adapter<SquarePostViewHolder> adapter;
    private static final String KEY_LAYOUT_STATE = "square_layout_state";
    private Parcelable pendingLayoutState;
    private final Map<String, String> reverseGeocodeCache = new HashMap<>();
    private final java.util.Set<String> reverseGeocodeInFlight = new java.util.HashSet<>();

    private static class SquarePostViewHolder extends RecyclerView.ViewHolder {
        final TextView type;
        final TextView status;
        final TextView text;
        final TextView location;
        final TextView time;
        final View container;
        final View refCatRow;
        final android.widget.ImageView refCatAvatar;
        final TextView refCatName;

        SquarePostViewHolder(@NonNull View itemView) {
            super(itemView);
            type = itemView.findViewById(R.id.item_square_type);
            status = itemView.findViewById(R.id.item_square_status);
            text = itemView.findViewById(R.id.item_square_text);
            location = itemView.findViewById(R.id.item_square_location);
            time = itemView.findViewById(R.id.item_square_time);
            container = itemView.findViewById(R.id.item_square_container);
            refCatRow = itemView.findViewById(R.id.item_square_ref_cat);
            refCatAvatar = itemView.findViewById(R.id.item_square_ref_cat_avatar);
            refCatName = itemView.findViewById(R.id.item_square_ref_cat_name);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_square, container, false);
        if (savedInstanceState != null) {
            pendingLayoutState = savedInstanceState.getParcelable(KEY_LAYOUT_STATE);
        }
        recyclerView = view.findViewById(R.id.square_recycler);
        refreshLayout = view.findViewById(R.id.square_swipe);
        sortGroup = view.findViewById(R.id.square_sort_group);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new RecyclerView.Adapter<SquarePostViewHolder>() {
            @NonNull
            @Override
            public SquarePostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_square_post, parent, false);
                return new SquarePostViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull SquarePostViewHolder holder, int position) {
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
                holder.time.setText(TimeUtils.getRelativeTime((String) item.get("createdAt")));

                bindLocationForList(holder, loc);

                Object refCat = item.get("referencedCat");
                Object refCatId = item.get("referencedCatId");
                if (refCat instanceof Map && refCatId instanceof Number) {
                    holder.refCatRow.setVisibility(View.VISIBLE);
                    Map<?, ?> rc = (Map<?, ?>) refCat;
                    String name = rc.get("name") != null ? rc.get("name").toString() : "";
                    holder.refCatName.setText(name.isEmpty() ? "引用: 未命名" : "引用: " + name);
                    String imgUrl = rc.get("primaryImageUrl") != null ? rc.get("primaryImageUrl").toString() : null;
                    if (imgUrl != null && !imgUrl.isEmpty()) {
                        com.bumptech.glide.Glide.with(holder.itemView.getContext()).load(imgUrl).centerCrop()
                                .into(holder.refCatAvatar);
                    } else {
                        holder.refCatAvatar.setImageResource(R.drawable.ic_default_avatar);
                    }
                    holder.refCatRow.setOnClickListener(null);
                    holder.refCatRow.setClickable(false);
                } else {
                    holder.refCatRow.setVisibility(View.GONE);
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
        if (!items.isEmpty()) {
            adapter.notifyDataSetChanged();
            restoreLayoutStateIfNeeded();
        } else {
            loadPosts();
        }
        return view;
    }

    private void bindLocationForList(@NonNull SquarePostViewHolder holder, @Nullable Object loc) {
        String raw = loc != null ? loc.toString() : "";
        String t = raw.trim();
        if (t.isEmpty()) {
            holder.location.setVisibility(View.GONE);
            holder.location.setTag(null);
            return;
        }

        holder.location.setVisibility(View.VISIBLE);
        android.graphics.drawable.Drawable locationIcon = androidx.core.content.ContextCompat
                .getDrawable(holder.itemView.getContext(), R.drawable.ic_map_crosshair);
        if (locationIcon != null) {
            int iconSizePx = (int) (holder.itemView.getResources().getDisplayMetrics().density * 14);
            locationIcon.setBounds(0, 0, iconSizePx, iconSizePx);
            holder.location.setCompoundDrawablesRelative(locationIcon, null, null, null);
        }

        String explicitName = extractNameAfterLatLng(t);
        if (explicitName != null && !explicitName.isEmpty()) {
            holder.location.setTag(null);
            holder.location.setText(explicitName);
            return;
        }

        String coords = extractLatLngPrefix(t);
        if (coords != null && isPureLatLng(t)) {
            holder.location.setTag(coords);
            String cached = reverseGeocodeCache.get(coords);
            if (cached != null && !cached.isEmpty()) {
                holder.location.setText(cached);
                return;
            }
            holder.location.setText("定位中...");
            requestReverseGeocode(coords);
            return;
        }

        holder.location.setTag(null);
        holder.location.setText(t);
    }

    @Nullable
    private String extractLatLngPrefix(@NonNull String raw) {
        String t = raw.trim();
        int space = t.indexOf(' ');
        String head = space > 0 ? t.substring(0, space).trim() : t;
        if (head.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
            return head;
        }
        return null;
    }

    @Nullable
    private String extractNameAfterLatLng(@NonNull String raw) {
        String t = raw.trim();
        int space = t.indexOf(' ');
        if (space <= 0) {
            return null;
        }
        String prefix = t.substring(0, space).trim();
        if (!prefix.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
            return null;
        }
        String rest = t.substring(space + 1).trim();
        return rest.isEmpty() ? null : rest;
    }

    private boolean isPureLatLng(@NonNull String raw) {
        String t = raw.trim();
        return t.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?");
    }

    private void requestReverseGeocode(@NonNull String coords) {
        if (!isAdded()) {
            return;
        }
        if (reverseGeocodeCache.containsKey(coords) || reverseGeocodeInFlight.contains(coords)) {
            return;
        }
        reverseGeocodeInFlight.add(coords);
        try {
            String[] parts = coords.split(",");
            if (parts.length != 2) {
                reverseGeocodeInFlight.remove(coords);
                reverseGeocodeCache.put(coords, "附近");
                updateVisibleLocationViews(coords, "附近");
                return;
            }
            double lat = Double.parseDouble(parts[0].trim());
            double lng = Double.parseDouble(parts[1].trim());

            GeocodeSearch geocodeSearch = new GeocodeSearch(requireContext());
            geocodeSearch.setOnGeocodeSearchListener(new GeocodeSearch.OnGeocodeSearchListener() {
                @Override
                public void onRegeocodeSearched(RegeocodeResult result, int rCode) {
                    if (!isAdded()) {
                        return;
                    }
                    String addressName = "附近";
                    if (rCode == 1000 && result != null && result.getRegeocodeAddress() != null) {
                        RegeocodeAddress address = result.getRegeocodeAddress();
                        String aoi = address.getAois() != null && !address.getAois().isEmpty()
                                && address.getAois().get(0) != null
                                        ? address.getAois().get(0).getAoiName()
                                        : null;
                        String poi = address.getPois() != null && !address.getPois().isEmpty()
                                && address.getPois().get(0) != null
                                        ? address.getPois().get(0).getTitle()
                                        : null;
                        String neighborhood = address.getNeighborhood();
                        String township = address.getTownship();
                        String district = address.getDistrict();
                        String city = address.getCity();
                        String province = address.getProvince();
                        String formatted = address.getFormatAddress();

                        if (aoi != null && !aoi.trim().isEmpty()) {
                            addressName = aoi.trim();
                        } else if (poi != null && !poi.trim().isEmpty()) {
                            addressName = poi.trim();
                        } else if (neighborhood != null && !neighborhood.trim().isEmpty()) {
                            addressName = neighborhood.trim();
                        } else if (township != null && !township.trim().isEmpty()) {
                            addressName = township.trim();
                        } else if (district != null && !district.trim().isEmpty()) {
                            addressName = district.trim();
                        } else if (city != null && !city.trim().isEmpty()) {
                            addressName = city.trim();
                        } else if (province != null && !province.trim().isEmpty()) {
                            addressName = province.trim();
                        } else if (formatted != null && !formatted.trim().isEmpty()) {
                            addressName = formatted.trim();
                        }
                        if (addressName.startsWith("中华人民共和国")) {
                            addressName = addressName.substring("中华人民共和国".length()).trim();
                        }
                        if (addressName.startsWith("中国")) {
                            addressName = addressName.substring("中国".length()).trim();
                        }
                        if (addressName.isEmpty() || "中华人民共和国".equals(addressName) || "中国".equals(addressName)) {
                            addressName = "附近";
                        }
                    }
                    reverseGeocodeInFlight.remove(coords);
                    reverseGeocodeCache.put(coords, addressName);
                    updateVisibleLocationViews(coords, addressName);
                }

                @Override
                public void onGeocodeSearched(com.amap.api.services.geocoder.GeocodeResult result, int rCode) {
                }
            });

            RegeocodeQuery query = new RegeocodeQuery(new LatLonPoint(lat, lng), 200, GeocodeSearch.AMAP);
            geocodeSearch.getFromLocationAsyn(query);
        } catch (Exception e) {
            reverseGeocodeInFlight.remove(coords);
            reverseGeocodeCache.put(coords, "附近");
            updateVisibleLocationViews(coords, "附近");
        }
    }

    private void updateVisibleLocationViews(@NonNull String coords, @NonNull String addressName) {
        if (recyclerView == null) {
            return;
        }
        recyclerView.post(() -> {
            if (recyclerView == null) {
                return;
            }
            for (int i = 0; i < recyclerView.getChildCount(); i++) {
                View child = recyclerView.getChildAt(i);
                if (child == null) {
                    continue;
                }
                TextView tv = child.findViewById(R.id.item_square_location);
                if (tv != null && coords.equals(tv.getTag())) {
                    tv.setText(addressName);
                }
            }
        });
    }

    private String formatLocationForDisplay(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        int space = t.indexOf(' ');
        if (space > 0) {
            String prefix = t.substring(0, space);
            if (prefix.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
                String rest = t.substring(space + 1).trim();
                if (!rest.isEmpty()) {
                    return rest;
                }
                return "附近";
            }
        }
        if (t.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
            return "附近";
        }
        return t;
    }

    private boolean hasCoordinates(String location) {
        if (location == null || location.trim().isEmpty()) {
            return false;
        }
        String t = location.trim();
        // 检查是否包含坐标格式 "lat,lng"
        return t.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?.*");
    }

    private void deletePost(long postId) {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除这条广播吗？")
                .setPositiveButton("删除", (d, w) -> {
                    RetrofitHelper.get().create(SquareApi.class).getPost(postId)
                            .enqueue(new Callback<Map<String, Object>>() {
                                @Override
                                public void onResponse(Call<Map<String, Object>> call,
                                        Response<Map<String, Object>> response) {
                                    long rescueActivityId = 0L;
                                    String type = "";
                                    if (response.isSuccessful() && response.body() != null) {
                                        Map<String, Object> post = response.body();
                                        type = post.get("type") != null ? String.valueOf(post.get("type")) : "";
                                        Object activityIdObj = post.get("rescueActivityId");
                                        if (activityIdObj instanceof Number) {
                                            rescueActivityId = ((Number) activityIdObj).longValue();
                                        }
                                    }

                                    if ("rescue".equals(type) && rescueActivityId > 0) {
                                        Map<String, String> body = new HashMap<>();
                                        body.put("status", "completed");
                                        RetrofitHelper.get().create(RescueApi.class)
                                                .updateActivityStatus(rescueActivityId, body)
                                                .enqueue(new Callback<Map<String, Object>>() {
                                                    @Override
                                                    public void onResponse(Call<Map<String, Object>> call,
                                                            Response<Map<String, Object>> response) {
                                                        performDeletePost(postId);
                                                    }

                                                    @Override
                                                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                                        performDeletePost(postId);
                                                    }
                                                });
                                        return;
                                    }

                                    performDeletePost(postId);
                                }

                                @Override
                                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                    performDeletePost(postId);
                                }
                            });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void performDeletePost(long postId) {
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
                                restoreLayoutStateIfNeeded();
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (recyclerView != null && recyclerView.getLayoutManager() != null) {
            outState.putParcelable(KEY_LAYOUT_STATE, recyclerView.getLayoutManager().onSaveInstanceState());
        }
    }

    private void restoreLayoutStateIfNeeded() {
        if (pendingLayoutState == null || recyclerView == null || recyclerView.getLayoutManager() == null) {
            return;
        }
        recyclerView.post(() -> {
            if (recyclerView.getLayoutManager() != null) {
                recyclerView.getLayoutManager().onRestoreInstanceState(pendingLayoutState);
                pendingLayoutState = null;
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        reverseGeocodeCache.clear();
        reverseGeocodeInFlight.clear();
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
        }
        recyclerView = null;
        refreshLayout = null;
        sortGroup = null;
        adapter = null;
    }
}
