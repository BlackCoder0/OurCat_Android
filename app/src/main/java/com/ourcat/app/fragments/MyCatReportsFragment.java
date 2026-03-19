package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.ourcat.app.R;
import com.ourcat.app.activities.MainActivity;
import com.ourcat.app.repositories.CatApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyCatReportsFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private final List<Map<String, Object>> items = new ArrayList<>();
    private ReportAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_cat_reports, container, false);

        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        recyclerView = view.findViewById(R.id.recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new ReportAdapter();
        recyclerView.setAdapter(adapter);

        swipeRefresh.setOnRefreshListener(this::loadData);
        loadData();
        return view;
    }

    private void loadData() {
        if (!isAdded())
            return;
        swipeRefresh.setRefreshing(true);
        RetrofitHelper.get().create(CatApi.class).getMyReports()
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                            Response<List<Map<String, Object>>> response) {
                        if (!isAdded())
                            return;
                        swipeRefresh.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            items.clear();
                            items.addAll(response.body());
                            adapter.notifyDataSetChanged();
                            if (items.isEmpty()) {
                                Toast.makeText(requireContext(), "暂无上报记录", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            String msg = "加载失败";
                            if (response.code() == 401) {
                                msg = "请先登录";
                            } else if (response.code() == 404) {
                                msg = "接口不存在，请更新后端";
                            } else {
                                msg = "服务器错误: " + response.code();
                            }
                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (isAdded()) {
                            swipeRefresh.setRefreshing(false);
                            Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void deleteItem(int position) {
        if (position < 0 || position >= items.size())
            return;
        Map<String, Object> item = items.get(position);
        Object idObj = item.get("id");
        if (idObj == null)
            return;
        long id = ((Number) idObj).longValue();

        new AlertDialog.Builder(requireContext())
                .setTitle("确认删除")
                .setMessage("确定要删除这条上报记录吗？")
                .setPositiveButton("删除", (d, w) -> {
                    RetrofitHelper.get().create(CatApi.class).deleteReport(id)
                            .enqueue(new Callback<Map<String, Object>>() {
                                @Override
                                public void onResponse(Call<Map<String, Object>> call,
                                        Response<Map<String, Object>> response) {
                                    if (!isAdded())
                                        return;
                                    if (response.isSuccessful()) {
                                        items.remove(position);
                                        adapter.notifyItemRemoved(position);
                                        Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show();
                                    }
                                }

                                @Override
                                public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                                    if (isAdded())
                                        Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            ImageView image;
            TextView color, feature, location, time;
            ImageButton delete;

            VH(View v) {
                super(v);
                image = v.findViewById(R.id.item_image);
                color = v.findViewById(R.id.item_color);
                feature = v.findViewById(R.id.item_feature);
                location = v.findViewById(R.id.item_location);
                time = v.findViewById(R.id.item_time);
                delete = v.findViewById(R.id.item_delete);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_cat_report, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            Map<String, Object> item = items.get(position);

            String colorStr = str(item.get("color"));
            h.color.setText(colorStr.isEmpty() ? "猫咪" : colorStr + "猫");

            String featureStr = str(item.get("feature"));
            String personalityStr = str(item.get("personality"));
            String combined = featureStr;
            if (!personalityStr.isEmpty()) {
                combined = combined.isEmpty() ? personalityStr : combined + " · " + personalityStr;
            }
            h.feature.setText(combined.isEmpty() ? "暂无描述" : combined);

            Object latObj = item.get("lat");
            Object lngObj = item.get("lng");
            if (latObj != null && lngObj != null) {
                h.location.setText(String.format("📍 %.4f, %.4f",
                        ((Number) latObj).doubleValue(), ((Number) lngObj).doubleValue()));
            } else {
                h.location.setText("");
            }

            String timeStr = str(item.get("reportTime"));
            h.time.setText(timeStr.isEmpty() ? "" : "🕐 " + timeStr);

            String imageUrl = str(item.get("imageUrl"));
            if (!imageUrl.isEmpty()) {
                Glide.with(h.image).load(imageUrl).centerCrop().into(h.image);
            } else {
                h.image.setImageResource(android.R.color.darker_gray);
            }

            h.delete.setOnClickListener(v -> deleteItem(h.getAdapterPosition()));

            h.itemView.setOnClickListener(v -> {
                int pos = h.getAdapterPosition();
                if (pos < 0 || pos >= items.size()) {
                    return;
                }
                Map<String, Object> clicked = items.get(pos);
                double lat = doubleVal(clicked.get("lat"));
                double lng = doubleVal(clicked.get("lng"));
                if (lat == 0 && lng == 0) {
                    Toast.makeText(requireContext(), "该上报缺少位置信息", Toast.LENGTH_SHORT).show();
                    return;
                }
                long catId = longVal(clicked.get("catId"));
                if (getActivity() instanceof MainActivity) {
                    MainActivity act = (MainActivity) getActivity();
                    if (catId > 0) {
                        act.showMapAtCatLocationNear(catId, lat, lng);
                    } else {
                        act.showMapAtLocation(lat, lng);
                    }
                } else {
                    Toast.makeText(requireContext(), String.format("📍 %.5f, %.5f", lat, lng), Toast.LENGTH_SHORT)
                            .show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private String str(Object o) {
            return o != null ? o.toString() : "";
        }

        private long longVal(Object v) {
            if (v instanceof Number) {
                return ((Number) v).longValue();
            }
            try {
                return v != null ? Long.parseLong(v.toString()) : 0L;
            } catch (Exception e) {
                return 0L;
            }
        }

        private double doubleVal(Object v) {
            if (v instanceof Number) {
                return ((Number) v).doubleValue();
            }
            try {
                return v != null ? Double.parseDouble(v.toString()) : 0.0;
            } catch (Exception e) {
                return 0.0;
            }
        }
    }
}
