package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;
import com.ourcat.app.adapters.CommentAdapter;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PostDetailFragment extends Fragment {

    private long postId;
    private Map<String, Object> postData;
    private long currentUserId;
    private int currentUserRole;
    private TextView titleView, authorView, contentView, likesCount, dislikesCount;
    private CommentAdapter commentAdapter;
    private EditText commentInput;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) postId = getArguments().getLong("postId", 0L);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_post_detail, container, false);
        titleView = view.findViewById(R.id.post_title);
        authorView = view.findViewById(R.id.post_author);
        contentView = view.findViewById(R.id.post_content);
        likesCount = view.findViewById(R.id.post_likes_count);
        dislikesCount = view.findViewById(R.id.post_dislikes_count);
        commentInput = view.findViewById(R.id.comment_input);
        RecyclerView commentsRecycler = view.findViewById(R.id.comments_recycler);
        commentsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        commentAdapter = new CommentAdapter();
        commentsRecycler.setAdapter(commentAdapter);

        view.findViewById(R.id.btn_like).setOnClickListener(v -> like(true));
        view.findViewById(R.id.btn_dislike).setOnClickListener(v -> like(false));
        view.findViewById(R.id.btn_send_comment).setOnClickListener(v -> sendComment());
        view.findViewById(R.id.btn_edit).setOnClickListener(v -> Toast.makeText(requireContext(), "编辑（可后续实现）", Toast.LENGTH_SHORT).show());
        view.findViewById(R.id.btn_delete).setOnClickListener(v -> deletePost());
        view.findViewById(R.id.btn_pin).setOnClickListener(v -> setPinned(true));

        loadProfileThenPost();
        return view;
    }

    private void loadProfileThenPost() {
        RetrofitHelper.get().create(com.ourcat.app.repositories.AuthApi.class).getProfile()
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Object id = response.body().get("id");
                            Object role = response.body().get("role");
                            currentUserId = id instanceof Number ? ((Number) id).longValue() : 0;
                            currentUserRole = role instanceof Number ? ((Number) role).intValue() : 1;
                        }
                        loadPost();
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        loadPost();
                    }
                });
    }

    private void loadPost() {
        RetrofitHelper.get().create(com.ourcat.app.repositories.ForumApi.class).getPost(postId)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            postData = response.body();
                            bindPost();
                            loadComments();
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void bindPost() {
        if (postData == null) return;
        titleView.setText(getStr(postData, "title"));
        authorView.setText("作者: " + getStr(postData, "authorName"));
        contentView.setText(getStr(postData, "content"));
        likesCount.setText(String.valueOf(getInt(postData, "likes")));
        dislikesCount.setText(String.valueOf(getInt(postData, "dislikes")));

        LinearLayout actions = requireView().findViewById(R.id.post_actions);
        long postUserId = getLong(postData, "userId");
        boolean isAuthor = currentUserId == postUserId;
        boolean canManage = currentUserRole >= 2;
        if (isAuthor || canManage) {
            actions.setVisibility(View.VISIBLE);
            requireView().findViewById(R.id.btn_edit).setVisibility(isAuthor ? View.VISIBLE : View.GONE);
            requireView().findViewById(R.id.btn_delete).setVisibility(canManage || isAuthor ? View.VISIBLE : View.GONE);
            requireView().findViewById(R.id.btn_pin).setVisibility(canManage ? View.VISIBLE : View.GONE);
        }
    }

    private void loadComments() {
        RetrofitHelper.get().create(com.ourcat.app.repositories.ForumApi.class).getComments(postId, 0, 50)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            commentAdapter.setItems(response.body());
                        }
                    }
                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {}
                });
    }

    private void like(boolean like) {
        RetrofitHelper.get().create(com.ourcat.app.repositories.ForumApi.class).like(postId, like)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Object likes = response.body().get("likes");
                            Object dislikes = response.body().get("dislikes");
                            if (likes != null) likesCount.setText(String.valueOf(likes));
                            if (dislikes != null) dislikesCount.setText(String.valueOf(dislikes));
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
    }

    private void sendComment() {
        String content = commentInput.getText().toString().trim();
        if (content.isEmpty()) return;
        Map<String, String> body = new HashMap<>();
        body.put("content", content);
        RetrofitHelper.get().create(com.ourcat.app.repositories.ForumApi.class).addComment(postId, body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            commentInput.setText("");
                            loadComments();
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Toast.makeText(requireContext(), "发送失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void deletePost() {
        RetrofitHelper.get().create(com.ourcat.app.repositories.ForumApi.class).deletePost(postId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show();
                            Navigation.findNavController(requireView()).navigateUp();
                        } else {
                            Toast.makeText(requireContext(), "无权限", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(requireContext(), "删除失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setPinned(boolean pinned) {
        RetrofitHelper.get().create(com.ourcat.app.repositories.ForumApi.class).setPinned(postId, pinned)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) Toast.makeText(requireContext(), pinned ? "已置顶" : "已取消置顶", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
    }

    private static String getStr(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o != null ? o.toString() : "";
    }

    private static int getInt(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof Number ? ((Number) o).intValue() : 0;
    }

    private static long getLong(Map<String, Object> m, String key) {
        Object o = m.get(key);
        return o instanceof Number ? ((Number) o).longValue() : 0;
    }
}
