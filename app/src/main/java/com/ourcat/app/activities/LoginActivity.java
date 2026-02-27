package com.ourcat.app.activities;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
import com.ourcat.app.R;
import com.ourcat.app.fragments.LoginFragment;
import com.ourcat.app.utils.TokenStore;

public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TokenStore tokenStore = new TokenStore(this);
        if (tokenStore.hasToken()) {
            startMainAndFinish();
            return;
        }
        setContentView(R.layout.activity_login);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.login_container, new LoginFragment())
                    .commit();
        }
    }

    public void startMainAndFinish() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
