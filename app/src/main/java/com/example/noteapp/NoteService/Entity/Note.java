package com.example.noteapp.NoteService.Entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.example.noteapp.UserService.Entity.User;

@Entity(
    tableName = "notes",
    indices = {@Index("user_id")}
)
public class Note {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "note_id")
    public int noteId;

    @ColumnInfo(name = "user_id")
    public int userId;

    @ColumnInfo(name = "title")
    public String title;

    @ColumnInfo(name = "subtitle")
    public String subtitle;

    @ColumnInfo(name = "content")
    public String content;

    @ColumnInfo(name = "color")
    public String color;

    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    public String createdAt;

    @ColumnInfo(name = "updated_at")
    public String updatedAt;

    @ColumnInfo(name = "is_pinned", defaultValue = "0")
    public int isPinned;

    @ColumnInfo(name = "is_deleted", defaultValue = "0")
    public int isDeleted;

    @ColumnInfo(name = "deleted_at", defaultValue = "0")
    public long deletedAt;
}
