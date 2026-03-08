package com.ourcat.app.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.ourcat.app.R;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 选择猫咪档案弹窗：用于论坛/广场发帖时引用猫咪。支持搜索、状态筛选、按距离就近排序。
 */
public class SelectCatDialogFragment extends BottomSheetDialogFragment {

    public interface OnCatSelectedListener {
        void onCatSelected(Map<String, Object> cat);
    }

    private static final int PAGE_SIZE = 20;
    private static final long SEARCH_DEBOUNCE_MS = 400;

    private TextInputEditText etSearch;
    private ChipGroup filterGroup;
    private Chip chipSortNearby;
    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView emptyView;

    private final List<Map<String, Object>> items = new ArrayList<>();
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;
    private OnCatSelectedListener listener;
    private Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int currentPage = 0;
    private boolean hasMore = true;
    private boolean loading = false;

    private String currentKeyword = "";
    private String currentStatus = "";
    private String currentSortBy = null;
    private Double currentLat = null;
    private Double currentLng = null;

    private AMapLocationClient locationClient;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                Boolean fine = result.get(android.Manifest.permission.ACCESS_FINE_LOCATION);
                Boolean coarse = result.get(android.Manifest.permission.ACCESS_COARSE_LOCATION);
                if (Boolean.TRUE.equals(fine) || Boolean.TRUE.equals(coarse)) {
                    startLocationForSort();
                } else {
                    Toast.makeText(requireContext(), "需要定位权限才能按距离排序", Toast.LENGTH_SHORT).show();
                    chipSortNearby.setChecked(false);
                    currentSortBy = null;
                    loadCats();
                }
            });

    public void setOnCatSelectedListener(OnCatSelectedListener listener) {
        this.listener = listener;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_select_cat, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        etSearch = view.findViewById(R.id.et_search);
        filterGroup = view.findViewById(R.id.select_cat_filter_group);
        chipSortNearby = view.findViewById(R.id.chip_sort_nearby);
        recycler = view.findViewById(R.id.select_cat_recycler);
        progress = view.findViewById(R.id.select_cat_progress);
        emptyView = view.findViewById(R.id.select_cat_empty);

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_select_cat, parent, false);
                return new RecyclerView.ViewHolder(v) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> cat = items.get(position);
                View itemView = holder.itemView;
                TextView name = itemView.findViewById(R.id.item_select_cat_name);
                TextView info = itemView.findViewById(R.id.item_select_cat_info);
                com.google.android.material.imageview.ShapeableImageView img = itemView
                        .findViewById(R.id.item_select_cat_image);

                String n = cat.get("name") != null ? cat.get("name").toString() : "";
                name.setText(n.isEmpty() ? "未命名" : n);

                StringBuilder sb = new StringBuilder();
                if (cat.get("color") != null && !cat.get("color").toString().isEmpty()) {
                    sb.append(cat.get("color"));
                }
                Object rc = cat.get("reportCount");
                if (rc != null) {
                    if (sb.length() > 0)
                        sb.append(" · ");
                    sb.append(rc).append("次上报");
                }
                Object dist = cat.get("distanceKm");
                if (dist != null) {
                    if (sb.length() > 0)
                        sb.append(" · ");
                    double km = dist instanceof Number ? ((Number) dist).doubleValue() : 0;
                    sb.append(String.format("%.1fkm", km));
                }
                info.setText(sb.toString());
                info.setVisibility(sb.length() > 0 ? View.VISIBLE : View.GONE);

                String url = cat.get("primaryImageUrl") != null ? cat.get("primaryImageUrl").toString() : null;
                if (url != null && !url.isEmpty()) {
                    Glide.with(itemView.getContext()).load(url).centerCrop().into(img);
                } else {
                    img.setImageResource(R.drawable.ic_default_avatar);
                }

                itemView.setOnClickListener(v -> {
                    if (listener != null)
                        listener.onCatSelected(cat);
                    dismiss();
                });
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        };
        recycler.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (pendingSearch != null)
                    debounceHandler.removeCallbacks(pendingSearch);
                pendingSearch = () -> {
                    currentKeyword = s != null ? s.toString().trim() : "";
                    currentPage = 0;
                    hasMore = true;
                    loadCats();
                };
                debounceHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MS);
            }
        });

        filterGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            int id = checkedIds.isEmpty() ? R.id.chip_status_all : checkedIds.get(0);
            if (id == R.id.chip_sort_nearby) {
                currentStatus = "";
                currentSortBy = "nearby";
                currentLat = null;
                currentLng = null;
                requestLocationThenLoad();
            } else {
                currentStatus = ""; // 默认为全部
                currentSortBy = null;
                currentLat = null;
                currentLng = null;
                currentPage = 0;
                hasMore = true;
                loadCats();
            }
        });

        loadCats();
    }

    private void requestLocationThenLoad() {
        if (getContext() == null)
            return;
        locationPermissionLauncher.launch(new String[] {
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private void startLocationForSort() {
        try {
            if (locationClient == null) {
                AMapLocationClient.updatePrivacyShow(requireContext(), true, true);
                AMapLocationClient.updatePrivacyAgree(requireContext(), true);
                locationClient = new AMapLocationClient(requireContext().getApplicationContext());
                AMapLocationClientOption opt = new AMapLocationClientOption();
                opt.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
                opt.setOnceLocation(true);
                opt.setNeedAddress(false);
                locationClient.setLocationOption(opt);
                locationClient.setLocationListener((AMapLocationListener) location -> {
                    if (location != null && location.getErrorCode() == 0) {
                        currentLat = location.getLatitude();
                        currentLng = location.getLongitude();
                        currentPage = 0;
                        hasMore = true;
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(this::loadCats);
                        }
                    } else {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(requireContext(), "定位失败，请检查权限或稍后重试", Toast.LENGTH_SHORT).show();
                                chipSortNearby.setChecked(false);
                                currentSortBy = null;
                                loadCats();
                            });
                        }
                    }
                });
            }
            Toast.makeText(requireContext(), "正在获取位置…", Toast.LENGTH_SHORT).show();
            locationClient.startLocation();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "定位失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            chipSortNearby.setChecked(false);
            currentSortBy = null;
            loadCats();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (debounceHandler != null && pendingSearch != null) {
            debounceHandler.removeCallbacks(pendingSearch);
        }
        if (locationClient != null) {
            locationClient.onDestroy();
            locationClient = null;
        }
    }

    @SuppressWarnings("unchecked")
    private void loadCats() {
        if (loading)
            return;
        loading = true;
        if (currentPage == 0) {
            progress.setVisibility(View.VISIBLE);
            emptyView.setVisibility(View.GONE);
            items.clear();
            adapter.notifyDataSetChanged();
        }

        String keyword = currentKeyword.isEmpty() ? null : currentKeyword;
        String status = currentStatus.isEmpty() ? null : currentStatus;
        Double lat = currentSortBy != null ? currentLat : null;
        Double lng = currentSortBy != null ? currentLng : null;
        String sortBy = currentSortBy;

        RetrofitHelper.get().create(com.ourcat.app.repositories.CatApi.class)
                .listCats(currentPage, PAGE_SIZE, keyword, status, lat, lng, sortBy)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        loading = false;
                        progress.setVisibility(View.GONE);
                        if (!response.isSuccessful() || response.body() == null) {
                            if (currentPage == 0)
                                emptyView.setVisibility(View.VISIBLE);
                            return;
                        }
                        Map<String, Object> body = response.body();
                        Object content = body.get("content");
                        if (content instanceof List) {
                            List<Map<String, Object>> list = (List<Map<String, Object>>) content;
                            int start = items.size();
                            items.addAll(list);
                            adapter.notifyItemRangeInserted(start, list.size());
                            hasMore = list.size() >= PAGE_SIZE;
                        }
                        if (currentPage == 0 && items.isEmpty()) {
                            emptyView.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        loading = false;
                        progress.setVisibility(View.GONE);
                        if (currentPage == 0)
                            emptyView.setVisibility(View.VISIBLE);
                        Toast.makeText(requireContext(), "加载失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
