package com.example.noteapp.Data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.noteapp.MediaService.DAO.AudioRecordingDao;
import com.example.noteapp.MediaService.Entity.AudioRecording;
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
        AppNotification.class,
        AudioRecording.class
    },
    version = 12,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public abstract NoteDao noteDao();
    public abstract TagDao tagDao();
    public abstract UserDao userDao();
    public abstract ReminderDao reminderDao();
    public abstract NotificationDao notificationDao();
    public abstract AudioRecordingDao audioRecordingDao();

    // Migration 8 → 9: thêm cột deleted_at
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE notes ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0");
        }
    };

    // Migration 9 → 10: thêm cột is_locked, pin_hash với DEFAULT '' đúng
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE notes ADD COLUMN is_locked INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE notes ADD COLUMN pin_hash TEXT DEFAULT ''");
        }
    };

    // Migration 10 → 11: thêm bảng audio_recordings
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS audio_recordings (" +
                "recording_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "note_id INTEGER NOT NULL, " +
                "file_path TEXT, " +
                "duration_ms INTEGER NOT NULL DEFAULT 0, " +
                "created_at TEXT, " +
                "FOREIGN KEY(note_id) REFERENCES notes(note_id) ON DELETE CASCADE)"
            );
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_audio_recordings_note_id ON audio_recordings(note_id)"
            );
        }
    };

    /**
     * Migration 11 → 12: Fix lỗi pin_hash bị defaultValue='undefined' do migration 9→10
     * chạy sai (thiếu DEFAULT '').
     * SQLite không cho ALTER COLUMN DEFAULT nên phải recreate bảng notes.
     * Giữ toàn bộ dữ liệu, chỉ thay đổi schema.
     */
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 1. Tạo bảng tạm với schema đúng
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS notes_new (" +
                "note_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "user_id INTEGER NOT NULL, " +
                "title TEXT, " +
                "subtitle TEXT, " +
                "content TEXT, " +
                "color TEXT, " +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TEXT, " +
                "is_pinned INTEGER NOT NULL DEFAULT 0, " +
                "is_deleted INTEGER NOT NULL DEFAULT 0, " +
                "deleted_at INTEGER NOT NULL DEFAULT 0, " +
                "is_locked INTEGER NOT NULL DEFAULT 0, " +
                "pin_hash TEXT DEFAULT '')"   // DEFAULT '' đúng với entity
            );

            // 2. Copy toàn bộ dữ liệu sang bảng mới
            // Dùng COALESCE để chuyển NULL → '' cho pin_hash
            database.execSQL(
                "INSERT INTO notes_new " +
                "(note_id, user_id, title, subtitle, content, color, created_at, updated_at, " +
                " is_pinned, is_deleted, deleted_at, is_locked, pin_hash) " +
                "SELECT note_id, user_id, title, subtitle, content, color, created_at, updated_at, " +
                "       is_pinned, is_deleted, deleted_at, " +
                "       COALESCE(is_locked, 0), " +
                "       COALESCE(pin_hash, '') " +
                "FROM notes"
            );

            // 3. Xóa bảng cũ
            database.execSQL("DROP TABLE notes");

            // 4. Đổi tên bảng mới
            database.execSQL("ALTER TABLE notes_new RENAME TO notes");

            // 5. Tạo lại index
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_notes_user_id ON notes(user_id)"
            );
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
                            .addMigrations(
                                MIGRATION_8_9,
                                MIGRATION_9_10,
                                MIGRATION_10_11,
                                MIGRATION_11_12
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}