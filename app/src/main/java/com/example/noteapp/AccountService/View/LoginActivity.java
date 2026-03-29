package com.example.noteapp.AccountService.View;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.R;
import com.example.noteapp.UserService.DAO.UserDao;
import com.example.noteapp.UserService.Entity.User;
import com.example.noteapp.NoteService.View.NoteHomeActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText edtUsername, edtPassword;
    private Button btnLogin;
    private UserDao userDao;
    private TextView tvRegister, tvForgot;

    private int loginAttempt = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // 🔥 Ánh xạ view
        edtUsername = findViewById(R.id.edtUsername);
        edtPassword = findViewById(R.id.edtPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);
        tvForgot = findViewById(R.id.tvForgot);

        // 🔥 Lấy database
        userDao = AppDatabase.getInstance(this).userDao();

        // 🔥 Bắt sự kiện login
        btnLogin.setOnClickListener(v -> handleLogin());

        // 👉 đi tới Register
        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // 👉 đi tới Forgot Password
        tvForgot.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });
    }

    private void handleLogin() {
        String username = edtUsername.getText().toString().trim();
        String password = edtPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập đầy đủ", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            User user = userDao.findByUsername(username);

            runOnUiThread(() -> {
                if (user != null && user.password.equals(password)) {

                    loginAttempt = 0;

                    // 🔥 lưu session
                    getSharedPreferences("USER", MODE_PRIVATE)
                            .edit()
                            .putInt("user_id", user.userId)
                            .putString("username", user.username)
                            .putBoolean("isLoggedIn", true)
                            .apply();

                    Toast.makeText(this, "Đăng nhập thành công", Toast.LENGTH_SHORT).show();

                    // 👉 chuyển màn hình
                    Intent intent = new Intent(LoginActivity.this, NoteHomeActivity.class);
                    startActivity(intent);
                    finish();

                } else {

                    loginAttempt++;

                    if (loginAttempt >= 3) {
                        btnLogin.setEnabled(false);
                        Toast.makeText(this, "Bạn đã nhập sai 3 lần", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Sai tài khoản hoặc mật khẩu", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }).start();
    }
}
