package com.example.noteapp.NoteService.View;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

public class CreateNoteActivity extends AppCompatActivity {

    private Button btnSave;
    private TextView txtCancel;
    private EditText edtTitle;
    private EditText edtContent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_note);

        btnSave = findViewById(R.id.btnSave);
        txtCancel = findViewById(R.id.txtCancel);
        edtTitle = findViewById(R.id.edtTitle);
        edtContent = findViewById(R.id.edtContent);

        btnSave.setOnClickListener(v -> saveNote());
        txtCancel.setOnClickListener(v -> finish());
    }

    private void saveNote() {
        String title = edtTitle.getText().toString().trim();
        String content = edtContent.getText().toString().trim();

        if (title.isEmpty() && content.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập ghi chú", Toast.LENGTH_SHORT).show();
            return;
        }

        Note note = new Note();
        note.userId = 1; // tạm hard-code nếu chưa có đăng nhập
        note.title = title;
        note.subtitle = "";
        note.content = content;
        note.color = "#FFFFFF";
        note.createdAt = getCurrentTime();
        note.updatedAt = getCurrentTime();
        note.isPinned = 0;
        note.isDeleted = 0;

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(getApplicationContext()).noteDao().insert(note);

            runOnUiThread(() -> {
                Toast.makeText(this, "Lưu ghi chú thành công", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
    }
}