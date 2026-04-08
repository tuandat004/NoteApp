package com.example.noteapp.ReminderService.DAO;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.noteapp.ReminderService.Entity.Reminder;
import java.util.List;

@Dao
public interface ReminderDao {

    @Insert
    long insert(Reminder reminder);

    @Update
    void update(Reminder reminder);

    @Delete
    void delete(Reminder reminder);

    @Query("SELECT * FROM reminders WHERE note_id = :noteId ORDER BY reminder_time ASC")
    List<Reminder> getByNoteId(int noteId);

    @Query("SELECT * FROM reminders WHERE note_id = :noteId ORDER BY reminder_time ASC LIMIT 1")
    Reminder getReminderByNoteId(int noteId);

    @Query("SELECT * FROM reminders WHERE user_id = :userId ORDER BY reminder_time ASC")
    LiveData<List<Reminder>> getAllByUser(int userId);

    @Query("SELECT * FROM reminders WHERE is_done = 0 AND user_id = :userId ORDER BY reminder_time ASC")
    List<Reminder> getPendingByUser(int userId);

    @Query("SELECT * FROM reminders WHERE reminder_id = :reminderId LIMIT 1")
    Reminder getById(int reminderId);

    @Query("UPDATE reminders SET is_done = 1 WHERE reminder_id = :reminderId")
    void markAsDone(int reminderId);

    @Query("DELETE FROM reminders WHERE note_id = :noteId")
    void deleteByNoteId(int noteId);
}
