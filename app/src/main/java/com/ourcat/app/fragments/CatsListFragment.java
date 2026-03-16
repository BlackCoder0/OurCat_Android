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
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
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
    private final List<Map<String, Object>> items = new ArrayList<>();
    private RecyclerView.Adapter<RecyclerView.ViewHolder> adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_cats_list, container, false);

        recyclerView = view.findViewById(R.id.cats_recycler);
        refreshLayout = view.findViewById(R.id.cats_swipe);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            class ViewHolder extends RecyclerView.ViewHolder {
                MaterialCardView container;
                ImageView avatar;
                TextView name, color, status;

                ViewHolder(View itemView) {
                    super(itemView);
                    container = itemView.findViewById(R.id.item_cat_container);
                    avatar = itemView.findViewById(R.id.item_cat_avatar);
                    name = itemView.findViewById(R.id.item_cat_name);
                    color = itemView.findViewById(R.id.item_cat_color);
                    status = itemView.findViewById(R.id.item_cat_status);
                }
            }

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view = inflater.inflate(R.layout.item_cat_list, parent, false);
                return new ViewHolder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                ViewHolder h = (ViewHolder) holder;
                Map<String, Object> cat = items.get(position);

                String catName = cat.get("name") != null ? cat.get("name").toString() : "未命名";
                String catColor = cat.get("color") != null ? cat.get("color").toString() : "";
                String catStatus = cat.get("status") != null ? cat.get("status").toString() : "";
                String avatarUrl = cat.get("primaryImageUrl") != null ? cat.get("primaryImageUrl").toString() : null;

                h.name.setText(catName);
                h.color.setText(catColor);
                h.status.setText(catStatus);

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

                long catId = cat.get("id") != null ? ((Number) cat.get("id")).longValue() : 0;
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

        refreshLayout.setOnRefreshListener(this::loadCats);
        loadCats();

        return view;
    }

    private void loadCats() {
        refreshLayout.setRefreshing(true);
        RetrofitHelper.get().create(CatApi.class).listCats(0, 50, null, null, null, null, null)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        refreshLayout.setRefreshing(false);
                        if (!isAdded()) return;
                        items.clear();
                        if (response.isSuccessful() && response.body() != null) {
                            Object content = response.body().get("content");
                            if (content instanceof List) {
                                items.addAll((List<Map<String, Object>>) content);
                            }
                        }
                        adapter.notifyDataSetChanged();
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
}
