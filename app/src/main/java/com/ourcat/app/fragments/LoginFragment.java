package com.ourcat.app.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.TextView;
import android.text.InputType;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AlertDialog;

import com.ourcat.app.R;
import com.ourcat.app.BuildConfig;
import com.ourcat.app.activities.LoginActivity;
import com.ourcat.app.utils.RetrofitHelper;
import com.ourcat.app.utils.RecentCatStore;
import com.ourcat.app.utils.ServerConfig;
import com.ourcat.app.utils.TokenStore;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginFragment extends Fragment {

    private EditText etUsername, etPassword, etCaptcha;
    private ImageView ivCaptcha;
    private Button btnLogin, btnGoRegister;
    private Button btnServer;
    private String captchaKey;
    private Call<Map<String, String>> captchaCall;
    private Call<Map<String, Object>> loginCall;
    private TextView connectionStatus;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_login, container, false);
        etUsername = view.findViewById(R.id.et_username);
        etPassword = view.findViewById(R.id.et_password);
        etCaptcha = view.findViewById(R.id.et_captcha);
        ivCaptcha = view.findViewById(R.id.iv_captcha);
        btnLogin = view.findViewById(R.id.btn_login);
        btnGoRegister = view.findViewById(R.id.btn_go_register);
        btnServer = view.findViewById(R.id.btn_server);
        connectionStatus = view.findViewById(R.id.connection_status);

        if (BuildConfig.ALLOW_SERVER_SWITCH) {
            btnServer.setVisibility(View.VISIBLE);
            btnServer.setOnClickListener(v -> showServerDialog());
        }

        loadCaptcha();
        ivCaptcha.setOnClickListener(v -> loadCaptcha());
        btnLogin.setOnClickListener(v -> doLogin());
        btnGoRegister.setOnClickListener(v -> {
            if (getActivity() instanceof LoginActivity) {
                ((LoginActivity) getActivity()).getSupportFragmentManager().beginTransaction()
                        .replace(R.id.login_container, new RegisterFragment())
                        .addToBackStack(null)
                        .commit();
            }
        });
        return view;
    }

    private void showServerDialog() {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("http://电脑地址:8080/");
        String current = ServerConfig.get(requireContext());
        input.setText(current != null ? current : RetrofitHelper.getBaseUrl());
        input.setSelectAllOnFocus(true);

        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        container.setPadding(padding, 0, padding, 0);
        container.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("设置后端地址")
                .setMessage("模拟器：http://10.0.2.2:8080/\n手机：填写电脑的局域网地址")
                .setView(container).setNegativeButton("取消", null)
                .setPositiveButton("保存并重新登录", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                applyServerUrl(RetrofitHelper.normalizeBaseUrl(input.getText().toString()));
                dialog.dismiss();
            } catch (IllegalArgumentException e) {
                input.setError(e.getMessage());
            }
        }));
        dialog.show();
    }

    private void applyServerUrl(String value) {
        try {
            String normalized = RetrofitHelper.normalizeBaseUrl(value);
            String old = RetrofitHelper.getBaseUrl();
            if (!normalized.equals(old)) {
                if (loginCall != null) loginCall.cancel();
                loginCall = null;
                btnLogin.setEnabled(true);
                ServerConfig.save(requireContext(), normalized);
                new TokenStore(requireContext()).clear();
                RecentCatStore.clear(requireContext());
                RetrofitHelper.init(requireContext(), normalized);
            }
            Toast.makeText(requireContext(), "后端地址已保存", Toast.LENGTH_SHORT).show();
            loadCaptcha();
        } catch (IllegalArgumentException e) {
            Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadCaptcha() {
        if (captchaCall != null) captchaCall.cancel();
        captchaKey = null;
        etCaptcha.setText("");
        ivCaptcha.setImageDrawable(null);
        if (BuildConfig.ALLOW_SERVER_SWITCH) {
            connectionStatus.setVisibility(View.VISIBLE);
            connectionStatus.setText("正在连接 " + RetrofitHelper.getBaseUrl());
        }
        final int session = RetrofitHelper.getGeneration();
        captchaCall = RetrofitHelper.get().create(com.ourcat.app.repositories.AuthApi.class).getCaptcha();
        captchaCall.enqueue(new Callback<Map<String, String>>() {
                    @Override
                    public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                        if (!isAdded() || getView() == null || call != captchaCall
                                || session != RetrofitHelper.getGeneration()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            captchaKey = response.body().get("key");
                            String image = response.body().get("image");
                            if (image != null && image.startsWith("data:image")) {
                                String base64 = image.substring(image.indexOf(",") + 1);
                                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                ivCaptcha.setImageBitmap(bitmap);
                                if (BuildConfig.ALLOW_SERVER_SWITCH) connectionStatus.setText("已连接 " + RetrofitHelper.getBaseUrl());
                            }
                        } else if (BuildConfig.ALLOW_SERVER_SWITCH) {
                            connectionStatus.setText("后端返回 " + response.code() + "，请检查地址后点击验证码重试");
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, String>> call, Throwable t) {
                        if (!isAdded() || getView() == null || call != captchaCall || call.isCanceled()
                                || session != RetrofitHelper.getGeneration()) return;
                        if (BuildConfig.ALLOW_SERVER_SWITCH) connectionStatus.setText("无法连接后端，请确认电脑已启动服务、地址正确且网络可达。点击验证码重试。");
                        Toast.makeText(requireContext(), "验证码加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();
        String captcha = etCaptcha.getText().toString().trim();
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password) || TextUtils.isEmpty(captcha)) {
            Toast.makeText(requireContext(), "请填写完整", Toast.LENGTH_SHORT).show();
            return;
        }
        if (captchaKey == null) {
            Toast.makeText(requireContext(), "请先加载验证码", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("captchaKey", captchaKey);
        body.put("captcha", captcha);

        btnLogin.setEnabled(false);
        final int session = RetrofitHelper.getGeneration();
        loginCall = RetrofitHelper.get().create(com.ourcat.app.repositories.AuthApi.class).login(body);
        loginCall.enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded() || getView() == null || call != loginCall
                                || session != RetrofitHelper.getGeneration()) return;
                        btnLogin.setEnabled(true);
                        if (response.isSuccessful() && response.body() != null) {
                            Object token = response.body().get("token");
                            if (token != null) {
                                new TokenStore(requireContext()).saveToken(token.toString());
                                if (getActivity() instanceof LoginActivity) {
                                    ((LoginActivity) getActivity()).startMainAndFinish();
                                }
                            }
                        } else {
                            try {
                                if (response.errorBody() != null) {
                                    com.google.gson.Gson gson = new com.google.gson.Gson();
                                    Map m = gson.fromJson(response.errorBody().string(), Map.class);
                                    Object msg = m.get("message");
                                    Toast.makeText(requireContext(), msg != null ? msg.toString() : "登录失败", Toast.LENGTH_SHORT).show();
                                } else Toast.makeText(requireContext(), "登录失败", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(requireContext(), "登录失败", Toast.LENGTH_SHORT).show();
                            }
                            loadCaptcha();
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (!isAdded() || getView() == null || call != loginCall || call.isCanceled()
                                || session != RetrofitHelper.getGeneration()) return;
                        btnLogin.setEnabled(true);
                        Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        if (captchaCall != null) captchaCall.cancel();
        if (loginCall != null) loginCall.cancel();
        captchaCall = null;
        loginCall = null;
        super.onDestroyView();
    }
}
