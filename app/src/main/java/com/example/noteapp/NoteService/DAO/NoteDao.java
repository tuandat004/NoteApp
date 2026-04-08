package com.example.noteapp.NoteService.DAO;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.noteapp.NoteService.Entity.Note;

import java.util.List;

@Dao
public interface NoteDao {

    @Insert
    long insert(Note note);

    @Update
    void update(Note note);

    @Query("SELECT * FROM notes WHERE is_deleted = 0 AND user_id = :userId ORDER BY note_id DESC")
    LiveData<List<Note>> getAllNotes(int userId);

    @Query("SELECT * FROM notes WHERE is_deleted = 0 AND user_id = :userId AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%') ORDER BY note_id DESC")
    LiveData<List<Note>> searchNotes(int userId, String query);

    @Query("SELECT * FROM notes WHERE note_id = :noteId LIMIT 1")
    Note getNoteById(int noteId);

    @Query("SELECT * FROM notes WHERE is_deleted = 1 AND user_id = :userId ORDER BY deleted_at DESC")
    LiveData<List<Note>> getDeletedNotes(int userId);

    @Query("UPDATE notes SET is_deleted = 1, deleted_at = :timestamp WHERE note_id = :noteId")
    void softDeleteNote(int noteId, long timestamp);

    @Query("UPDATE notes SET is_deleted = 0, deleted_at = 0 WHERE note_id = :noteId")
    void restoreNote(int noteId);

    @Query("DELETE FROM notes WHERE note_id = :noteId")
    void hardDeleteNote(int noteId);
}