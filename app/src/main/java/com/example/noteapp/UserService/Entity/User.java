package com.example.noteapp.UserService.Entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "users",
    indices = {
        @Index(value = "username", unique = true),
        @Index(value = "email", unique = true)
    }
)
public class User {
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "user_id")
    public int userId;

    @ColumnInfo(name = "username")
    public String username;

    @ColumnInfo(name = "email")
    public String email;

    @ColumnInfo(name = "password")
    public String password;

    @ColumnInfo(name = "full_name")
    public String fullName;

    @ColumnInfo(name = "phone")
    public String phone;

    @ColumnInfo(name = "avatar")
    public String avatar;

    // "local" | "google" | "facebook"
    @ColumnInfo(name = "login_type", defaultValue = "local")
    public String loginType;

    @ColumnInfo(name = "google_id")
    public String googleId;

    @ColumnInfo(name = "facebook_id")
    public String facebookId;

    @ColumnInfo(name = "created_at", defaultValue = "CURRENT_TIMESTAMP")
    public String createdAt;

    @ColumnInfo(name = "updated_at", defaultValue = "CURRENT_TIMESTAMP")
    public String updatedAt;
}
