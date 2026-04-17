package com.example.noteapp.AccountService.View;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;

import com.example.noteapp.AccountService.Service.EmailOtpService;
import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.R;
import com.example.noteapp.UserService.DAO.UserDao;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * ForgotPasswordActivity - 3 bước:
 *   Step 1: Nhập email → Gửi OTP qua Gmail
 *   Step 2: Nhập OTP 6 chữ số (auto-jump giữa các ô)
 *   Step 3: Nhập mật khẩu mới (chỉ mở khi OTP đúng)
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    // Step containers
    private LinearLayout stepEmailContainer, stepOtpContainer, stepPasswordContainer;

    // Step 1
    private EditText edtEmail;
    private Button btnSendCode;
    private TextView tvResend, tvCountdown;

    // Step 2 - OTP boxes
    private EditText otp1, otp2, otp3, otp4, otp5, otp6;

    // Step 3
    private EditText edtNewPassword, edtConfirmNewPassword;
    private Button btnReset;
    private ImageView eyeNew, eyeConfirmNew;

    // Back
    private LinearLayout btnBack;

    // State
    private UserDao userDao;
    private String generatedOtp = "";
    private String verifiedEmail = "";
    private boolean otpVerified  = false;
    private boolean isShowNew    = false;
    private boolean isShowConfirmNew = false;

    private CountDownTimer countDownTimer;
    private static final int OTP_EXPIRE_SECONDS = 300; // 5 phút

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
        setContentView(R.layout.activity_forgot_password);

        userDao = AppDatabase.getInstance(this).userDao();

        // Bind views
        btnBack = findViewById(R.id.btnBack);

        stepEmailContainer    = findViewById(R.id.stepEmailContainer);
        stepOtpContainer      = findViewById(R.id.stepOtpContainer);
        stepPasswordContainer = findViewById(R.id.stepPasswordContainer);

        edtEmail    = findViewById(R.id.edtEmail);
        btnSendCode = findViewById(R.id.btnSendCode);
        tvResend    = findViewById(R.id.tvResend);
        tvCountdown = findViewById(R.id.tvCountdown);

        otp1 = findViewById(R.id.otp1);
        otp2 = findViewById(R.id.otp2);
        otp3 = findViewById(R.id.otp3);
        otp4 = findViewById(R.id.otp4);
        otp5 = findViewById(R.id.otp5);
        otp6 = findViewById(R.id.otp6);

        edtNewPassword       = findViewById(R.id.edtNewPassword);
        edtConfirmNewPassword = findViewById(R.id.edtConfirmNewPassword);
        btnReset             = findViewById(R.id.btnReset);
        eyeNew               = findViewById(R.id.eyeNew);
        eyeConfirmNew        = findViewById(R.id.eyeConfirmNew);

        // Initial state: chỉ hiện step 1
        showStep(1);

        // Listeners
        btnBack.setOnClickListener(v -> finish());
        btnSendCode.setOnClickListener(v -> sendOtp());
        tvResend.setOnClickListener(v -> {
            if (tvResend.isEnabled()) sendOtp();
        });
        btnReset.setOnClickListener(v -> resetPassword());

        // Eye toggles
        eyeNew.setOnClickListener(v -> {
            isShowNew = !isShowNew;
            edtNewPassword.setInputType(isShowNew
                ? InputType.TYPE_CLASS_TEXT
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            edtNewPassword.setSelection(edtNewPassword.length());
            eyeNew.setAlpha(isShowNew ? 1f : 0.4f);
        });
        eyeConfirmNew.setOnClickListener(v -> {
            isShowConfirmNew = !isShowConfirmNew;
            edtConfirmNewPassword.setInputType(isShowConfirmNew
                ? InputType.TYPE_CLASS_TEXT
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            edtConfirmNewPassword.setSelection(edtConfirmNewPassword.length());
            eyeConfirmNew.setAlpha(isShowConfirmNew ? 1f : 0.4f);
        });

        // Setup OTP auto-jump
        setupOtpAutoJump(otp1, otp2, otp3, otp4, otp5, otp6);
    }

    // ─── Step 1: Gửi OTP ─────────────────────────────────────────────────────
    private void sendOtp() {
        String email = edtEmail.getText().toString().trim();

        if (email.isEmpty()) {
            toast("Vui lòng nhập email");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Email không hợp lệ");
            return;
        }

        btnSendCode.setEnabled(false);
        btnSendCode.setText("Đang gửi...");
        tvResend.setEnabled(false);
        tvResend.setAlpha(0.4f);

        new Thread(() -> {
            com.example.noteapp.UserService.Entity.User user = userDao.findByEmail(email);
            runOnUiThread(() -> {
                if (user == null) {
                    toast("Email không tồn tại trong hệ thống");
                    btnSendCode.setEnabled(true);
                    btnSendCode.setText("Gửi mã OTP");
                    tvResend.setEnabled(true);
                    tvResend.setAlpha(1f);
                    return;
                }

                verifiedEmail = email;
                generatedOtp  = EmailOtpService.generateOtp();
                android.util.Log.d("ForgotPassword", "Generated OTP: " + generatedOtp);

                if (stepOtpContainer.getVisibility() == View.VISIBLE) {
                    otp1.setText(""); otp2.setText(""); otp3.setText("");
                    otp4.setText(""); otp5.setText(""); otp6.setText("");
                    otp1.requestFocus();
                }

                // Gửi email thực
                EmailOtpService.sendOtp(email, generatedOtp, new EmailOtpService.OtpCallback() {
                    @Override
                    public void onSuccess(String otp) {
                        toast("Mã OTP đã gửi đến " + email);
                        btnSendCode.setText("Đã gửi ✓");
                        showStep(2);
                        startCountdown();
                    }

                    @Override
                    public void onFailure(String errorMessage) {
                        runOnUiThread(() -> {
                            new android.app.AlertDialog.Builder(ForgotPasswordActivity.this)
                                .setTitle("\u26a0\ufe0f Kh\u00f4ng th\u1ec3 g\u1eedi Email OTP")
                                .setMessage(errorMessage)
                                .setPositiveButton("OK", null)
                                .setCancelable(false)
                                .show();
                            btnSendCode.setEnabled(true);
                            btnSendCode.setText("G\u1eedi m\u00e3 OTP");
                            tvResend.setEnabled(true);
                            tvResend.setAlpha(1f);
                        });
                    }
                });
            });
        }).start();
    }

    // ─── OTP auto-jump ────────────────────────────────────────────────────────
    private void setupOtpAutoJump(EditText... boxes) {
        // Giới hạn mỗi ô 1 ký tự
        InputFilter[] filter = new InputFilter[]{new InputFilter.LengthFilter(1)};
        for (EditText box : boxes) {
            box.setFilters(filter);
        }

        for (int i = 0; i < boxes.length; i++) {
            final int idx = i;
            final EditText current = boxes[i];
            final EditText next    = (i < boxes.length - 1) ? boxes[i + 1] : null;
            final EditText prev    = (i > 0) ? boxes[i - 1] : null;

            current.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void afterTextChanged(Editable s) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (s.length() == 1 && next != null) {
                        next.requestFocus();
                    }
                    // Khi đủ 6 ô → tự động verify
                    if (idx == boxes.length - 1 && s.length() == 1) {
                        verifyOtpAndProceed(boxes);
                    }
                }
            });

            // Xử lý backspace
            current.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DEL
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && current.getText().toString().isEmpty()
                    && prev != null) {
                    prev.requestFocus();
                    prev.setText("");
                }
                return false;
            });
        }
    }

    // ─── Verify OTP ──────────────────────────────────────────────────────────
    private void verifyOtpAndProceed(EditText[] boxes) {
        StringBuilder sb = new StringBuilder();
        for (EditText box : boxes) sb.append(box.getText().toString());
        String inputOtp = sb.toString();

        if (inputOtp.length() < 6) return;

        if (inputOtp.equals(generatedOtp)) {
            otpVerified = true;
            if (countDownTimer != null) countDownTimer.cancel();
            toast("OTP hợp lệ! ✓");
            showStep(3);
        } else {
            toast("Mã OTP không đúng, vui lòng thử lại");
            // Xóa hết các ô
            for (EditText box : boxes) box.setText("");
            boxes[0].requestFocus();
        }
    }

    // ─── Step 3: Đặt lại mật khẩu ────────────────────────────────────────────
    private void resetPassword() {
        if (!otpVerified) {
            toast("Vui lòng xác minh OTP trước");
            return;
        }

        String newPass     = edtNewPassword.getText().toString().trim();
        String confirmPass = edtConfirmNewPassword.getText().toString().trim();

        if (newPass.isEmpty() || confirmPass.isEmpty()) {
            toast("Vui lòng nhập đầy đủ mật khẩu mới");
            return;
        }

        if (!newPass.equals(confirmPass)) {
            toast("Mật khẩu không khớp");
            return;
        }

        if (!isValidPassword(newPass)) {
            toast("Mật khẩu cần ≥8 ký tự, có chữ hoa, chữ thường và số");
            return;
        }

        btnReset.setEnabled(false);

        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        new Thread(() -> {
            userDao.updatePassword(verifiedEmail, newPass, now);
            runOnUiThread(() -> {
                showSuccessDialog();
            });
        }).start();
    }

    // ─── Countdown timer ─────────────────────────────────────────────────────
    private void startCountdown() {
        tvResend.setEnabled(false);
        tvResend.setAlpha(0.4f);

        if (countDownTimer != null) countDownTimer.cancel();

        countDownTimer = new CountDownTimer(OTP_EXPIRE_SECONDS * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                int secs = (int)(millisUntilFinished / 1000);
                int m = secs / 60, s = secs % 60;
                tvCountdown.setText(String.format(Locale.getDefault(), "Mã hết hạn sau %02d:%02d", m, s));
            }

            @Override
            public void onFinish() {
                tvCountdown.setText("Mã đã hết hạn");
                tvResend.setEnabled(true);
                tvResend.setAlpha(1f);
                generatedOtp = ""; // Vô hiệu hóa mã cũ
            }
        }.start();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private void showStep(int step) {
        stepEmailContainer.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        stepOtpContainer.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        stepPasswordContainer.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
    }

    private void showSuccessDialog() {
        new AlertDialog.Builder(this)
            .setTitle("✅ Thành công")
            .setMessage("Mật khẩu đã được cập nhật!\nBạn có thể đăng nhập với mật khẩu mới.")
            .setCancelable(false)
            .setPositiveButton("Đăng nhập ngay", (d, w) -> {
                Intent intent = new Intent(this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            })
            .show();
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
    }
}