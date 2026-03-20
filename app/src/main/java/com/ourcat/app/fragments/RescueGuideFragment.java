package com.ourcat.app.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;
import com.ourcat.app.models.RescueGuideViewModel;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RescueGuideFragment extends Fragment {

    private RecyclerView guideList;
    private RecyclerView contactsList;
    private RecyclerView articlesList;
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
        articlesList = view.findViewById(R.id.articles_list);
        guideList.setLayoutManager(new LinearLayoutManager(requireContext()));
        contactsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        articlesList.setLayoutManager(new LinearLayoutManager(requireContext()));

        List<Map<String, Object>> authoritativeGuides = buildAuthoritativeGuides();
        viewModel.getGuides().setValue(authoritativeGuides);
        setGuideAdapter(authoritativeGuides);

        if (viewModel.getContacts().getValue() != null && !viewModel.getContacts().getValue().isEmpty()) {
            setContactsAdapter(viewModel.getContacts().getValue());
        } else {
            loadContacts();
        }

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
        guideList.setAdapter(new RecyclerView.Adapter<GuideViewHolder>() {
            @NonNull
            @Override
            public GuideViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_rescue_guide_step, parent,
                        false);
                return new GuideViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull GuideViewHolder holder, int position) {
                Map<String, Object> m = list.get(position);
                holder.title.setText(str(m, "title"));
                holder.content.setText(str(m, "content"));
            }

            @Override
            public int getItemCount() {
                return list.size();
            }
        });
    }

    private void setContactsAdapter(List<Map<String, Object>> list) {
        contactsList.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_info_row, parent,
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

    private List<Map<String, Object>> buildAuthoritativeGuides() {
        List<Map<String, Object>> guides = new ArrayList<>();
        guides.add(guide(
                "\u5148\u5224\u65ad\u8fd9\u662f\u793e\u533a\u732b\uff0c\u8fd8\u662f\u9700\u8981\u7acb\u5373\u4ecb\u5165\u7684\u4e2a\u4f53",
                "\u5148\u89c2\u5bdf\u662f\u5426\u6709\u8033\u5c16\u526a\u6807\u8bb0\u3001\u9879\u5708\u3001\u660e\u663e\u5916\u4f24\u6216\u6781\u5ea6\u6d88\u7626\u3002\u5bf9\u5065\u5eb7\u3001\u8b66\u60d5\u7684\u793e\u533a\u732b\uff0c\u5148\u4e0d\u8981\u7acb\u5373\u7a81\u7136\u642c\u79bb\u73b0\u573a\uff1b\u5bf9\u53d7\u4f24\u3001\u88ab\u56f0\u3001\u547c\u5438\u56f0\u96be\u6216\u65e0\u6cd5\u7ad9\u7acb\u7684\u732b\uff0c\u5c3d\u5feb\u8054\u7cfb\u517d\u533b\u6216\u6551\u52a9\u7ec4\u7ec7\u3002"));
        guides.add(guide(
                "\u5065\u5eb7\u6210\u732b\u4ee5 TNR \u4e3a\u4f18\u5148\u7b56\u7565",
                "\u6743\u5a01\u6307\u5357\u90fd\u628a Trap-Neuter-Return \u4f5c\u4e3a\u5065\u5eb7\u793e\u533a\u732b\u7684\u6838\u5fc3\u65b9\u6848\uff1a\u4eba\u9053\u6293\u6355\uff0c\u7edd\u80b2\uff0c\u75ab\u82d7\uff0c\u8033\u5c16\u526a\u6807\uff0c\u672f\u540e\u539f\u5730\u653e\u56de\u3002\u8fd9\u6837\u80fd\u964d\u4f4e\u65b0\u751f\u732b\u589e\u957f\uff0c\u4e5f\u80fd\u907f\u514d\u628a\u4e0d\u9002\u5408\u6536\u5bb9\u7684\u793e\u533a\u732b\u53cd\u590d\u9001\u5165\u5bb9\u91cf\u7d27\u5f20\u7684\u6536\u5bb9\u4f53\u7cfb\u3002"));
        guides.add(guide(
                "\u5e7c\u732b\u4e0d\u8981\u56e0\u7d27\u5f20\u800c\u7acb\u523b\u62b1\u8d70",
                "\u5982\u679c\u53d1\u73b0\u7684\u662f\u5f88\u5c0f\u7684\u5e7c\u732b\uff0c\u800c\u4e14\u6ca1\u6709\u53d7\u4f24\u3001\u4f53\u6e29\u8fc7\u4f4e\u6216\u7acb\u5373\u5371\u9669\uff0c\u901a\u5e38\u9700\u8981\u5148\u5728\u8fdc\u5904\u89c2\u5bdf\u6bcd\u732b\u662f\u5426\u4f1a\u8fd4\u56de\u3002\u8fc7\u65e9\u79fb\u8d70\u5065\u5eb7\u5e7c\u732b\uff0c\u53ef\u80fd\u53cd\u800c\u9020\u6210\u4eba\u5de5\u55ab\u517b\u548c\u6210\u6d3b\u98ce\u9669\u4e0a\u5347\u3002"));
        guides.add(guide(
                "\u6293\u6355\u4e0e\u672f\u540e\u76d1\u6d4b\u8981\u6309\u6807\u51c6\u6d41\u7a0b\u6267\u884c",
                "\u4f7f\u7528\u4eba\u9053\u8bf1\u6355\u7b3c\uff0c\u63d0\u524d\u9884\u7ea6\u7edd\u80b2\u624b\u672f\uff0c\u8986\u76d6\u7b3c\u4f53\u51cf\u538b\uff0c\u5e76\u6309\u673a\u6784\u8981\u6c42\u7981\u98df\u3002\u672f\u540e\u8981\u5728\u5b89\u9759\u3001\u4fdd\u6696\u3001\u5c01\u95ed\u7684\u7a7a\u95f4\u76d1\u6d4b\u6062\u590d\uff0c\u786e\u8ba4\u6e05\u9192\u3001\u547c\u5438\u5e73\u7a33\u3001\u65e0\u5f02\u5e38\u51fa\u8840\u540e\u518d\u653e\u56de\u3002"));
        guides.add(guide(
                "\u4ec0\u4e48\u60c5\u51b5\u8981\u4f18\u5148\u627e\u517d\u533b\u6216\u672c\u5730\u6551\u52a9",
                "\u5982\u679c\u732b\u54aa\u51fa\u73b0\u91cd\u5ea6\u8131\u6c34\u3001\u4e0d\u5403\u4e0d\u52a8\u3001\u9aa8\u6298\u7591\u4f3c\u3001\u773c\u9f3b\u5927\u91cf\u5206\u6ccc\u7269\u3001\u88ab\u8f66\u649e\u6216\u88ab\u7ebf\u7f51\u7f20\u7ed5\uff0c\u4e0d\u8981\u7b49\u89c2\u5bdf\uff0c\u5e94\u76f4\u63a5\u8fdb\u5165\u533b\u7597\u6216\u7d27\u6025\u6551\u52a9\u901a\u9053\u3002"));
        return guides;
    }

    private Map<String, Object> guide(String title, String content) {
        Map<String, Object> item = new HashMap<>();
        item.put("title", title);
        item.put("content", content);
        return item;
    }

    static class GuideViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView content;

        GuideViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.guide_title);
            content = itemView.findViewById(R.id.guide_content);
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
            holder.category.setText(categoryStr.isEmpty() ? "\u901a\u7528" : categoryStr);

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
