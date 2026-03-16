package com.ourcat.app.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RescueGuideFragment extends Fragment {

    private RecyclerView guideList, contactsList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rescue_guide, container, false);
        guideList = view.findViewById(R.id.guide_list);
        contactsList = view.findViewById(R.id.contacts_list);
        guideList.setLayoutManager(new LinearLayoutManager(requireContext()));
        contactsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        loadGuide();
        loadContacts();
        return view;
    }

    private void loadGuide() {
        RetrofitHelper.get().create(RescueApi.class).getGuide().enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    guideList.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        final List<Map<String, Object>> list = response.body();
                        @NonNull
                        @Override
                        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
                            return new RecyclerView.ViewHolder(v) {};
                        }
                        @Override
                        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                            Map<String, Object> m = list.get(position);
                            ((TextView) holder.itemView.findViewById(android.R.id.text1)).setText(str(m, "title"));
                            ((TextView) holder.itemView.findViewById(android.R.id.text2)).setText(str(m, "content"));
                        }
                        @Override
                        public int getItemCount() { return list.size(); }
                    });
                }
            }
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {}
        });
    }

    private void loadContacts() {
        RetrofitHelper.get().create(RescueApi.class).getContacts().enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    List<Map<String, Object>> list = response.body();
                    contactsList.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        @NonNull
                        @Override
                        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
                            return new RecyclerView.ViewHolder(v) {};
                        }
                        @Override
                        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                            Map<String, Object> m = list.get(position);
                            String name = str(m, "name");
                            String phone = str(m, "phone");
                            String desc = str(m, "description");
                            ((TextView) holder.itemView.findViewById(android.R.id.text1)).setText(name + " " + phone);
                            ((TextView) holder.itemView.findViewById(android.R.id.text2)).setText(desc);
                            holder.itemView.setOnClickListener(v -> {
                                Intent i = new Intent(Intent.ACTION_DIAL);
                                i.setData(Uri.parse("tel:" + phone));
                                startActivity(i);
                            });
                        }
                        @Override
                        public int getItemCount() { return list.size(); }
                    });
                }
            }
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {}
        });
    }

    private static String str(Map<String, Object> m, String k) { Object v = m.get(k); return v != null ? v.toString() : ""; }
}
