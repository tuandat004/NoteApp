package com.example.noteapp.AccountService.View;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.R;
import com.example.noteapp.UserService.DAO.UserDao;
import com.example.noteapp.UserService.Entity.User;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private EditText edtUsername, edtEmail, edtPassword, edtConfirmPassword;
    private Button btnRegister;
    private LinearLayout btnBack;
    private TextView tvLogin;
    private ImageView eyePass, eyeConfirm;

    private boolean isShowPass    = false;
    private boolean isShowConfirm = false;

    private UserDao userDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        edtUsername       = findViewById(R.id.edtUsername);
        edtEmail          = findViewById(R.id.edtEmail);
        edtPassword       = findViewById(R.id.edtPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);
        btnRegister       = findViewById(R.id.btnRegister);
        btnBack           = findViewById(R.id.btnBack);
        tvLogin           = findViewById(R.id.tvLogin);
        eyePass           = findViewById(R.id.eyePassword);
        eyeConfirm        = findViewById(R.id.eyeConfirm);

        userDao = AppDatabase.getInstance(this).userDao();

        btnBack.setOnClickListener(v -> finish());
        tvLogin.setOnClickListener(v -> finish());

        eyePass.setOnClickListener(v -> {
            isShowPass = !isShowPass;
            edtPassword.setInputType(isShowPass
                ? InputType.TYPE_CLASS_TEXT
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            edtPassword.setSelection(edtPassword.length());
            eyePass.setAlpha(isShowPass ? 1f : 0.4f);
        });

        eyeConfirm.setOnClickListener(v -> {
            isShowConfirm = !isShowConfirm;
            edtConfirmPassword.setInputType(isShowConfirm
                ? InputType.TYPE_CLASS_TEXT
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            edtConfirmPassword.setSelection(edtConfirmPassword.length());
            eyeConfirm.setAlpha(isShowConfirm ? 1f : 0.4f);
        });

        btnRegister.setOnClickListener(v -> handleRegister());
    }

    private void handleRegister() {
        String username = edtUsername.getText().toString().trim();
        String email    = edtEmail.getText().toString().trim();
        String pass     = edtPassword.getText().toString().trim();
        String confirm  = edtConfirmPassword.getText().toString().trim();

        // Validate
        if (username.isEmpty() || email.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
            toast("Vui lòng nhập đầy đủ thông tin");
            return;
        }

        if (username.length() < 4) {
            toast("Tên đăng nhập tối thiểu 4 ký tự");
            return;
        }

        if (!isValidEmail(email)) {
            toast("Email không hợp lệ");
            return;
        }

        if (!pass.equals(confirm)) {
            toast("Mật khẩu không khớp");
            return;
        }

        if (!isValidPassword(pass)) {
            toast("Mật khẩu cần ≥8 ký tự, có chữ hoa, chữ thường và số");
            return;
        }

        btnRegister.setEnabled(false);

        new Thread(() -> {
            if (userDao.findByUsername(username) != null) {
                runOnUiThread(() -> {
                    btnRegister.setEnabled(true);
                    toast("Tên đăng nhập đã tồn tại");
                });
                return;
            }
            if (userDao.findByEmail(email) != null) {
                runOnUiThread(() -> {
                    btnRegister.setEnabled(true);
                    toast("Email đã được sử dụng");
                });
                return;
            }

            User user      = new User();
            user.username  = username;
            user.email     = email;
            user.password  = pass;
            user.loginType = "local";
            user.createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            userDao.insert(user);

            runOnUiThread(() -> {
                toast("Đăng ký thành công! Vui lòng đăng nhập");
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
        }).start();
    }

    private boolean isValidEmail(String email) {
        return Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
                      .matcher(email).matches();
    }

    private boolean isValidPassword(String pass) {
        return pass.length() >= 8
            && pass.matches(".*[A-Z].*")
            && pass.matches(".*[a-z].*")
            && pass.matches(".*[0-9].*");
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}