package com.ourcat.app.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.ourcat.app.R;
import com.ourcat.app.repositories.RescueApi;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RescueDetailFragment extends Fragment {

    private long activityId;
    private TextView detailTitle;
    private TextView detailUrgencyStatus;
    private TextView detailDescription;
    private TextView linkSquarePost;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            activityId = getArguments().getLong("activityId", 0L);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_rescue_detail, container, false);
        detailTitle = view.findViewById(R.id.detail_title);
        detailUrgencyStatus = view.findViewById(R.id.detail_urgency_status);
        detailDescription = view.findViewById(R.id.detail_description);
        linkSquarePost = view.findViewById(R.id.link_square_post);

        Button btnClaim = view.findViewById(R.id.btn_claim);
        Button btnAssign = view.findViewById(R.id.btn_assign);
        Button btnMarkComplete = view.findViewById(R.id.btn_mark_complete);
        RecyclerView tasksList = view.findViewById(R.id.tasks_list);
        TextView linkMap = view.findViewById(R.id.link_map);
        btnClaim.setVisibility(View.GONE);
        btnAssign.setVisibility(View.GONE);
        btnMarkComplete.setVisibility(View.GONE);
        tasksList.setVisibility(View.GONE);
        linkMap.setVisibility(View.GONE);

        load();
        return view;
    }

    private void load() {
        RetrofitHelper.get().create(RescueApi.class).getActivity(activityId)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded()) {
                            return;
                        }
                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(requireContext(), "加载失败", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        Map<String, Object> activity = response.body();
                        detailTitle.setText(stringValue(activity.get("title")));
                        String status = stringValue(activity.get("status"));
                        detailUrgencyStatus.setText(status);
                        detailDescription.setText(stringValue(activity.get("description")));

                        long squarePostId = longValue(activity.get("squarePostId"));
                        if (squarePostId > 0) {
                            Bundle args = new Bundle();
                            args.putLong("squarePostId", squarePostId);
                            Navigation.findNavController(requireView()).navigate(R.id.square_detail, args);
                            return;
                        }
                        linkSquarePost.setVisibility(View.GONE);
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private String stringValue(Object value) {
        return value != null ? value.toString() : "";
    }

    private long longValue(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }
}
