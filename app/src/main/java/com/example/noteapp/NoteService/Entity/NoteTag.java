package com.example.noteapp.NoteService.Entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import com.example.noteapp.TagService.Entity.Tag;

@Entity(
    tableName = "note_tags",
    primaryKeys = {"note_id", "tag_id"},
    foreignKeys = {
        @ForeignKey(
            entity = Note.class,
            parentColumns = "note_id",
            childColumns = "note_id",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = Tag.class,
            parentColumns = "tag_id",
            childColumns = "tag_id",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {@Index("tag_id"), @Index("note_id")}
)
public class NoteTag {
    @ColumnInfo(name = "note_id")
    public int noteId;

    @ColumnInfo(name = "tag_id")
    public int tagId;
}
