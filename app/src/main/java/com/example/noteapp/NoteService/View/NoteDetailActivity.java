package com.example.noteapp.NoteService.View;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.R;
import com.example.noteapp.TagService.Entity.Tag;

import java.util.List;
import java.util.concurrent.Executors;

public class NoteDetailActivity extends AppCompatActivity {

    private TextView txtBack;
    private TextView txtHeaderTitle;
    private TextView txtTitle;
    private TextView txtTags;
    private TextView txtCreatedAt;
    private TextView txtSubtitle;
    private TextView txtContent;
    private TextView txtReminder;
    private TextView txtLink;
    private Button btnEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        txtBack = findViewById(R.id.txtBack);
        txtHeaderTitle = findViewById(R.id.txtHeaderTitle);
        txtTitle = findViewById(R.id.txtTitle);
        txtTags = findViewById(R.id.txtTags);
        txtCreatedAt = findViewById(R.id.txtCreatedAt);
        txtSubtitle = findViewById(R.id.txtSubtitle);
        txtContent = findViewById(R.id.txtContent);
        txtReminder = findViewById(R.id.txtReminder);
        txtLink = findViewById(R.id.txtLink);
        btnEdit = findViewById(R.id.btnEdit);

        txtBack.setOnClickListener(v -> finish());

        int noteId = getIntent().getIntExtra("note_id", -1);
        if (noteId == -1) {
            Toast.makeText(this, "Không tìm thấy ghi chú", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadNoteDetail(noteId);
    }

    private void loadNoteDetail(int noteId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());

            Note note = db.noteDao().getNoteById(noteId);
            List<Tag> tags = db.tagDao().getTagsByNoteId(noteId);

            runOnUiThread(() -> {
                if (note == null) {
                    Toast.makeText(this, "Ghi chú không tồn tại", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                String title = note.title != null ? note.title : "";
                String subtitle = note.subtitle != null ? note.subtitle : "";
                String content = note.content != null ? note.content : "";
                String createdAt = note.createdAt != null ? note.createdAt : "";

                txtHeaderTitle.setText(title);
                txtTitle.setText(title);
                txtContent.setText(content);
                txtCreatedAt.setText(createdAt);

                if (subtitle.isEmpty()) {
                    txtSubtitle.setVisibility(View.GONE);
                } else {
                    txtSubtitle.setVisibility(View.VISIBLE);
                    txtSubtitle.setText(subtitle);
                }

                if (tags != null && !tags.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < tags.size(); i++) {
                        sb.append(tags.get(i).tagName);
                        if (i < tags.size() - 1) {
                            sb.append(", ");
                        }
                    }
                    txtTags.setText(sb.toString());
                    txtTags.setVisibility(View.VISIBLE);
                } else {
                    txtTags.setVisibility(View.GONE);
                }

                txtReminder.setVisibility(View.GONE);
                txtLink.setVisibility(View.GONE);
            });
        });
    }
}