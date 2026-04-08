package com.example.noteapp.TagService.View;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.R;
import com.example.noteapp.TagService.Entity.Tag;

import java.util.List;
import java.util.concurrent.Executors;
import android.content.SharedPreferences;

public class ManageTagsActivity extends AppCompatActivity {

    private TextView txtBack;
    private TextView txtAddTag;
    private Button btnEdit;
    private LinearLayout layoutTagList;
    private LinearLayout layoutBottomTagChips;

    private boolean isEditMode = false;
    private int sessionUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_tags);

        txtBack = findViewById(R.id.txtBack);
        txtAddTag = findViewById(R.id.txtAddTag);
        btnEdit = findViewById(R.id.btnEdit);
        layoutTagList = findViewById(R.id.layoutTagList);
        layoutBottomTagChips = findViewById(R.id.layoutBottomTagChips);

        SharedPreferences prefs = getSharedPreferences("USER", MODE_PRIVATE);
        sessionUserId = prefs.getInt("user_id", -1);

        txtBack.setOnClickListener(v -> finish());
        txtAddTag.setOnClickListener(v -> showAddTagDialog());

        btnEdit.setOnClickListener(v -> {
            isEditMode = !isEditMode;
            btnEdit.setText(isEditMode ? "Done" : "Edit");
            loadTags();
        });

        loadTags();
    }

    private void showAddTagDialog() {
        EditText input = new EditText(this);
        input.setHint("Nhập tên tag");
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(this)
                .setTitle("Add Tag")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String tagName = input.getText().toString().trim();
                    addTag(tagName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addTag(String tagName) {
        if (tagName.isEmpty()) {
            Toast.makeText(this, "Nhập tên tag", Toast.LENGTH_SHORT).show();
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());

            Tag exist = db.tagDao().getTagByName(tagName, sessionUserId);
            if (exist == null) {
                Tag tag = new Tag();
                tag.tagName = tagName;
                tag.userId = sessionUserId;
                db.tagDao().insertTag(tag);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Đã thêm tag", Toast.LENGTH_SHORT).show();
                    loadTags();
                });
            } else {
                runOnUiThread(() ->
                        Toast.makeText(this, "Tag đã tồn tại", Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void loadTags() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Tag> tags = AppDatabase.getInstance(getApplicationContext())
                    .tagDao()
                    .getAllTags(sessionUserId);

            runOnUiThread(() -> {
                renderTags(tags);
                renderBottomChips(tags);
            });
        });
    }

    private void renderTags(List<Tag> tags) {
        layoutTagList.removeAllViews();

        for (int i = 0; i < tags.size(); i++) {
            Tag tag = tags.get(i);

            TextView tv = new TextView(this);
            tv.setText(tag.tagName);
            tv.setTextSize(18f);
            tv.setTextColor(0xFF3A312B);
            tv.setPadding(30, 24, 30, 24);
            tv.setBackgroundColor(getTagColor(i));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            if (i > 0) {
                params.topMargin = 10;
            }
            tv.setLayoutParams(params);

            if (isEditMode) {
                tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_menu_delete, 0);
                tv.setCompoundDrawablePadding(16);

                tv.setOnClickListener(v -> deleteTag(tag));
            }

            layoutTagList.addView(tv);
        }
    }

    private void renderBottomChips(List<Tag> tags) {
        layoutBottomTagChips.removeAllViews();

        TextView addChip = createBottomChip("+ Tag", 0xFFE9E3DC);
        addChip.setOnClickListener(v -> showAddTagDialog());
        layoutBottomTagChips.addView(addChip);

        for (int i = 0; i < tags.size(); i++) {
            TextView chip = createBottomChip(tags.get(i).tagName, getTagColor(i));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) chip.getLayoutParams();
            params.setMarginStart(10);
            chip.setLayoutParams(params);
            layoutBottomTagChips.addView(chip);
        }
    }

    private TextView createBottomChip(String text, int color) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(14f);
        chip.setTextColor(0xFF5A5048);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(24, 12, 24, 12);
        chip.setBackgroundColor(color);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(36)
        );
        chip.setLayoutParams(params);

        return chip;
    }

    private void deleteTag(Tag tag) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());

            db.tagDao().deleteNoteTagsByTagId(tag.tagId);
            db.tagDao().deleteTagById(tag.tagId);

            runOnUiThread(() -> {
                Toast.makeText(this, "Đã xóa tag", Toast.LENGTH_SHORT).show();
                loadTags();
            });
        });
    }

    private int getTagColor(int position) {
        int[] colors = {
                0xFFE9ECEF, // xám nhạt
                0xFFE8DFC3, // vàng be
                0xFFDCE4D8, // xanh nhạt
                0xFFD8CCE0  // tím nhạt
        };
        return colors[position % colors.length];
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}