package com.ourcat.app.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.ourcat.app.R;
import com.ourcat.app.utils.RetrofitHelper;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportCatDialogFragment extends DialogFragment {

    private Runnable onReportSuccess;

    public void setOnReportSuccess(Runnable r) {
        onReportSuccess = r;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_report_cat, container, false);
        EditText etLat = view.findViewById(R.id.report_lat);
        EditText etLng = view.findViewById(R.id.report_lng);
        EditText etDesc = view.findViewById(R.id.report_description);
        EditText etImageUrl = view.findViewById(R.id.report_image_url);
        EditText etColor = view.findViewById(R.id.report_color);
        EditText etFeature = view.findViewById(R.id.report_feature);
        EditText etPersonality = view.findViewById(R.id.report_personality);

        view.findViewById(R.id.report_submit).setOnClickListener(v -> {
            String latS = etLat.getText().toString().trim();
            String lngS = etLng.getText().toString().trim();
            if (latS.isEmpty() || lngS.isEmpty()) {
                Toast.makeText(requireContext(), "请填写经纬度", Toast.LENGTH_SHORT).show();
                return;
            }
            double lat, lng;
            try {
                lat = Double.parseDouble(latS);
                lng = Double.parseDouble(lngS);
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), "经纬度格式错误", Toast.LENGTH_SHORT).show();
                return;
            }
            Map<String, Object> body = new HashMap<>();
            body.put("lat", lat);
            body.put("lng", lng);
            body.put("description", etDesc.getText().toString().trim());
            body.put("imageUrl", etImageUrl.getText().toString().trim());
            String color = etColor.getText().toString().trim();
            String feature = etFeature.getText().toString().trim();
            String personality = etPersonality.getText().toString().trim();
            if (!color.isEmpty()) body.put("color", color);
            if (!feature.isEmpty()) body.put("feature", feature);
            if (!personality.isEmpty()) body.put("personality", personality);

            RetrofitHelper.get().create(com.ourcat.app.repositories.CatApi.class).report(body)
                    .enqueue(new Callback<Map<String, Object>>() {
                        @Override
                        public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(requireContext(), "上报成功", Toast.LENGTH_SHORT).show();
                                dismiss();
                                if (onReportSuccess != null) onReportSuccess.run();
                            } else {
                                Toast.makeText(requireContext(), "上报失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                            Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                        }
                    });
        });
        view.findViewById(R.id.report_cancel).setOnClickListener(v -> dismiss());
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog d = getDialog();
        if (d != null && d.getWindow() != null) {
            d.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}
