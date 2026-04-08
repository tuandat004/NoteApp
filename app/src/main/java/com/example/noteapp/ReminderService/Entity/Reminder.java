package com.example.noteapp.ReminderService.Entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.noteapp.NoteService.Entity.Note;

@Entity(
    tableName = "reminders",
    foreignKeys = @ForeignKey(
        entity = Note.class,
        parentColumns = "note_id",
        childColumns = "note_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("note_id"), @Index("user_id")}
)
public class Reminder {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "reminder_id")
    public int reminderId;

    @ColumnInfo(name = "note_id")
    public int noteId;

    @ColumnInfo(name = "user_id")
    public int userId;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "reminder_time")
    public String reminderTime; // ISO format: yyyy-MM-dd HH:mm

    @ColumnInfo(name = "is_done", defaultValue = "0")
    public int isDone;

    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    public String createdAt;
}
