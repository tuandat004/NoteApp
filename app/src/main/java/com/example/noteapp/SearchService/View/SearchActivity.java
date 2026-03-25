package com.example.noteapp.SearchService.View;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.noteapp.R;

public class SearchActivity extends AppCompatActivity {

    private EditText edtSearch;
    private Button btnCancel;
    private RecyclerView rvResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        edtSearch = findViewById(R.id.edtSearchKeyword);
        btnCancel = findViewById(R.id.btnCancel);
        rvResult = findViewById(R.id.rvSearchResult);

        rvResult.setLayoutManager(new LinearLayoutManager(this));

        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> finish());
        }
    }
}