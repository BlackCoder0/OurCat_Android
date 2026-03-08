package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.ourcat.app.R;
import com.ourcat.app.repositories.CatApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 猫咪档案详情页面
 * 展示猫咪基本信息、照片记录和上报历史
 */
public class CatProfileFragment extends Fragment {

    private static final String ARG_CAT_ID = "catId";

    private long catId;
    private ImageView mainImage;
    private RecyclerView recyclerView;
    private CatProfileAdapter adapter;

    // Data cache
    private Map<String, Object> catData;
    private List<Map<String, Object>> reportHistory = new ArrayList<>();
    private List<String> photos = new ArrayList<>();

    // State cache
    private int userRole = 1; // 1=user, 2=volunteer, 3=admin

    // 当前猫咪信息缓存，用于编辑
    private String currentName, currentColor, currentFeature, currentPersonality, currentStatus, currentAvatarUrl;

    public static CatProfileFragment newInstance(long catId) {
        CatProfileFragment f = new CatProfileFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_CAT_ID, catId);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            catId = getArguments().getLong(ARG_CAT_ID, -1);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_cat_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mainImage = view.findViewById(R.id.cat_main_image);
        recyclerView = view.findViewById(R.id.cat_profile_recycler);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CatProfileAdapter();
        recyclerView.setAdapter(adapter);

        if (catId > 0) {
            loadCatDetail();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        checkUserRole();
    }

    private void checkUserRole() {
        if (!new com.ourcat.app.utils.TokenStore(requireContext()).hasToken()) {
            userRole = 1;
            if (adapter != null)
                adapter.notifyItemChanged(0);
            return;
        }

        com.ourcat.app.repositories.AuthApi authApi = RetrofitHelper.get()
                .create(com.ourcat.app.repositories.AuthApi.class);
        authApi.getProfile().enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (!isAdded())
                    return;
                if (response.isSuccessful() && response.body() != null) {
                    Object roleObj = response.body().get("role");
                    userRole = roleObj != null ? ((Number) roleObj).intValue() : 1;
                    if (adapter != null)
                        adapter.notifyItemChanged(0);
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                // ignore
            }
        });
    }

    private void showEditDialog() {
        EditCatDialogFragment dialog = EditCatDialogFragment.newInstance(
                catId, currentName, currentColor, currentFeature, currentPersonality, currentStatus, currentAvatarUrl);
        dialog.setListener(this::loadCatDetail);
        dialog.show(getChildFragmentManager(), "EditCat");
    }

    private void loadCatDetail() {
        RetrofitHelper.get().create(CatApi.class).getCatDetail(catId)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        if (response.isSuccessful() && response.body() != null) {
                            displayCatInfo(response.body());
                        } else {
                            Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void displayCatInfo(Map<String, Object> data) {
        this.catData = data;

        // Update cache for editing
        currentName = (String) data.get("name");
        currentColor = (String) data.get("color");
        currentFeature = (String) data.get("feature");
        currentPersonality = (String) data.get("personality");
        currentStatus = (String) data.get("status");
        currentAvatarUrl = (String) data.get("primaryImageUrl");

        // Update main image (in CollapsingToolbar)
        if (currentAvatarUrl != null && !currentAvatarUrl.isEmpty()) {
            Glide.with(this).load(currentAvatarUrl).into(mainImage);
        }

        // Update lists
        List<Map<String, Object>> history = (List<Map<String, Object>>) data.get("reportHistory");
        if (history != null) {
            reportHistory.clear();
            reportHistory.addAll(history);

            photos.clear();
            for (Map<String, Object> report : history) {
                String imageUrl = (String) report.get("imageUrl");
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    photos.add(imageUrl);
                }
            }
        }

        adapter.notifyDataSetChanged();
    }

    private String getStatusText(String status) {
        if (status == null)
            return "活跃";
        switch (status) {
            case "active":
                return "活跃";
            case "missing":
                return "失踪";
            case "adopted":
                return "收养";
            default:
                return status;
        }
    }

    private String formatTime(String isoTime) {
        if (isoTime == null || isoTime.isEmpty())
            return "";
        try {
            return isoTime.substring(0, 10) + " " + isoTime.substring(11, 16);
        } catch (Exception e) {
            return isoTime;
        }
    }

    private void openReassignDialog(long reportId, Number lat, Number lng) {
        if (lat == null || lng == null) {
            Toast.makeText(requireContext(), "位置信息缺失", Toast.LENGTH_SHORT).show();
            return;
        }
        ReassignDialogFragment dialog = ReassignDialogFragment.newInstance(
                reportId, lat.doubleValue(), lng.doubleValue(), catId);
        dialog.setOnReassignSuccessListener(this::loadCatDetail);
        dialog.show(getChildFragmentManager(), "reassign");
    }

    // ==================== Adapter Classes ====================

    private class CatProfileAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_ITEM = 1;

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HEADER) {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_cat_profile_header, parent, false);
                return new HeaderViewHolder(v);
            } else {
                View v = LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_report_history, parent, false);
                return new HistoryViewHolder(v);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (holder instanceof HeaderViewHolder) {
                ((HeaderViewHolder) holder).bind();
            } else if (holder instanceof HistoryViewHolder) {
                // position - 1 because header is at 0
                ((HistoryViewHolder) holder).bind(reportHistory.get(position - 1));
            }
        }

        @Override
        public int getItemCount() {
            // Header + History items
            return 1 + reportHistory.size();
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? TYPE_HEADER : TYPE_ITEM;
        }

        // Header ViewHolder
        class HeaderViewHolder extends RecyclerView.ViewHolder {
            TextView catName, catColor, catReportCount, catStatus, catFeature, catPersonality;
            View btnEditCat;
            RecyclerView photoList;
            PhotoAdapter photoAdapter;

            HeaderViewHolder(View v) {
                super(v);
                catName = v.findViewById(R.id.cat_name);
                catColor = v.findViewById(R.id.cat_color);
                catReportCount = v.findViewById(R.id.cat_report_count);
                catStatus = v.findViewById(R.id.cat_status);
                catFeature = v.findViewById(R.id.cat_feature);
                catPersonality = v.findViewById(R.id.cat_personality);
                btnEditCat = v.findViewById(R.id.btn_edit_cat);
                photoList = v.findViewById(R.id.photo_list);

                btnEditCat.setOnClickListener(view -> showEditDialog());

                // Setup nested horizontal recycler view
                photoList.setLayoutManager(
                        new LinearLayoutManager(v.getContext(), LinearLayoutManager.HORIZONTAL, false));
                photoAdapter = new PhotoAdapter();
                photoList.setAdapter(photoAdapter);
            }

            void bind() {
                if (catData == null)
                    return;

                catName.setText(currentName != null && !currentName.isEmpty() ? currentName : "未命名猫咪");
                catColor.setText(currentColor != null && !currentColor.isEmpty() ? currentColor : "未知");

                Number reportCount = (Number) catData.get("reportCount");
                catReportCount.setText(reportCount != null ? String.valueOf(reportCount.intValue()) : "0");

                catStatus.setText(getStatusText(currentStatus));

                catFeature
                        .setText("特征：" + (currentFeature != null && !currentFeature.isEmpty() ? currentFeature : "未知"));
                catPersonality.setText("性格："
                        + (currentPersonality != null && !currentPersonality.isEmpty() ? currentPersonality : "未知"));

                // Role check
                btnEditCat.setVisibility(userRole >= 2 ? View.VISIBLE : View.GONE);

                // Refresh photos
                photoAdapter.notifyDataSetChanged();
            }
        }

        // History ViewHolder
        class HistoryViewHolder extends RecyclerView.ViewHolder {
            ImageView image, btnReassign;
            TextView time, location, desc;

            HistoryViewHolder(View v) {
                super(v);
                image = v.findViewById(R.id.report_image);
                time = v.findViewById(R.id.report_time);
                location = v.findViewById(R.id.report_location);
                desc = v.findViewById(R.id.report_desc);
                btnReassign = v.findViewById(R.id.btn_reassign);
            }

            void bind(Map<String, Object> report) {
                String imageUrl = (String) report.get("imageUrl");
                if (imageUrl != null && !imageUrl.isEmpty()) {
                    Glide.with(image).load(imageUrl).centerCrop().into(image);
                }

                String reportTime = (String) report.get("reportTime");
                time.setText(formatTime(reportTime));

                Number lat = (Number) report.get("lat");
                Number lng = (Number) report.get("lng");
                if (lat != null && lng != null) {
                    location.setText(String.format("%.4f, %.4f", lat.doubleValue(), lng.doubleValue()));
                }

                String description = (String) report.get("description");
                desc.setText(description != null && !description.isEmpty() ? description : "无描述");

                Number reportId = (Number) report.get("id");
                btnReassign.setOnClickListener(v -> {
                    if (reportId != null) {
                        openReassignDialog(reportId.longValue(), lat, lng);
                    }
                });
            }
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            ImageView image;

            VH(View v) {
                super(v);
                image = v.findViewById(R.id.photo_image);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_cat_photo, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Glide.with(h.image).load(photos.get(position)).centerCrop().into(h.image);
        }

        @Override
        public int getItemCount() {
            return photos.size();
        }
    }
}
