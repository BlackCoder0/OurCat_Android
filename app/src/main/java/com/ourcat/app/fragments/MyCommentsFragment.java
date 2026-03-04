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
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;
import com.ourcat.app.adapters.MyCommentAdapter;
import com.ourcat.app.repositories.ForumApi;
import com.ourcat.app.utils.RetrofitHelper;

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
        View btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        }
        // ((TextView) view.findViewById(R.id.tv_page_title)).setText("我的评论");

        RecyclerView rv = view.findViewById(R.id.rv_list);
        tvEmpty = view.findViewById(R.id.tv_empty);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new MyCommentAdapter();
        adapter.setOnItemClickListener(postId -> {
            Bundle args = new Bundle();
            args.putLong("postId", postId);
            NavController nav = Navigation.findNavController(requireView());
            nav.navigate(R.id.post_detail, args);
        });
        rv.setAdapter(adapter);

        loadMyComments();
        return view;
    }

    private void loadMyComments() {
        RetrofitHelper.get().create(ForumApi.class).myComments(0, 50)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Object content = response.body().get("content");
                            if (content instanceof List) {
                                List<Map<String, Object>> list = new ArrayList<>();
                                for (Object o : (List<?>) content) {
                                    if (o instanceof Map)
                                        list.add((Map<String, Object>) o);
                                }
                                adapter.setItems(list);
                                tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
