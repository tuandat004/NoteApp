package com.example.noteapp.AccountService.View;

import android.os.Bundle;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.R;
import com.example.noteapp.UserService.DAO.UserDao;
import com.example.noteapp.UserService.Entity.User;

import java.util.Random;

public class ForgotPasswordActivity extends AppCompatActivity {

    EditText edtEmail, edtNewPassword;
    EditText otp1, otp2, otp3, otp4, otp5, otp6;
    Button btnSendCode, btnReset;
    LinearLayout btnBack;
    TextView tvResend;

    UserDao userDao;
    String generatedOtp = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        // Ánh xạ
        edtEmail = findViewById(R.id.edtEmail);
        edtNewPassword = findViewById(R.id.edtNewPassword);

        otp1 = findViewById(R.id.otp1);
        otp2 = findViewById(R.id.otp2);
        otp3 = findViewById(R.id.otp3);
        otp4 = findViewById(R.id.otp4);
        otp5 = findViewById(R.id.otp5);
        otp6 = findViewById(R.id.otp6);

        btnSendCode = findViewById(R.id.btnSendCode);
        btnReset = findViewById(R.id.btnReset);

        btnBack = findViewById(R.id.btnBack);
        tvResend = findViewById(R.id.tvResend);

        userDao = AppDatabase.getInstance(this).userDao();

        // BACK
        btnBack.setOnClickListener(v -> finish());

        // SEND OTP
        btnSendCode.setOnClickListener(v -> sendOtp());

        // RESEND
        tvResend.setOnClickListener(v -> sendOtp());

        // RESET
        btnReset.setOnClickListener(v -> resetPassword());
    }

    private void sendOtp() {
        String email = edtEmail.getText().toString().trim();

        if (email.isEmpty()) {
            toast("Nhập email");
            return;
        }

        new Thread(() -> {
            User user = userDao.findByEmail(email);

            runOnUiThread(() -> {
                if (user == null) {
                    toast("Email không tồn tại");
                } else {
                    generatedOtp = String.valueOf(new Random().nextInt(900000) + 100000);
                    toast("OTP: " + generatedOtp); // demo
                }
            });
        }).start();
    }

    private void resetPassword() {
        String email = edtEmail.getText().toString().trim();
        String newPass = edtNewPassword.getText().toString().trim();

        String inputOtp =
                otp1.getText().toString() +
                        otp2.getText().toString() +
                        otp3.getText().toString() +
                        otp4.getText().toString() +
                        otp5.getText().toString() +
                        otp6.getText().toString();

        // VALIDATE
        if (email.isEmpty() || newPass.isEmpty() || inputOtp.isEmpty()) {
            toast("Nhập đầy đủ thông tin");
            return;
        }

        if (generatedOtp.isEmpty()) {
            toast("Vui lòng gửi OTP trước");
            return;
        }

        if (newPass.length() < 6) {
            toast("Password quá ngắn");
            return;
        }

        if (!inputOtp.equals(generatedOtp)) {
            toast("OTP sai");
            return;
        }

        new Thread(() -> {
            User user = userDao.findByEmail(email);

            runOnUiThread(() -> {
                if (user == null) {
                    toast("Email không tồn tại");
                } else {
                    user.password = newPass;
                    userDao.update(user);

                    toast("Đổi mật khẩu thành công");
                    finish(); // quay về Login
                }
            });
        }).start();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}