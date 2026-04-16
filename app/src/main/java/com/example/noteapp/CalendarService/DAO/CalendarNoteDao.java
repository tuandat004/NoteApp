package com.example.noteapp.CalendarService.DAO;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.noteapp.CalendarService.Entity.CalendarNote;

import java.util.List;

@Dao
public interface CalendarNoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(CalendarNote note);

    @Update
    void update(CalendarNote note);

    @Delete
    void delete(CalendarNote note);

    @Query("SELECT * FROM calendar_notes WHERE userId = :userId AND dateStr = :dateStr ORDER BY id ASC")
    List<CalendarNote> getNotesByDate(int userId, String dateStr);

    /** Returns distinct dates that have notes in a given month (prefix "yyyy-MM") */
    @Query("SELECT DISTINCT dateStr FROM calendar_notes WHERE userId = :userId AND dateStr LIKE :monthPrefix || '%'")
    List<String> getDatesWithNotes(int userId, String monthPrefix);

    @Query("SELECT * FROM calendar_notes WHERE id = :id LIMIT 1")
    CalendarNote getById(int id);

    @Query("DELETE FROM calendar_notes WHERE id = :id")
    void deleteById(int id);
}
