package com.example.noteapp.NotificationService.DAO;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.noteapp.NotificationService.Entity.AppNotification;
import java.util.List;

@Dao
public interface NotificationDao {

    @Insert
    void insert(AppNotification notification);

    @Query("SELECT * FROM app_notifications WHERE user_id = :userId ORDER BY created_at DESC")
    LiveData<List<AppNotification>> getAllByUser(int userId);

    @Query("SELECT * FROM app_notifications WHERE user_id = :userId AND is_read = 0 ORDER BY created_at DESC")
    List<AppNotification> getUnreadByUser(int userId);

    @Query("SELECT COUNT(*) FROM app_notifications WHERE user_id = :userId AND is_read = 0")
    int getUnreadCount(int userId);

    @Query("UPDATE app_notifications SET is_read = 1 WHERE notif_id = :notifId")
    void markAsRead(int notifId);

    @Query("UPDATE app_notifications SET is_read = 1 WHERE user_id = :userId")
    void markAllAsRead(int userId);

    @Query("DELETE FROM app_notifications WHERE user_id = :userId")
    void clearAll(int userId);
}
