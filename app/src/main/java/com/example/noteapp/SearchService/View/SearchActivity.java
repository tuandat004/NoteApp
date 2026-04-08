package com.example.noteapp.SearchService.View;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.NoteService.Adapter.NoteAdapter;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.R;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private EditText edtSearchKeyword;
    private Button btnCancel;
    private RecyclerView rvSearchResult;
    private android.widget.TextView txtResultCount;
    
    private NoteAdapter adapter;
    private int sessionUserId;
    private LiveData<List<Note>> allNotesData;
    private final List<Note> fullList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        edtSearchKeyword = findViewById(R.id.edtSearchKeyword);
        btnCancel = findViewById(R.id.btnCancel);
        rvSearchResult = findViewById(R.id.rvSearchResult);
        txtResultCount = findViewById(R.id.txtResultCount);

        SharedPreferences prefs = getSharedPreferences("USER", MODE_PRIVATE);
        sessionUserId = prefs.getInt("user_id", -1);

        adapter = new NoteAdapter(this);
        rvSearchResult.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResult.setAdapter(adapter);

        if (btnCancel != null) btnCancel.setOnClickListener(v -> finish());

        allNotesData = AppDatabase.getInstance(this).noteDao().getAllNotes(sessionUserId);
        allNotesData.observe(this, notes -> {
            if (notes != null) {
                fullList.clear();
                fullList.addAll(notes);
                performSearch(edtSearchKeyword.getText().toString().trim());
            }
        });

        edtSearchKeyword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                performSearch(s.toString().trim());
            }
        });
    }

    private void performSearch(String query) {
        if (query.isEmpty()) {
            adapter.setData(fullList);
            txtResultCount.setText("Tổng cộng có " + fullList.size() + " ghi chú");
            return;
        }

        String normalizedQuery = removeAccents(query.toLowerCase());
        List<Note> filtered = new ArrayList<>();
        
        for (Note note : fullList) {
            String title = note.title != null ? removeAccents(note.title.toLowerCase()) : "";
            String content = note.content != null ? removeAccents(note.content.toLowerCase()) : "";
            
            if (title.contains(normalizedQuery) || content.contains(normalizedQuery)) {
                filtered.add(note);
            }
        }
        
        adapter.setHighlightKeyword(query);
        adapter.setData(filtered);
        
        if (filtered.isEmpty()) {
            txtResultCount.setText("Không có kết quả nào cho \"" + query + "\"");
        } else {
            txtResultCount.setText("Tìm thấy " + filtered.size() + " ghi chú cho \"" + query + "\"");
        }
    }
    
    private String removeAccents(String s) {
        if (s == null) return "";
        String normalized = Normalizer.normalize(s, Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").replace("đ", "d").replace("Đ", "d");
    }
}