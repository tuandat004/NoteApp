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

public class RegisterActivity extends AppCompatActivity {

    EditText edtUsername, edtEmail, edtPassword, edtConfirmPassword;
    Button btnRegister;
    LinearLayout btnBack;
    TextView tvLogin;

    ImageView eyePass, eyeConfirm;
    boolean isShowPass = false;
    boolean isShowConfirm = false;

    UserDao userDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Ánh xạ
        edtUsername = findViewById(R.id.edtUsername);
        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword);

        btnRegister = findViewById(R.id.btnRegister);
        btnBack = findViewById(R.id.btnBack);
        tvLogin = findViewById(R.id.tvLogin);

        eyePass = findViewById(R.id.eyePassword);
        eyeConfirm = findViewById(R.id.eyeConfirm);

        // DB
        userDao = AppDatabase.getInstance(this).userDao();

        // BACK
        btnBack.setOnClickListener(v -> finish());

        // LOGIN TEXT
        tvLogin.setOnClickListener(v -> finish());

        // SHOW PASSWORD
        eyePass.setOnClickListener(v -> {
            isShowPass = !isShowPass;
            if (isShowPass) {
                edtPassword.setInputType(InputType.TYPE_CLASS_TEXT);
            } else {
                edtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            edtPassword.setSelection(edtPassword.length());
        });

        // SHOW CONFIRM
        eyeConfirm.setOnClickListener(v -> {
            isShowConfirm = !isShowConfirm;
            if (isShowConfirm) {
                edtConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT);
            } else {
                edtConfirmPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            }
            edtConfirmPassword.setSelection(edtConfirmPassword.length());
        });

        // REGISTER
        btnRegister.setOnClickListener(v -> handleRegister());
    }

    private void handleRegister() {

        String username = edtUsername.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String pass = edtPassword.getText().toString().trim();
        String confirm = edtConfirmPassword.getText().toString().trim();

        // VALIDATE
        if (username.isEmpty() || email.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
            toast("Vui lòng nhập đầy đủ");
            return;
        }

        if (!email.contains("@")) {
            toast("Email không hợp lệ");
            return;
        }

        if (!pass.equals(confirm)) {
            toast("Mật khẩu không khớp");
            return;
        }

        // 🔥 VALIDATE PASSWORD THEO ĐỀ
        if (!isValidPassword(pass)) {
            toast("Password >=8 ký tự, có chữ, số, chữ hoa");
            return;
        }

        new Thread(() -> {

            // check trùng username
            if (userDao.findByUsername(username) != null) {
                runOnUiThread(() -> toast("Username đã tồn tại"));
                return;
            }

            // check trùng email
            if (userDao.findByEmail(email) != null) {
                runOnUiThread(() -> toast("Email đã tồn tại"));
                return;
            }

            // tạo user
            User user = new User();
            user.username = username;
            user.email = email;
            user.password = pass;

            userDao.insert(user);

            runOnUiThread(() -> {
                Toast.makeText(this, "Đăng ký thành công", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(RegisterActivity.this, LoginActivity.class);
                startActivity(intent);
                finish();
            });

        }).start();
    }

    // 🔥 VALIDATE PASSWORD
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