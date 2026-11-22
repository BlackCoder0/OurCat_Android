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

public class RegisterFragment extends Fragment {

    private EditText etUsername, etPassword, etNickname, etCaptcha;
    private ImageView ivCaptcha;
    private Button btnRegister, btnGoLogin;
    private String captchaKey;
    private Call<?> captchaCall, registerCall;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_register, container, false);
        etUsername = view.findViewById(R.id.et_username);
        etPassword = view.findViewById(R.id.et_password);
        etNickname = view.findViewById(R.id.et_nickname);
        etCaptcha = view.findViewById(R.id.et_captcha);
        ivCaptcha = view.findViewById(R.id.iv_captcha);
        btnRegister = view.findViewById(R.id.btn_register);
        btnGoLogin = view.findViewById(R.id.btn_go_login);

        loadCaptcha();
        ivCaptcha.setOnClickListener(v -> loadCaptcha());
        btnRegister.setOnClickListener(v -> doRegister());
        btnGoLogin.setOnClickListener(v -> getParentFragmentManager().popBackStack());
        return view;
    }

    private void loadCaptcha() {
        if (captchaCall != null) captchaCall.cancel();
        captchaKey = null;
        etCaptcha.setText("");
        final int session = RetrofitHelper.getGeneration();
        Call<Map<String, String>> request = RetrofitHelper.get().create(com.ourcat.app.repositories.AuthApi.class).getCaptcha();
        captchaCall = request;
        request.enqueue(new Callback<Map<String, String>>() {
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
                            }
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, String>> call, Throwable t) {
                        if (!isAdded() || getView() == null || call != captchaCall || call.isCanceled()
                                || session != RetrofitHelper.getGeneration()) return;
                        Toast.makeText(requireContext(), "验证码加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void doRegister() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();
        String nickname = etNickname.getText().toString().trim();
        String captcha = etCaptcha.getText().toString().trim();
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password) || TextUtils.isEmpty(captcha)) {
            Toast.makeText(requireContext(), "请填写账号、密码和验证码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (captchaKey == null) {
            Toast.makeText(requireContext(), "请先加载验证码", Toast.LENGTH_SHORT).show();
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        body.put("nickname", TextUtils.isEmpty(nickname) ? username : nickname);
        body.put("captchaKey", captchaKey);
        body.put("captcha", captcha);

        btnRegister.setEnabled(false);
        final int session = RetrofitHelper.getGeneration();
        Call<Map<String, Object>> request = RetrofitHelper.get().create(com.ourcat.app.repositories.AuthApi.class).register(body);
        registerCall = request;
        request.enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (!isAdded() || getView() == null || call != registerCall
                                || session != RetrofitHelper.getGeneration()) return;
                        btnRegister.setEnabled(true);
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
                                    Toast.makeText(requireContext(), msg != null ? msg.toString() : "注册失败", Toast.LENGTH_SHORT).show();
                                } else Toast.makeText(requireContext(), "注册失败", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                                Toast.makeText(requireContext(), "注册失败", Toast.LENGTH_SHORT).show();
                            }
                            loadCaptcha();
                        }
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (!isAdded() || getView() == null || call != registerCall || call.isCanceled()
                                || session != RetrofitHelper.getGeneration()) return;
                        btnRegister.setEnabled(true);
                        Toast.makeText(requireContext(), "网络错误: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        if (captchaCall != null) captchaCall.cancel();
        if (registerCall != null) registerCall.cancel();
        captchaCall = null;
        registerCall = null;
        super.onDestroyView();
    }
}
