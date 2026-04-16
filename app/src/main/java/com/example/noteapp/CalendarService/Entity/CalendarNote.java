package com.example.noteapp.CalendarService.Entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "calendar_notes")
public class CalendarNote {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public int userId;

    /** Format: "yyyy-MM-dd" */
    public String dateStr;

    public String title;
    public String content;

    /** Nullable — ISO datetime "yyyy-MM-dd HH:mm" */
    public String reminderTime;

    public String createdAt;
    public String updatedAt;
}
