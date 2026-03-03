package com.ourcat.app.activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import com.ourcat.app.R;
import com.ourcat.app.fragments.LoginFragment;
import com.ourcat.app.utils.TokenStore;

public class LoginActivity extends AppCompatActivity {

    private static final long SPLASH_DELAY_MS = 1500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_OurCat);
        super.onCreate(savedInstanceState);
        TokenStore tokenStore = new TokenStore(this);
        if (tokenStore.hasToken()) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed(this::startMainAndFinish, SPLASH_DELAY_MS);
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(() -> {
                    setContentView(R.layout.activity_login);
                    if (savedInstanceState == null) {
                        getSupportFragmentManager().beginTransaction()
                                .replace(R.id.login_container, new LoginFragment())
                                .commit();
                    }
                }, SPLASH_DELAY_MS);
    }

    public void startMainAndFinish() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
