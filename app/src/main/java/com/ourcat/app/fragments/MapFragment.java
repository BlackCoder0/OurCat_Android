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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ourcat.app.R;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MapFragment extends Fragment {

    private RecyclerView locationsList;
    private TextView placeholder;
    private final List<Map<String, Object>> locations = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_map, container, false);
        placeholder = view.findViewById(R.id.map_placeholder);
        locationsList = view.findViewById(R.id.map_locations_list);
        locationsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        locationsList.setAdapter(new androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public androidx.recyclerview.widget.RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
                return new androidx.recyclerview.widget.RecyclerView.ViewHolder(v) {};
            }
            @Override
            public void onBindViewHolder(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> item = locations.get(position);
                TextView t1 = holder.itemView.findViewById(android.R.id.text1);
                TextView t2 = holder.itemView.findViewById(android.R.id.text2);
                Object lat = item.get("lat");
                Object lng = item.get("lng");
                Object desc = item.get("description");
                t1.setText("位置: " + lat + ", " + lng);
                t2.setText(desc != null ? desc.toString() : "");
            }
            @Override
            public int getItemCount() { return locations.size(); }
        });

        view.findViewById(R.id.map_fab_report).setOnClickListener(v -> {
            ReportCatDialogFragment dialog = new ReportCatDialogFragment();
            dialog.setOnReportSuccess(this::loadLocations);
            dialog.show(getChildFragmentManager(), "report");
        });
        loadLocations();
        return view;
    }

    private void loadLocations() {
        RetrofitHelper.get().create(com.ourcat.app.repositories.CatApi.class).getLocations()
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            locations.clear();
                            locations.addAll(response.body());
                            locationsList.getAdapter().notifyDataSetChanged();
                            if (locations.isEmpty()) {
                                placeholder.setVisibility(View.VISIBLE);
                                locationsList.setVisibility(View.GONE);
                            } else {
                                placeholder.setVisibility(View.GONE);
                                locationsList.setVisibility(View.VISIBLE);
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
