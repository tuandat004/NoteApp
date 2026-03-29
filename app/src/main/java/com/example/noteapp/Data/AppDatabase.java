package com.example.noteapp.Data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.noteapp.NoteService.DAO.NoteDao;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.NoteService.Entity.NoteTag;
import com.example.noteapp.TagService.DAO.TagDao;
import com.example.noteapp.TagService.Entity.Tag;
import com.example.noteapp.UserService.Entity.User;//user
import com.example.noteapp.UserService.DAO.UserDao;//dao user

@Database(entities = {Note.class, Tag.class, NoteTag.class , User.class}, version = 3, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract NoteDao noteDao();
    public abstract TagDao tagDao();
    public abstract UserDao userDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "note_app_db"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}