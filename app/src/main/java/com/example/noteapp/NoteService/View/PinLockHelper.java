package com.example.noteapp.NoteService.View;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.security.MessageDigest;
import java.util.concurrent.Executor;

/**
 * Trợ lý quản lý khóa ghi chú bằng PIN hoặc vân tay.
 */
public class PinLockHelper {

    public interface LockCallback {
        void onSuccess();
        void onCancel();
    }

    // ─── Hash PIN ────────────────────────────────────────────────────────────

    public static String hashPin(String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(pin.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return pin; // Fallback nếu lỗi
        }
    }

    public static boolean verifyPin(String inputPin, String storedHash) {
        if (storedHash == null || storedHash.isEmpty()) return false;
        return hashPin(inputPin).equals(storedHash);
    }

    // ─── Kiểm tra vân tay có dùng được không ────────────────────────────────

    public static boolean isBiometricAvailable(Context context) {
        BiometricManager manager = BiometricManager.from(context);
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    // ─── Dialog chọn loại khóa ───────────────────────────────────────────────

    public static void showLockTypeDialog(Context context, OnLockTypeSelectedListener listener) {
        boolean bioAvailable = isBiometricAvailable(context);
        String[] options = bioAvailable
                ? new String[]{"🔢 Khóa bằng mã PIN", "👆 Khóa bằng vân tay"}
                : new String[]{"🔢 Khóa bằng mã PIN"};

        new AlertDialog.Builder(context)
                .setTitle("Chọn loại khóa")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) listener.onPinSelected();
                    else if (which == 1) listener.onBiometricSelected();
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    public interface OnLockTypeSelectedListener {
        void onPinSelected();
        void onBiometricSelected();
    }

    // ─── Dialog nhập PIN mới (khi khóa) ─────────────────────────────────────

    public static void showSetPinDialog(Context context, OnPinSetListener listener) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 20);
        layout.setPadding(pad, dp(context, 12), pad, 0);

        EditText edtPin = new EditText(context);
        edtPin.setHint("Nhập mã PIN (4–8 số)");
        edtPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(edtPin);

        EditText edtConfirm = new EditText(context);
        edtConfirm.setHint("Nhập lại mã PIN");
        edtConfirm.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        edtConfirm.setPadding(0, dp(context, 12), 0, 0);
        layout.addView(edtConfirm);

        new AlertDialog.Builder(context)
                .setTitle("Đặt mã PIN")
                .setView(layout)
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    String pin = edtPin.getText().toString().trim();
                    String confirm = edtConfirm.getText().toString().trim();
                    if (pin.length() < 4) {
                        Toast.makeText(context, "PIN phải có ít nhất 4 chữ số", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!pin.equals(confirm)) {
                        Toast.makeText(context, "Mã PIN không khớp!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    listener.onPinSet(hashPin(pin));
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    public interface OnPinSetListener {
        void onPinSet(String pinHash);
    }

    // ─── Dialog xác nhận PIN (khi mở khóa / hủy khóa) ──────────────────────

    public static void showVerifyPinDialog(Context context, String storedHash, LockCallback callback) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(context, 20);
        layout.setPadding(pad, dp(context, 12), pad, 0);

        EditText edtPin = new EditText(context);
        edtPin.setHint("Nhập mã PIN");
        edtPin.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        layout.addView(edtPin);

        new AlertDialog.Builder(context)
                .setTitle("Xác nhận mã PIN")
                .setView(layout)
                .setPositiveButton("Xác nhận", (dialog, which) -> {
                    String input = edtPin.getText().toString().trim();
                    if (verifyPin(input, storedHash)) {
                        callback.onSuccess();
                    } else {
                        Toast.makeText(context, "Mã PIN không đúng!", Toast.LENGTH_SHORT).show();
                        callback.onCancel();
                    }
                })
                .setNegativeButton("Hủy", (d, w) -> callback.onCancel())
                .show();
    }

    // ─── Biometric authentication ────────────────────────────────────────────

    public static void showBiometricPrompt(FragmentActivity activity, LockCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                callback.onSuccess();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(activity, "Xác thực vân tay thất bại", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                callback.onCancel();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Xác thực ghi chú")
                .setSubtitle("Sử dụng vân tay để xác nhận")
                .setNegativeButtonText("Hủy")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    // ─── Util ────────────────────────────────────────────────────────────────

    private static int dp(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
