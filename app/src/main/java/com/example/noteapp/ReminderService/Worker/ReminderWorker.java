package com.example.noteapp.ReminderService.Worker;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.noteapp.NoteService.View.NoteDetailActivity;
import com.example.noteapp.R;

public class ReminderWorker extends Worker {

    public static final String KEY_NOTE_ID = "NOTE_ID";
    public static final String KEY_TITLE = "TITLE";
    public static final String KEY_MESSAGE = "MESSAGE";
    private static final String CHANNEL_ID = "NOTE_REMINDER_CHANNEL";

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String title = getInputData().getString(KEY_TITLE);
        String message = getInputData().getString(KEY_MESSAGE);
        int noteId = getInputData().getInt(KEY_NOTE_ID, -1);

        if (title == null) title = "Time to check your Note!";
        if (message == null) message = "A recorded reminder has triggered.";

        showNotification(title, message, noteId);
        return Result.success();
    }

    private void showNotification(String title, String message, int noteId) {
        Context context = getApplicationContext();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Note Reminders",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Shows notifications for scheduled note reminders");
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Intent intent = new Intent(context, NoteDetailActivity.class);
        if (noteId != -1) {
            intent.putExtra("note_id", noteId);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                noteId != -1 ? noteId : 0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (manager != null) {
            manager.notify(noteId != -1 ? noteId : (int) System.currentTimeMillis(), builder.build());
        }
    }
}
