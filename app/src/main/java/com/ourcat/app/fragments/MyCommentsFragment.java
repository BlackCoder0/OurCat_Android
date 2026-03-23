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
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;
import com.ourcat.app.adapters.MyCommentAdapter;
import com.ourcat.app.repositories.ForumApi;
import com.ourcat.app.repositories.SquareApi;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyCommentsFragment extends Fragment {

    private MyCommentAdapter adapter;
    private TextView tvEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_posts, container, false);
        // ((TextView) view.findViewById(R.id.tv_page_title)).setText("我的评论");

        RecyclerView rv = view.findViewById(R.id.rv_list);
        tvEmpty = view.findViewById(R.id.tv_empty);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MyCommentAdapter();
        adapter.setOnItemClickListener(item -> {
            String source = item.get("source") != null ? item.get("source").toString() : "";
            NavController nav = Navigation.findNavController(requireView());
            Bundle args = new Bundle();
            if ("square".equals(source)) {
                long squarePostId = item.get("squarePostId") instanceof Number ? ((Number) item.get("squarePostId")).longValue() : 0;
                if (squarePostId > 0) {
                    args.putLong("squarePostId", squarePostId);
                    nav.navigate(R.id.square_detail, args);
                }
            } else {
                long postId = item.get("postId") instanceof Number ? ((Number) item.get("postId")).longValue() : 0;
                if (postId > 0) {
                    args.putLong("postId", postId);
                    nav.navigate(R.id.post_detail, args);
                }
            }
        });
        rv.setAdapter(adapter);

        loadMyComments();
        return view;
    }

    private void loadMyComments() {
        List<Map<String, Object>> merged = new ArrayList<>();
        boolean[] done = new boolean[] { false, false };

        Runnable tryFinish = () -> {
            if (!done[0] || !done[1]) {
                return;
            }
            merged.sort((a, b) -> Long.compare(parseEpochMs(b), parseEpochMs(a)));
            adapter.setItems(merged);
            tvEmpty.setVisibility(merged.isEmpty() ? View.VISIBLE : View.GONE);
        };

        RetrofitHelper.get().create(ForumApi.class).myComments(0, 50).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object content = response.body().get("content");
                    if (content instanceof List) {
                        for (Object o : (List<?>) content) {
                            if (o instanceof Map) {
                                Map<String, Object> item = (Map<String, Object>) o;
                                item.put("source", "forum");
                                merged.add(item);
                            }
                        }
                    }
                }
                done[0] = true;
                if (isAdded()) {
                    tryFinish.run();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                done[0] = true;
                if (isAdded()) {
                    Toast.makeText(requireContext(), "论坛评论加载失败", Toast.LENGTH_SHORT).show();
                    tryFinish.run();
                }
            }
        });

        RetrofitHelper.get().create(SquareApi.class).myComments(0, 50).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object content = response.body().get("content");
                    if (content instanceof List) {
                        for (Object o : (List<?>) content) {
                            if (o instanceof Map) {
                                merged.add((Map<String, Object>) o);
                            }
                        }
                    }
                }
                done[1] = true;
                if (isAdded()) {
                    tryFinish.run();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                done[1] = true;
                if (isAdded()) {
                    Toast.makeText(requireContext(), "广场评论加载失败", Toast.LENGTH_SHORT).show();
                    tryFinish.run();
                }
            }
        });
    }

    private long parseEpochMs(Map<String, Object> item) {
        Object createdAt = item.get("createdAt");
        if (createdAt == null) {
            return 0L;
        }
        return TimeUtils.parseToMillis(createdAt.toString());
    }
}
