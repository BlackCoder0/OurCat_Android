package com.ourcat.app.fragments;

import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import androidx.lifecycle.ViewModelProvider;
import com.ourcat.app.models.RescueViewModel;

public class RescueListFragment extends Fragment {

    private RecyclerView recycler;
    private ProgressBar progress;
    private TextView emptyText;
    private final List<Map<String, Object>> items = new ArrayList<>();
    private RescueViewModel viewModel;
    private static final String KEY_LAYOUT_STATE = "rescue_list_layout_state";
    private Parcelable pendingLayoutState;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(RescueViewModel.class);
        View view = inflater.inflate(R.layout.fragment_rescue_list, container, false);
        if (savedInstanceState != null) {
            pendingLayoutState = savedInstanceState.getParcelable(KEY_LAYOUT_STATE);
        }
        if (pendingLayoutState == null) {
            pendingLayoutState = viewModel.getLayoutState();
        }
        recycler = view.findViewById(R.id.rescue_list);
        progress = view.findViewById(R.id.progress);
        emptyText = view.findViewById(R.id.empty_text);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rescue_activity, parent, false);
                return new RecyclerView.ViewHolder(v) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> m = items.get(position);
                View v = holder.itemView;
                ((TextView) v.findViewById(R.id.item_rescue_title)).setText(str(m, "title"));
                ((TextView) v.findViewById(R.id.item_rescue_desc)).setText(str(m, "description"));
                ((TextView) v.findViewById(R.id.item_rescue_status)).setText(str(m, "status"));
                ((TextView) v.findViewById(R.id.item_rescue_time)).setText(str(m, "createdAt"));
                Object id = m.get("id");
                v.setOnClickListener(v2 -> {
                    if (id != null) {
                        Bundle args = new Bundle();
                        args.putLong("activityId", ((Number) id).longValue());
                        Navigation.findNavController(v2).navigate(R.id.rescue_detail, args);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return items.size();
            }
        });

        if (viewModel.getItems().getValue() != null && !viewModel.getItems().getValue().isEmpty()) {
            items.clear();
            items.addAll(viewModel.getItems().getValue());
            recycler.getAdapter().notifyDataSetChanged();
            progress.setVisibility(View.GONE);
            emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
            restoreLayoutStateIfNeeded();
        } else {
            load();
        }

        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm != null) {
                    viewModel.setScrollPosition(lm.findFirstVisibleItemPosition());
                }
            }
        });

        return view;
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        emptyText.setVisibility(View.GONE);
        RetrofitHelper.get().create(RescueApi.class).listActivities(0, 50, null, null)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        progress.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) {
                            Object content = response.body().get("content");
                            items.clear();
                            if (content instanceof List) {
                                for (Object o : (List<?>) content) {
                                    if (o instanceof Map)
                                        items.add((Map<String, Object>) o);
                                }
                            }
                            viewModel.setItems(new ArrayList<>(items));
                            recycler.getAdapter().notifyDataSetChanged();
                            emptyText.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                            restoreLayoutStateIfNeeded();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded()) {
                            progress.setVisibility(View.GONE);
                            emptyText.setVisibility(View.VISIBLE);
                        }
                    }
                });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (recycler != null && recycler.getLayoutManager() != null) {
            outState.putParcelable(KEY_LAYOUT_STATE, recycler.getLayoutManager().onSaveInstanceState());
        }
    }

    @Override
    public void onDestroyView() {
        if (recycler != null && recycler.getLayoutManager() != null) {
            viewModel.setLayoutState(recycler.getLayoutManager().onSaveInstanceState());
        }
        super.onDestroyView();
    }

    private void restoreLayoutStateIfNeeded() {
        if (pendingLayoutState == null || recycler == null || recycler.getLayoutManager() == null) {
            return;
        }
        recycler.getLayoutManager().onRestoreInstanceState(pendingLayoutState);
        pendingLayoutState = null;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null ? v.toString() : "";
    }
}
