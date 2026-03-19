package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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

public class RescueKnowledgeFragment extends Fragment {

    private RecyclerView articlesList;
    private ArticlesAdapter articlesAdapter;
    private List<Map<String, Object>> articles = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rescue_knowledge, container, false);

        articlesList = view.findViewById(R.id.articles_list);
        articlesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        articlesAdapter = new ArticlesAdapter();
        articlesList.setAdapter(articlesAdapter);

        loadArticles();
        return view;
    }

    private void loadArticles() {
        RetrofitHelper.get().create(RescueApi.class).getArticles(null)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                            Response<List<Map<String, Object>>> response) {
                        if (!isAdded()) return;
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        articles.clear();
                        articles.addAll(response.body());
                        articlesAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    class ArticlesAdapter extends RecyclerView.Adapter<ArticlesAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_rescue_article, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Map<String, Object> article = articles.get(position);
            holder.title.setText(String.valueOf(article.get("title")));
            String category = String.valueOf(article.get("category"));
            holder.category.setText(category.isEmpty() ? "通用" : category);

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

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, category;

            ViewHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.article_title);
                category = itemView.findViewById(R.id.article_category);
            }
        }
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
