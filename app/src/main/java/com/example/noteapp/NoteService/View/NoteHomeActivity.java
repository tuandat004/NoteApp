package com.example.noteapp.NoteService.View;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.NoteService.Adapter.NoteAdapter;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.R;
import com.example.noteapp.SearchService.View.SearchActivity;
import com.example.noteapp.TagService.Entity.Tag;
import com.example.noteapp.TagService.View.ManageTagsActivity;
import com.example.noteapp.UserService.DAO.UserDao;
import com.example.noteapp.UserService.Entity.User;
import com.example.noteapp.UserService.View.UserProfileActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoteHomeActivity extends AppCompatActivity {

    private ImageButton btnAdd;
    private FloatingActionButton fabAdd;
    private EditText edtSearch;
    private RecyclerView rvNotes;
    private NoteAdapter adapter;
    private TextView btnManageTags;
    private LinearLayout layoutTagChips;
    private ImageView imgAvatar;
    private TextView tvUserName;
    private UserDao userDao;
    private int sessionUserId;

    private final List<Tag> allTags = new ArrayList<>();
    private Integer selectedTagId = null;

    // Shared ExecutorService – tái dụng thread pool, tránh tạo mới liên tục
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_home);

        btnAdd         = findViewById(R.id.btnAdd);
        fabAdd         = findViewById(R.id.fabAdd);
        edtSearch      = findViewById(R.id.edtSearch);
        rvNotes        = findViewById(R.id.rvNotes);
        btnManageTags  = findViewById(R.id.btnManageTags);
        layoutTagChips = findViewById(R.id.layoutTagChips);
        imgAvatar      = findViewById(R.id.imgAvatar);
        tvUserName     = findViewById(R.id.tvUserName);

        userDao = AppDatabase.getInstance(this).userDao();
        SharedPreferences prefs = getSharedPreferences("USER", MODE_PRIVATE);
        sessionUserId = prefs.getInt("user_id", -1);

        rvNotes.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NoteAdapter(this);
        rvNotes.setAdapter(adapter); // Chỉ set 1 lần duy nhất

        btnAdd.setOnClickListener(v -> openCreate());
        fabAdd.setOnClickListener(v -> openCreate());
        edtSearch.setOnClickListener(v -> startActivity(new Intent(this, SearchActivity.class)));
        btnManageTags.setOnClickListener(v -> startActivity(new Intent(this, ManageTagsActivity.class)));

        TextView btnTrash = findViewById(R.id.btnTrash);
        btnTrash.setOnClickListener(v -> startActivity(new Intent(this, TrashActivity.class)));

        // Click: mở ghi chú (kiểm tra khóa nếu cần)
        adapter.setOnNoteClickListener(note -> {
            if (note.isLocked == 1) {
                handleLockedNoteOpen(note);
            } else {
                openNoteDetail(note.noteId);
            }
        });

        // Long click: menu tùy chọn (xóa / khóa / hủy khóa)
        adapter.setOnNoteLongClickListener(this::showNoteOptionsDialog);

        imgAvatar.setOnClickListener(v -> startActivity(new Intent(this, UserProfileActivity.class)));
        tvUserName.setOnClickListener(v -> startActivity(new Intent(this, UserProfileActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTags();
        loadNotes();
        loadUserProfile();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown(); // Giải phóng thread pool khi Activity bị hủy
    }

    // ─── Mở ghi chú ──────────────────────────────────────────────────────────

    private void openNoteDetail(int noteId) {
        Intent intent = new Intent(this, NoteDetailActivity.class);
        intent.putExtra("note_id", noteId);
        startActivity(intent);
    }

    /** Ghi chú đang bị khóa → hỏi PIN hoặc vân tay trước khi mở */
    private void handleLockedNoteOpen(Note note) {
        // Xác định loại khóa: nếu pin_hash có giá trị → khóa PIN; không thì biometric
        boolean hasPinHash = note.pinHash != null && !note.pinHash.isEmpty();

        if (hasPinHash) {
            PinLockHelper.showVerifyPinDialog(this, note.pinHash, new PinLockHelper.LockCallback() {
                @Override public void onSuccess() { openNoteDetail(note.noteId); }
                @Override public void onCancel() {}
            });
        } else if (PinLockHelper.isBiometricAvailable(this)) {
            PinLockHelper.showBiometricPrompt(this, new PinLockHelper.LockCallback() {
                @Override public void onSuccess() { openNoteDetail(note.noteId); }
                @Override public void onCancel() {}
            });
        } else {
            // Dự phòng: không có khóa hợp lệ → mở thẳng
            openNoteDetail(note.noteId);
        }
    }

    // ─── Long click menu ─────────────────────────────────────────────────────

    private void showNoteOptionsDialog(Note note) {
        boolean isLocked = note.isLocked == 1;
        String[] options = isLocked
                ? new String[]{"✏️ Xem / Sửa", "🔓 Hủy khóa ghi chú", "🗑 Chuyển vào thùng rác"}
                : new String[]{"✏️ Xem / Sửa", "🔐 Khóa ghi chú",    "🗑 Chuyển vào thùng rác"};

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(note.title != null && !note.title.isEmpty() ? note.title : "Ghi chú")
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        // Xem/sửa (qua kiểm tra khóa)
                        if (isLocked) handleLockedNoteOpen(note);
                        else openNoteDetail(note.noteId);
                    } else if (which == 1) {
                        if (isLocked) handleUnlockNote(note);
                        else handleLockNote(note);
                    } else {
                        confirmDeleteNote(note);
                    }
                })
                .create();

        dialog.setOnShowListener(d -> {
            if (dialog.getListView() != null) {
                dialog.getListView().setDivider(null);
            }
        });
        dialog.show();
    }

    // ─── Khóa ghi chú ────────────────────────────────────────────────────────

    private void handleLockNote(Note note) {
        PinLockHelper.showLockTypeDialog(this, new PinLockHelper.OnLockTypeSelectedListener() {
            @Override
            public void onPinSelected() {
                PinLockHelper.showSetPinDialog(NoteHomeActivity.this, pinHash -> {
                    saveLock(note.noteId, 1, pinHash, "Đã khóa bằng mã PIN");
                });
            }
            @Override
            public void onBiometricSelected() {
                // Khóa vân tay: pin_hash để trống (biometric không lưu hash)
                saveLock(note.noteId, 1, "", "Đã khóa bằng vân tay");
            }
        });
    }

    // ─── Hủy khóa ghi chú ────────────────────────────────────────────────────

    private void handleUnlockNote(Note note) {
        boolean hasPinHash = note.pinHash != null && !note.pinHash.isEmpty();
        if (hasPinHash) {
            PinLockHelper.showVerifyPinDialog(this, note.pinHash, new PinLockHelper.LockCallback() {
                @Override public void onSuccess() { saveLock(note.noteId, 0, null, "Đã hủy khóa ghi chú"); }
                @Override public void onCancel() {}
            });
        } else if (PinLockHelper.isBiometricAvailable(this)) {
            PinLockHelper.showBiometricPrompt(this, new PinLockHelper.LockCallback() {
                @Override public void onSuccess() { saveLock(note.noteId, 0, null, "Đã hủy khóa ghi chú"); }
                @Override public void onCancel() {}
            });
        } else {
            saveLock(note.noteId, 0, null, "Đã hủy khóa ghi chú");
        }
    }

    private void saveLock(int noteId, int isLocked, String pinHash, String message) {
        executor.execute(() -> {
            AppDatabase.getInstance(getApplicationContext())
                    .noteDao().updateLockStatus(noteId, isLocked, pinHash);
            runOnUiThread(() -> {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                loadNotes();
            });
        });
    }

    // ─── Xóa ghi chú ─────────────────────────────────────────────────────────

    private void confirmDeleteNote(Note note) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Xóa Ghi Chú")
                .setMessage("Chuyển ghi chú này vào thùng rác?")
                .setPositiveButton("Xóa", (d, which) -> {
                    executor.execute(() -> {
                        AppDatabase.getInstance(getApplicationContext())
                                .noteDao().softDeleteNote(note.noteId, System.currentTimeMillis());
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Đã chuyển vào thùng rác", Toast.LENGTH_SHORT).show();
                            loadNotes();
                        });
                    });
                })
                .setNegativeButton("Hủy", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(getResources().getColor(R.color.btn_delete_text)));
        dialog.show();
    }

    // ─── Load data ────────────────────────────────────────────────────────────

    private void loadUserProfile() {
        if (sessionUserId == -1) return;
        executor.execute(() -> {
            User user = userDao.getUserById(sessionUserId);
            runOnUiThread(() -> {
                if (user != null) {
                    tvUserName.setText(
                            (user.fullName != null && !user.fullName.isEmpty()) ? user.fullName : user.username);
                    if (user.avatar != null && !user.avatar.isEmpty()) {
                        Glide.with(this).load(user.avatar).into(imgAvatar);
                    }
                }
            });
        });
    }

    private void openCreate() {
        startActivity(new Intent(this, CreateNoteActivity.class));
    }

    private void loadTags() {
        executor.execute(() -> {
            List<Tag> tags = AppDatabase.getInstance(getApplicationContext())
                    .tagDao().getAllTags(sessionUserId);
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
        chipAll.setOnClickListener(v -> { selectedTagId = null; renderTagChips(); loadNotes(); });
        layoutTagChips.addView(chipAll);
        for (Tag tag : allTags) {
            TextView chip = createTagChip(tag.tagName, selectedTagId != null && selectedTagId == tag.tagId);
            chip.setOnClickListener(v -> { selectedTagId = tag.tagId; renderTagChips(); loadNotes(); });
            layoutTagChips.addView(chip);
        }
        TextView chipEdit = createGrayChip("+ Edit");
        chipEdit.setOnClickListener(v -> startActivity(new Intent(this, ManageTagsActivity.class)));
        layoutTagChips.addView(chipEdit);
    }

    private TextView createTagChip(String text, boolean isSelected) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(14f);
        chip.setPadding(24, 14, 24, 14);
        chip.setTextColor(0xFF5A5048);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMarginEnd(12);
        chip.setLayoutParams(p);
        chip.setBackgroundResource(isSelected ? R.drawable.bg_btn_yellow : R.drawable.bg_action_chip);
        return chip;
    }

    private TextView createGrayChip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(14f);
        chip.setPadding(24, 14, 24, 14);
        chip.setTextColor(0xFF5A5048);
        chip.setBackgroundResource(R.drawable.bg_input);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMarginEnd(12);
        chip.setLayoutParams(p);
        return chip;
    }

    private void loadNotes() {
        executor.execute(() -> {
            List<Note> notes;
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            if (selectedTagId == null) {
                notes = db.tagDao().getAllNotesRaw(sessionUserId);
            } else {
                notes = db.tagDao().getNotesByTagId(selectedTagId, sessionUserId);
            }
            runOnUiThread(() -> adapter.setData(notes));
        });
    }
}