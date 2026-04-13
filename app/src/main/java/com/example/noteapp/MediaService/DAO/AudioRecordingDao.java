package com.example.noteapp.MediaService.DAO;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.noteapp.MediaService.Entity.AudioRecording;

import java.util.List;

@Dao
public interface AudioRecordingDao {

    @Insert
    long insert(AudioRecording recording);

    @Delete
    void delete(AudioRecording recording);

    @Query("SELECT * FROM audio_recordings WHERE note_id = :noteId ORDER BY recording_id DESC")
    List<AudioRecording> getRecordingsByNoteId(int noteId);

    @Query("DELETE FROM audio_recordings WHERE note_id = :noteId")
    void deleteAllForNote(int noteId);
}
