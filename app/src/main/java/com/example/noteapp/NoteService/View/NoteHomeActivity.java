package com.example.noteapp.NoteService.View;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.NoteService.Adapter.NoteAdapter;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.R;
import com.example.noteapp.SearchService.View.SearchActivity;
import com.example.noteapp.TagService.Entity.Tag;
import com.example.noteapp.TagService.View.ManageTagsActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class NoteHomeActivity extends AppCompatActivity {

    private ImageButton btnAdd;
    private FloatingActionButton fabAdd;
    private EditText edtSearch;
    private RecyclerView rvNotes;
    private NoteAdapter adapter;
    private TextView btnManageTags;
    private LinearLayout layoutTagChips;

    private final List<Tag> allTags = new ArrayList<>();
    private Integer selectedTagId = null; // null = All

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_home);

        btnAdd = findViewById(R.id.btnAdd);
        fabAdd = findViewById(R.id.fabAdd);
        edtSearch = findViewById(R.id.edtSearch);
        rvNotes = findViewById(R.id.rvNotes);
        btnManageTags = findViewById(R.id.btnManageTags);
        layoutTagChips = findViewById(R.id.layoutTagChips);

        rvNotes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NoteAdapter(this);
        rvNotes.setAdapter(adapter);

        btnAdd.setOnClickListener(v -> openCreate());
        fabAdd.setOnClickListener(v -> openCreate());

        edtSearch.setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class))
        );

        btnManageTags.setOnClickListener(v ->
                startActivity(new Intent(this, ManageTagsActivity.class))
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTags();
        loadNotes();
    }

    private void openCreate() {
        startActivity(new Intent(this, CreateNoteActivity.class));
    }

    private void loadTags() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Tag> tags = AppDatabase.getInstance(getApplicationContext())
                    .tagDao()
                    .getAllTags();

            runOnUiThread(() -> {
                allTags.clear();
                allTags.addAll(tags);
                renderTagChips();
            });
        });
    }

    private void renderTagChips() {
        layoutTagChips.removeAllViews();

        TextView chipAll = createTagChip("All", selectedTagId == null);
        chipAll.setOnClickListener(v -> {
            selectedTagId = null;
            renderTagChips();
            loadNotes();
        });
        layoutTagChips.addView(chipAll);

        for (Tag tag : allTags) {
            TextView chip = createTagChip(tag.tagName, selectedTagId != null && selectedTagId == tag.tagId);
            chip.setOnClickListener(v -> {
                selectedTagId = tag.tagId;
                renderTagChips();
                loadNotes();
            });
            layoutTagChips.addView(chip);
        }

        TextView chipEdit = createGrayChip("+ Edit");
        chipEdit.setOnClickListener(v ->
                startActivity(new Intent(this, ManageTagsActivity.class))
        );
        layoutTagChips.addView(chipEdit);
    }

    private TextView createTagChip(String text, boolean isSelected) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(14f);
        chip.setPadding(24, 14, 24, 14);
        chip.setTextColor(0xFF5A5048);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(12);
        chip.setLayoutParams(params);

        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_btn_yellow);
        } else {
            chip.setBackgroundResource(R.drawable.bg_action_chip);
        }

        return chip;
    }

    private TextView createGrayChip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(14f);
        chip.setPadding(24, 14, 24, 14);
        chip.setTextColor(0xFF5A5048);
        chip.setBackgroundResource(R.drawable.bg_input);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMarginEnd(12);
        chip.setLayoutParams(params);

        return chip;
    }

    private void loadNotes() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Note> notes;
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());

            if (selectedTagId == null) {
                notes = db.tagDao().getAllNotesRaw();
            } else {
                notes = db.tagDao().getNotesByTagId(selectedTagId);
            }

            runOnUiThread(() -> adapter.setData(notes));
        });
    }
}