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
import androidx.viewpager2.widget.ViewPager2;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ourcat.app.R;
import com.ourcat.app.adapters.CommentAdapter;
import com.ourcat.app.adapters.ImagePagerAdapter;
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
    private TextView titleView, authorView, contentView, likesCount, dislikesCount, imageIndicator;
    private ViewPager2 imagePager;
    private ImagePagerAdapter imagePagerAdapter;
    private CommentAdapter commentAdapter;
    private EditText commentInput;
    private Button btnLike, btnDislike;
    private int userVote = 0; // 1=liked, -1=disliked, 0=none

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
        imagePager = view.findViewById(R.id.post_image_pager);
        imageIndicator = view.findViewById(R.id.tv_image_indicator);
        btnLike = view.findViewById(R.id.btn_like);
        btnDislike = view.findViewById(R.id.btn_dislike);

        imagePagerAdapter = new ImagePagerAdapter();
        imagePager.setAdapter(imagePagerAdapter);
        imagePager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                int total = imagePagerAdapter.getItemCount();
                if (total > 1) {
                    imageIndicator.setText((position + 1) + " / " + total);
                }
            }
        });

        RecyclerView commentsRecycler = view.findViewById(R.id.comments_recycler);
        commentsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        commentAdapter = new CommentAdapter();
        commentsRecycler.setAdapter(commentAdapter);

        view.findViewById(R.id.btn_like).setOnClickListener(v -> like(true));
        view.findViewById(R.id.btn_dislike).setOnClickListener(v -> like(false));
        view.findViewById(R.id.btn_send_comment).setOnClickListener(v -> sendComment());
        view.findViewById(R.id.btn_edit).setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putInt("editPostId", (int) postId);
            Navigation.findNavController(requireView()).navigate(R.id.create_edit_post, args);
        });
        view.findViewById(R.id.btn_delete).setOnClickListener(v -> deletePost());
        view.findViewById(R.id.btn_pin).setOnClickListener(v -> togglePin());

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
        if (postData == null || getView() == null) return;
        titleView.setText(getStr(postData, "title"));
        authorView.setText("作者: " + getStr(postData, "authorName"));
        contentView.setText(getStr(postData, "content"));
        likesCount.setText(String.valueOf(getInt(postData, "likes")));
        dislikesCount.setText(String.valueOf(getInt(postData, "dislikes")));

        Object imgs = postData.get("images");
        if (imgs != null) {
            try {
                List<String> urls = new Gson().fromJson(imgs.toString(),
                        new TypeToken<List<String>>() {}.getType());
                if (urls != null && !urls.isEmpty()) {
                    imagePager.setVisibility(View.VISIBLE);
                    imagePagerAdapter.setUrls(urls);
                    if (urls.size() > 1) {
                        imageIndicator.setVisibility(View.VISIBLE);
                        imageIndicator.setText("1 / " + urls.size());
                    }
                }
            } catch (Exception ignored) {}
        }

        LinearLayout actions = requireView().findViewById(R.id.post_actions);
        long postUserId = getLong(postData, "userId");
        boolean isAuthor = currentUserId != 0 && currentUserId == postUserId;
        boolean canManage = currentUserRole >= 2;

        actions.setVisibility(View.VISIBLE);
        requireView().findViewById(R.id.btn_edit).setVisibility(isAuthor ? View.VISIBLE : View.GONE);
        requireView().findViewById(R.id.btn_delete).setVisibility((canManage || isAuthor) ? View.VISIBLE : View.GONE);
        requireView().findViewById(R.id.btn_pin).setVisibility(canManage ? View.VISIBLE : View.GONE);

        if (!isAuthor && !canManage) {
            actions.setVisibility(View.GONE);
        }

        boolean pinned = getBool(postData, "pinned");
        requireView().findViewById(R.id.btn_pin).setTag(pinned);
        ((Button) requireView().findViewById(R.id.btn_pin)).setText(pinned ? "取消置顶" : "置顶");

        Object uv = postData.get("userVote");
        userVote = uv instanceof Number ? ((Number) uv).intValue() : 0;
        updateLikeDislikeButtons();
    }

    private void updateLikeDislikeButtons() {
        if (btnLike == null || btnDislike == null) return;
        btnLike.setText(userVote == 1 ? "👍 已赞" : "👍 赞");
        btnDislike.setText(userVote == -1 ? "👎 已踩" : "👎 踩");
        btnLike.setAlpha(userVote == 1 ? 1f : 0.5f);
        btnDislike.setAlpha(userVote == -1 ? 1f : 0.5f);
    }

    private static boolean getBool(Map<String, Object> m, String key) {
        Object o = m.get(key);
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).intValue() != 0;
        return false;
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
                            Map<String, Object> body = response.body();
                            Object likes = body.get("likes");
                            Object dislikes = body.get("dislikes");
                            Object uv = body.get("userVote");
                            if (likes != null) likesCount.setText(String.valueOf(((Number) likes).intValue()));
                            if (dislikes != null) dislikesCount.setText(String.valueOf(((Number) dislikes).intValue()));
                            userVote = uv instanceof Number ? ((Number) uv).intValue() : 0;
                            updateLikeDislikeButtons();
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

    private void togglePin() {
        View btn = requireView().findViewById(R.id.btn_pin);
        boolean currentlyPinned = Boolean.TRUE.equals(btn.getTag());
        boolean newPinned = !currentlyPinned;
        RetrofitHelper.get().create(com.ourcat.app.repositories.ForumApi.class).setPinned(postId, newPinned)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            btn.setTag(newPinned);
                            ((android.widget.Button) btn).setText(newPinned ? "取消置顶" : "置顶");
                            Toast.makeText(requireContext(), newPinned ? "已置顶" : "已取消置顶", Toast.LENGTH_SHORT).show();
                        }
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
