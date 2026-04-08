package com.example.noteapp.AccountService.Service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * EmailOtpService - Gửi OTP thực qua Gmail SMTP (JavaMail)
 *
 * Cấu hình:
 *   - Thay SENDER_EMAIL bằng Gmail của bạn
 *   - Thay SENDER_APP_PASSWORD bằng "App Password" (không phải password Gmail thường)
 *     Tạo App Password tại: myaccount.google.com → Bảo mật → Mật khẩu ứng dụng
 */
public class EmailOtpService {

    // ===== C\u1ea4U H\u00ccNH GMAIL =====
    // B\u01b0\u1edbc 1: V\u00e0o myaccount.google.com \u2192 B\u1ea3o m\u1eadt \u2192 X\u00e1c minh 2 b\u01b0\u1edbc (b\u1eadt l\u00ean n\u1ebfu ch\u01b0a b\u1eadt)
    // B\u01b0\u1edbc 2: V\u00e0o myaccount.google.com \u2192 B\u1ea3o m\u1eadt \u2192 M\u1eadt kh\u1ea9u \u1ee9ng d\u1ee5ng
    // B\u01b0\u1edbc 3: Ch\u1ecdn app "Kh\u00e1c" \u2192 \u0110\u1eb7t t\u00ean "NoteApp" \u2192 T\u1ea1o \u2192 Copy 16 k\u00fd t\u1ef1 (kh\u00f4ng c\u00f3 d\u1ea5u c\u00e1ch)
    // B\u01b0\u1edbc 4: D\u00e1n v\u00e0o SENDER_APP_PASS b\u00ean d\u01b0\u1edbi (16 k\u00fd t\u1ef1 d\u1ea1ng "xxxx xxxx xxxx xxxx")
    private static final String SENDER_EMAIL    = "nguyenduongquoc9a11@gmail.com"; // Xin hãy thay bằng Gmail của bạn!
    private static final String SENDER_APP_PASS = "fozxjdujcrshbiyq"; // Đã bỏ khoảng trắng theo chuẩn
    // ==========================

    // Ki\u1ec3m tra c\u1ea5u h\u00ecnh h\u1ee3p l\u1ec7
    private static boolean isEmailConfigured() {
        return SENDER_EMAIL != null && !SENDER_EMAIL.contains("YOUR_")
            && SENDER_APP_PASS != null && !SENDER_APP_PASS.contains("YOUR_")
            && SENDER_APP_PASS.length() >= 16;
    }

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OtpCallback {
        void onSuccess(String otp);
        void onFailure(String errorMessage);
    }

    /**
     * Tạo mã OTP 6 số ngẫu nhiên
     */
    public static String generateOtp() {
        int otp = (int)(Math.random() * 900000) + 100000;
        return String.valueOf(otp);
    }

    /**
     * Gửi OTP về email người dùng
     * @param recipientEmail email nhận
     * @param otp            mã OTP 6 số
     * @param callback       callback trả về UI thread
     */
    public static void sendOtp(String recipientEmail, String otp, OtpCallback callback) {
        // Ki\u1ec3m tra c\u1ea5u h\u00ecnh tr\u01b0\u1edbc
        if (!isEmailConfigured()) {
            mainHandler.post(() -> callback.onFailure(
                "Email ch\u01b0a \u0111\u01b0\u1ee3c c\u1ea5u h\u00ecnh!\n"
                + "Vui l\u00f2ng \u0111i\u1ec1n SENDER_EMAIL v\u00e0 SENDER_APP_PASS\n"
                + "trong file EmailOtpService.java\n"
                + "(C\u1ea7n App Password 16 k\u00fd t\u1ef1 t\u1eeb myaccount.google.com)"));
            return;
        }

        executor.execute(() -> {
            try {
                Properties props = new Properties();
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
                props.put("mail.smtp.connectiontimeout", "15000");
                props.put("mail.smtp.timeout", "15000");
                props.put("mail.smtp.writetimeout", "15000");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(SENDER_EMAIL, SENDER_APP_PASS);
                    }
                });
                session.setDebug(false); // \u0110\u1eb7t true \u0111\u1ec3 xem log SMTP khi debug

                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(SENDER_EMAIL, "NoteApp"));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail));
                message.setSubject("NoteApp - M\u00e3 x\u00e1c th\u1ef1c OTP c\u1ee7a b\u1ea1n");
                message.setContent(buildHtmlEmail(otp), "text/html; charset=utf-8");

                Transport.send(message);
                android.util.Log.d("EmailOTP", "OTP sent successfully to: " + recipientEmail);

                mainHandler.post(() -> callback.onSuccess(otp));

            } catch (Exception e) {
                android.util.Log.e("EmailOTP", "Failed to send OTP: " + e.getMessage(), e);
                String errMsg = e.getMessage();
                if (errMsg != null && errMsg.contains("AuthenticationFailedException")) {
                    errMsg = "X\u00e1c th\u1ef1c Gmail th\u1ea5t b\u1ea1i!\nKi\u1ec3m tra l\u1ea1i App Password (ph\u1ea3i d\u00f9ng App Password, kh\u00f4ng ph\u1ea3i m\u1eadt kh\u1ea9u Gmail th\u01b0\u1eddng)";
                } else if (errMsg != null && errMsg.contains("connect")) {
                    errMsg = "Kh\u00f4ng k\u1ebft n\u1ed1i \u0111\u01b0\u1ee3c! Ki\u1ec3m tra k\u1ebft n\u1ed1i m\u1ea1ng.";
                }
                final String finalErr = errMsg;
                mainHandler.post(() -> callback.onFailure(finalErr));
            }
        });
    }

    /**
     * HTML template email OTP đẹp
     */
    private static String buildHtmlEmail(String otp) {
        return "<!DOCTYPE html>" +
               "<html><head><meta charset='utf-8'></head><body style='margin:0;padding:0;background:#f6f6f6;font-family:Arial,sans-serif'>" +
               "<table width='100%' cellpadding='0' cellspacing='0'>" +
               "<tr><td align='center' style='padding:40px 20px'>" +
               "<table width='480' style='background:#fff;border-radius:12px;overflow:hidden;box-shadow:0 4px 20px rgba(0,0,0,0.1)'>" +
               // Header
               "<tr><td style='background:#1A1A2E;padding:30px;text-align:center'>" +
               "<h1 style='color:#F4B400;margin:0;font-size:28px'>📝 NoteApp</h1>" +
               "<p style='color:#aaa;margin:8px 0 0'>Xác thực tài khoản</p>" +
               "</td></tr>" +
               // Body
               "<tr><td style='padding:40px 30px;text-align:center'>" +
               "<p style='color:#333;font-size:16px'>Mã OTP của bạn là:</p>" +
               "<div style='background:#FFF8E1;border:2px dashed #F4B400;border-radius:12px;padding:20px;margin:20px 0'>" +
               "<span style='font-size:40px;font-weight:bold;letter-spacing:12px;color:#1A1A2E'>" + otp + "</span>" +
               "</div>" +
               "<p style='color:#888;font-size:14px'>Mã có hiệu lực trong <strong>5 phút</strong>. Không chia sẻ mã này với ai.</p>" +
               "</td></tr>" +
               // Footer
               "<tr><td style='background:#f9f9f9;padding:20px;text-align:center;border-top:1px solid #eee'>" +
               "<p style='color:#aaa;font-size:12px;margin:0'>NoteApp • Ứng dụng ghi chú thông minh</p>" +
               "</td></tr>" +
               "</table>" +
               "</td></tr>" +
               "</table>" +
               "</body></html>";
    }
}
