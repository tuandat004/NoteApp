package com.example.noteapp.NotificationService.Manager;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.NotificationService.Entity.AppNotification;
import com.example.noteapp.NoteService.View.NoteHomeActivity;
import com.example.noteapp.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * AppNotificationManager:
 *  - sendSystemNotification(): hiển thị Android system notification
 *  - createInAppNotification(): lưu thông báo vào DB (in-app)
 *  - sendAndStore(): gửi system + lưu in-app cùng lúc
 */
public class AppNotificationManager {

    private static final String CHANNEL_GENERAL = "noteapp_general";
    private static final String CHANNEL_REMINDER = "noteapp_reminders";

    private static int notifIdCounter = 1000;

    /**
     * Tạo Notification Channels (gọi khi khởi động app)
     */
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = context.getSystemService(NotificationManager.class);

            // General channel
            NotificationChannel general = new NotificationChannel(
                CHANNEL_GENERAL,
                "Thông báo chung",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            general.setDescription("Thông báo từ NoteApp");
            general.enableLights(true);
            general.setLightColor(Color.parseColor("#F4B400"));
            nm.createNotificationChannel(general);

            // Reminder channel
            NotificationChannel reminder = new NotificationChannel(
                CHANNEL_REMINDER,
                "Nhắc nhở ghi chú",
                NotificationManager.IMPORTANCE_HIGH
            );
            reminder.setDescription("Nhắc nhở ghi chú định kỳ");
            reminder.enableLights(true);
            reminder.setLightColor(Color.parseColor("#F4B400"));
            reminder.enableVibration(true);
            nm.createNotificationChannel(reminder);
        }
    }

    /**
     * Gửi Android system notification
     */
    public static void sendSystemNotification(Context context, String title, String message, String channelId) {
        Intent intent = new Intent(context, NoteHomeActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_note_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setColor(Color.parseColor("#F4B400"))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message));

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(notifIdCounter++, builder.build());
    }

    /**
     * Lưu in-app notification vào DB
     */
    public static void createInAppNotification(Context context, int userId, String title, String message, String type) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppNotification notif = new AppNotification();
            notif.userId  = userId;
            notif.title   = title;
            notif.message = message;
            notif.type    = type;
            notif.isRead  = 0;
            notif.createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

            AppDatabase.getInstance(context).notificationDao().insert(notif);
        });
    }

    /**
     * Gửi system notification + lưu in-app cùng lúc
     */
    public static void sendAndStore(Context context, String title, String message, String type) {
        SharedPreferences prefs = context.getSharedPreferences("USER", Context.MODE_PRIVATE);
        int userId = prefs.getInt("user_id", -1);

        String channel = "reminder".equals(type) ? CHANNEL_REMINDER : CHANNEL_GENERAL;
        sendSystemNotification(context, title, message, channel);

        if (userId != -1) {
            createInAppNotification(context, userId, title, message, type);
        }
    }
}
