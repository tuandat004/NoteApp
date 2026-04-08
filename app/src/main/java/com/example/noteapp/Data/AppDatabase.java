package com.example.noteapp.Data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.noteapp.NoteService.DAO.NoteDao;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.NoteService.Entity.NoteTag;
import com.example.noteapp.NotificationService.DAO.NotificationDao;
import com.example.noteapp.NotificationService.Entity.AppNotification;
import com.example.noteapp.ReminderService.DAO.ReminderDao;
import com.example.noteapp.ReminderService.Entity.Reminder;
import com.example.noteapp.TagService.DAO.TagDao;
import com.example.noteapp.TagService.Entity.Tag;
import com.example.noteapp.UserService.DAO.UserDao;
import com.example.noteapp.UserService.Entity.User;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
    entities = {
        Note.class,
        Tag.class,
        NoteTag.class,
        User.class,
        Reminder.class,
        AppNotification.class
    },
    version = 9,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract NoteDao noteDao();
    public abstract TagDao tagDao();
    public abstract UserDao userDao();
    public abstract ReminderDao reminderDao();
    public abstract NotificationDao notificationDao();

    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE notes ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "note_app_db"
                            )
                            .addMigrations(MIGRATION_8_9)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}