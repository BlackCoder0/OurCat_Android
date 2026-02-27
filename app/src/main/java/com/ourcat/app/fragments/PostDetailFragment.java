package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
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
import com.ourcat.app.repositories.ForumApi;
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
    private ImageView btnMore;
    private int userVote = 0;

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
        btnMore = view.findViewById(R.id.btn_more);

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
        commentAdapter.setOnDeleteListener((commentId) -> deleteComment(commentId));
        commentsRecycler.setAdapter(commentAdapter);

        btnLike.setOnClickListener(v -> like(true));
        btnDislike.setOnClickListener(v -> like(false));
        view.findViewById(R.id.btn_send_comment).setOnClickListener(v -> sendComment());
        btnMore.setOnClickListener(this::showPostMenu);

        loadProfileThenPost();
        return view;
    }

    private void showPostMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.menu_post_actions, popup.getMenu());

        long postUserId = getLong(postData, "userId");
        boolean isAuthor = currentUserId != 0 && currentUserId == postUserId;
        boolean canManage = currentUserRole >= 2;
        boolean pinned = getBool(postData, "pinned");

        popup.getMenu().findItem(R.id.action_edit).setVisible(isAuthor);
        popup.getMenu().findItem(R.id.action_delete).setVisible(isAuthor || canManage);

        MenuItem pinItem = popup.getMenu().findItem(R.id.action_pin);
        pinItem.setVisible(canManage);
        pinItem.setTitle(pinned ? "取消置顶" : "置顶");

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_edit) {
                Bundle args = new Bundle();
                args.putInt("editPostId", (int) postId);
                Navigation.findNavController(requireView()).navigate(R.id.create_edit_post, args);
                return true;
            } else if (id == R.id.action_delete) {
                deletePost();
                return true;
            } else if (id == R.id.action_pin) {
                togglePin(!pinned);
                return true;
            }
            return false;
        });
        popup.show();
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
        RetrofitHelper.get().create(ForumApi.class).getPost(postId)
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

        long postUserId = getLong(postData, "userId");
        boolean isAuthor = currentUserId != 0 && currentUserId == postUserId;
        boolean canManage = currentUserRole >= 2;
        btnMore.setVisibility((isAuthor || canManage) ? View.VISIBLE : View.GONE);

        commentAdapter.setCurrentUserId(currentUserId);
        commentAdapter.setCurrentUserRole(currentUserRole);

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

    private void loadComments() {
        RetrofitHelper.get().create(ForumApi.class).getComments(postId, 0, 50)
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
        RetrofitHelper.get().create(ForumApi.class).like(postId, like)
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
        RetrofitHelper.get().create(ForumApi.class).addComment(postId, body)
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
        RetrofitHelper.get().create(ForumApi.class).deletePost(postId)
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

    private void togglePin(boolean newPinned) {
        RetrofitHelper.get().create(ForumApi.class).setPinned(postId, newPinned)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful()) {
                            postData.put("pinned", newPinned);
                            Toast.makeText(requireContext(), newPinned ? "已置顶" : "已取消置顶", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                });
    }

    private void deleteComment(long commentId) {
        RetrofitHelper.get().create(ForumApi.class).deleteComment(commentId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(), "评论已删除", Toast.LENGTH_SHORT).show();
                            loadComments();
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

    private static boolean getBool(Map<String, Object> m, String key) {
        Object o = m.get(key);
        if (o instanceof Boolean) return (Boolean) o;
        if (o instanceof Number) return ((Number) o).intValue() != 0;
        return false;
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
