package com.example.noteapp.NoteService.View;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.NoteService.Adapter.NoteAdapter;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.R;

import java.util.List;
import java.util.concurrent.Executors;

public class TrashActivity extends AppCompatActivity {

    private ImageView btnBackTrash;
    private RecyclerView rvTrashNotes;
    private NoteAdapter adapter;
    private int sessionUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trash);

        btnBackTrash = findViewById(R.id.btnBackTrash);
        rvTrashNotes = findViewById(R.id.rvTrashNotes);

        btnBackTrash.setOnClickListener(v -> finish());

        SharedPreferences prefs = getSharedPreferences("USER", MODE_PRIVATE);
        sessionUserId = prefs.getInt("user_id", -1);

        rvTrashNotes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NoteAdapter(this);
        
        // Custom click logic for Trash
        adapter.setOnNoteClickListener(note -> showTrashOptions(note));
        
        rvTrashNotes.setAdapter(adapter);

        loadDeletedNotes();
    }

    private void loadDeletedNotes() {
        if (sessionUserId == -1) return;
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        db.noteDao().getDeletedNotes(sessionUserId).observe(this, notes -> {
            adapter.setData(notes);
        });
    }

    private void showTrashOptions(Note note) {
        String[] options = {"Khôi phục ghi chú", "Xóa vĩnh viễn"};
        new AlertDialog.Builder(this)
            .setTitle(note.title == null || note.title.isEmpty() ? "Ghi chú" : note.title)
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    restoreNote(note.noteId);
                } else if (which == 1) {
                    permanentlyDeleteNote(note.noteId);
                }
            })
            .show();
    }

    private void restoreNote(int noteId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            db.noteDao().restoreNote(noteId);
            runOnUiThread(() -> Toast.makeText(this, "Đã khôi phục ghi chú", Toast.LENGTH_SHORT).show());
        });
    }

    private void permanentlyDeleteNote(int noteId) {
        new AlertDialog.Builder(this)
            .setTitle("Xóa Vĩnh Viễn")
            .setMessage("Hành động này không thể hoàn tác. Bạn có chắc chắn muốn xóa?")
            .setPositiveButton("Xóa", (dialog, which) -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                    db.noteDao().hardDeleteNote(noteId);
                    runOnUiThread(() -> Toast.makeText(this, "Đã xóa vĩnh viễn", Toast.LENGTH_SHORT).show());
                });
            })
            .setNegativeButton("Hủy", null)
            .show();
    }
}
