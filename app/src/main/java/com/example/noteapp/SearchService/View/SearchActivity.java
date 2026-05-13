package com.example.noteapp.SearchService.View;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
    private ImageButton btnBack;
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
        btnBack = findViewById(R.id.btnBack);
        rvSearchResult = findViewById(R.id.rvSearchResult);
        txtResultCount = findViewById(R.id.txtResultCount);

        // Apply status bar top padding
        View rootView = findViewById(R.id.searchRootLayout);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            v.setPadding(v.getPaddingLeft(),
                    top + (int)(12 * getResources().getDisplayMetrics().density),
                    v.getPaddingRight(), v.getPaddingBottom());
            return WindowInsetsCompat.CONSUMED;
        });

        SharedPreferences prefs = getSharedPreferences("USER", MODE_PRIVATE);
        sessionUserId = prefs.getInt("user_id", -1);

        adapter = new NoteAdapter(this);
        rvSearchResult.setLayoutManager(new LinearLayoutManager(this));
        rvSearchResult.setAdapter(adapter);

        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

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
            // Strip HTML tags before searching to avoid false matches on HTML tag names
            String rawContent = note.content != null ? note.content : "";
            String plainContent = rawContent.replaceAll("<[^>]+>", " ")
                    .replace("&nbsp;", " ").replace("&amp;", "&")
                    .replace("&lt;", "<").replace("&gt;", ">")
                    .replaceAll("\\s+", " ").trim();
            String content = removeAccents(plainContent.toLowerCase());
            
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