package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ourcat.app.R;
import com.ourcat.app.repositories.CatApi;
import com.ourcat.app.repositories.ForumApi;
import com.ourcat.app.repositories.SquareApi;
import com.ourcat.app.utils.PressFeedbackUtils;
import com.ourcat.app.utils.RecentCatStore;
import com.ourcat.app.utils.RetrofitHelper;

import java.lang.reflect.Type;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RescueHomeFragment extends Fragment {

    private static final String KEY_SCROLL_Y = "home_scroll_y";
    private static final int FEED_PAGE_SIZE = 10;
    private static final int FEED_LOAD_MORE_THRESHOLD_DP = 240;
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();
    private static final int[] PHOTO_PLACEHOLDERS = {
            R.drawable.bg_home_story_cover_amber,
            R.drawable.bg_home_story_cover_mint,
            R.drawable.bg_home_story_cover_blue
    };

    private final Gson gson = new Gson();
    private final List<Map<String, Object>> squareItems = new ArrayList<>();
    private final List<Map<String, Object>> forumItems = new ArrayList<>();
    private int squarePage = 0;
    private int forumPage = 0;
    private boolean squareHasMore = true;
    private boolean forumHasMore = true;
    private boolean squareLoading;
    private boolean forumLoading;

    private ScrollView homeScrollView;
    private TextView spotlightBadgeView;
    private TextView spotlightTitleView;
    private TextView spotlightDescView;
    private TextView spotlightStatusView;
    private TextView spotlightMetaOneView;
    private TextView spotlightMetaTwoView;
    private TextView spotlightActionView;
    private ImageView spotlightCoverView;
    private LinearLayout feedColumnLeft;
    private LinearLayout feedColumnRight;
    private long spotlightCatId;
    private int retainedScrollY;
    private boolean shouldRestoreScroll;

    private static final class FeedCardModel {
        final String badge;
        final String title;
        final String meta;
        final String hint;
        final String imageUrl;
        final boolean hasImage;
        final int fallbackRes;
        final int destinationId;
        final Bundle args;
        final long timestamp;

        FeedCardModel(@NonNull String badge, @NonNull String title, @NonNull String meta, @NonNull String hint,
                @Nullable String imageUrl, boolean hasImage, int fallbackRes, int destinationId, @NonNull Bundle args,
                long timestamp) {
            this.badge = badge;
            this.title = title;
            this.meta = meta;
            this.hint = hint;
            this.imageUrl = imageUrl != null ? imageUrl : "";
            this.hasImage = hasImage;
            this.fallbackRes = fallbackRes;
            this.destinationId = destinationId;
            this.args = args;
            this.timestamp = timestamp;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        retainedScrollY = savedInstanceState != null ? savedInstanceState.getInt(KEY_SCROLL_Y, retainedScrollY)
                : retainedScrollY;
        shouldRestoreScroll = retainedScrollY > 0;

        View view = inflater.inflate(R.layout.fragment_rescue_home, container, false);
        homeScrollView = view.findViewById(R.id.home_scroll_view);
        spotlightBadgeView = view.findViewById(R.id.home_spotlight_badge);
        spotlightTitleView = view.findViewById(R.id.home_spotlight_title);
        spotlightDescView = view.findViewById(R.id.home_spotlight_desc);
        spotlightStatusView = view.findViewById(R.id.home_spotlight_status_chip);
        spotlightMetaOneView = view.findViewById(R.id.home_spotlight_meta_one);
        spotlightMetaTwoView = view.findViewById(R.id.home_spotlight_meta_two);
        spotlightActionView = view.findViewById(R.id.home_spotlight_action);
        spotlightCoverView = view.findViewById(R.id.home_spotlight_cover);
        feedColumnLeft = view.findViewById(R.id.home_feed_column_left);
        feedColumnRight = view.findViewById(R.id.home_feed_column_right);

        bindStaticCopy(view);
        bindInteractions(view);
        bindSpotlightFallback();
        bindFeedFallback();
        setupFeedScrollListener();
        resetFeedState();
        refreshSpotlightCard();
        loadMoreStories();
        maybeRestoreScroll();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSpotlightCard();
        maybeRestoreScroll();
    }

    @Override
    public void onPause() {
        captureScrollState();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        captureScrollState();
        outState.putInt(KEY_SCROLL_Y, retainedScrollY);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroyView() {
        captureScrollState();
        if (homeScrollView != null) {
            homeScrollView.setOnScrollChangeListener(null);
        }
        homeScrollView = null;
        spotlightBadgeView = null;
        spotlightTitleView = null;
        spotlightDescView = null;
        spotlightStatusView = null;
        spotlightMetaOneView = null;
        spotlightMetaTwoView = null;
        spotlightActionView = null;
        spotlightCoverView = null;
        feedColumnLeft = null;
        feedColumnRight = null;
        super.onDestroyView();
    }

    private void bindStaticCopy(@NonNull View view) {
        setText(view, R.id.home_search_text, "搜猫咪、广播现场和论坛帖子");
        setText(view, R.id.home_entry_square_text, "广场广播");
        setText(view, R.id.home_entry_forum_text, "论坛帖子");
        setText(view, R.id.home_entry_latest_text, "最新行动");
        setText(view, R.id.home_entry_guide_text, "救助指南");
    }

    private void bindInteractions(@NonNull View view) {
        setupEntry(view, R.id.home_search_entry, v -> {
            Bundle args = new Bundle();
            args.putString("query", "");
            Navigation.findNavController(v).navigate(R.id.search_result, args);
        });
        setupEntry(view, R.id.home_button_publish,
                v -> Navigation.findNavController(v).navigate(R.id.create_square_post));
        setupEntry(view, R.id.entry_square, v -> Navigation.findNavController(v).navigate(R.id.nav_square));
        setupEntry(view, R.id.entry_forum, v -> Navigation.findNavController(v).navigate(R.id.forum_list));
        setupEntry(view, R.id.entry_latest, v -> Navigation.findNavController(v).navigate(R.id.rescue_list));
        setupEntry(view, R.id.entry_guide, v -> Navigation.findNavController(v).navigate(R.id.rescue_guide));
        setupEntry(view, R.id.home_spotlight_card, this::openSpotlightDestination);
        setupEntry(view, R.id.home_spotlight_action, this::openSpotlightDestination);
    }

    private void setupEntry(@NonNull View root, int viewId, @NonNull View.OnClickListener listener) {
        View target = root.findViewById(viewId);
        if (target == null) {
            return;
        }
        target.setOnClickListener(listener);
        PressFeedbackUtils.attach(target, 0.985f);
    }

    private void openSpotlightDestination(@NonNull View view) {
        if (spotlightCatId > 0L) {
            Bundle args = new Bundle();
            args.putLong("catId", spotlightCatId);
            Navigation.findNavController(view).navigate(R.id.cat_profile, args);
            return;
        }
        Navigation.findNavController(view).navigate(R.id.nav_cats);
    }

    private void bindSpotlightFallback() {
        spotlightCatId = 0L;
        loadImage(spotlightCoverView, null, PHOTO_PLACEHOLDERS[0]);
        applySpotlightCopy(
                "最近看过",
                "继续浏览",
                "把你刚看过的猫继续接回来",
                "这里会记住你最近打开过的猫咪档案，返回首页时不用重新寻找。",
                "猫咪档案",
                "随时续看",
                "继续查看");
    }

    private void refreshSpotlightCard() {
        if (!isAdded()) {
            return;
        }
        RecentCatStore.Entry recentEntry = RecentCatStore.read(requireContext());
        if (recentEntry != null && recentEntry.isValid()) {
            bindRecentSpotlight(recentEntry);
            maybeRestoreScroll();
            return;
        }
        bindSpotlightFallback();
        loadSpotlightCandidate();
        maybeRestoreScroll();
    }

    private void bindRecentSpotlight(@NonNull RecentCatStore.Entry entry) {
        spotlightCatId = entry.catId;
        loadImage(spotlightCoverView, entry.imageUrl, PHOTO_PLACEHOLDERS[0]);
        applySpotlightCopy(
                "最近看过",
                entry.hasActiveRescue ? "救助跟进中" : getCatStatusText(entry.status),
                buildRecentSpotlightTitle(entry),
                buildRecentSpotlightDesc(entry),
                buildSpotlightMeta(entry.reportCount, entry.feature, entry.personality),
                buildViewedMeta(entry.viewedAt),
                "继续查看");
    }

    private void loadSpotlightCandidate() {
        RetrofitHelper.get().create(CatApi.class).listCats(0, 8, null, null, null, null, null)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(@NonNull Call<Map<String, Object>> call,
                            @NonNull Response<Map<String, Object>> response) {
                        if (!isAdded() || !response.isSuccessful() || response.body() == null) {
                            return;
                        }
                        RecentCatStore.Entry latestEntry = RecentCatStore.read(requireContext());
                        if (latestEntry != null && latestEntry.isValid()) {
                            bindRecentSpotlight(latestEntry);
                            return;
                        }
                        Map<String, Object> candidate = pickSpotlightCandidate(
                                extractMapList(response.body().get("content")));
                        if (candidate != null) {
                            bindSuggestedSpotlight(candidate);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable throwable) {
                    }
                });
    }
    @Nullable
    private Map<String, Object> pickSpotlightCandidate(@NonNull List<Map<String, Object>> cats) {
        Map<String, Object> backup = null;
        for (Map<String, Object> cat : cats) {
            if (backup == null) {
                backup = cat;
            }
            String status = stringValue(cat.get("status"));
            if ("adopted".equalsIgnoreCase(status) || "deceased".equalsIgnoreCase(status)) {
                continue;
            }
            if (!stringValue(cat.get("primaryImageUrl")).isEmpty()) {
                return cat;
            }
        }
        return backup;
    }

    private void bindSuggestedSpotlight(@NonNull Map<String, Object> cat) {
        long candidateCatId = longValue(cat.get("id"));
        if (candidateCatId <= 0L) {
            return;
        }
        spotlightCatId = candidateCatId;
        String feature = stringValue(cat.get("feature"));
        String personality = stringValue(cat.get("personality"));
        int reportCount = intValue(cat.get("reportCount"));
        boolean hasActiveRescue = Boolean.TRUE.equals(cat.get("hasActiveRescue"));

        loadImage(spotlightCoverView, stringValue(cat.get("primaryImageUrl")), PHOTO_PLACEHOLDERS[1]);
        applySpotlightCopy(
                "\u503c\u5f97\u5173\u6ce8",
                hasActiveRescue ? "\u6551\u52a9\u8ddf\u8fdb\u4e2d" : getCatStatusText(stringValue(cat.get("status"))),
                buildSuggestedSpotlightTitle(stringValue(cat.get("name"))),
                buildSuggestedSpotlightDesc(feature, personality, reportCount, hasActiveRescue),
                buildSpotlightMeta(reportCount, feature, personality),
                "\u732b\u54aa\u6863\u6848",
                "\u53bb\u770b\u770b");
    }

    private void applySpotlightCopy(@NonNull String badge, @NonNull String status, @NonNull String title,
            @NonNull String desc, @NonNull String metaOne, @NonNull String metaTwo, @NonNull String actionText) {
        if (spotlightBadgeView != null) {
            spotlightBadgeView.setText(badge);
        }
        if (spotlightStatusView != null) {
            spotlightStatusView.setText(status);
        }
        if (spotlightTitleView != null) {
            spotlightTitleView.setText(title);
        }
        if (spotlightDescView != null) {
            spotlightDescView.setText(desc);
        }
        if (spotlightMetaOneView != null) {
            spotlightMetaOneView.setText(metaOne);
        }
        if (spotlightMetaTwoView != null) {
            spotlightMetaTwoView.setText(metaTwo);
        }
        if (spotlightActionView != null) {
            spotlightActionView.setText(actionText);
        }
    }

    @NonNull
    private String buildRecentSpotlightTitle(@NonNull RecentCatStore.Entry entry) {
        String name = entry.name.trim().isEmpty() ? "\u8fd9\u53ea\u5c0f\u5bb6\u4f19" : entry.name.trim();
        return entry.hasActiveRescue ? name + " \u6709\u65b0\u7684\u6551\u52a9\u8fdb\u5c55" : "\u7ee7\u7eed\u770b\u770b " + name;
    }

    @NonNull
    private String buildRecentSpotlightDesc(@NonNull RecentCatStore.Entry entry) {
        if (entry.hasActiveRescue) {
            return "\u5b83\u6b63\u5728\u88ab\u6301\u7eed\u5173\u6ce8\uff0c\u56de\u5230\u6863\u6848\u9875\u53ef\u4ee5\u7ee7\u7eed\u770b\u6700\u65b0\u8bb0\u5f55\u548c\u72b6\u6001\u53d8\u5316\u3002";
        }
        String traitSummary = buildTraitSummary(entry.feature, entry.personality);
        if (!traitSummary.isEmpty()) {
            return "\u4f60\u4e0a\u6b21\u505c\u5728\u5b83\u7684\u6863\u6848\u9875\uff0c\u8fd9\u6b21\u53ef\u4ee5\u63a5\u7740\u770b\u770b " + traitSummary + "\u3002";
        }
        if (entry.reportCount > 0) {
            return "\u8fd9\u53ea\u732b\u5df2\u7ecf\u79ef\u7d2f\u4e86 " + entry.reportCount + " \u6761\u4e0a\u62a5\u8bb0\u5f55\uff0c\u56de\u53bb\u770b\u770b\u6700\u8fd1\u7559\u4e0b\u4e86\u4ec0\u4e48\u7ebf\u7d22\u3002";
        }
        return "\u56de\u5230\u5b83\u7684\u6863\u6848\u9875\uff0c\u7ee7\u7eed\u67e5\u770b\u7167\u7247\u3001\u8bb0\u5f55\u548c\u6700\u8fd1\u72b6\u6001\u3002";
    }

    @NonNull
    private String buildSuggestedSpotlightTitle(@Nullable String rawName) {
        String name = rawName != null ? rawName.trim() : "";
        return name.isEmpty() ? "\u8ba4\u8bc6\u4e00\u53ea\u503c\u5f97\u5173\u6ce8\u7684\u732b" : "\u8ba4\u8bc6\u4e00\u4e0b " + name;
    }

    @NonNull
    private String buildSuggestedSpotlightDesc(@Nullable String feature, @Nullable String personality, int reportCount,
            boolean hasActiveRescue) {
        if (hasActiveRescue) {
            return "\u5b83\u6b63\u5728\u88ab\u6551\u52a9\uff0c\u70b9\u8fdb\u53bb\u53ef\u4ee5\u7ee7\u7eed\u770b\u6863\u6848\u548c\u6700\u8fd1\u7167\u62a4\u8fdb\u5c55\u3002";
        }
        String traitSummary = buildTraitSummary(feature, personality);
        if (!traitSummary.isEmpty()) {
            return "\u6863\u6848\u91cc\u5df2\u7ecf\u7559\u4e0b\u4e86 " + traitSummary + "\uff0c\u70b9\u8fdb\u53bb\u4f1a\u66f4\u5bb9\u6613\u8bb0\u4f4f\u5b83\u3002";
        }
        if (reportCount > 0) {
            return "\u5b83\u5df2\u7ecf\u6709 " + reportCount + " \u6761\u4e0a\u62a5\u8bb0\u5f55\uff0c\u70b9\u8fdb\u53bb\u770b\u770b\u6700\u8fd1\u53d1\u751f\u4e86\u4ec0\u4e48\u3002";
        }
        return "\u6253\u5f00\u6863\u6848\uff0c\u770b\u770b\u5b83\u7684\u7167\u7247\u3001\u72b6\u6001\u548c\u66fe\u7ecf\u7559\u4e0b\u7684\u73b0\u573a\u8bb0\u5f55\u3002";
    }

    @NonNull
    private String buildSpotlightMeta(int reportCount, @Nullable String feature, @Nullable String personality) {
        if (reportCount > 0) {
            return "\u4e0a\u62a5 " + reportCount + " \u6b21";
        }
        String traitSummary = buildTraitSummary(feature, personality);
        return traitSummary.isEmpty() ? "\u732b\u54aa\u6863\u6848" : traitSummary;
    }

    @NonNull
    private String buildTraitSummary(@Nullable String feature, @Nullable String personality) {
        String featureText = feature != null ? feature.trim() : "";
        String personalityText = personality != null ? personality.trim() : "";
        if (!featureText.isEmpty() && !personalityText.isEmpty()) {
            return featureText + " \u00b7 " + personalityText;
        }
        if (!featureText.isEmpty()) {
            return featureText;
        }
        return personalityText;
    }

    @NonNull
    private String buildViewedMeta(long viewedAt) {
        if (viewedAt <= 0L) {
            return "\u521a\u521a\u6d4f\u89c8";
        }
        long diff = System.currentTimeMillis() - viewedAt;
        if (diff < 60_000L) {
            return "\u521a\u521a\u6d4f\u89c8";
        }
        long minutes = diff / 60_000L;
        if (minutes < 60L) {
            return minutes + " \u5206\u949f\u524d\u6d4f\u89c8";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return hours + " \u5c0f\u65f6\u524d\u6d4f\u89c8";
        }
        long days = hours / 24L;
        if (days < 7L) {
            return days + " \u5929\u524d\u6d4f\u89c8";
        }
        return "\u6700\u8fd1\u6d4f\u89c8";
    }

    @NonNull
    private String getCatStatusText(@Nullable String status) {
        String normalized = status != null ? status.trim() : "";
        if (normalized.isEmpty()) {
            return "\u5f85\u5173\u6ce8";
        }
        switch (normalized) {
            case "active":
            case "\u6d3b\u8dc3":
                return "\u6d3b\u8dc3";
            case "missing":
            case "\u5931\u8e2a":
                return "\u5931\u8e2a";
            case "adopted":
            case "\u6536\u517b":
            case "\u5df2\u5b89\u7f6e":
                return "\u5df2\u5b89\u7f6e";
            case "injured":
            case "\u53d7\u4f24":
                return "\u53d7\u4f24";
            case "disease":
            case "\u751f\u75c5":
            case "\u75be\u75c5":
                return "\u751f\u75c5";
            case "plight":
            case "\u56f0\u5883":
                return "\u56f0\u5883";
            case "deceased":
            case "\u5df2\u6545":
                return "\u5df2\u6545";
            default:
                return normalized;
        }
    }

    private void bindFeedFallback() {
        if (feedColumnLeft == null || feedColumnRight == null) {
            return;
        }
        feedColumnLeft.removeAllViews();
        feedColumnRight.removeAllViews();
        addFeedCard(feedColumnLeft, new FeedCardModel(
                "\u5e7f\u573a\u5e7f\u64ad",
                "\u65b0\u7684\u5e7f\u573a\u73b0\u573a\u4f1a\u4ece\u8fd9\u91cc\u6309\u65f6\u95f4\u987a\u5e8f\u5c55\u793a",
                "\u6b63\u5728\u6574\u7406 \u00b7 \u521a\u521a",
                "\u6709\u56fe\u5c31\u76f4\u63a5\u5c55\u793a\u5b9e\u62cd\uff0c\u6ca1\u6709\u56fe\u5c31\u4fdd\u7559\u6587\u5b57\u4fe1\u606f\uff0c\u4e0d\u518d\u653e\u56fe\u7247\u5360\u4f4d\u3002",
                null,
                false,
                PHOTO_PLACEHOLDERS[0],
                R.id.nav_square,
                new Bundle(),
                0L));
        addFeedCard(feedColumnRight, new FeedCardModel(
                "\u8bba\u575b\u5e16\u5b50",
                "\u8bba\u575b\u91cc\u7684\u6700\u65b0\u5e16\u5b50\u4e5f\u4f1a\u4e00\u8d77\u5e76\u8fdb\u8fd9\u6761\u52a8\u6001\u6d41",
                "\u6b63\u5728\u6574\u7406 \u00b7 \u521a\u521a",
                "\u8fd9\u91cc\u73b0\u5728\u53ea\u6df7\u6392\u5e7f\u573a\u548c\u8bba\u575b\uff0c\u987a\u5e8f\u56fa\u5b9a\u4e3a\u4ece\u65b0\u5230\u65e7\u3002",
                null,
                false,
                PHOTO_PLACEHOLDERS[1],
                R.id.forum_list,
                new Bundle(),
                0L));
    }

    private void setupFeedScrollListener() {
        if (homeScrollView == null) {
            return;
        }
        homeScrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (scrollY > oldScrollY && isNearFeedBottom()) {
                loadMoreStories();
            }
        });
    }

    private void resetFeedState() {
        squareItems.clear();
        forumItems.clear();
        squarePage = 0;
        forumPage = 0;
        squareHasMore = true;
        forumHasMore = true;
        squareLoading = false;
        forumLoading = false;
    }

    private boolean isNearFeedBottom() {
        if (homeScrollView == null || homeScrollView.getChildCount() == 0) {
            return false;
        }
        View content = homeScrollView.getChildAt(0);
        int threshold = dp(FEED_LOAD_MORE_THRESHOLD_DP);
        int remaining = content.getBottom() - (homeScrollView.getHeight() + homeScrollView.getScrollY());
        return remaining <= threshold;
    }

    private void loadMoreStories() {
        if (!isAdded()) {
            return;
        }
        if (squareLoading || forumLoading) {
            return;
        }
        if (!squareHasMore && !forumHasMore) {
            return;
        }
        if (squareHasMore) {
            loadSquareStories();
        }
        if (forumHasMore) {
            loadForumStories();
        }
    }

    private void loadSquareStories() {
        final int requestPage = squarePage;
        squareLoading = true;
        RetrofitHelper.get().create(SquareApi.class).listPosts(requestPage, FEED_PAGE_SIZE, "createdAt,desc")
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(@NonNull Call<Map<String, Object>> call,
                            @NonNull Response<Map<String, Object>> response) {
                        squareLoading = false;
                        if (!isAdded() || !response.isSuccessful() || response.body() == null) {
                            return;
                        }
                        List<Map<String, Object>> incoming = extractMapList(response.body().get("content"));
                        squareItems.addAll(incoming);
                        squarePage = requestPage + 1;
                        squareHasMore = hasMoreData(response.body(), incoming.size(), squareItems.size(), requestPage);
                        renderUnifiedContent();
                        if (isNearFeedBottom()) {
                            loadMoreStories();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable throwable) {
                        squareLoading = false;
                    }
                });
    }

    private void loadForumStories() {
        final int requestPage = forumPage;
        forumLoading = true;
        RetrofitHelper.get().create(ForumApi.class).listPosts(requestPage, FEED_PAGE_SIZE, null)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(@NonNull Call<Map<String, Object>> call,
                            @NonNull Response<Map<String, Object>> response) {
                        forumLoading = false;
                        if (!isAdded() || !response.isSuccessful() || response.body() == null) {
                            return;
                        }
                        List<Map<String, Object>> incoming = extractMapList(response.body().get("content"));
                        forumItems.addAll(incoming);
                        forumPage = requestPage + 1;
                        forumHasMore = hasMoreData(response.body(), incoming.size(), forumItems.size(), requestPage);
                        renderUnifiedContent();
                        if (isNearFeedBottom()) {
                            loadMoreStories();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable throwable) {
                        forumLoading = false;
                    }
                });
    }
    private void renderUnifiedContent() {
        if (!isAdded() || feedColumnLeft == null || feedColumnRight == null) {
            return;
        }
        List<FeedCardModel> cards = buildMergedFeed();
        feedColumnLeft.removeAllViews();
        feedColumnRight.removeAllViews();
        if (cards.isEmpty()) {
            bindFeedFallback();
            maybeRestoreScroll();
            return;
        }
        for (int i = 0; i < cards.size(); i++) {
            addFeedCard(i % 2 == 0 ? feedColumnLeft : feedColumnRight, cards.get(i));
        }
        maybeRestoreScroll();
    }

    @NonNull
    private List<FeedCardModel> buildMergedFeed() {
        List<FeedCardModel> cards = new ArrayList<>();
        for (int i = 0; i < squareItems.size(); i++) {
            FeedCardModel model = buildSquareFeedCard(squareItems.get(i), i);
            if (model != null) {
                cards.add(model);
            }
        }
        for (int i = 0; i < forumItems.size(); i++) {
            FeedCardModel model = buildForumFeedCard(forumItems.get(i), i);
            if (model != null) {
                cards.add(model);
            }
        }
        cards.sort(Comparator.comparingLong((FeedCardModel item) -> item.timestamp).reversed());
        return cards;
    }

    private boolean hasMoreData(@NonNull Map<String, Object> body, int incomingCount, int loadedCount, int currentPage) {
        int totalPages = intValue(body.get("totalPages"));
        if (totalPages > 0) {
            return currentPage + 1 < totalPages;
        }
        long totalElements = longValue(body.get("totalElements"));
        if (totalElements > 0L) {
            return loadedCount < totalElements;
        }
        return incomingCount >= FEED_PAGE_SIZE;
    }

    @Nullable
    private FeedCardModel buildSquareFeedCard(@NonNull Map<String, Object> item, int index) {
        long postId = longValue(item.get("id"));
        if (postId <= 0L) {
            return null;
        }

        String text = compactText(stringValue(item.get("text")));
        String title = text.isEmpty() ? "\u4e00\u6761\u65b0\u7684\u5e7f\u573a\u5e7f\u64ad" : ellipsize(text, 34);
        String status = mapSquareStatus(stringValue(item.get("status")));
        String time = formatTime(stringValue(item.get("createdAt")));
        String location = compactText(stringValue(item.get("location")));
        String imageUrl = extractFirstImageUrl(item.get("images"));
        boolean hasImage = !imageUrl.isEmpty();
        String hint = !location.isEmpty()
                ? "\u5730\u70b9\uff1a" + ellipsize(location, 28)
                : "\u70b9\u51fb\u67e5\u770b\u73b0\u573a\u8be6\u60c5\u548c\u8bc4\u8bba";

        Bundle args = new Bundle();
        args.putLong("squarePostId", postId);
        return new FeedCardModel(
                "\u5e7f\u573a\u5e7f\u64ad",
                title,
                joinParts(status, time),
                hint,
                imageUrl,
                hasImage,
                PHOTO_PLACEHOLDERS[index % PHOTO_PLACEHOLDERS.length],
                R.id.square_detail,
                args,
                parseTimestamp(stringValue(item.get("createdAt"))));
    }

    @Nullable
    private FeedCardModel buildForumFeedCard(@NonNull Map<String, Object> item, int index) {
        long postId = longValue(item.get("id"));
        if (postId <= 0L) {
            return null;
        }

        String title = compactText(stringValue(item.get("title")));
        String content = compactText(stringValue(item.get("content")));
        String resolvedTitle = !title.isEmpty() ? ellipsize(title, 34)
                : (!content.isEmpty() ? ellipsize(content, 34) : "\u4e00\u6761\u65b0\u7684\u8bba\u575b\u5e16\u5b50");
        String hint = !title.isEmpty() && !content.isEmpty()
                ? ellipsize(content, 42)
                : "\u70b9\u51fb\u67e5\u770b\u5b8c\u6574\u5e16\u5b50\u548c\u8bc4\u8bba";
        String imageUrl = extractFirstImageUrl(item.get("images"));
        boolean hasImage = !imageUrl.isEmpty();
        String badge = Boolean.TRUE.equals(item.get("pinned")) ? "\u8bba\u575b\u7f6e\u9876" : "\u8bba\u575b\u5e16\u5b50";

        Bundle args = new Bundle();
        args.putLong("postId", postId);
        return new FeedCardModel(
                badge,
                resolvedTitle,
                joinParts(valueOrFallback(item.get("authorName"), "\u793e\u533a\u4f19\u4f34"),
                        formatTime(stringValue(item.get("createdAt")))),
                hint,
                imageUrl,
                hasImage,
                PHOTO_PLACEHOLDERS[(index + 1) % PHOTO_PLACEHOLDERS.length],
                R.id.post_detail,
                args,
                parseTimestamp(stringValue(item.get("createdAt"))));
    }

    private void addFeedCard(@NonNull LinearLayout parent, @NonNull FeedCardModel model) {
        if (parent == null || !isAdded()) {
            return;
        }
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_home_rescue_story, parent,
                false);
        MaterialCardView cardRoot = card.findViewById(R.id.home_visual_card);
        FrameLayout mediaView = card.findViewById(R.id.home_visual_media);
        LinearLayout contentView = card.findViewById(R.id.home_visual_content);
        ImageView imageView = card.findViewById(R.id.home_visual_image);
        TextView badgeView = card.findViewById(R.id.home_visual_badge);
        TextView inlineBadgeView = card.findViewById(R.id.home_visual_badge_inline);
        TextView titleView = card.findViewById(R.id.home_visual_title);
        TextView metaView = card.findViewById(R.id.home_visual_meta);
        TextView hintView = card.findViewById(R.id.home_visual_hint);

        badgeView.setText(model.badge);
        inlineBadgeView.setText(model.badge);
        titleView.setText(model.title);
        metaView.setText(model.meta);
        hintView.setText(model.hint);

        cardRoot.setRadius(dp(6));
        cardRoot.setStrokeWidth(dp(1));
        cardRoot.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.divider));
        cardRoot.setCardBackgroundColor(ContextCompat.getColor(requireContext(),
                model.hasImage ? R.color.surface : R.color.primary_surface));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);

        if (model.hasImage) {
            mediaView.setVisibility(View.VISIBLE);
            inlineBadgeView.setVisibility(View.GONE);
            contentView.setPadding(dp(13), dp(11), dp(13), dp(12));
            loadImage(imageView, model.imageUrl, model.fallbackRes);
        } else {
            mediaView.setVisibility(View.GONE);
            inlineBadgeView.setVisibility(View.VISIBLE);
            contentView.setPadding(dp(13), dp(12), dp(13), dp(12));
            imageView.setImageDrawable(null);
        }

        card.setOnClickListener(v -> Navigation.findNavController(v).navigate(model.destinationId, model.args));
        PressFeedbackUtils.attach(card, 0.988f);
        parent.addView(card);
    }

    private void captureScrollState() {
        if (homeScrollView == null) {
            return;
        }
        retainedScrollY = homeScrollView.getScrollY();
    }

    private void maybeRestoreScroll() {
        if (!shouldRestoreScroll || homeScrollView == null) {
            return;
        }
        homeScrollView.post(() -> {
            if (homeScrollView == null) {
                return;
            }
            homeScrollView.scrollTo(0, retainedScrollY);
            shouldRestoreScroll = false;
        });
    }

    private void loadImage(@Nullable ImageView imageView, @Nullable String imageUrl, int fallbackRes) {
        if (imageView == null) {
            return;
        }
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            imageView.setImageResource(fallbackRes);
            return;
        }
        Glide.with(imageView)
                .load(imageUrl)
                .placeholder(fallbackRes)
                .error(fallbackRes)
                .centerCrop()
                .into(imageView);
    }
    @NonNull
    private List<Map<String, Object>> extractMapList(@Nullable Object raw) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (!(raw instanceof List)) {
            return list;
        }
        for (Object item : (List<?>) raw) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) item;
                list.add(casted);
            }
        }
        return list;
    }

    @NonNull
    private String extractFirstImageUrl(@Nullable Object raw) {
        if (raw == null) {
            return "";
        }
        if (raw instanceof List) {
            for (Object item : (List<?>) raw) {
                String candidate = stringValue(item);
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
            return "";
        }
        if (raw instanceof String) {
            String value = ((String) raw).trim();
            if (value.isEmpty()) {
                return "";
            }
            if (value.startsWith("[")) {
                try {
                    List<String> urls = gson.fromJson(value, STRING_LIST_TYPE);
                    if (urls != null) {
                        for (String url : urls) {
                            if (url != null && !url.trim().isEmpty()) {
                                return url.trim();
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
                return "";
            }
            return value;
        }
        return stringValue(raw);
    }

    @NonNull
    private String compactText(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace('\n', ' ').replace('\r', ' ').trim().replaceAll("\\s{2,}", " ");
    }

    @NonNull
    private String ellipsize(@NonNull String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)).trim() + "\u2026";
    }

    @NonNull
    private String valueOrFallback(@Nullable Object value, @NonNull String fallback) {
        String text = stringValue(value);
        return text.isEmpty() ? fallback : text;
    }

    @NonNull
    private String mapSquareStatus(@Nullable String status) {
        String normalized = status != null ? status.trim() : "";
        if ("resolved".equalsIgnoreCase(normalized)) {
            return "\u5df2\u89e3\u51b3";
        }
        if ("open".equalsIgnoreCase(normalized) || normalized.isEmpty()) {
            return "\u8fdb\u884c\u4e2d";
        }
        return normalized;
    }

    @NonNull
    private String formatTime(@Nullable String createdAt) {
        long timestamp = parseTimestamp(createdAt);
        if (timestamp <= 0L) {
            return "\u521a\u521a";
        }
        long diff = System.currentTimeMillis() - timestamp;
        if (diff < 60_000L) {
            return "\u521a\u521a";
        }
        long minutes = diff / 60_000L;
        if (minutes < 60L) {
            return minutes + " \u5206\u949f\u524d";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return hours + " \u5c0f\u65f6\u524d";
        }
        long days = hours / 24L;
        if (days < 30L) {
            return days + " \u5929\u524d";
        }
        Date date = new Date(timestamp);
        return new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(date);
    }

    private long parseTimestamp(@Nullable String raw) {
        if (raw == null) {
            return 0L;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return 0L;
        }

        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                if (!pattern.endsWith("XXX") && !pattern.endsWith("'Z'")) {
                    format.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date date = format.parse(value);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return 0L;
    }
    @NonNull
    private String joinParts(@Nullable String first, @Nullable String second) {
        boolean hasFirst = first != null && !first.trim().isEmpty();
        boolean hasSecond = second != null && !second.trim().isEmpty();
        if (hasFirst && hasSecond) {
            return first.trim() + " \u00b7 " + second.trim();
        }
        if (hasFirst) {
            return first.trim();
        }
        if (hasSecond) {
            return second.trim();
        }
        return "";
    }

    @NonNull
    private String stringValue(@Nullable Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private long longValue(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(stringValue(value));
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private int intValue(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(stringValue(value));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }

    private void setText(@NonNull View root, int viewId, @NonNull String text) {
        TextView textView = root.findViewById(viewId);
        if (textView != null) {
            textView.setText(text);
        }
    }
}
