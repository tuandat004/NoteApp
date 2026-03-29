package com.example.noteapp.Splash;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;

import com.example.noteapp.R;
import com.example.noteapp.AccountService.View.LoginActivity;
import com.example.noteapp.NoteService.View.NoteHomeActivity;

public class SplashActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private int progress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        progressBar = findViewById(R.id.progressBar);

        Handler handler = new Handler(getMainLooper());

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {

                progress += 5;
                progressBar.setProgress(progress);

                if (progress < 100) {
                    handler.postDelayed(this, 80);
                } else {
                    checkLogin();
                }
            }
        }, 80);
    }

    private void checkLogin() {
        new Handler(getMainLooper()).postDelayed(() -> {
            if (isLoggedIn()) {
                startActivity(new Intent(this, NoteHomeActivity.class));
            } else {
                startActivity(new Intent(this, LoginActivity.class));
            }
            finish();
        }, 200); // delay nhẹ
    }

    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("USER", MODE_PRIVATE);
        return prefs.getBoolean("isLoggedIn", false);
    }
}