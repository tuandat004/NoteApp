package com.example.noteapp.UserService.DAO;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.noteapp.UserService.Entity.User;

@Dao
public interface UserDao {

    @Insert
    void insert(User user);

    @Update
    void update(User user);

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    User findByUsername(String username);

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User findByEmail(String email);

    @Query("SELECT * FROM users WHERE user_id = :userId LIMIT 1")
    User getUserById(int userId);

    @Query("SELECT * FROM users WHERE google_id = :googleId LIMIT 1")
    User findByGoogleId(String googleId);

    @Query("SELECT * FROM users WHERE facebook_id = :facebookId LIMIT 1")
    User findByFacebookId(String facebookId);

    @Query("UPDATE users SET full_name = :fullName, phone = :phone, avatar = :avatar, updated_at = :updatedAt WHERE user_id = :userId")
    void updateProfile(int userId, String fullName, String phone, String avatar, String updatedAt);

    @Query("UPDATE users SET password = :newPassword, updated_at = :updatedAt WHERE email = :email")
    void updatePassword(String email, String newPassword, String updatedAt);
}