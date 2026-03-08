package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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
import androidx.viewpager2.widget.ViewPager2;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.bumptech.glide.Glide;
import com.ourcat.app.R;
import com.ourcat.app.adapters.ImagePagerAdapter;
import com.ourcat.app.utils.TimeUtils;
import com.ourcat.app.repositories.ForumApi;
import com.ourcat.app.utils.CatTagUtils;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.TimeUtils;

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
    private TextView titleView, authorView, likesCount, dislikesCount, imageIndicator, timeView;
    private TextView contentView;
    private ImageView postAvatar;
    private ViewPager2 imagePager;
    private ImagePagerAdapter imagePagerAdapter;
    private ViewGroup commentsContainer;
    private EditText commentInput;
    private com.google.android.material.button.MaterialButton btnLike, btnDislike;
    private ImageView btnMore;
    private int userVote = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null)
            postId = getArguments().getLong("postId", 0L);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_post_detail, container, false);
        titleView = view.findViewById(R.id.post_title);
        authorView = view.findViewById(R.id.post_author);
        timeView = view.findViewById(R.id.post_time);
        postAvatar = view.findViewById(R.id.post_avatar);
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

        commentsContainer = view.findViewById(R.id.comments_container);

        btnLike.setOnClickListener(v -> like(true));
        btnDislike.setOnClickListener(v -> like(false));
        view.findViewById(R.id.btn_send_comment).setOnClickListener(v -> sendComment());
        commentInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendComment();
                return true;
            }
            return false;
        });
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
        if (postData == null || getView() == null)
            return;
        titleView.setText(getStr(postData, "title"));
        authorView.setText("作者: " + getStr(postData, "authorName"));
        timeView.setText(TimeUtils.getRelativeTime(getStr(postData, "createdAt")));

        String content = getStr(postData, "content");
        CatTagUtils.parseAndBind(requireContext(), contentView, content, catId -> {
            Bundle args = new Bundle();
            args.putLong("catId", catId);
            Navigation.findNavController(requireView()).navigate(R.id.cat_profile, args);
        });
        likesCount.setText(String.valueOf(getInt(postData, "likes")));
        dislikesCount.setText(String.valueOf(getInt(postData, "dislikes")));

        String avatarUrl = getStr(postData, "authorAvatar");
        if (!avatarUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(this).load(avatarUrl).circleCrop().placeholder(R.drawable.ic_default_avatar)
                    .into(postAvatar);
        } else {
            postAvatar.setImageResource(R.drawable.ic_default_avatar);
        }

        View.OnClickListener profileListener = v -> {
            long userId = getLong(postData, "userId");
            if (userId != 0) {
                Bundle args = new Bundle();
                args.putLong("userId", userId);
                Navigation.findNavController(requireView()).navigate(R.id.user_profile, args);
            }
        };
        postAvatar.setOnClickListener(profileListener);
        authorView.setOnClickListener(profileListener);

        Object imgs = postData.get("images");
        if (imgs != null) {
            try {
                List<String> urls = new Gson().fromJson(imgs.toString(), new TypeToken<List<String>>() {
                }.getType());
                if (urls != null && !urls.isEmpty()) {
                    imagePager.setVisibility(View.VISIBLE);
                    imageIndicator.setVisibility(View.VISIBLE);
                    imagePagerAdapter.setUrls(urls);
                    imageIndicator.setText("1/" + urls.size());
                } else {
                    imagePager.setVisibility(View.GONE);
                    imageIndicator.setVisibility(View.GONE);
                }
            } catch (Exception ignored) {
            }
        } else {
            imagePager.setVisibility(View.GONE);
            imageIndicator.setVisibility(View.GONE);
        }

        long postUserId = getLong(postData, "userId");
        boolean isAuthor = currentUserId != 0 && currentUserId == postUserId;
        boolean canManage = currentUserRole >= 2;
        btnMore.setVisibility((isAuthor || canManage) ? View.VISIBLE : View.GONE);

        Object uv = postData.get("userVote");
        userVote = uv instanceof Number ? ((Number) uv).intValue() : 0;
        updateLikeDislikeButtons();
    }

    private void updateLikeDislikeButtons() {

        if (userVote == 1) {
            btnLike.setIconResource(R.drawable.thumb_up);
            btnLike.setIconTintResource(R.color.primary);
        } else {
            btnLike.setIconResource(R.drawable.thumb_up_outline);
            btnLike.setIconTintResource(R.color.primary);
        }

        if (userVote == -1) {
            btnDislike.setIconResource(R.drawable.thumb_down);
            btnDislike.setIconTintResource(R.color.primary);
        } else {
            btnDislike.setIconResource(R.drawable.thumb_down_outline);
            btnDislike.setIconTintResource(R.color.primary);
        }

        // 不再使用透明度，而是用图标状态区分
        btnLike.setAlpha(1f);
        btnDislike.setAlpha(1f);
    }

    private void loadComments() {
        RetrofitHelper.get().create(ForumApi.class).getComments(postId, 0, 50)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                            Response<List<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            bindComments(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                    }
                });
    }

    private void bindComments(List<Map<String, Object>> comments) {
        commentsContainer.removeAllViews();
        if (comments == null) return;
        for (Map<String, Object> m : comments) {
            View item = getLayoutInflater().inflate(R.layout.item_comment, commentsContainer, false);
            TextView author = item.findViewById(R.id.item_comment_author);
            TextView content = item.findViewById(R.id.item_comment_content);
            TextView time = item.findViewById(R.id.item_comment_time);
            ImageView avatar = item.findViewById(R.id.item_comment_avatar);
            ImageView btnMore = item.findViewById(R.id.btn_comment_more);

            author.setText(m.get("authorName") != null ? m.get("authorName").toString() : "");
            content.setText(m.get("content") != null ? m.get("content").toString() : "");
            time.setText(TimeUtils.getRelativeTime((String) m.get("createdAt")));

            Object avatarUrl = m.get("authorAvatar");
            if (avatarUrl != null && !avatarUrl.toString().isEmpty()) {
                Glide.with(requireContext()).load(avatarUrl.toString()).circleCrop()
                        .placeholder(R.drawable.ic_default_avatar).into(avatar);
            } else {
                avatar.setImageResource(R.drawable.ic_default_avatar);
            }

            long commentUserId = m.get("userId") instanceof Number ? ((Number) m.get("userId")).longValue() : 0;
            long commentId = m.get("id") instanceof Number ? ((Number) m.get("id")).longValue() : 0;

            View.OnClickListener profileListener = v -> {
                if (commentUserId != 0) {
                    Bundle args = new Bundle();
                    args.putLong("userId", commentUserId);
                    Navigation.findNavController(v).navigate(R.id.user_profile, args);
                }
            };
            avatar.setOnClickListener(profileListener);
            author.setOnClickListener(profileListener);

            boolean isOwnComment = currentUserId != 0 && currentUserId == commentUserId;
            boolean canManage = currentUserRole >= 2;
            if (isOwnComment || canManage) {
                btnMore.setVisibility(View.VISIBLE);
                btnMore.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(v.getContext(), v);
                    popup.getMenu().add("删除");
                    popup.setOnMenuItemClickListener(menuItem -> {
                        if ("删除".equals(menuItem.getTitle())) deleteComment(commentId);
                        return true;
                    });
                    popup.show();
                });
            } else {
                btnMore.setVisibility(View.GONE);
            }
            commentsContainer.addView(item);
        }
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
                            if (likes != null)
                                likesCount.setText(String.valueOf(((Number) likes).intValue()));
                            if (dislikes != null)
                                dislikesCount.setText(String.valueOf(((Number) dislikes).intValue()));
                            userVote = uv instanceof Number ? ((Number) uv).intValue() : 0;
                            updateLikeDislikeButtons();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    }
                });
    }

    private void sendComment() {
        String content = commentInput.getText().toString().trim();
        if (content.isEmpty())
            return;
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
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    }
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
        if (o instanceof Boolean)
            return (Boolean) o;
        if (o instanceof Number)
            return ((Number) o).intValue() != 0;
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
