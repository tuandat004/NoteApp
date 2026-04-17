package com.example.noteapp.AccountService.View;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.NotificationService.Manager.AppNotificationManager;
import com.example.noteapp.NoteService.View.NoteHomeActivity;
import com.example.noteapp.R;
import com.example.noteapp.UserService.DAO.UserDao;
import com.example.noteapp.UserService.Entity.User;

public class LoginActivity extends AppCompatActivity {

    private EditText edtUsername, edtPassword;
    private Button btnLogin;
    private TextView tvRegister, tvForgot;
    private ImageView eyePassword;
    private LinearLayout btnGoogle, btnFacebook;
    private ProgressBar progressLoading;

    private UserDao userDao;
    private boolean isShowPass = false;
    private int loginAttempt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_login);

        edtUsername     = findViewById(R.id.edtUsername);
        edtPassword     = findViewById(R.id.edtPassword);
        btnLogin        = findViewById(R.id.btnLogin);
        tvRegister      = findViewById(R.id.tvRegister);
        tvForgot        = findViewById(R.id.tvForgot);
        eyePassword     = findViewById(R.id.eyePassword);
        btnGoogle       = findViewById(R.id.btnGoogle);
        btnFacebook     = findViewById(R.id.btnFacebook);
        progressLoading = findViewById(R.id.progressLoading);

        userDao = AppDatabase.getInstance(this).userDao();

        // Khởi tạo kênh thông báo
        AppNotificationManager.createChannels(this);

        // Hiện/ẩn mật khẩu
        eyePassword.setOnClickListener(v -> {
            isShowPass = !isShowPass;
            if (isShowPass) {
                edtPassword.setInputType(InputType.TYPE_CLASS_TEXT);
                eyePassword.setAlpha(1f);
            } else {
                edtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                eyePassword.setAlpha(0.4f);
            }
            edtPassword.setSelection(edtPassword.length());
        });

        btnLogin.setOnClickListener(v -> handleLogin());

        tvForgot.setOnClickListener(v ->
            startActivity(new Intent(this, ForgotPasswordActivity.class))
        );

        tvRegister.setOnClickListener(v ->
            startActivity(new Intent(this, RegisterActivity.class))
        );

        // Đăng nhập Google/Facebook: tính năng đang phát triển
        btnGoogle.setOnClickListener(v ->
            Toast.makeText(this, "Tính năng này đang được tiến hành", Toast.LENGTH_SHORT).show()
        );

        btnFacebook.setOnClickListener(v ->
            Toast.makeText(this, "Tính năng này đang được tiến hành", Toast.LENGTH_SHORT).show()
        );
    }

    // ─── Đăng nhập bằng username/password ────────────────────────────────────
    private void handleLogin() {
        String username = edtUsername.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            toast("Vui lòng nhập đầy đủ thông tin");
            return;
        }

        setLoading(true);

        new Thread(() -> {
            User user = userDao.findByUsername(username);
            runOnUiThread(() -> {
                setLoading(false);
                if (user != null && user.password != null && user.password.equals(password)) {
                    loginAttempt = 0;
                    saveSession(user);
                    toast("Đăng nhập thành công! Chào " + user.username);
                    goToHome();
                } else {
                    loginAttempt++;
                    if (loginAttempt >= 5) {
                        btnLogin.setEnabled(false);
                        toast("Đăng nhập sai 5 lần. Vui lòng thử lại sau.");
                    } else {
                        toast("Sai tài khoản hoặc mật khẩu (" + loginAttempt + "/5)");
                    }
                }
            });
        }).start();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────
    private void saveSession(User user) {
        getSharedPreferences("USER", MODE_PRIVATE)
            .edit()
            .putInt("user_id", user.userId)
            .putString("username", user.username)
            .putString("email", user.email != null ? user.email : "")
            .putString("full_name", user.fullName != null ? user.fullName : "")
            .putString("avatar", user.avatar != null ? user.avatar : "")
            .putBoolean("isLoggedIn", true)
            .apply();
    }

    private void goToHome() {
        Intent intent = new Intent(this, NoteHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading) {
        progressLoading.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
