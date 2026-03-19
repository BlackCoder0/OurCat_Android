package com.ourcat.app.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
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
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.ourcat.app.R;
import com.ourcat.app.adapters.ImagePagerAdapter;
import com.ourcat.app.repositories.CatApi;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.repositories.SquareApi;
import com.ourcat.app.utils.CatTagUtils;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private TextView authorText, timeText;
    private ImageView authorAvatar;
    private TextView textView;
    private ViewPager2 imagePager;
    private ImagePagerAdapter imagePagerAdapter;
    private View metaBlock;
    private View locationRow;
    private ImageView locationIcon;
    private TextView locationText;
    private TextView locationAnnotationText;
    private TextView locationMismatchHintText;
    private MaterialButton btnMarkSolved;
    private View rescueCardContainer;
    private View rescueActivityCard;
    private TextView rescueActivityTitle;
    private TextView rescueActivityStatus;
    private View rescueActionsCard;
    private MaterialButton rescueClaimButton;
    private MaterialButton rescueAssignButton;
    private RecyclerView rescueTasksView;
    private TextView rescueTasksEmpty;
    private RescueTasksAdapter rescueTasksAdapter;
    private RecyclerView rescueTimelineView;
    private RescueTimelineAdapter rescueTimelineAdapter;
    private final List<TimelineItem> rescueTimelineItems = new ArrayList<>();
    private long timelineLoadedActivityId;
    private View refCatCard;
    private ImageView refCatAvatar;
    private TextView refCatName;
    private ViewGroup commentsContainer;
    private EditText commentInput;
    private static final double CAT_LOCATION_MISMATCH_THRESHOLD_METERS = 3000.0;

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
        authorText = view.findViewById(R.id.square_detail_author);
        timeText = view.findViewById(R.id.square_detail_time);
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
        metaBlock = view.findViewById(R.id.square_detail_meta_block);
        locationRow = view.findViewById(R.id.square_detail_location_row);
        locationIcon = view.findViewById(R.id.square_detail_location_icon);
        locationText = view.findViewById(R.id.square_detail_location);
        locationAnnotationText = view.findViewById(R.id.square_detail_location_annotation);
        locationMismatchHintText = view.findViewById(R.id.square_detail_location_mismatch_hint);
        btnMarkSolved = view.findViewById(R.id.btn_mark_solved);
        rescueCardContainer = view.findViewById(R.id.square_rescue_card_container);
        rescueActivityCard = view.findViewById(R.id.square_rescue_activity_card);
        rescueActivityTitle = view.findViewById(R.id.square_rescue_activity_title);
        rescueActivityStatus = view.findViewById(R.id.square_rescue_activity_status);
        rescueActionsCard = view.findViewById(R.id.square_rescue_actions_card);
        rescueClaimButton = view.findViewById(R.id.square_rescue_claim);
        rescueAssignButton = view.findViewById(R.id.square_rescue_assign);
        rescueTasksView = view.findViewById(R.id.square_rescue_tasks);
        rescueTasksEmpty = view.findViewById(R.id.square_rescue_tasks_empty);
        rescueTimelineView = view.findViewById(R.id.square_rescue_timeline);
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

        rescueTimelineAdapter = new RescueTimelineAdapter();
        LinearLayoutManager timelineLayoutManager = new LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false);
        rescueTimelineView.setLayoutManager(timelineLayoutManager);
        rescueTimelineView.setAdapter(rescueTimelineAdapter);
        new LinearSnapHelper().attachToRecyclerView(rescueTimelineView);

        rescueCardContainer.setVisibility(View.GONE);
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
        int chipColor = "rescue".equals(type) ? getResources().getColor(R.color.primary, null)
                : Color.parseColor("#1976D2");
        typeChip.setChipBackgroundColor(ColorStateList.valueOf(chipColor));
        typeChip.setTextColor(Color.WHITE);
        authorText.setText(authorName);
        timeText.setText(TimeUtils.getRelativeTime(stringValue(postData.get("createdAt"), "")));

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
        boolean allowLocationJump = !("rescue".equals(type) && longValue(postData.get("rescueActivityId")) > 0);
        bindLocation(postData.get("location"), allowLocationJump);
        bindReferencedCat(postData.get("referencedCat"), postData.get("referencedCatId"));
        bindCatLocationDistanceHint();
        bindRescueSection(type, status);

        boolean canMarkSolved = canCurrentUserMarkSolved(type, status, postUserId);
        btnMarkSolved.setVisibility(canMarkSolved ? View.VISIBLE : View.GONE);
        if (resolvedOverlay != null) {
            resolvedOverlay.setVisibility(View.GONE);
        }
    }

    private boolean canCurrentUserMarkSolved(String type, String postStatus, long postUserId) {
        if (currentUserId <= 0 || "resolved".equals(postStatus)) {
            return false;
        }
        if (!"rescue".equals(type)) {
            return currentUserId == postUserId || currentUserRole >= 2;
        }
        if (currentUserId == postUserId) {
            return true;
        }
        long createdBy = longValue(postData != null ? postData.get("rescueActivityCreatedBy") : null);
        if (createdBy > 0 && currentUserId == createdBy) {
            return true;
        }
        for (Map<String, Object> task : rescueTasks) {
            long assigneeId = longValue(task.get("assigneeUserId"));
            long assignerId = longValue(task.get("assignerUserId"));
            if (assigneeId > 0 && assigneeId == currentUserId) {
                return true;
            }
            if (assignerId > 0 && assignerId == currentUserId) {
                return true;
            }
        }
        return false;
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

    private void bindLocation(Object locationObj, boolean allowMapJump) {
        String location = stringValue(locationObj, "").trim();
        Log.d("SquareDetail", "原始位置数据: [" + location + "]");
        if (location.isEmpty()) {
            if (metaBlock != null) {
                metaBlock.setVisibility(View.GONE);
            }
            if (locationRow != null) {
                locationRow.setVisibility(View.GONE);
            }
            locationText.setOnClickListener(null);
            locationAnnotationText.setVisibility(View.GONE);
            if (locationMismatchHintText != null) {
                locationMismatchHintText.setVisibility(View.GONE);
            }
            return;
        }
        if (metaBlock != null) {
            metaBlock.setVisibility(View.VISIBLE);
        }
        String[] parts = splitLocationAndAnnotation(location);
        String locationMain = parts[0];
        String locationAnno = parts[1];
        Log.d("SquareDetail", "分割后 - main: [" + locationMain + "], anno: [" + locationAnno + "]");
        String displayText = stripLatLngForDisplay(locationMain);
        Log.d("SquareDetail", "显示文本: [" + displayText + "]");
        if (locationRow != null) {
            locationRow.setVisibility(View.VISIBLE);
        }
        locationText.setText("位置: " + displayText);
        if (!locationAnno.isEmpty()) {
            locationAnnotationText.setVisibility(View.VISIBLE);
            locationAnnotationText.setText("批注：" + locationAnno);
        } else {
            locationAnnotationText.setVisibility(View.GONE);
        }
        if (locationMismatchHintText != null) {
            locationMismatchHintText.setVisibility(View.GONE);
        }
        if (!allowMapJump) {
            locationText.setOnClickListener(null);
            locationText.setClickable(false);
            return;
        }
        locationText.setClickable(true);
        locationText.setOnClickListener(v -> {
            double[] coords = parseLocation(locationMain);
            if (coords == null) {
                Toast.makeText(requireContext(), "位置信息格式错误", Toast.LENGTH_SHORT).show();
                return;
            }
            if (getActivity() instanceof com.ourcat.app.activities.MainActivity) {
                ((com.ourcat.app.activities.MainActivity) getActivity()).showMapAtLocation(coords[0], coords[1]);
            }
        });
    }

    private void bindCatLocationDistanceHint() {
        if (postData == null || locationMismatchHintText == null) {
            return;
        }
        locationMismatchHintText.setVisibility(View.GONE);

        long refCatId = longValue(postData.get("referencedCatId"));
        if (refCatId <= 0) {
            return;
        }
        String location = stringValue(postData.get("location"), "").trim();
        if (location.isEmpty()) {
            return;
        }
        String[] parts = splitLocationAndAnnotation(location);
        String locationMain = parts[0];
        double[] coords = parseLocation(locationMain);
        if (coords == null) {
            return;
        }

        Log.d("SquareDetail", "距离提示: postCoords=" + coords[0] + "," + coords[1] + " refCatId=" + refCatId);
        RetrofitHelper.get().create(CatApi.class).getLocations()
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                            Response<List<Map<String, Object>>> response) {
                        if (!isAdded() || postData == null || locationMismatchHintText == null) {
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null) {
                            Log.d("SquareDetail", "距离提示: locations 响应失败 code=" + response.code());
                            return;
                        }
                        double bestDist = Double.MAX_VALUE;
                        boolean found = false;
                        for (Map<String, Object> item : response.body()) {
                            if (item == null) {
                                continue;
                            }
                            Object catIdObj = item.get("catId");
                            if (!(catIdObj instanceof Number) || ((Number) catIdObj).longValue() != refCatId) {
                                continue;
                            }
                            Object latObj = item.get("lat");
                            Object lngObj = item.get("lng");
                            if (!(latObj instanceof Number) || !(lngObj instanceof Number)) {
                                continue;
                            }
                            double lat = ((Number) latObj).doubleValue();
                            double lng = ((Number) lngObj).doubleValue();
                            double dist = haversineMeters(coords[0], coords[1], lat, lng);
                            if (dist < bestDist) {
                                bestDist = dist;
                                found = true;
                            }
                        }
                        Log.d("SquareDetail", "距离提示: found=" + found + " bestDistMeters=" + bestDist);
                        if (!found || bestDist <= CAT_LOCATION_MISMATCH_THRESHOLD_METERS) {
                            locationMismatchHintText.setVisibility(View.GONE);
                            return;
                        }
                        locationMismatchHintText.setVisibility(View.VISIBLE);
                        locationMismatchHintText.setText("提示：猫档案最近标点距发布定位约 " + formatDistance(bestDist) + "，请核对");
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        Log.d("SquareDetail", "距离提示: locations 请求失败 " + t);
                    }
                });
    }

    private String formatDistance(double meters) {
        if (meters < 1000) {
            return String.format(Locale.CHINA, "%.0f 米", meters);
        }
        return String.format(Locale.CHINA, "%.1f 公里", meters / 1000.0);
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }

    private String[] splitLocationAndAnnotation(String location) {
        String main = location;
        String anno = "";
        int idx = location.lastIndexOf(" (");
        if (idx > 0 && location.endsWith(")")) {
            String maybeAnno = location.substring(idx + 2, location.length() - 1).trim();
            String maybeMain = location.substring(0, idx).trim();
            if (!maybeAnno.isEmpty() && !maybeMain.isEmpty()) {
                main = maybeMain;
                anno = maybeAnno;
            }
        }
        return new String[] { main, anno };
    }

    private String stripLatLngForDisplay(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        int space = t.indexOf(' ');
        if (space > 0) {
            String prefix = t.substring(0, space);
            if (prefix.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
                String rest = t.substring(space + 1).trim();
                if (!rest.isEmpty()) {
                    return rest;
                }
                return "附近";
            }
        }
        // 如果是纯坐标,尝试逆地理编码
        if (t.matches("-?\\d+(\\.\\d+)?,-?\\d+(\\.\\d+)?")) {
            reverseGeocodeForDisplay(t);
            return "定位中...";
        }
        return t;
    }

    private void reverseGeocodeForDisplay(String coords) {
        String[] parts = coords.split(",");
        if (parts.length != 2)
            return;

        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lng = Double.parseDouble(parts[1].trim());

            com.amap.api.services.geocoder.GeocodeSearch geocodeSearch = new com.amap.api.services.geocoder.GeocodeSearch(
                    requireContext());
            geocodeSearch.setOnGeocodeSearchListener(
                    new com.amap.api.services.geocoder.GeocodeSearch.OnGeocodeSearchListener() {
                        @Override
                        public void onRegeocodeSearched(com.amap.api.services.geocoder.RegeocodeResult result,
                                int rCode) {
                            if (!isAdded())
                                return;

                            String addressName = "附近";
                            if (rCode == 1000 && result != null && result.getRegeocodeAddress() != null) {
                                com.amap.api.services.geocoder.RegeocodeAddress address = result.getRegeocodeAddress();
                                addressName = pickDisplayAddress(address);
                            }

                            if (metaBlock != null) {
                                metaBlock.setVisibility(View.VISIBLE);
                            }
                            if (locationRow != null) {
                                locationRow.setVisibility(View.VISIBLE);
                            }
                            locationText.setText("位置: " + addressName);
                        }

                        @Override
                        public void onGeocodeSearched(com.amap.api.services.geocoder.GeocodeResult result, int rCode) {
                        }
                    });

            com.amap.api.services.geocoder.RegeocodeQuery query = new com.amap.api.services.geocoder.RegeocodeQuery(
                    new com.amap.api.services.core.LatLonPoint(lat, lng),
                    200,
                    com.amap.api.services.geocoder.GeocodeSearch.AMAP);
            geocodeSearch.getFromLocationAsyn(query);
        } catch (Exception e) {
            Log.e("SquareDetail", "逆地理编码失败", e);
            locationText.setText("位置: 附近");
        }
    }

    private String pickDisplayAddress(@NonNull com.amap.api.services.geocoder.RegeocodeAddress address) {
        String aoi = safeTrim(
                () -> address.getAois() != null && !address.getAois().isEmpty() && address.getAois().get(0) != null
                        ? address.getAois().get(0).getAoiName()
                        : null);
        if (!aoi.isEmpty()) {
            return normalizeDisplayAddress(aoi);
        }

        String poi = safeTrim(
                () -> address.getPois() != null && !address.getPois().isEmpty() && address.getPois().get(0) != null
                        ? address.getPois().get(0).getTitle()
                        : null);
        if (!poi.isEmpty()) {
            return normalizeDisplayAddress(poi);
        }

        String neighborhood = safeTrim(address::getNeighborhood);
        String township = safeTrim(address::getTownship);
        String district = safeTrim(address::getDistrict);
        String city = safeTrim(address::getCity);
        String province = safeTrim(address::getProvince);

        String area = !neighborhood.isEmpty() ? neighborhood
                : (!township.isEmpty() ? township
                        : (!district.isEmpty() ? district : ""));
        if (!area.isEmpty()) {
            String candidate = area;
            if (!city.isEmpty() && !candidate.contains(city)) {
                candidate = city + candidate;
            } else if (city.isEmpty() && !province.isEmpty() && !candidate.contains(province)) {
                candidate = province + candidate;
            }
            return normalizeDisplayAddress(candidate);
        }
        if (!district.isEmpty()) {
            return normalizeDisplayAddress(district);
        }
        if (!city.isEmpty()) {
            return normalizeDisplayAddress(city);
        }
        if (!province.isEmpty()) {
            return normalizeDisplayAddress(province);
        }

        String formatted = safeTrim(address::getFormatAddress);
        if (!formatted.isEmpty()) {
            return normalizeDisplayAddress(formatted);
        }
        return "附近";
    }

    private String normalizeDisplayAddress(@NonNull String raw) {
        String t = raw.trim();
        if (t.startsWith("中华人民共和国")) {
            t = t.substring("中华人民共和国".length()).trim();
        }
        if (t.startsWith("中国")) {
            t = t.substring("中国".length()).trim();
        }
        if (t.isEmpty() || "中华人民共和国".equals(t) || "中国".equals(t)) {
            return "附近";
        }
        return t;
    }

    private interface UnsafeStringSupplier {
        String get() throws Exception;
    }

    private String safeTrim(@NonNull UnsafeStringSupplier supplier) {
        try {
            String s = supplier.get();
            return s != null ? s.trim() : "";
        } catch (Exception ignored) {
            return "";
        }
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
            Glide.with(this).load(imageUrl).circleCrop().into(refCatAvatar);
        } else {
            refCatAvatar.setImageResource(R.drawable.ic_default_avatar);
        }
        refCatCard.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putLong("catId", refCatId);
            Navigation.findNavController(requireView()).navigate(R.id.cat_profile, args);
        });
    }

    private void bindRescueSection(String type, String postStatus) {
        long rescueActivityId = longValue(postData.get("rescueActivityId"));
        if (!"rescue".equals(type) || rescueActivityId <= 0) {
            rescueCardContainer.setVisibility(View.GONE);
            return;
        }

        rescueCardContainer.setVisibility(View.VISIBLE);
        rescueActivityTitle.setText(stringValue(postData.get("rescueActivityTitle"), "救助活动"));
        String rescueStatus = stringValue(postData.get("rescueActivityStatus"), "created");
        rescueActivityStatus.setText("状态: " + mapRescueStatusText(rescueStatus));
        View.OnClickListener rescueMapJumpListener = v -> jumpRescueActivityToMap();
        rescueActivityCard.setOnClickListener(rescueMapJumpListener);

        boolean isCompleted = "completed".equals(rescueStatus) || "resolved".equals(postStatus);

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
        boolean currentUserHasTask = false;
        for (Map<String, Object> task : rescueTasks) {
            long assigneeId = longValue(task.get("assigneeUserId"));
            if (assigneeId > 0 && assigneeId == currentUserId) {
                currentUserHasTask = true;
                break;
            }
        }
        rescueClaimButton.setVisibility((isCompleted || currentUserHasTask) ? View.GONE : View.VISIBLE);
        rescueClaimButton.setEnabled(!(isCompleted || currentUserHasTask));
        rescueTasksAdapter.notifyDataSetChanged();
        rescueTasksEmpty.setVisibility(rescueTasks.isEmpty() ? View.VISIBLE : View.GONE);
        rescueTasksView.setVisibility(rescueTasks.isEmpty() ? View.GONE : View.VISIBLE);

        loadRescueTimeline(rescueActivityId);
    }

    private void loadRescueTimeline(long rescueActivityId) {
        if (rescueActivityId <= 0 || rescueTimelineView == null) {
            return;
        }
        if (timelineLoadedActivityId == rescueActivityId && !rescueTimelineItems.isEmpty()) {
            rescueTimelineAdapter.notifyDataSetChanged();
            return;
        }
        timelineLoadedActivityId = rescueActivityId;
        RetrofitHelper.get().create(RescueApi.class).getActivity(rescueActivityId)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null) {
                            return;
                        }
                        buildRescueTimeline(response.body());
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                    }
                });
    }

    @SuppressWarnings("unchecked")
    private void buildRescueTimeline(Map<String, Object> activity) {
        rescueTimelineItems.clear();

        List<Map<String, Object>> tasks = new ArrayList<>();
        Object tasksObj = activity.get("tasks");
        if (tasksObj instanceof List) {
            for (Object o : (List<?>) tasksObj) {
                if (o instanceof Map) {
                    tasks.add((Map<String, Object>) o);
                }
            }
        }

        String createdAt = stringValue(activity.get("createdAt"), "");
        String createdBy = stringValue(activity.get("creatorName"), "");
        String operatorText = createdBy.isEmpty() ? "" : ("操作人: " + createdBy);

        rescueTimelineItems.add(new TimelineItem("发现问题", createdAt, operatorText));
        rescueTimelineItems.add(new TimelineItem("创建活动", createdAt, operatorText));

        if (!tasks.isEmpty()) {
            String firstAssignedAt = "";
            for (Map<String, Object> t : tasks) {
                String at = stringValue(t.get("assignedAt"), "");
                if (!at.isEmpty()) {
                    firstAssignedAt = at;
                    break;
                }
            }
            rescueTimelineItems.add(new TimelineItem("分配/申领任务", firstAssignedAt, buildTaskMembersText(tasks)));
        }

        if (!tasks.isEmpty()) {
            int doneCount = 0;
            for (Map<String, Object> t : tasks) {
                if ("done".equalsIgnoreCase(stringValue(t.get("status"), ""))) {
                    doneCount++;
                }
            }
            rescueTimelineItems.add(new TimelineItem("执行救助", "", "进度: " + doneCount + "/" + tasks.size() + " 已完成"));
        }

        String status = stringValue(activity.get("status"), "");
        if ("completed".equals(status)) {
            String completedAt = stringValue(activity.get("completedAt"), "");
            rescueTimelineItems.add(new TimelineItem("完成救助", completedAt, ""));
        }

        java.util.Collections.reverse(rescueTimelineItems);
        rescueTimelineAdapter.notifyDataSetChanged();
        rescueTimelineView.scrollToPosition(0);
    }

    private String buildTaskMembersText(List<Map<String, Object>> tasks) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> t : tasks) {
            String name = stringValue(t.get("assigneeName"), "");
            if (name.isEmpty()) {
                name = "未分配";
            }
            names.add(name);
        }
        String joined = joinDistinct(names, 4);
        return joined.isEmpty() ? "" : ("成员: " + joined);
    }

    private String joinDistinct(List<String> list, int maxItems) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s == null || s.trim().isEmpty()) {
                continue;
            }
            boolean exists = false;
            for (String r : result) {
                if (r.equals(s)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                result.add(s);
            }
            if (result.size() >= maxItems) {
                break;
            }
        }
        if (result.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.size(); i++) {
            if (i > 0) {
                sb.append("、");
            }
            sb.append(result.get(i));
        }
        if (list.size() > result.size()) {
            sb.append("…");
        }
        return sb.toString();
    }

    static class TimelineItem {
        final String title;
        final String time;
        final String operator;

        TimelineItem(String title, String time, String operator) {
            this.title = title;
            this.time = time;
            this.operator = operator;
        }
    }

    class RescueTimelineAdapter extends RecyclerView.Adapter<RescueTimelineAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView time;
            final TextView operator;
            final View leftLine;
            final View rightLine;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.timeline_title);
                time = itemView.findViewById(R.id.timeline_time);
                operator = itemView.findViewById(R.id.timeline_operator);
                leftLine = itemView.findViewById(R.id.timeline_left_line);
                rightLine = itemView.findViewById(R.id.timeline_right_line);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rescue_timeline_horizontal, parent,
                    false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            TimelineItem item = rescueTimelineItems.get(position);
            holder.title.setText(item.title);
            holder.time.setText(item.time.isEmpty() ? "" : TimeUtils.getRelativeTime(item.time));
            holder.operator.setText(item.operator != null ? item.operator : "");
            holder.leftLine.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
            holder.rightLine.setVisibility(position == rescueTimelineItems.size() - 1 ? View.INVISIBLE : View.VISIBLE);
        }

        @Override
        public int getItemCount() {
            return rescueTimelineItems.size();
        }
    }

    private void jumpRescueActivityToMap() {
        if (!(getActivity() instanceof com.ourcat.app.activities.MainActivity) || postData == null) {
            return;
        }
        com.ourcat.app.activities.MainActivity mainActivity = (com.ourcat.app.activities.MainActivity) getActivity();
        long refCatId = longValue(postData.get("referencedCatId"));
        boolean isResolved = "resolved".equals(stringValue(postData.get("status"), ""));
        String location = stringValue(postData.get("location"), "").trim();
        double[] coords = location.isEmpty() ? null : parseLocation(location);

        if (isResolved) {
            completeRescueActivityIfNeeded(postData);
        }

        if (refCatId > 0) {
            if (coords != null) {
                if (isResolved) {
                    mainActivity.showMapAtCatLocationNearSolved(refCatId, coords[0], coords[1]);
                } else {
                    mainActivity.showMapAtCatLocationNear(refCatId, coords[0], coords[1]);
                }
            } else {
                mainActivity.showMapAtCatLocationNearestToUser(refCatId);
            }
            return;
        }

        if (coords != null) {
            if (isResolved) {
                mainActivity.showMapAtRescueLocationSolved(coords[0], coords[1]);
            } else {
                mainActivity.showMapAtRescueLocation(coords[0], coords[1]);
            }
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
                            completeRescueActivityIfNeeded(postData);
                            notifyRescueAssigneesSolved();
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

    private void notifyRescueAssigneesSolved() {
        if (postData == null) {
            return;
        }
        String type = stringValue(postData.get("type"), "");
        if (!"rescue".equals(type)) {
            return;
        }
        if (!"resolved".equals(stringValue(postData.get("status"), ""))) {
            return;
        }
        if (!rescueTasks.isEmpty()) {
            sendSolvedUpdatesToTasks(rescueTasks);
            return;
        }
        long activityId = longValue(postData.get("rescueActivityId"));
        if (activityId <= 0) {
            return;
        }
        RetrofitHelper.get().create(RescueApi.class).listActivityTasks(activityId)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                            Response<List<Map<String, Object>>> response) {
                        if (!response.isSuccessful() || response.body() == null) {
                            return;
                        }
                        sendSolvedUpdatesToTasks(response.body());
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                    }
                });
    }

    private void sendSolvedUpdatesToTasks(List<Map<String, Object>> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Map<Long, Boolean> taskIds = new HashMap<>();
        for (Map<String, Object> task : tasks) {
            long taskId = longValue(task.get("id"));
            long assigneeId = longValue(task.get("assigneeUserId"));
            if (taskId > 0 && assigneeId > 0) {
                taskIds.put(taskId, true);
            }
        }
        if (taskIds.isEmpty()) {
            return;
        }
        for (Long taskId : taskIds.keySet()) {
            Map<String, String> update = new HashMap<>();
            update.put("status", "done");
            RetrofitHelper.get().create(RescueApi.class).updateTask(taskId, update)
                    .enqueue(new Callback<Map<String, Object>>() {
                        @Override
                        public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        }

                        @Override
                        public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        }
                    });

            Map<String, String> log = new HashMap<>();
            log.put("content", "事件已解决");
            log.put("logType", "progress");
            RetrofitHelper.get().create(RescueApi.class).addTaskLog(taskId, log)
                    .enqueue(new Callback<Map<String, Object>>() {
                        @Override
                        public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        }

                        @Override
                        public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        }
                    });
        }
    }

    private void completeRescueActivityIfNeeded(Map<String, Object> data) {
        if (data == null) {
            return;
        }
        String type = stringValue(data.get("type"), "");
        if (!"rescue".equals(type)) {
            return;
        }
        long activityId = longValue(data.get("rescueActivityId"));
        if (activityId <= 0) {
            return;
        }
        Map<String, String> body = new HashMap<>();
        body.put("status", "completed");
        RetrofitHelper.get().create(RescueApi.class).updateActivityStatus(activityId, body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        if (response.isSuccessful()) {
                            loadPost();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
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

    private String mapTaskStatusText(String status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case "assigned":
                return "待执行";
            case "done":
                return "已完成";
            default:
                return status;
        }
    }

    private class RescueTasksAdapter extends RecyclerView.Adapter<RescueTasksAdapter.TaskHolder> {
        @NonNull
        @Override
        public TaskHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_rescue_task, parent, false);
            return new TaskHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull TaskHolder holder, int position) {
            Map<String, Object> task = rescueTasks.get(position);
            long assigneeId = longValue(task.get("assigneeUserId"));
            String status = stringValue(task.get("status"), "assigned");
            String assigneeName = stringValue(task.get("assigneeName"), "");
            String assigneeAvatar = stringValue(task.get("assigneeAvatar"), "");
            if (assigneeId > 0) {
                holder.name.setText(!assigneeName.isEmpty() ? assigneeName : ("成员 #" + assigneeId));
                if (!assigneeAvatar.isEmpty()) {
                    Glide.with(SquareDetailFragment.this)
                            .load(assigneeAvatar)
                            .circleCrop()
                            .placeholder(R.drawable.ic_default_avatar)
                            .into(holder.avatar);
                } else {
                    holder.avatar.setImageResource(R.drawable.ic_default_avatar);
                }
                holder.status.setText("状态: " + mapTaskStatusText(status));
            } else {
                holder.name.setText("待领取任务");
                holder.avatar.setImageResource(R.drawable.ic_default_avatar);
                holder.status.setText("状态: 待领取");
            }
        }

        @Override
        public int getItemCount() {
            return rescueTasks.size();
        }

        class TaskHolder extends RecyclerView.ViewHolder {
            ImageView avatar;
            TextView name;
            TextView status;

            TaskHolder(@NonNull View itemView) {
                super(itemView);
                avatar = itemView.findViewById(R.id.task_avatar);
                name = itemView.findViewById(R.id.task_name);
                status = itemView.findViewById(R.id.task_status);
            }
        }
    }
}
