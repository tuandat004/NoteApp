package com.example.noteapp.NotificationService.Entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "app_notifications",
    indices = {@Index("user_id")}
)
public class AppNotification {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "notif_id")
    public int notifId;

    @ColumnInfo(name = "user_id")
    public int userId;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "message")
    public String message;

    // "reminder" | "system" | "update" | "general"
    @ColumnInfo(name = "type", defaultValue = "general")
    public String type;

    @ColumnInfo(name = "is_read", defaultValue = "0")
    public int isRead;

    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    public String createdAt;
}
