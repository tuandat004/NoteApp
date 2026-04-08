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
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        SharedPreferences appPrefs = getSharedPreferences("USER", MODE_PRIVATE);
        boolean isDark = appPrefs.getBoolean("dark_mode", false);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
            isDark ? androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES 
                   : androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        );
        
        setContentView(R.layout.activity_splash);

        progressBar = findViewById(R.id.progressBar);

        handler = new Handler(getMainLooper());
        handler.postDelayed(runnable, 80);
    }

    private Runnable runnable = new Runnable() {
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
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacks(runnable);
        }
    }

    private void checkLogin() {
        if (isLoggedIn()) {
            startActivity(new Intent(this, NoteHomeActivity.class));
        } else {
            startActivity(new Intent(this, LoginActivity.class));
        }
        finish();
    }

    private boolean isLoggedIn() {
        SharedPreferences prefs = getSharedPreferences("USER", MODE_PRIVATE);
        return prefs.getBoolean("isLoggedIn", false);
    }
}