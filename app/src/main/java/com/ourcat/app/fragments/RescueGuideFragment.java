package com.ourcat.app.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import android.widget.ScrollView;
import androidx.lifecycle.ViewModelProvider;
import com.ourcat.app.models.RescueGuideViewModel;

public class RescueGuideFragment extends Fragment {

    private RecyclerView guideList, contactsList, resourcesList, articlesList;
    private ScrollView scrollView;
    private final List<Map<String, Object>> articles = new ArrayList<>();
    private RescueGuideViewModel viewModel;
    private static final String KEY_SCROLL_Y = "rescue_guide_scroll_y";
    private Integer pendingScrollY;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(RescueGuideViewModel.class);
        View view = inflater.inflate(R.layout.fragment_rescue_guide, container, false);
        if (savedInstanceState != null) {
            pendingScrollY = savedInstanceState.getInt(KEY_SCROLL_Y, 0);
        }
        scrollView = view.findViewById(R.id.guide_scroll_view);
        guideList = view.findViewById(R.id.guide_list);
        contactsList = view.findViewById(R.id.contacts_list);
        resourcesList = view.findViewById(R.id.resources_list);
        articlesList = view.findViewById(R.id.articles_list);
        guideList.setLayoutManager(new LinearLayoutManager(requireContext()));
        contactsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        resourcesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        articlesList.setLayoutManager(new LinearLayoutManager(requireContext()));

        if (viewModel.getGuides().getValue() != null && !viewModel.getGuides().getValue().isEmpty()) {
            setGuideAdapter(viewModel.getGuides().getValue());
        } else {
            loadGuide();
        }

        if (viewModel.getContacts().getValue() != null && !viewModel.getContacts().getValue().isEmpty()) {
            setContactsAdapter(viewModel.getContacts().getValue());
        } else {
            loadContacts();
        }

        loadResources();

        if (viewModel.getArticles().getValue() != null && !viewModel.getArticles().getValue().isEmpty()) {
            articles.clear();
            articles.addAll(viewModel.getArticles().getValue());
            articlesList.setAdapter(new ArticlesAdapter());
        } else {
            loadArticles();
        }

        scrollView.post(() -> {
            if (pendingScrollY != null) {
                scrollView.scrollTo(0, pendingScrollY);
                pendingScrollY = null;
            } else {
                scrollView.scrollTo(0, viewModel.getScrollY());
            }
        });

        scrollView.getViewTreeObserver().addOnScrollChangedListener(() -> {
            if (scrollView != null) {
                viewModel.setScrollY(scrollView.getScrollY());
            }
        });

        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (scrollView != null) {
            outState.putInt(KEY_SCROLL_Y, scrollView.getScrollY());
        }
    }

    @Override
    public void onPause() {
        if (scrollView != null) {
            viewModel.setScrollY(scrollView.getScrollY());
        }
        super.onPause();
    }

    private void setGuideAdapter(List<Map<String, Object>> list) {
        guideList.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent,
                        false);
                return new RecyclerView.ViewHolder(v) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> m = list.get(position);
                ((TextView) holder.itemView.findViewById(android.R.id.text1)).setText(str(m, "title"));
                ((TextView) holder.itemView.findViewById(android.R.id.text2)).setText(str(m, "content"));
            }

            @Override
            public int getItemCount() {
                return list.size();
            }
        });
    }

    private void loadGuide() {
        RetrofitHelper.get().create(RescueApi.class).getGuide().enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!isAdded())
                    return;
                if (response.isSuccessful() && response.body() != null) {
                    viewModel.getGuides().setValue(response.body());
                    setGuideAdapter(response.body());
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
            }
        });
    }

    private void setContactsAdapter(List<Map<String, Object>> list) {
        contactsList.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent,
                        false);
                return new RecyclerView.ViewHolder(v) {
                };
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                Map<String, Object> m = list.get(position);
                String name = str(m, "name");
                String phone = str(m, "phone");
                String desc = str(m, "description");
                ((TextView) holder.itemView.findViewById(android.R.id.text1)).setText(name + " " + phone);
                ((TextView) holder.itemView.findViewById(android.R.id.text2)).setText(desc);
                holder.itemView.setOnClickListener(v -> {
                    Intent i = new Intent(Intent.ACTION_DIAL);
                    i.setData(Uri.parse("tel:" + phone));
                    startActivity(i);
                });
            }

            @Override
            public int getItemCount() {
                return list.size();
            }
        });
    }

    private void loadContacts() {
        RetrofitHelper.get().create(RescueApi.class).getContacts().enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (!isAdded())
                    return;
                if (response.isSuccessful() && response.body() != null) {
                    viewModel.getContacts().setValue(response.body());
                    setContactsAdapter(response.body());
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
            }
        });
    }

    private void loadResources() {
        List<ResourceItem> items = List.of(
                new ResourceItem("Alley Cat Allies：TNR（抓捕-绝育-放归）步骤指南",
                        "https://www.alleycat.org/resources/how-to-help-community-cats-a-step-by-step-guide-to-trap-neuter-return/"),
                new ResourceItem("Alley Cat Allies：TNR 概念与依据",
                        "https://www.alleycat.org/our-work/trap-neuter-return/"),
                new ResourceItem("ASPCA：社区猫科普与 TNRM",
                        "https://www.aspca.org/helping-shelters-people-pets/closer-look-community-cats"),
                new ResourceItem("ASPCA：TNR 术后护理（PDF）",
                        "https://www.aspca.org/sites/default/files/en_-_caring_for_tnr_cats_after_surgery_2.2024.pdf"));
        resourcesList.setAdapter(new ResourcesAdapter(items));
    }

    private void loadArticles() {
        RetrofitHelper.get().create(RescueApi.class).getArticles(null)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                            Response<List<Map<String, Object>>> response) {
                        if (!isAdded())
                            return;
                        if (!response.isSuccessful() || response.body() == null) {
                            return;
                        }
                        articles.clear();
                        articles.addAll(response.body());
                        viewModel.getArticles().setValue(new ArrayList<>(articles));
                        articlesList.setAdapter(new ArticlesAdapter());
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                    }
                });
    }

    static class ResourceItem {
        String title;
        String url;

        ResourceItem(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    class ResourcesAdapter extends RecyclerView.Adapter<ResourcesAdapter.VH> {
        private final List<ResourceItem> items;

        ResourcesAdapter(List<ResourceItem> items) {
            this.items = items;
        }

        class VH extends RecyclerView.ViewHolder {
            TextView title;
            TextView subtitle;

            VH(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(android.R.id.text1);
                subtitle = itemView.findViewById(android.R.id.text2);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent,
                    false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ResourceItem item = items.get(position);
            holder.title.setText(item.title);
            holder.subtitle.setText(item.url);
            holder.itemView.setOnClickListener(v -> {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(item.url));
                startActivity(i);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    class ArticlesAdapter extends RecyclerView.Adapter<ArticlesAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            TextView title, category;

            VH(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.article_title);
                category = itemView.findViewById(R.id.article_category);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_rescue_article, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Map<String, Object> article = articles.get(position);
            holder.title.setText(String.valueOf(article.get("title")));
            String categoryStr = String.valueOf(article.get("category"));
            holder.category.setText(categoryStr.isEmpty() ? "通用" : categoryStr);

            holder.itemView.setOnClickListener(v -> {
                Bundle args = new Bundle();
                args.putLong("articleId", longValue(article.get("id")));
                Navigation.findNavController(v).navigate(R.id.rescue_article_detail, args);
            });
        }

        @Override
        public int getItemCount() {
            return articles.size();
        }
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v != null ? v.toString() : "";
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
