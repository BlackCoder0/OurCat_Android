package com.ourcat.app.fragments;

import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.textfield.TextInputEditText;
import com.ourcat.app.R;
import com.ourcat.app.adapters.PostAdapter;
import com.ourcat.app.models.PostItem;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForumFragment extends Fragment {

    private RecyclerView recyclerView;
    private SwipeRefreshLayout refreshLayout;
    private PostAdapter adapter;
    private TextInputEditText etSearch;
    private static final int PAGE_SIZE = 20;
    private static final String KEY_LAYOUT_STATE = "forum_layout_state";
    private Parcelable pendingLayoutState;
    private final List<PostItem> cachedPosts = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_forum, container, false);
        if (savedInstanceState != null) {
            pendingLayoutState = savedInstanceState.getParcelable(KEY_LAYOUT_STATE);
        }
        recyclerView = view.findViewById(R.id.forum_recycler);
        refreshLayout = view.findViewById(R.id.forum_swipe);
        etSearch = view.findViewById(R.id.et_search);

        refreshLayout.setColorSchemeResources(R.color.primary_variant, R.color.primary, R.color.info);
        refreshLayout.setProgressBackgroundColorSchemeResource(R.color.surface);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new PostAdapter();
        adapter.setOnItemClickListener(item -> {
            Bundle args = new Bundle();
            args.putLong("postId", item.getId());
            NavController nav = Navigation.findNavController(requireView());
            nav.navigate(R.id.post_detail, args);
        });
        adapter.setOnRefCatClickListener(catId -> {
            Bundle args = new Bundle();
            args.putLong("catId", catId);
            Navigation.findNavController(requireView()).navigate(R.id.cat_profile, args);
        });
        recyclerView.setAdapter(adapter);

        refreshLayout.setOnRefreshListener(this::loadPosts);

        view.findViewById(R.id.forum_fab).setOnClickListener(v -> {
            NavController nav = Navigation.findNavController(requireView());
            nav.navigate(R.id.create_edit_post);
        });

        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String q = etSearch.getText() != null ? etSearch.getText().toString().trim() : "";
                if (!q.isEmpty()) {
                    Bundle args = new Bundle();
                    args.putString("query", q);
                    NavController nav = Navigation.findNavController(requireView());
                    nav.navigate(R.id.search_result, args);
                } else {
                    loadPosts();
                }
                return true;
            }
            return false;
        });

        if (!cachedPosts.isEmpty()) {
            adapter.setItems(cachedPosts);
            restoreLayoutStateIfNeeded();
        } else {
            loadPosts();
        }
        return view;
    }

    private void loadPosts() {
        refreshLayout.setRefreshing(true);
        String search = etSearch != null && etSearch.getText() != null
                ? etSearch.getText().toString().trim()
                : null;
        if (search != null && search.isEmpty()) {
            search = null;
        }

        RetrofitHelper.get().create(com.ourcat.app.repositories.ForumApi.class)
                .listPosts(0, PAGE_SIZE, search)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        refreshLayout.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            Object content = response.body().get("content");
                            if (content instanceof List) {
                                List<PostItem> list = new ArrayList<>();
                                for (Object o : (List<?>) content) {
                                    if (o instanceof Map) {
                                        PostItem item = mapToPostItem((Map<String, Object>) o);
                                        list.add(item);
                                    }
                                }
                                cachedPosts.clear();
                                cachedPosts.addAll(list);
                                adapter.setItems(cachedPosts);
                                restoreLayoutStateIfNeeded();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        refreshLayout.setRefreshing(false);
                        Toast.makeText(requireContext(), "\u52a0\u8f7d\u5931\u8d25", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (recyclerView != null && recyclerView.getLayoutManager() != null) {
            outState.putParcelable(KEY_LAYOUT_STATE, recyclerView.getLayoutManager().onSaveInstanceState());
        }
    }

    private void restoreLayoutStateIfNeeded() {
        if (pendingLayoutState == null || recyclerView == null || recyclerView.getLayoutManager() == null) {
            return;
        }
        recyclerView.getLayoutManager().onRestoreInstanceState(pendingLayoutState);
        pendingLayoutState = null;
    }

    private static PostItem mapToPostItem(Map<String, Object> m) {
        PostItem item = new PostItem();
        if (m.get("id") instanceof Number) {
            item.setId(((Number) m.get("id")).longValue());
        }
        if (m.get("title") != null) {
            item.setTitle(m.get("title").toString());
        }
        if (m.get("content") != null) {
            item.setContent(m.get("content").toString());
        }
        if (m.get("images") != null) {
            item.setImages(m.get("images").toString());
        }
        if (m.get("likes") instanceof Number) {
            item.setLikes(((Number) m.get("likes")).intValue());
        }
        if (m.get("dislikes") instanceof Number) {
            item.setDislikes(((Number) m.get("dislikes")).intValue());
        }
        if (m.get("userId") instanceof Number) {
            item.setUserId(((Number) m.get("userId")).longValue());
        }
        if (m.get("pinned") != null) {
            item.setPinned(Boolean.TRUE.equals(m.get("pinned")));
        }
        if (m.get("createdAt") != null) {
            item.setCreatedAt(m.get("createdAt").toString());
        }
        if (m.get("authorName") != null) {
            item.setAuthorName(m.get("authorName").toString());
        }
        if (m.get("referencedCatId") instanceof Number) {
            item.setReferencedCatId(((Number) m.get("referencedCatId")).longValue());
        }
        Object refCat = m.get("referencedCat");
        if (refCat instanceof Map) {
            Map<?, ?> rc = (Map<?, ?>) refCat;
            if (rc.get("name") != null) {
                item.setReferencedCatName(rc.get("name").toString());
            }
            if (rc.get("primaryImageUrl") != null) {
                item.setReferencedCatImageUrl(rc.get("primaryImageUrl").toString());
            }
        }
        return item;
    }

    public static PostItem mapToPostItemPublic(Map<String, Object> m) {
        return mapToPostItem(m);
    }
}
