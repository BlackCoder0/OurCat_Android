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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ourcat.app.R;
import com.ourcat.app.activities.LoginActivity;
import com.ourcat.app.utils.RetrofitHelper;
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
    private String captchaKey;

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

    private void loadCaptcha() {
        RetrofitHelper.get().create(com.ourcat.app.repositories.AuthApi.class).getCaptcha()
                .enqueue(new Callback<Map<String, String>>() {
                    @Override
                    public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            captchaKey = response.body().get("key");
                            String image = response.body().get("image");
                            if (image != null && image.startsWith("data:image")) {
                                String base64 = image.substring(image.indexOf(",") + 1);
                                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                                ivCaptcha.setImageBitmap(bitmap);
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, String>> call, Throwable t) {
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
        RetrofitHelper.get().create(com.ourcat.app.repositories.AuthApi.class).login(body)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
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
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        btnLogin.setEnabled(true);
                        Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
