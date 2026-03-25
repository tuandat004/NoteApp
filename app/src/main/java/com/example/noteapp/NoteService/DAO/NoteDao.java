package com.example.noteapp.NoteService.DAO;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.noteapp.NoteService.Entity.Note;

import java.util.List;

@Dao
public interface NoteDao {

    @Insert
    long insert(Note note);

    @Query("SELECT * FROM notes WHERE is_deleted = 0 ORDER BY note_id DESC")
    LiveData<List<Note>> getAllNotes();
}