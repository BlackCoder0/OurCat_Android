package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
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

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.ourcat.app.R;
import com.ourcat.app.adapters.MyCommentAdapter;
import com.ourcat.app.adapters.PostAdapter;
import com.ourcat.app.adapters.UserSearchAdapter;
import com.ourcat.app.models.PostItem;
import com.ourcat.app.repositories.ForumApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchResultFragment extends Fragment {

    private TextInputEditText etSearch;
    private ChipGroup chipGroup;
    private RecyclerView rvResults;
    private TextView tvEmpty;

    private PostAdapter postAdapter;
    private UserSearchAdapter userAdapter;
    private MyCommentAdapter commentAdapter;

    private String currentType = "post";
    private String currentQuery = "";

    private List<PostItem> cachedPosts = new ArrayList<>();
    private List<Map<String, Object>> cachedUsers = new ArrayList<>();
    private List<Map<String, Object>> cachedComments = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search_result, container, false);
        etSearch = view.findViewById(R.id.et_search);
        chipGroup = view.findViewById(R.id.chip_group);
        rvResults = view.findViewById(R.id.rv_results);
        tvEmpty = view.findViewById(R.id.tv_empty);

        rvResults.setLayoutManager(new LinearLayoutManager(requireContext()));

        postAdapter = new PostAdapter();
        postAdapter.setOnItemClickListener(item -> {
            Bundle args = new Bundle();
            args.putLong("postId", item.getId());
            NavController nav = Navigation.findNavController(requireView());
            nav.navigate(R.id.post_detail, args);
        });

        userAdapter = new UserSearchAdapter();
        userAdapter.setOnUserClickListener(userId -> {
            Bundle args = new Bundle();
            args.putLong("userId", userId);
            NavController nav = Navigation.findNavController(requireView());
            nav.navigate(R.id.user_profile, args);
        });

        commentAdapter = new MyCommentAdapter();
        commentAdapter.setOnItemClickListener(item -> {
            long postId = item.get("postId") instanceof Number ? ((Number) item.get("postId")).longValue() : 0;
            if (postId > 0) {
                Bundle args = new Bundle();
                args.putLong("postId", postId);
                NavController nav = Navigation.findNavController(requireView());
                nav.navigate(R.id.post_detail, args);
            }
        });

        if (savedInstanceState != null) {
            currentQuery = savedInstanceState.getString("query", "");
            currentType = savedInstanceState.getString("type", "post");
        } else if (getArguments() != null) {
            currentQuery = getArguments().getString("query", "");
        }
        etSearch.setText(currentQuery);

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                currentQuery = etSearch.getText() != null ? etSearch.getText().toString().trim() : "";
                clearCaches();
                doSearch();
                return true;
            }
            return false;
        });

        boolean skipInitListener = true;
        if ("comment".equals(currentType)) {
            chipGroup.check(R.id.chip_comment);
        } else if ("user".equals(currentType)) {
            chipGroup.check(R.id.chip_user);
        } else {
            chipGroup.check(R.id.chip_post);
        }

        chipGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            String newType;
            if (checkedIds.contains(R.id.chip_comment)) {
                newType = "comment";
            } else if (checkedIds.contains(R.id.chip_user)) {
                newType = "user";
            } else {
                newType = "post";
            }
            if (!newType.equals(currentType)) {
                currentType = newType;
                restoreOrSearch();
            }
        });

        if (!currentQuery.isEmpty()) {
            restoreOrSearch();
        }

        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("query", currentQuery);
        outState.putString("type", currentType);
    }

    private void clearCaches() {
        cachedPosts.clear();
        cachedUsers.clear();
        cachedComments.clear();
    }

    private void restoreOrSearch() {
        if ("user".equals(currentType) && !cachedUsers.isEmpty()) {
            rvResults.setAdapter(userAdapter);
            userAdapter.setItems(cachedUsers);
            showEmpty(false);
        } else if ("comment".equals(currentType) && !cachedComments.isEmpty()) {
            rvResults.setAdapter(commentAdapter);
            commentAdapter.setItems(cachedComments);
            showEmpty(false);
        } else if ("post".equals(currentType) && !cachedPosts.isEmpty()) {
            rvResults.setAdapter(postAdapter);
            postAdapter.setItems(cachedPosts);
            showEmpty(false);
        } else {
            doSearch();
        }
    }

    private void doSearch() {
        if (currentQuery.isEmpty())
            return;

        if ("user".equals(currentType)) {
            rvResults.setAdapter(userAdapter);
        } else if ("comment".equals(currentType)) {
            rvResults.setAdapter(commentAdapter);
        } else {
            rvResults.setAdapter(postAdapter);
        }

        RetrofitHelper.get().create(ForumApi.class)
                .search(currentQuery, currentType, 0, 20)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded())
                            return;
                        if (response.isSuccessful() && response.body() != null) {
                            Object content = response.body().get("content");
                            if (content instanceof List) {
                                List<?> list = (List<?>) content;
                                if ("user".equals(currentType)) {
                                    List<Map<String, Object>> users = new ArrayList<>();
                                    for (Object o : list) {
                                        if (o instanceof Map)
                                            users.add((Map<String, Object>) o);
                                    }
                                    cachedUsers = users;
                                    userAdapter.setItems(users);
                                    showEmpty(users.isEmpty());
                                } else if ("comment".equals(currentType)) {
                                    List<Map<String, Object>> comments = new ArrayList<>();
                                    for (Object o : list) {
                                        if (o instanceof Map)
                                            comments.add((Map<String, Object>) o);
                                    }
                                    cachedComments = comments;
                                    commentAdapter.setItems(comments);
                                    showEmpty(comments.isEmpty());
                                } else {
                                    List<PostItem> posts = new ArrayList<>();
                                    for (Object o : list) {
                                        if (o instanceof Map) {
                                            posts.add(ForumFragment.mapToPostItemPublic((Map<String, Object>) o));
                                        }
                                    }
                                    cachedPosts = posts;
                                    postAdapter.setItems(posts);
                                    showEmpty(posts.isEmpty());
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (!isAdded())
                            return;
                        Toast.makeText(requireContext(), "搜索失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showEmpty(boolean empty) {
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        rvResults.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
