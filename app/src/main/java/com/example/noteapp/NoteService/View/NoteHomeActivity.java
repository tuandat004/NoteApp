package com.example.noteapp.NoteService.View;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.NoteService.Adapter.NoteAdapter;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.SearchService.View.SearchActivity;
import com.example.noteapp.R;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class NoteHomeActivity extends AppCompatActivity {

    private ImageButton btnAdd;
    private FloatingActionButton fabAdd;
    private EditText edtSearch;
    private RecyclerView rvNotes;
    private NoteAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_home);

        btnAdd = findViewById(R.id.btnAdd);
        fabAdd = findViewById(R.id.fabAdd);
        edtSearch = findViewById(R.id.edtSearch);
        rvNotes = findViewById(R.id.rvNotes);

        rvNotes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NoteAdapter(this);
        rvNotes.setAdapter(adapter);

        AppDatabase.getInstance(this)
                .noteDao()
                .getAllNotes()
                .observe(this, new Observer<List<Note>>() {
                    @Override
                    public void onChanged(List<Note> notes) {
                        adapter.setData(notes);
                    }
                });

        btnAdd.setOnClickListener(v -> openCreate());
        fabAdd.setOnClickListener(v -> openCreate());

        edtSearch.setOnClickListener(v -> {
            startActivity(new Intent(this, SearchActivity.class));
        });
    }

    private void openCreate() {
        startActivity(new Intent(this, CreateNoteActivity.class));
    }
}