package com.example.noteapp.MediaService.Entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.example.noteapp.NoteService.Entity.Note;

@Entity(
    tableName = "audio_recordings",
    foreignKeys = @ForeignKey(
        entity = Note.class,
        parentColumns = "note_id",
        childColumns = "note_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("note_id")}
)
public class AudioRecording {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "recording_id")
    public int recordingId;

    @ColumnInfo(name = "note_id")
    public int noteId;

    @ColumnInfo(name = "file_path")
    public String filePath;

    @ColumnInfo(name = "duration_ms")
    public long durationMs;

    @ColumnInfo(name = "created_at")
    public String createdAt;
}
