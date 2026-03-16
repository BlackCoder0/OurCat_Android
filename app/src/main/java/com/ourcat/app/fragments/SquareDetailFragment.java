package com.ourcat.app.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ourcat.app.R;
import com.ourcat.app.adapters.ImagePagerAdapter;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.repositories.SquareApi;
import com.ourcat.app.utils.CatTagUtils;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SquareDetailFragment extends Fragment {

    private long squarePostId;
    private long currentUserId;
    private int currentUserRole;
    private Map<String, Object> postData;
    private final List<Map<String, Object>> rescueTasks = new ArrayList<>();

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
    private View rescueActivityCard;
    private TextView rescueActivityTitle;
    private TextView rescueActivityStatus;
    private View rescueActionsCard;
    private MaterialButton rescueClaimButton;
    private MaterialButton rescueAssignButton;
    private RecyclerView rescueTasksView;
    private TextView rescueTasksEmpty;
    private RescueTasksAdapter rescueTasksAdapter;
    private View refCatCard;
    private ImageView refCatAvatar;
    private TextView refCatName;
    private ViewGroup commentsContainer;
    private EditText commentInput;

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
        rescueActivityCard = view.findViewById(R.id.square_rescue_activity_card);
        rescueActivityTitle = view.findViewById(R.id.square_rescue_activity_title);
        rescueActivityStatus = view.findViewById(R.id.square_rescue_activity_status);
        rescueActionsCard = view.findViewById(R.id.square_rescue_actions_card);
        rescueClaimButton = view.findViewById(R.id.square_rescue_claim);
        rescueAssignButton = view.findViewById(R.id.square_rescue_assign);
        rescueTasksView = view.findViewById(R.id.square_rescue_tasks);
        rescueTasksEmpty = view.findViewById(R.id.square_rescue_tasks_empty);
        refCatCard = view.findViewById(R.id.square_ref_cat_card);
        refCatAvatar = view.findViewById(R.id.square_ref_cat_avatar);
        refCatName = view.findViewById(R.id.square_ref_cat_name);
        commentsContainer = view.findViewById(R.id.square_comments_container);
        commentInput = view.findViewById(R.id.square_comment_input);

        imagePagerAdapter = new ImagePagerAdapter();
        imagePager.setAdapter(imagePagerAdapter);
        rescueTasksAdapter = new RescueTasksAdapter();
        rescueTasksView.setLayoutManager(new LinearLayoutManager(requireContext()));
        rescueTasksView.setAdapter(rescueTasksAdapter);

        rescueActivityCard.setVisibility(View.GONE);
        rescueActionsCard.setVisibility(View.GONE);
        refCatCard.setVisibility(View.GONE);

        btnMarkSolved.setOnClickListener(v -> markSolved());
        rescueClaimButton.setOnClickListener(v -> claimRescueTask());
        rescueAssignButton.setOnClickListener(v -> openAssignDialog());
        view.findViewById(R.id.btn_send_square_comment).setOnClickListener(v -> sendComment());
        commentInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendComment();
                return true;
            }
            return false;
        });

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
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void bindPost() {
        if (postData == null || getView() == null)
            return;
        String type = stringValue(postData.get("type"), "inquiry");
        String status = stringValue(postData.get("status"), "open");
        String authorName = stringValue(postData.get("authorName"), "匿名");
        String avatarUrl = stringValue(postData.get("authorAvatar"), "");
        long postUserId = longValue(postData.get("userId"));

        typeChip.setText("rescue".equals(type) ? "救助" : "询问");
        int chipColor = "rescue".equals(type) ? Color.parseColor("#E65100") : Color.parseColor("#1976D2");
        typeChip.setChipBackgroundColor(ColorStateList.valueOf(chipColor));
        typeChip.setTextColor(Color.WHITE);
        statusText.setText("resolved".equals(status) ? "已解决" : "进行中");
        statusText.setTextColor("resolved".equals(status) ? 0xFF4CAF50 : 0xFFFF9800);
        authorText.setText(authorName);

        String content = stringValue(postData.get("text"), "");
        CatTagUtils.parseAndBind(requireContext(), textView, content, catId -> {
            Bundle args = new Bundle();
            args.putLong("catId", catId);
            Navigation.findNavController(requireView()).navigate(R.id.cat_profile, args);
        });

        if (!avatarUrl.isEmpty()) {
            Glide.with(this).load(avatarUrl).circleCrop().placeholder(R.drawable.ic_default_avatar).into(authorAvatar);
        } else {
            authorAvatar.setImageResource(R.drawable.ic_default_avatar);
        }

        View.OnClickListener profileListener = v -> {
            if (postUserId > 0) {
                Bundle args = new Bundle();
                args.putLong("userId", postUserId);
                NavHostFragment.findNavController(this).navigate(R.id.user_profile, args);
            }
        };
        authorAvatar.setOnClickListener(profileListener);
        authorText.setOnClickListener(profileListener);

        bindImages(postData.get("images"));
        bindLocation(postData.get("location"));
        bindReferencedCat(postData.get("referencedCat"), postData.get("referencedCatId"));
        bindRescueSection(type, status);

        boolean isAuthor = currentUserId != 0 && currentUserId == postUserId;
        btnMarkSolved.setVisibility(!"resolved".equals(status) && isAuthor ? View.VISIBLE : View.GONE);
        if (resolvedOverlay != null) {
            resolvedOverlay.setVisibility(View.GONE);
        }
    }

    private void bindImages(Object imagesObj) {
        imagePager.setVisibility(View.GONE);
        imagePagerAdapter.setUrls(new ArrayList<>());
        if (imagesObj == null) {
            return;
        }
        try {
            List<String> urls = new Gson().fromJson(imagesObj.toString(), new TypeToken<List<String>>() {
            }.getType());
            if (urls != null && !urls.isEmpty()) {
                imagePager.setVisibility(View.VISIBLE);
                imagePagerAdapter.setUrls(urls);
            }
        } catch (Exception ignored) {
        }
    }

    private void bindLocation(Object locationObj) {
        String location = stringValue(locationObj, "").trim();
        if (location.isEmpty()) {
            locationText.setVisibility(View.GONE);
            locationText.setOnClickListener(null);
            return;
        }
        locationText.setVisibility(View.VISIBLE);
        locationText.setText("位置: " + location);
        locationText.setOnClickListener(v -> {
            double[] coords = parseLocation(location);
            if (coords == null) {
                Toast.makeText(requireContext(), "位置信息格式错误", Toast.LENGTH_SHORT).show();
                return;
            }
            if (getActivity() instanceof com.ourcat.app.activities.MainActivity) {
                ((com.ourcat.app.activities.MainActivity) getActivity()).showMapAtLocation(coords[0], coords[1]);
            }
        });
    }

    private void bindReferencedCat(Object refCatObj, Object refCatIdObj) {
        long refCatId = longValue(refCatIdObj);
        if (!(refCatObj instanceof Map) || refCatId <= 0) {
            refCatCard.setVisibility(View.GONE);
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> refCat = (Map<String, Object>) refCatObj;
        refCatCard.setVisibility(View.VISIBLE);
        refCatName.setText(stringValue(refCat.get("name"), "未命名"));
        String imageUrl = stringValue(refCat.get("primaryImageUrl"), "");
        if (!imageUrl.isEmpty()) {
            Glide.with(this).load(imageUrl).centerCrop().into(refCatAvatar);
        } else {
            refCatAvatar.setImageResource(R.drawable.ic_default_avatar);
        }
        refCatCard.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putLong("catId", refCatId);
            Navigation.findNavController(requireView()).navigate(R.id.action_square_detail_to_cat_profile, args);
        });
    }

    private void bindRescueSection(String type, String postStatus) {
        long rescueActivityId = longValue(postData.get("rescueActivityId"));
        if (!"rescue".equals(type) || rescueActivityId <= 0) {
            rescueActivityCard.setVisibility(View.GONE);
            rescueActionsCard.setVisibility(View.GONE);
            return;
        }

        rescueActivityCard.setVisibility(View.VISIBLE);
        rescueActionsCard.setVisibility(View.VISIBLE);
        rescueActivityTitle.setText(stringValue(postData.get("rescueActivityTitle"), "救助活动"));
        String rescueStatus = stringValue(postData.get("rescueActivityStatus"), "created");
        rescueActivityStatus.setText(mapRescueStatusText(rescueStatus));
        rescueActivityStatus.setTextColor(mapRescueStatusColor(rescueStatus));
        View.OnClickListener rescueMapJumpListener = v -> jumpRescueActivityToMap();
        rescueActivityCard.setOnClickListener(rescueMapJumpListener);
        rescueActivityTitle.setOnClickListener(rescueMapJumpListener);
        rescueActivityStatus.setOnClickListener(rescueMapJumpListener);

        boolean isCompleted = "completed".equals(rescueStatus) || "resolved".equals(postStatus);
        rescueClaimButton.setVisibility(isCompleted ? View.GONE : View.VISIBLE);
        rescueClaimButton.setEnabled(!isCompleted);

        long createdBy = longValue(postData.get("rescueActivityCreatedBy"));
        boolean showAssign = !isCompleted && currentUserId > 0 && currentUserId == createdBy;
        rescueAssignButton.setVisibility(showAssign ? View.VISIBLE : View.GONE);

        rescueTasks.clear();
        Object tasksObj = postData.get("rescueTasks");
        if (tasksObj instanceof List) {
            for (Object task : (List<?>) tasksObj) {
                if (task instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) task;
                    rescueTasks.add(map);
                }
            }
        }
        rescueTasksAdapter.notifyDataSetChanged();
        rescueTasksEmpty.setVisibility(rescueTasks.isEmpty() ? View.VISIBLE : View.GONE);
        rescueTasksView.setVisibility(rescueTasks.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void jumpRescueActivityToMap() {
        if (!(getActivity() instanceof com.ourcat.app.activities.MainActivity) || postData == null) {
            return;
        }
        com.ourcat.app.activities.MainActivity mainActivity = (com.ourcat.app.activities.MainActivity) getActivity();
        long refCatId = longValue(postData.get("referencedCatId"));
        String location = stringValue(postData.get("location"), "").trim();
        double[] coords = location.isEmpty() ? null : parseLocation(location);

        if (refCatId > 0) {
            if (coords != null) {
                mainActivity.showMapAtCatLocationNear(refCatId, coords[0], coords[1]);
            } else {
                mainActivity.showMapAtCatLocationNearestToUser(refCatId);
            }
            return;
        }

        if (coords != null) {
            mainActivity.showMapAtRescueLocation(coords[0], coords[1]);
            return;
        }

        Toast.makeText(requireContext(), "该救助活动暂无可用定位", Toast.LENGTH_SHORT).show();
    }

    private void claimRescueTask() {
        long activityId = longValue(postData != null ? postData.get("rescueActivityId") : null);
        if (activityId <= 0) {
            return;
        }
        RetrofitHelper.get().create(RescueApi.class).claimTask(activityId)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        if (response.isSuccessful()) {
                            Toast.makeText(requireContext(), "申领成功", Toast.LENGTH_SHORT).show();
                            loadPost();
                        } else {
                            Toast.makeText(requireContext(), "申领失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void openAssignDialog() {
        long activityId = longValue(postData != null ? postData.get("rescueActivityId") : null);
        if (activityId <= 0) {
            return;
        }
        AssignMemberDialogFragment dialog = new AssignMemberDialogFragment();
        Bundle args = new Bundle();
        args.putLong("activityId", activityId);
        dialog.setArguments(args);
        dialog.setListener(this::loadPost);
        dialog.show(getParentFragmentManager(), "AssignMember");
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
                            bindComments(response.body());
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
                        if (response.isSuccessful() && response.body() != null) {
                            Toast.makeText(requireContext(), "已标记为已解决", Toast.LENGTH_SHORT).show();
                            postData = response.body();
                            bindPost();
                        } else {
                            Toast.makeText(requireContext(), "操作失败", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void sendComment() {
        String content = commentInput.getText() != null ? commentInput.getText().toString().trim() : "";
        if (content.isEmpty()) {
            Toast.makeText(requireContext(), "请输入评论内容", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, String> body = new HashMap<>();
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
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void bindComments(List<Map<String, Object>> comments) {
        commentsContainer.removeAllViews();
        if (comments == null)
            return;
        for (Map<String, Object> item : comments) {
            View view = getLayoutInflater().inflate(R.layout.item_comment, commentsContainer, false);
            TextView author = view.findViewById(R.id.item_comment_author);
            TextView content = view.findViewById(R.id.item_comment_content);
            TextView time = view.findViewById(R.id.item_comment_time);
            ImageView avatar = view.findViewById(R.id.item_comment_avatar);
            ImageView btnMore = view.findViewById(R.id.btn_comment_more);
            btnMore.setVisibility(View.GONE);

            author.setText(stringValue(item.get("authorName"), ""));
            content.setText(stringValue(item.get("content"), ""));
            time.setText(TimeUtils.getRelativeTime(stringValue(item.get("createdAt"), "")));

            String avatarUrl = stringValue(item.get("authorAvatar"), "");
            if (!avatarUrl.isEmpty()) {
                Glide.with(requireContext()).load(avatarUrl).circleCrop()
                        .placeholder(R.drawable.ic_default_avatar).into(avatar);
            } else {
                avatar.setImageResource(R.drawable.ic_default_avatar);
            }
            commentsContainer.addView(view);
        }
    }

    private double[] parseLocation(String location) {
        String[] parts = location.split(",");
        if (parts.length < 2) {
            return null;
        }
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lng = Double.parseDouble(parts[1].trim().split("\\s+")[0]);
            return new double[] { lat, lng };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String mapRescueStatusText(String status) {
        switch (status) {
            case "completed":
                return "已完成";
            case "in_progress":
                return "进行中";
            default:
                return "待响应";
        }
    }

    private int mapRescueStatusColor(String status) {
        switch (status) {
            case "completed":
                return Color.parseColor("#4CAF50");
            case "in_progress":
                return Color.parseColor("#FF9800");
            default:
                return Color.parseColor("#2196F3");
        }
    }

    private String stringValue(Object value, String defaultValue) {
        return value != null ? value.toString() : defaultValue;
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private class RescueTasksAdapter extends RecyclerView.Adapter<RescueTasksAdapter.TaskHolder> {
        @NonNull
        @Override
        public TaskHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            return new TaskHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TaskHolder holder, int position) {
            Map<String, Object> task = rescueTasks.get(position);
            long assigneeId = longValue(task.get("assigneeUserId"));
            holder.title.setText(assigneeId > 0 ? "成员 #" + assigneeId : "待领取任务");
            String status = stringValue(task.get("status"), "assigned");
            holder.subtitle.setText("状态: " + status);
        }

        @Override
        public int getItemCount() {
            return rescueTasks.size();
        }

        class TaskHolder extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;

            TaskHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.text1);
                subtitle = itemView.findViewById(android.R.id.text2);
            }
        }
    }
}
