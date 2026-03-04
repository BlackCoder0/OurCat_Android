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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.ourcat.app.R;
import com.ourcat.app.repositories.CatApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 用户异议修改对话框
 * 允许用户将上报记录重新分配到其他猫咪或标记为新猫咪
 */
public class ReassignDialogFragment extends BottomSheetDialogFragment {

    private static final String ARG_REPORT_ID = "reportId";
    private static final String ARG_LAT = "lat";
    private static final String ARG_LNG = "lng";
    private static final String ARG_CURRENT_CAT_ID = "currentCatId";

    private long reportId;
    private double lat, lng;
    private long currentCatId;
    private final List<Map<String, Object>> nearbyCats = new ArrayList<>();
    private NearbyCatAdapter adapter;
    private Runnable onReassignSuccess;

    public static ReassignDialogFragment newInstance(long reportId, double lat, double lng, long currentCatId) {
        ReassignDialogFragment f = new ReassignDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_REPORT_ID, reportId);
        args.putDouble(ARG_LAT, lat);
        args.putDouble(ARG_LNG, lng);
        args.putLong(ARG_CURRENT_CAT_ID, currentCatId);
        f.setArguments(args);
        return f;
    }

    public void setOnReassignSuccessListener(Runnable listener) {
        this.onReassignSuccess = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            reportId = getArguments().getLong(ARG_REPORT_ID);
            lat = getArguments().getDouble(ARG_LAT);
            lng = getArguments().getDouble(ARG_LNG);
            currentCatId = getArguments().getLong(ARG_CURRENT_CAT_ID);
        }
    }

    @Override
    public int getTheme() {
        return R.style.ThemeOverlay_App_BottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_reassign, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.nearby_cats_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new NearbyCatAdapter();
        recyclerView.setAdapter(adapter);

        view.findViewById(R.id.btn_mark_new_cat).setOnClickListener(v -> {
            reassignToNewCat();
        });

        view.findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());

        loadNearbyCats();
    }

    private void loadNearbyCats() {
        RetrofitHelper.get().create(CatApi.class)
                .getNearbyCats(lat, lng, 15.0)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            nearbyCats.clear();
                            nearbyCats.addAll(response.body());
                            adapter.notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "加载附近猫咪失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void reassignToNewCat() {
        Map<String, Object> body = new HashMap<>();
        body.put("isNewCat", true);

        RetrofitHelper.get().create(CatApi.class)
                .reassignReport(reportId, body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(), "已创建新猫咪档案", Toast.LENGTH_SHORT).show();
                            if (onReassignSuccess != null) onReassignSuccess.run();
                            dismiss();
                        } else {
                            Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
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

    private void reassignToCat(long newCatId) {
        if (newCatId == currentCatId) {
            Toast.makeText(requireContext(), "已经属于这只猫咪", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("newCatId", newCatId);
        body.put("isNewCat", false);

        RetrofitHelper.get().create(CatApi.class)
                .reassignReport(reportId, body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(), "修改成功", Toast.LENGTH_SHORT).show();
                            if (onReassignSuccess != null) onReassignSuccess.run();
                            dismiss();
                        } else {
                            Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
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

    private class NearbyCatAdapter extends RecyclerView.Adapter<NearbyCatAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            ImageView image, indicator;
            TextView name, info;

            VH(View v) {
                super(v);
                image = v.findViewById(R.id.cat_image);
                name = v.findViewById(R.id.cat_name);
                info = v.findViewById(R.id.cat_info);
                indicator = v.findViewById(R.id.current_indicator);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_nearby_cat, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Map<String, Object> cat = nearbyCats.get(position);

            Number id = (Number) cat.get("id");
            long catId = id != null ? id.longValue() : 0;

            String name = (String) cat.get("name");
            String color = (String) cat.get("color");
            Number reportCount = (Number) cat.get("reportCount");
            String imageUrl = (String) cat.get("primaryImageUrl");

            h.name.setText(name != null && !name.isEmpty() ? name : 
                    (color != null && !color.isEmpty() ? color + "猫" : "未命名"));
            
            String infoText = (color != null ? color : "未知颜色") + " · " + 
                    (reportCount != null ? reportCount.intValue() : 0) + "次上报";
            h.info.setText(infoText);

            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(h.image).load(imageUrl).circleCrop().into(h.image);
            } else {
                h.image.setImageResource(R.drawable.ic_default_avatar);
            }

            h.indicator.setVisibility(catId == currentCatId ? View.VISIBLE : View.GONE);

            h.itemView.setOnClickListener(v -> reassignToCat(catId));
        }

        @Override
        public int getItemCount() {
            return nearbyCats.size();
        }
    }
}
