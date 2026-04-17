package com.example.noteapp.ReminderService.Worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;


import androidx.core.app.NotificationCompat;

import com.example.noteapp.CalendarService.View.CalendarActivity;
import com.example.noteapp.NoteService.View.CreateNoteActivity;
import com.example.noteapp.NoteService.View.NoteHomeActivity;
import androidx.core.app.TaskStackBuilder;
import com.example.noteapp.R;

/**
 * ReminderReceiver - BroadcastReceiver cho AlarmManager
 * - Hiển thị system notification với deep-link vào NoteDetailActivity
 * - Hoạt động ngay cả khi app đã bị tắt (do AlarmManager gửi)
 * - Nhận BOOT_COMPLETED để reschedule alarm sau khi điện thoại khởi động lại
 */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String KEY_NOTE_ID  = "NOTE_ID";
    public static final String KEY_TITLE    = "TITLE";
    public static final String KEY_SUBTITLE = "SUBTITLE";
    public static final String KEY_MESSAGE  = "MESSAGE";

    private static final String CHANNEL_ID   = "noteapp_reminders";
    private static final String CHANNEL_NAME = "Nhắc nhở ghi chú";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        // Xử lý BOOT_COMPLETED: không cần reschedule vì dùng AlarmManager.setExactAndAllowWhileIdle
        // Alarm sẽ tự fire khi đến thời điểm. Chỉ cần log.
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.d("ReminderReceiver", "Device booted - alarms will auto-trigger as scheduled");
            return;
        }

        // Xử lý alarm thông thường
        int noteId      = intent.getIntExtra(KEY_NOTE_ID, -1);
        String title    = intent.getStringExtra(KEY_TITLE);
        String subtitle = intent.getStringExtra(KEY_SUBTITLE);
        String message  = intent.getStringExtra(KEY_MESSAGE);

        if (title == null)    title    = "Nhắc nhở Ghi chú";
        if (subtitle == null) subtitle = "";
        if (message == null)  message  = subtitle.isEmpty() ? "Đã đến giờ xem ghi chú của bạn!" : subtitle;

        Log.d("ReminderReceiver", "Firing reminder for noteId=" + noteId + ", title=" + title);

        showNotification(context, intent, noteId, title, subtitle, message);
    }

    private void showNotification(Context context, Intent intent, int noteId, String title, String subtitle, String message) {
        NotificationManager manager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        // Tạo channel (idempotent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Nhắc nhở ghi chú của bạn");
            channel.enableLights(true);
            channel.setLightColor(Color.parseColor("#F4B400"));
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 200, 500});
            manager.createNotificationChannel(channel);
        }

        boolean isCalendarNote = intent.getBooleanExtra("IS_CALENDAR_NOTE", false);
        PendingIntent pendingIntent;

        if (isCalendarNote) {
            String calendarDate = intent.getStringExtra("CALENDAR_DATE");
            int calendarNoteId = intent.getIntExtra("CALENDAR_NOTE_ID", -1);

            Intent calendarIntent = new Intent(context, CalendarActivity.class);
            calendarIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            calendarIntent.putExtra("IS_CALENDAR_NOTE", true);
            calendarIntent.putExtra("CALENDAR_DATE", calendarDate);
            calendarIntent.putExtra("CALENDAR_NOTE_ID", calendarNoteId);

            pendingIntent = PendingIntent.getActivity(
                context,
                noteId,
                calendarIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            // Mở NoteHomeActivity ở dưới, CreateNoteActivity (chỉnh sửa) ở trên cho ghi chú thường
            Intent homeIntent = new Intent(context, NoteHomeActivity.class);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            Intent editIntent = new Intent(context, CreateNoteActivity.class);
            editIntent.putExtra("note_id", noteId);

            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
            stackBuilder.addNextIntent(homeIntent);
            stackBuilder.addNextIntent(editIntent);

            pendingIntent = stackBuilder.getPendingIntent(
                noteId,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        }

        // Build expanded text: subtitle on second line if available
        String contentText = subtitle.isEmpty() ? message : subtitle;
        NotificationCompat.BigTextStyle bigStyle = new NotificationCompat.BigTextStyle()
            .setBigContentTitle(title)
            .bigText(subtitle.isEmpty() ? message : subtitle + "\n" + message);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_note_logo)
            .setContentTitle("📝 " + title)
            .setContentText(contentText)
            .setSubText("QQ Notes")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setColor(Color.parseColor("#F4B400"))
            .setStyle(bigStyle)
            .setContentIntent(pendingIntent);

        // Dùng noteId làm notification ID để mỗi note có notification riêng
        int notifId = noteId > 0 ? noteId : (int) (System.currentTimeMillis() % 100000);
        manager.notify(notifId, builder.build());
    }
}
