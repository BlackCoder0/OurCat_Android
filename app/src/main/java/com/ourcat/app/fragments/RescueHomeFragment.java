package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ourcat.app.R;
import com.ourcat.app.repositories.ForumApi;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.repositories.SquareApi;
import com.ourcat.app.utils.PressFeedbackUtils;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.TimeUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RescueHomeFragment extends Fragment {

    private static final int[] PHOTO_PLACEHOLDERS = {
            R.drawable.bg_home_story_cover_amber,
            R.drawable.bg_home_story_cover_mint,
            R.drawable.bg_home_story_cover_blue
    };

    private final Gson gson = new Gson();
    private final List<Map<String, Object>> squarePhotoItems = new ArrayList<>();
    private final List<Map<String, Object>> forumPhotoItems = new ArrayList<>();
    private final List<Map<String, Object>> rescueItems = new ArrayList<>();

    private ImageView spotlightPrimaryImage;
    private ImageView spotlightSecondaryImage;
    private ImageView spotlightTertiaryImage;
    private LinearLayout squareStoryRow;
    private LinearLayout forumStoryRow;
    private LinearLayout rescueUpdateList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rescue_home_center, container, false);

        spotlightPrimaryImage = view.findViewById(R.id.home_spotlight_primary_image);
        spotlightSecondaryImage = view.findViewById(R.id.home_spotlight_secondary_image);
        spotlightTertiaryImage = view.findViewById(R.id.home_spotlight_tertiary_image);
        squareStoryRow = view.findViewById(R.id.home_square_story_row);
        forumStoryRow = view.findViewById(R.id.home_forum_story_row);
        rescueUpdateList = view.findViewById(R.id.home_rescue_update_list);

        bindStaticCopy(view);
        setupEntry(view, R.id.home_search_entry, v -> {
            Bundle args = new Bundle();
            args.putString("query", "");
            Navigation.findNavController(v).navigate(R.id.search_result, args);
        });
        setupEntry(view, R.id.home_button_message,
                v -> Navigation.findNavController(v).navigate(R.id.message_center));
        setupEntry(view, R.id.home_button_publish,
                v -> Navigation.findNavController(v).navigate(R.id.create_square_post));
        setupEntry(view, R.id.entry_square, v -> Navigation.findNavController(v).navigate(R.id.nav_square));
        setupEntry(view, R.id.entry_forum, v -> Navigation.findNavController(v).navigate(R.id.forum_list));
        setupEntry(view, R.id.entry_latest, v -> Navigation.findNavController(v).navigate(R.id.rescue_list));
        setupEntry(view, R.id.entry_guide, v -> Navigation.findNavController(v).navigate(R.id.rescue_guide));
        setupEntry(view, R.id.home_spotlight_action,
                v -> Navigation.findNavController(v).navigate(R.id.nav_square));
        setupEntry(view, R.id.action_square_more, v -> Navigation.findNavController(v).navigate(R.id.nav_square));
        setupEntry(view, R.id.action_forum_more, v -> Navigation.findNavController(v).navigate(R.id.forum_list));
        setupEntry(view, R.id.action_rescue_more, v -> Navigation.findNavController(v).navigate(R.id.rescue_list));

        bindSpotlightFallback();
        bindSquareFallback();
        bindForumFallback();
        bindRescueFallback();

        loadSquareStories();
        loadForumStories();
        loadRescueUpdates();
        return view;
    }

    private void bindStaticCopy(@NonNull View view) {
        setText(view, R.id.home_top_hint, "\u771f\u5b9e\u56fe\u7247\u4f18\u5148");
        setText(view, R.id.home_search_text, "\u641c\u732b\u54aa\u3001\u5e7f\u573a\u5e16\u5b50\u3001\u793e\u533a\u6545\u4e8b");
        setText(view, R.id.home_entry_square_text, "\u6551\u52a9\u5e7f\u573a");
        setText(view, R.id.home_entry_forum_text, "\u793e\u533a\u8ba8\u8bba");
        setText(view, R.id.home_entry_latest_text, "\u6700\u65b0\u884c\u52a8");
        setText(view, R.id.home_entry_guide_text, "\u6551\u52a9\u6307\u5357");

        setText(view, R.id.home_spotlight_badge, "\u4eca\u65e5\u6709\u56fe\u7cbe\u9009");
        setText(view, R.id.home_spotlight_title, "\u5148\u770b\u4eca\u5929\u7684\u4e09\u5f20\u771f\u5b9e\u7167\u7247");
        setText(view, R.id.home_spotlight_desc,
                "\u5e7f\u573a\u3001\u8bba\u575b\u548c\u6551\u52a9\u8bb0\u5f55\u91cc\u7684\u5b9e\u62cd\u56fe\u7247\u4f1a\u6392\u5728\u524d\u9762\uff0c\u88c5\u9970\u63d2\u753b\u53ea\u7528\u6765\u8ba9\u9875\u9762\u66f4\u4eb2\u548c\u3002");
        setText(view, R.id.home_spotlight_action, "\u53bb\u901b\u5e7f\u573a");

        setText(view, R.id.home_square_section_title, "\u5e7f\u573a\u73b0\u573a");
        setText(view, R.id.home_square_section_desc, "\u4f18\u5148\u5c55\u793a\u7528\u6237\u4e0a\u4f20\u7684\u73b0\u573a\u56fe\u7247");
        setText(view, R.id.action_square_more, "\u8fdb\u5165\u5e7f\u573a");

        setText(view, R.id.home_forum_section_title, "\u793e\u533a\u6545\u4e8b");
        setText(view, R.id.home_forum_section_desc, "\u5e26\u56fe\u5e16\u5b50\u4f1a\u5728\u8fd9\u91cc\u66f4\u8f7b\u5730\u5c55\u5f00");
        setText(view, R.id.action_forum_more, "\u8fdb\u5165\u8ba8\u8bba");

        setText(view, R.id.home_rescue_section_title, "\u6700\u65b0\u884c\u52a8");
        setText(view, R.id.home_rescue_section_desc, "\u6539\u6210\u66f4\u77ed\u7684\u6551\u52a9\u66f4\u65b0\u5361\u7247\uff0c\u9047\u5230\u56fe\u7247\u76f4\u63a5\u7528\u5b9e\u62cd");
        setText(view, R.id.action_rescue_more, "\u67e5\u770b\u5168\u90e8");
    }

    private void setText(@NonNull View root, int id, @NonNull String text) {
        TextView textView = root.findViewById(id);
        if (textView != null) {
            textView.setText(text);
        }
    }

    private void setupEntry(@NonNull View root, int id, @NonNull View.OnClickListener listener) {
        View view = root.findViewById(id);
        if (view != null) {
            PressFeedbackUtils.attach(view);
            view.setOnClickListener(listener);
        }
    }

    private void bindSpotlightFallback() {
        loadImage(spotlightPrimaryImage, null, PHOTO_PLACEHOLDERS[0]);
        loadImage(spotlightSecondaryImage, null, PHOTO_PLACEHOLDERS[1]);
        loadImage(spotlightTertiaryImage, null, PHOTO_PLACEHOLDERS[2]);
    }

    private void bindSquareFallback() {
        if (squareStoryRow == null) {
            return;
        }
        squareStoryRow.removeAllViews();
        addVisualCard(squareStoryRow,
                "\u5e7f\u573a\u73b0\u573a",
                "\u7b49\u4e00\u5f20\u65b0\u73b0\u573a\u7167\u7247\u51fa\u73b0",
                "\u6709\u56fe\u5185\u5bb9\u4f1a\u4f18\u5148\u6392\u5728\u8fd9\u91cc",
                "\u70b9\u8fdb\u5e7f\u573a\u53bb\u770b\u770b\u5927\u5bb6\u6b63\u5728\u53d1\u751f\u4ec0\u4e48",
                null,
                PHOTO_PLACEHOLDERS[0],
                v -> Navigation.findNavController(v).navigate(R.id.nav_square),
                0,
                1);
    }

    private void bindForumFallback() {
        if (forumStoryRow == null) {
            return;
        }
        forumStoryRow.removeAllViews();
        addVisualCard(forumStoryRow,
                "\u793e\u533a\u6545\u4e8b",
                "\u5e26\u56fe\u5e16\u5b50\u4f1a\u51fa\u73b0\u5728\u8fd9\u91cc",
                "\u5148\u7528\u771f\u5b9e\u56fe\u7247\u8ba9\u9875\u9762\u6709\u547c\u5438\u611f",
                "\u6ca1\u6709\u65b0\u56fe\u65f6\u53ea\u7559\u4e0b\u8f7b\u88c5\u9970\u611f",
                null,
                PHOTO_PLACEHOLDERS[1],
                v -> Navigation.findNavController(v).navigate(R.id.forum_list),
                0,
                1);
    }

    private void bindRescueFallback() {
        if (rescueUpdateList == null) {
            return;
        }
        rescueUpdateList.removeAllViews();
        addRescueCard(
                "\u6700\u65b0\u884c\u52a8",
                "\u9644\u8fd1\u6682\u65f6\u8fd8\u6ca1\u6709\u65b0\u7684\u6551\u52a9\u56fe\u6587",
                "\u6709\u65b0\u8fdb\u5c55\u65f6\u4f1a\u5728\u8fd9\u91cc\u66f4\u65b0",
                "\u53bb\u770b\u5168\u90e8",
                null,
                PHOTO_PLACEHOLDERS[2],
                v -> Navigation.findNavController(v).navigate(R.id.rescue_list));
    }

    private void loadSquareStories() {
        RetrofitHelper.get().create(SquareApi.class).listPosts(0, 10, null)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded() || !response.isSuccessful() || response.body() == null) {
                            return;
                        }
                        squarePhotoItems.clear();
                        for (Map<String, Object> item : extractMapList(response.body().get("content"))) {
                            if (!findFirstImageUrl(item.get("images")).isEmpty()) {
                                squarePhotoItems.add(item);
                            }
                        }
                        bindSquareStories();
                        bindSpotlightImages();
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    }
                });
    }

    private void loadForumStories() {
        RetrofitHelper.get().create(ForumApi.class).listPosts(0, 10, null)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded() || !response.isSuccessful() || response.body() == null) {
                            return;
                        }
                        forumPhotoItems.clear();
                        for (Map<String, Object> item : extractMapList(response.body().get("content"))) {
                            if (!findFirstImageUrl(item.get("images")).isEmpty()) {
                                forumPhotoItems.add(item);
                            }
                        }
                        bindForumStories();
                        bindSpotlightImages();
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    }
                });
    }

    private void loadRescueUpdates() {
        RetrofitHelper.get().create(RescueApi.class).listActivities(0, 6, null, null)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded() || !response.isSuccessful() || response.body() == null) {
                            return;
                        }
                        rescueItems.clear();
                        rescueItems.addAll(extractMapList(response.body().get("content")));
                        bindRescueUpdates();
                        bindSpotlightImages();
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    }
                });
    }

    private void bindSpotlightImages() {
        List<String> urls = new ArrayList<>();
        addSpotlightUrl(urls, getImageFromSquare(0));
        addSpotlightUrl(urls, getImageFromForum(0));
        addSpotlightUrl(urls, getImageFromRescue(0));
        addSpotlightUrl(urls, getImageFromSquare(1));
        addSpotlightUrl(urls, getImageFromForum(1));
        addSpotlightUrl(urls, getImageFromRescue(1));

        loadImage(spotlightPrimaryImage, getAt(urls, 0), PHOTO_PLACEHOLDERS[0]);
        loadImage(spotlightSecondaryImage, getAt(urls, 1), PHOTO_PLACEHOLDERS[1]);
        loadImage(spotlightTertiaryImage, getAt(urls, 2), PHOTO_PLACEHOLDERS[2]);
    }

    private void bindSquareStories() {
        if (squareStoryRow == null) {
            return;
        }
        squareStoryRow.removeAllViews();
        if (squarePhotoItems.isEmpty()) {
            bindSquareFallback();
            return;
        }
        int total = Math.min(2, squarePhotoItems.size());
        for (int i = 0; i < total; i++) {
            Map<String, Object> item = squarePhotoItems.get(i);
            long postId = longValue(item.get("id"));
            Bundle args = new Bundle();
            args.putLong("squarePostId", postId);
            addVisualCard(squareStoryRow,
                    "\u5e7f\u573a\u73b0\u573a",
                    snippet(valueOrFallback(item.get("text"), "\u9644\u8fd1\u521a\u521a\u4e0a\u4f20\u4e86\u65b0\u73b0\u573a"), 24),
                    joinParts(mapSquareStatus(stringValue(item.get("status"))), formatTime(stringValue(item.get("createdAt")))),
                    valueOrFallback(extractLocationDisplay(stringValue(item.get("location"))), "\u70b9\u5f00\u67e5\u770b\u73b0\u573a\u8be6\u60c5"),
                    findFirstImageUrl(item.get("images")),
                    PHOTO_PLACEHOLDERS[i % PHOTO_PLACEHOLDERS.length],
                    v -> Navigation.findNavController(v).navigate(R.id.square_detail, args),
                    i,
                    total);
        }
    }

    private void bindForumStories() {
        if (forumStoryRow == null) {
            return;
        }
        forumStoryRow.removeAllViews();
        if (forumPhotoItems.isEmpty()) {
            bindForumFallback();
            return;
        }
        int total = Math.min(2, forumPhotoItems.size());
        for (int i = 0; i < total; i++) {
            Map<String, Object> item = forumPhotoItems.get(i);
            long postId = longValue(item.get("id"));
            Bundle args = new Bundle();
            args.putLong("postId", postId);
            addVisualCard(forumStoryRow,
                    "\u793e\u533a\u6545\u4e8b",
                    valueOrFallback(item.get("title"), "\u4e00\u6761\u65b0\u7684\u5e26\u56fe\u5e16\u5b50"),
                    joinParts(valueOrFallback(item.get("authorName"), "\u793e\u533a\u4f19\u4f34"), formatTime(stringValue(item.get("createdAt")))),
                    snippet(stringValue(item.get("content")), 22),
                    findFirstImageUrl(item.get("images")),
                    PHOTO_PLACEHOLDERS[(i + 1) % PHOTO_PLACEHOLDERS.length],
                    v -> Navigation.findNavController(v).navigate(R.id.post_detail, args),
                    i,
                    total);
        }
    }

    private void bindRescueUpdates() {
        if (rescueUpdateList == null) {
            return;
        }
        rescueUpdateList.removeAllViews();
        if (rescueItems.isEmpty()) {
            bindRescueFallback();
            return;
        }
        int added = 0;
        for (int i = 0; i < rescueItems.size() && added < 3; i++) {
            Map<String, Object> item = rescueItems.get(i);
            long activityId = longValue(item.get("id"));
            Bundle args = new Bundle();
            args.putLong("activityId", activityId);
            addRescueCard(
                    mapRescueStatus(stringValue(item.get("status"))),
                    valueOrFallback(item.get("title"), "\u6700\u65b0\u6551\u52a9\u8fdb\u5c55"),
                    joinParts(formatTime(stringValue(item.get("createdAt"))), snippet(stringValue(item.get("description")), 20)),
                    "\u67e5\u770b\u8be6\u60c5",
                    stringValue(item.get("imageUrl")),
                    PHOTO_PLACEHOLDERS[(i + 2) % PHOTO_PLACEHOLDERS.length],
                    v -> Navigation.findNavController(v).navigate(R.id.rescue_detail, args));
            added++;
        }
        if (added == 0) {
            bindRescueFallback();
        }
    }

    private void addVisualCard(@NonNull LinearLayout row, @NonNull String badge, @NonNull String title,
            @NonNull String meta, @Nullable String hint, @Nullable String imageUrl, int fallbackRes,
            @NonNull View.OnClickListener listener, int index, int total) {
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_home_visual_post, row, false);
        ((TextView) card.findViewById(R.id.home_visual_badge)).setText(badge);
        ((TextView) card.findViewById(R.id.home_visual_title)).setText(title);
        ((TextView) card.findViewById(R.id.home_visual_meta)).setText(meta);
        TextView hintView = card.findViewById(R.id.home_visual_hint);
        if (hint == null || hint.trim().isEmpty()) {
            hintView.setVisibility(View.GONE);
        } else {
            hintView.setVisibility(View.VISIBLE);
            hintView.setText(hint);
        }
        loadImage((ImageView) card.findViewById(R.id.home_visual_image), imageUrl, fallbackRes);
        PressFeedbackUtils.attach(card);
        card.setOnClickListener(listener);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (index < total - 1) {
            params.setMarginEnd(dpToPx(10));
        }
        card.setLayoutParams(params);
        row.addView(card);
    }

    private void addRescueCard(@NonNull String status, @NonNull String title, @NonNull String meta,
            @NonNull String action, @Nullable String imageUrl, int fallbackRes,
            @NonNull View.OnClickListener listener) {
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_home_rescue_update, rescueUpdateList,
                false);
        ((TextView) card.findViewById(R.id.home_update_status)).setText(status);
        ((TextView) card.findViewById(R.id.home_update_title)).setText(title);
        ((TextView) card.findViewById(R.id.home_update_meta)).setText(meta);
        ((TextView) card.findViewById(R.id.home_update_action)).setText(action);
        loadImage((ImageView) card.findViewById(R.id.home_update_image), imageUrl, fallbackRes);
        PressFeedbackUtils.attach(card);
        card.setOnClickListener(listener);
        rescueUpdateList.addView(card);
    }

    private void addSpotlightUrl(@NonNull List<String> urls, @Nullable String url) {
        if (url == null || url.isEmpty() || urls.contains(url)) {
            return;
        }
        urls.add(url);
    }

    @Nullable
    private String getImageFromSquare(int index) {
        if (index < 0 || index >= squarePhotoItems.size()) {
            return null;
        }
        return findFirstImageUrl(squarePhotoItems.get(index).get("images"));
    }

    @Nullable
    private String getImageFromForum(int index) {
        if (index < 0 || index >= forumPhotoItems.size()) {
            return null;
        }
        return findFirstImageUrl(forumPhotoItems.get(index).get("images"));
    }

    @Nullable
    private String getImageFromRescue(int index) {
        if (index < 0 || index >= rescueItems.size()) {
            return null;
        }
        return stringValue(rescueItems.get(index).get("imageUrl"));
    }

    private void loadImage(@Nullable ImageView imageView, @Nullable String imageUrl, int fallbackRes) {
        if (imageView == null || !isAdded()) {
            return;
        }
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageView.setImageResource(fallbackRes);
            return;
        }
        Glide.with(this)
                .load(imageUrl)
                .placeholder(fallbackRes)
                .error(fallbackRes)
                .centerCrop()
                .into(imageView);
    }

    @NonNull
    private List<Map<String, Object>> extractMapList(@Nullable Object content) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!(content instanceof List)) {
            return result;
        }
        for (Object item : (List<?>) content) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }

    @NonNull
    private String findFirstImageUrl(@Nullable Object imagesObject) {
        for (String url : parseImageUrls(imagesObject)) {
            if (!url.isEmpty()) {
                return url;
            }
        }
        return "";
    }

    @NonNull
    private List<String> parseImageUrls(@Nullable Object imagesObject) {
        List<String> urls = new ArrayList<>();
        if (imagesObject == null) {
            return urls;
        }
        if (imagesObject instanceof List) {
            for (Object item : (List<?>) imagesObject) {
                String url = stringValue(item);
                if (!url.isEmpty()) {
                    urls.add(url);
                }
            }
            return urls;
        }
        String raw = stringValue(imagesObject);
        if (raw.isEmpty() || "[]".equals(raw)) {
            return urls;
        }
        try {
            Type type = new TypeToken<List<String>>() {
            }.getType();
            List<String> parsed = gson.fromJson(raw, type);
            if (parsed != null) {
                for (String url : parsed) {
                    if (url != null && !url.trim().isEmpty()) {
                        urls.add(url.trim());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return urls;
    }

    @NonNull
    private String formatTime(@Nullable String createdAt) {
        if (createdAt == null || createdAt.trim().isEmpty()) {
            return "\u521a\u521a";
        }
        String relative = TimeUtils.getRelativeTime(createdAt);
        if (!relative.isEmpty()) {
            return relative;
        }
        String normalized = createdAt.replace('T', ' ');
        return normalized.length() > 16 ? normalized.substring(0, 16) : normalized;
    }

    @NonNull
    private String mapSquareStatus(@Nullable String status) {
        if ("resolved".equalsIgnoreCase(status)) {
            return "\u5df2\u89e3\u51b3";
        }
        return "\u8fdb\u884c\u4e2d";
    }

    @NonNull
    private String mapRescueStatus(@Nullable String status) {
        if ("completed".equalsIgnoreCase(status)) {
            return "\u5df2\u5b8c\u6210";
        }
        if ("in_progress".equalsIgnoreCase(status)) {
            return "\u8fdb\u884c\u4e2d";
        }
        if ("created".equalsIgnoreCase(status)) {
            return "\u5f85\u54cd\u5e94";
        }
        return "\u6700\u65b0\u52a8\u6001";
    }

    @Nullable
    private String extractLocationDisplay(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        int spaceIndex = text.indexOf(' ');
        if (spaceIndex > 0) {
            String prefix = text.substring(0, spaceIndex).trim();
            if (prefix.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
                String rest = text.substring(spaceIndex + 1).trim();
                return rest.isEmpty() ? null : rest;
            }
        }
        if (text.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
            return null;
        }
        return text;
    }

    @NonNull
    private String joinParts(@Nullable String first, @Nullable String second) {
        String a = first != null ? first.trim() : "";
        String b = second != null ? second.trim() : "";
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + " \u00b7 " + b;
    }

    @NonNull
    private String snippet(@Nullable String text, int maxLength) {
        String value = text != null ? text.trim() : "";
        if (value.isEmpty()) {
            return "\u70b9\u8fdb\u770b\u770b\u8fd9\u5f20\u56fe\u7247\u80cc\u540e\u7684\u6545\u4e8b";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    @NonNull
    private String valueOrFallback(@Nullable Object value, @NonNull String fallback) {
        String text = stringValue(value);
        return text.isEmpty() ? fallback : text;
    }

    @Nullable
    private String getAt(@NonNull List<String> urls, int index) {
        return index >= 0 && index < urls.size() ? urls.get(index) : null;
    }

    @NonNull
    private static String stringValue(@Nullable Object value) {
        return value != null ? value.toString().trim() : "";
    }

    private static long longValue(@Nullable Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}