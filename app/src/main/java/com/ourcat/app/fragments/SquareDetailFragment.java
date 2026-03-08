package com.ourcat.app.fragments;

import android.os.Bundle;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.res.ColorStateList;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ourcat.app.R;
import com.ourcat.app.adapters.ImagePagerAdapter;
import com.ourcat.app.repositories.SquareApi;
import com.ourcat.app.utils.CatTagUtils;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 广场广播详情页。对应计划文档「阶段 6 → 前端 → 详情页 + 评论展示、标记已解决 UI 交互」。
 */
public class SquareDetailFragment extends Fragment {

    private long squarePostId;
    private long currentUserId;
    private int currentUserRole;
    private Map<String, Object> postData;

    private View resolvedOverlay;
    private Chip typeChip;
    private TextView statusText;
    private TextView authorText;
    private ImageView authorAvatar;
    private TextView textView;
    private ViewPager2 imagePager;
    private ImagePagerAdapter imagePagerAdapter;
    private TextView locationText;
    private MaterialButton btnMarkSolved;
    private RecyclerView commentsRecycler;
    private EditText commentInput;
    private RecyclerView.Adapter<RecyclerView.ViewHolder> commentAdapter;
    private final List<Map<String, Object>> commentItems = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            squarePostId = getArguments().getLong("squarePostId", 0L);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_square_detail, container, false);
        resolvedOverlay = view.findViewById(R.id.square_resolved_overlay);
        typeChip = view.findViewById(R.id.square_detail_type);
        statusText = view.findViewById(R.id.square_detail_status);
        authorText = view.findViewById(R.id.square_detail_author);
        authorAvatar = view.findViewById(R.id.square_detail_avatar);
        if (Build.VERSION.SDK_INT >= 26) {
            authorAvatar.setDefaultFocusHighlightEnabled(false);
        }
        if (Build.VERSION.SDK_INT >= 23) {
            authorAvatar.setForeground(null);
        }
        authorAvatar.setFocusable(false);
        textView = view.findViewById(R.id.square_detail_text);
        imagePager = view.findViewById(R.id.square_detail_image_pager);
        locationText = view.findViewById(R.id.square_detail_location);
        btnMarkSolved = view.findViewById(R.id.btn_mark_solved);
        commentsRecycler = view.findViewById(R.id.square_comments_recycler);
        commentInput = view.findViewById(R.id.square_comment_input);

        imagePagerAdapter = new ImagePagerAdapter();
        imagePager.setAdapter(imagePagerAdapter);

        commentsRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        commentAdapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_comment, parent, false);
                v.findViewById(R.id.btn_comment_more).setVisibility(View.GONE);
                return new RecyclerView.ViewHolder(v) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> m = commentItems.get(position);
                TextView author = holder.itemView.findViewById(R.id.item_comment_author);
                TextView content = holder.itemView.findViewById(R.id.item_comment_content);
                author.setText(m.get("authorName") != null ? m.get("authorName").toString() : "");
                content.setText(m.get("content") != null ? m.get("content").toString() : "");
            }

            @Override
            public int getItemCount() {
                return commentItems.size();
            }
        };
        commentsRecycler.setAdapter(commentAdapter);

        btnMarkSolved.setOnClickListener(v -> markSolved());
        view.findViewById(R.id.btn_send_square_comment).setOnClickListener(v -> sendComment());

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
        RetrofitHelper.get().create(SquareApi.class).getPost(squarePostId)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        if (response.isSuccessful() && response.body() != null) {
                            postData = response.body();
                            bindPost();
                            loadComments();
                        } else {
                            Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded())
                            Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void bindPost() {
        if (postData == null || getView() == null)
            return;
        String type = postData.get("type") != null ? postData.get("type").toString() : "inquiry";
        String status = postData.get("status") != null ? postData.get("status").toString() : "open";
        String authorName = postData.get("authorName") != null ? postData.get("authorName").toString() : "匿名";
        String avatarUrl = postData.get("authorAvatar") != null ? postData.get("authorAvatar").toString() : null;
        Object userIdObj = postData.get("userId");
        long postUserId = userIdObj instanceof Number ? ((Number) userIdObj).longValue() : 0;

        typeChip.setText("rescue".equals(type) ? "救助" : "询问");
        int color = "rescue".equals(type) ? Color.parseColor("#E65100") : Color.parseColor("#1976D2");
        typeChip.setChipBackgroundColor(ColorStateList.valueOf(color));
        typeChip.setTextColor(Color.WHITE);
        statusText.setText("resolved".equals(status) ? "已解决" : "进行中");
        statusText.setTextColor("resolved".equals(status) ? 0xFF4CAF50 : 0xFFFF9800);
        authorText.setText(authorName);

        String content = postData.get("text") != null ? postData.get("text").toString() : "";
        CatTagUtils.parseAndBind(requireContext(), textView, content, catId -> {
            Bundle args = new Bundle();
            args.putLong("catId", catId);
            Navigation.findNavController(requireView()).navigate(R.id.cat_profile, args);
        });

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this).load(avatarUrl).circleCrop().placeholder(R.drawable.ic_default_avatar).into(authorAvatar);
        } else {
            authorAvatar.setImageResource(R.drawable.ic_default_avatar);
        }

        View.OnClickListener profileListener = v -> {
            if (postUserId != 0) {
                Bundle args = new Bundle();
                args.putLong("userId", postUserId);
                NavHostFragment.findNavController(this).navigate(R.id.user_profile, args);
            }
        };
        authorAvatar.setOnClickListener(profileListener);
        authorText.setOnClickListener(profileListener);

        Object imgs = postData.get("images");
        if (imgs != null) {
            try {
                List<String> urls = new Gson().fromJson(imgs.toString(), new TypeToken<List<String>>() {
                }.getType());
                if (urls != null && !urls.isEmpty()) {
                    imagePager.setVisibility(View.VISIBLE);
                    imagePagerAdapter.setUrls(urls);
                }
            } catch (Exception ignored) {
            }
        }
        Object loc = postData.get("location");
        if (loc != null && !loc.toString().trim().isEmpty()) {
            locationText.setVisibility(View.VISIBLE);
            locationText.setText("位置: " + loc.toString());
        }

        boolean isAuthor = currentUserId != 0 && currentUserId == postUserId;
        boolean canManage = currentUserRole >= 2;
        boolean resolved = "resolved".equals(status);
        if (!resolved && isAuthor) { // Only author can mark solved
            btnMarkSolved.setVisibility(View.VISIBLE);
        } else {
            btnMarkSolved.setVisibility(View.GONE);
        }

        if (resolved && resolvedOverlay != null) {
            resolvedOverlay.setVisibility(View.GONE);
        } else if (resolvedOverlay != null) {
            resolvedOverlay.setVisibility(View.GONE);
        }
    }

    private void loadComments() {
        RetrofitHelper.get().create(SquareApi.class).getComments(squarePostId, 0, 50)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                            Response<List<Map<String, Object>>> response) {
                        if (!isAdded())
                            return;
                        if (response.isSuccessful() && response.body() != null) {
                            commentItems.clear();
                            commentItems.addAll(response.body());
                            commentAdapter.notifyDataSetChanged();
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                    }
                });
    }

    private void markSolved() {
        RetrofitHelper.get().create(SquareApi.class).markSolved(squarePostId)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(), "已标记为已解决", Toast.LENGTH_SHORT).show();
                            postData = response.body();
                            bindPost();
                        } else {
                            Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded())
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void sendComment() {
        String content = commentInput.getText() != null ? commentInput.getText().toString().trim() : "";
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "请输入评论内容", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, String> body = new java.util.HashMap<>();
        body.put("content", content);
        RetrofitHelper.get().create(SquareApi.class).addComment(squarePostId, body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        if (response.isSuccessful()) {
                            commentInput.setText("");
                            loadComments();
                        } else {
                            Toast.makeText(requireContext(), "发送失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded())
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
