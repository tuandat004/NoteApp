package com.example.noteapp.MediaService.Entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import com.example.noteapp.NoteService.Entity.Note;

@Entity(
    tableName = "images",
    foreignKeys = @ForeignKey(
        entity = Note.class,
        parentColumns = "note_id",
        childColumns = "note_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("note_id")}
)
public class Image {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "image_id")
    public int imageId;

    @ColumnInfo(name = "note_id")
    public int noteId;

    @ColumnInfo(name = "image_path")
    public String imagePath;
}
