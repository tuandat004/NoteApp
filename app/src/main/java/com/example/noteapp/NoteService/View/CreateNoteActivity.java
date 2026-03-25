package com.example.noteapp.NoteService.View;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.NoteService.Entity.NoteTag;
import com.example.noteapp.R;
import com.example.noteapp.TagService.Entity.Tag;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

public class CreateNoteActivity extends AppCompatActivity {

    private Button btnSave;
    private TextView txtCancel;
    private EditText edtTitle, edtSubtitle, edtContent;
    private LinearLayout layoutTagsContainer;

    private final List<Tag> allTags = new ArrayList<>();
    private final Set<Integer> selectedTagIds = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_note);

        btnSave = findViewById(R.id.btnSave);
        txtCancel = findViewById(R.id.txtCancel);
        edtTitle = findViewById(R.id.edtTitle);
        edtSubtitle = findViewById(R.id.edtSubtitle);
        edtContent = findViewById(R.id.edtContent);
        layoutTagsContainer = findViewById(R.id.layoutTagsContainer);

        btnSave.setOnClickListener(v -> saveNote());
        txtCancel.setOnClickListener(v -> finish());

        loadTags();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTags();
    }

    private void loadTags() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Tag> tags = AppDatabase.getInstance(getApplicationContext())
                    .tagDao()
                    .getAllTags();

            runOnUiThread(() -> {
                allTags.clear();
                allTags.addAll(tags);
                renderTags();
            });
        });
    }

    private void renderTags() {
        layoutTagsContainer.removeAllViews();

        if (allTags.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("Chưa có tag nào. Hãy tạo tag ở màn hình chính.");
            emptyView.setTextSize(14f);
            emptyView.setTextColor(0xFF8A7F73);
            layoutTagsContainer.addView(emptyView);
            return;
        }

        for (Tag tag : allTags) {
            TextView chip = new TextView(this);
            chip.setText(tag.tagName);
            chip.setTextSize(14f);
            chip.setTextColor(0xFF3A312B);
            chip.setPadding(28, 16, 28, 16);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMarginEnd(16);
            chip.setLayoutParams(params);

            if (selectedTagIds.contains(tag.tagId)) {
                chip.setBackgroundResource(R.drawable.bg_btn_yellow);
            } else {
                chip.setBackgroundResource(R.drawable.bg_action_chip);
            }

            chip.setOnClickListener(v -> {
                if (selectedTagIds.contains(tag.tagId)) {
                    selectedTagIds.remove(tag.tagId);
                } else {
                    selectedTagIds.add(tag.tagId);
                }
                renderTags();
            });

            layoutTagsContainer.addView(chip);
        }
    }

    private void saveNote() {
        String title = edtTitle.getText().toString().trim();
        String subtitle = edtSubtitle.getText().toString().trim();
        String content = edtContent.getText().toString().trim();

        if (title.isEmpty() && subtitle.isEmpty() && content.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập ghi chú", Toast.LENGTH_SHORT).show();
            return;
        }

        Note note = new Note();
        note.userId = 1;
        note.title = title;
        note.subtitle = subtitle;
        note.content = content;
        note.color = "#FFFFFF";
        note.createdAt = getCurrentTime();
        note.updatedAt = getCurrentTime();
        note.isPinned = 0;
        note.isDeleted = 0;

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());

            long insertedNoteId = db.noteDao().insert(note);
            int noteId = (int) insertedNoteId;

            for (Integer tagId : selectedTagIds) {
                NoteTag noteTag = new NoteTag();
                noteTag.noteId = noteId;
                noteTag.tagId = tagId;
                db.tagDao().insertNoteTag(noteTag);
            }

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