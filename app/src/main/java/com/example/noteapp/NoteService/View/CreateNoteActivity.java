package com.example.noteapp.NoteService.View;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.NoteService.Entity.NoteTag;
import com.example.noteapp.R;
import com.example.noteapp.ReminderService.Entity.Reminder;
import com.example.noteapp.ReminderService.Worker.ReminderReceiver;
import com.example.noteapp.TagService.Entity.Tag;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executors;

import jp.wasabeef.richeditor.RichEditor;

public class CreateNoteActivity extends AppCompatActivity {

    private Button btnSave, btnAddTag;
    private TextView txtCancel, tvRemindMe, tvAddImage, tvInsertLink;
    private TextView btnBold, btnItalic, btnUnderline, btnBullets, btnChecklist, btnTextSize, btnAlign, btnTable,
            btnHideToolbar;
    private EditText edtTitle, edtSubtitle;
    private LinearLayout layoutTagsContainer, layoutFormattingToolbar, layoutLinksContainer;
    private View colorYellow, colorGreen, colorCyan, colorBlue, colorPurple, colorPink, colorGray, colorBlack,
            currentColorView;

    // Image preview views
    private RelativeLayout layoutImagePreview;
    private ImageView imgPreview;
    private TextView btnRemoveImage, txtImageName;

    private RichEditor editor;

    private int currentHeading = 0;
    private int currentAlign = 0;

    private final List<Tag> allTags = new ArrayList<>();
    private final Set<Integer> selectedTagIds = new HashSet<>();
    private int sessionUserId;

    private String selectedReminderTime = null;
    private long selectedReminderMillis = 0;

    // Existing note ID for edit mode
    private int existingNoteId = -1;

    // Selected Note Color
    private String selectedNoteColor = "";

    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;

    private final ActivityResultLauncher<String> requestPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(this, "Quyền thông báo bị từ chối, nhắc nhở có thể không tự hoạt động",
                            Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_note);

        btnSave = findViewById(R.id.btnSave);
        txtCancel = findViewById(R.id.txtCancel);
        edtTitle = findViewById(R.id.edtTitle);
        edtSubtitle = findViewById(R.id.edtSubtitle);
        editor = findViewById(R.id.editor);
        btnAddTag = findViewById(R.id.btnAddTag);
        layoutTagsContainer = findViewById(R.id.layoutTagsContainer);
        tvRemindMe = findViewById(R.id.tvRemindMe);
        tvAddImage = findViewById(R.id.tvAddImage);
        tvInsertLink = findViewById(R.id.tvInsertLink);
        layoutFormattingToolbar = findViewById(R.id.layoutFormattingToolbar);
        layoutLinksContainer = findViewById(R.id.layoutLinksContainer);

        // Image preview
        layoutImagePreview = findViewById(R.id.layoutImagePreview);
        imgPreview = findViewById(R.id.imgPreview);
        btnRemoveImage = findViewById(R.id.btnRemoveImage);
        txtImageName = findViewById(R.id.txtImageName);

        // Formatting buttons
        btnBold = findViewById(R.id.btnBold);
        btnItalic = findViewById(R.id.btnItalic);
        btnUnderline = findViewById(R.id.btnUnderline);
        btnBullets = findViewById(R.id.btnBullets);
        btnChecklist = findViewById(R.id.btnChecklist);
        btnTextSize = findViewById(R.id.btnTextSize);
        btnAlign = findViewById(R.id.btnAlign);
        btnTable = findViewById(R.id.btnTable);
        btnHideToolbar = findViewById(R.id.btnHideToolbar);

        colorYellow = findViewById(R.id.colorYellow);
        colorGreen = findViewById(R.id.colorGreen);
        colorCyan = findViewById(R.id.colorCyan);
        colorBlue = findViewById(R.id.colorBlue);
        colorPurple = findViewById(R.id.colorPurple);
        colorPink = findViewById(R.id.colorPink);
        colorGray = findViewById(R.id.colorGray);
        colorBlack = findViewById(R.id.colorBlack);

        SharedPreferences prefs = getSharedPreferences("USER", MODE_PRIVATE);
        sessionUserId = prefs.getInt("user_id", -1);

        setupEditor();
        setupClickListeners();
        setupImagePicker();
        setupEditorFocusListener();
        loadTags();
        askNotificationPermission();

        existingNoteId = getIntent().getIntExtra("note_id", -1);
        if (existingNoteId != -1) {
            TextView txtHeaderTitle = findViewById(R.id.txtHeaderTitle);
            if (txtHeaderTitle != null) {
                txtHeaderTitle.setText("Chỉnh sửa Ghi chú");
            }
            loadExistingNote(existingNoteId);
        }
    }

    private void loadExistingNote(int noteId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            Note note = db.noteDao().getNoteById(noteId);
            List<Tag> tags = db.tagDao().getTagsByNoteId(noteId);
            Reminder rem = db.reminderDao().getReminderByNoteId(noteId);

            if (note != null) {
                runOnUiThread(() -> {
                    edtTitle.setText(note.title);
                    edtSubtitle.setText(note.subtitle);

                    String fullContent = note.content != null ? note.content : "";

                    if (note.color != null && !note.color.isEmpty()) {
                        selectedNoteColor = note.color;
                        try {
                            findViewById(android.R.id.content).setBackgroundColor(Color.parseColor(selectedNoteColor));
                        } catch (Exception ignored) {
                        }
                    }

                    editor.setHtml(fullContent);

                    if (tags != null) {
                        for (Tag t : tags)
                            selectedTagIds.add(t.tagId);
                        renderTags();
                    }
                    if (rem != null && rem.reminderTime != null) {
                        tvRemindMe.setText("⏰ " + rem.reminderTime);
                        tvRemindMe.setTextColor(Color.parseColor("#D3C08D"));
                    }
                });
            }
        });
    }

    // ─── Permissions ─────────────────────────────────────────────────────────
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    // ─── Editor setup ─────────────────────────────────────────────────────────
    private void setupEditor() {
        editor.setEditorFontSize(18);
        editor.setEditorFontColor(Color.parseColor("#2E2A27"));
        editor.setPadding(14, 14, 14, 14);
        editor.setPlaceholder("Nội dung ghi chú... (Nhấn để nhập, toolbar định dạng sẽ hiện)");

        // Bật tính năng bôi đen / chọn chữ
        editor.setOnLongClickListener(null);
        editor.setLongClickable(true);
        editor.postDelayed(() -> {
            editor.evaluateJavascript(
                    "document.body.style.userSelect='auto'; document.body.style.webkitUserSelect='auto';", null);
        }, 500);
    }

    /**
     * Lắng nghe keyboard show/hide để hiện/ẩn formatting toolbar
     * Khi keyboard xuất hiện = user đang focus vào editor → hiện toolbar
     */
    private void setupEditorFocusListener() {
        // Dùng globalLayoutListener để detect keyboard
        View rootView = getWindow().getDecorView().getRootView();
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private boolean wasKeyboardVisible = false;

            @Override
            public void onGlobalLayout() {
                android.graphics.Rect r = new android.graphics.Rect();
                rootView.getWindowVisibleDisplayFrame(r);
                int screenHeight = rootView.getRootView().getHeight();
                int keypadHeight = screenHeight - r.bottom;
                boolean isKeyboardVisible = keypadHeight > screenHeight * 0.15;

                if (isKeyboardVisible != wasKeyboardVisible) {
                    wasKeyboardVisible = isKeyboardVisible;
                    if (isKeyboardVisible) {
                        layoutFormattingToolbar.setVisibility(View.VISIBLE);
                    } else {
                        layoutFormattingToolbar.setVisibility(View.GONE);
                    }
                }
            }
        });

        // Nút ẩn toolbar thủ công
        btnHideToolbar.setOnClickListener(v -> {
            layoutFormattingToolbar.setVisibility(View.GONE);
            // Ẩn keyboard
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View currentFocus = getCurrentFocus();
            if (currentFocus != null)
                imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        });
    }

    // ─── Image picker ─────────────────────────────────────────────────────────
    private void setupImagePicker() {
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                try {
                    // Lấy tên file
                    String fileName = getFileNameFromUri(uri);

                    InputStream is = getContentResolver().openInputStream(uri);
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                    is.close();

                    // Convert to base64
                    ByteArrayOutputStream bao = new ByteArrayOutputStream();
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bao);
                    byte[] ba = bao.toByteArray();
                    String base64 = android.util.Base64.encodeToString(ba, android.util.Base64.NO_WRAP);
                    String dataUrl = "data:image/jpeg;base64," + base64;

                    // Use DOM marker to place image exactly at the cursor
                    String js = "var marker = document.getElementById('cursor_marker_img');" +
                            "if(marker) {" +
                            "  var img = document.createElement('img');" +
                            "  img.src = '" + dataUrl + "';" +
                            "  img.alt = '" + fileName + "';" +
                            "  img.style.maxWidth = '100%';" +
                            "  marker.parentNode.replaceChild(img, marker);" +
                            "  if(typeof RE !== 'undefined' && RE.callback) RE.callback();" +
                            "} else {" +
                            "  document.execCommand('insertImage', false, '" + dataUrl + "');" +
                            "}";
                    editor.evaluateJavascript(js, null);

                    // Xóa marker nếu còn sót lại ở bất kỳ đâu do bấm nhiều lần
                    editor.evaluateJavascript(
                            "var m; while(m = document.getElementById('cursor_marker_img')) { m.parentNode.removeChild(m); }",
                            null);

                    Toast.makeText(this, "Đã chèn ảnh: " + fileName, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(this, "Lỗi khi thêm ảnh!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String getFileNameFromUri(Uri uri) {
        String name = "image.png";
        try {
            android.database.Cursor cursor = getContentResolver()
                    .query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0)
                    name = cursor.getString(idx);
                cursor.close();
            }
        } catch (Exception ignored) {
        }
        return name;
    }

    // ─── Click listeners ──────────────────────────────────────────────────────
    private void setupClickListeners() {
        btnSave.setOnClickListener(v -> saveNote());
        txtCancel.setOnClickListener(v -> finish());
        btnAddTag.setOnClickListener(v -> showTagsDialog());

        // Image remove button (hidden as we use inline images now)
        btnRemoveImage.setVisibility(View.GONE);
        layoutImagePreview.setVisibility(View.GONE);

        // Add Image
        tvAddImage.setOnClickListener(v -> {
            editor.focusEditor();
            editor.evaluateJavascript(
                    "document.execCommand('insertHTML', false, '<span id=\"cursor_marker_img\"></span>');", null);
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build());
        });

        // Insert Link
        tvInsertLink.setOnClickListener(v -> {
            editor.focusEditor();
            editor.evaluateJavascript(
                    "document.execCommand('insertHTML', false, '<span id=\"cursor_marker_link\"></span>');", null);
            showInsertLinkDialog();
        });

        // Note Color Toggle
        View tvNoteColor = findViewById(R.id.tvNoteColor);
        View layoutColorPicker = findViewById(R.id.layoutColorPicker);
        if (tvNoteColor != null && layoutColorPicker != null) {
            tvNoteColor.setOnClickListener(v -> {
                if (layoutColorPicker.getVisibility() == View.VISIBLE) {
                    layoutColorPicker.setVisibility(View.GONE);
                } else {
                    layoutColorPicker.setVisibility(View.VISIBLE);
                }
            });
        }

        // Remind me
        tvRemindMe.setOnClickListener(v -> showDateTimePicker());

        // Formatting Toolbar State Synchronization
        editor.setOnDecorationChangeListener((text, types) -> {
            boolean isBold = types.contains(RichEditor.Type.BOLD);
            boolean isItalic = types.contains(RichEditor.Type.ITALIC);
            boolean isUnderline = types.contains(RichEditor.Type.UNDERLINE);

            btnBold.setTextColor(isBold ? Color.BLACK : 0xFF5F5650);
            btnBold.setBackgroundColor(isBold ? 0xFFE0E0E0 : Color.TRANSPARENT);

            btnItalic.setTextColor(isItalic ? Color.BLACK : 0xFF5F5650);
            btnItalic.setBackgroundColor(isItalic ? 0xFFE0E0E0 : Color.TRANSPARENT);

            btnUnderline.setTextColor(isUnderline ? Color.BLACK : 0xFF5F5650);
            btnUnderline.setBackgroundColor(isUnderline ? 0xFFE0E0E0 : Color.TRANSPARENT);
        });

        // Mỗi nút format chỉ cần gọi thẳng action — editor đã được focus sẵn khi
        // toolbar hiện
        btnBold.setOnClickListener(v -> editor.setBold());
        btnItalic.setOnClickListener(v -> editor.setItalic());
        btnUnderline.setOnClickListener(v -> editor.setUnderline());

        btnBullets.setOnClickListener(v -> {
            editor.setBullets();
            Toast.makeText(this, "Danh sách chấm", Toast.LENGTH_SHORT).show();
        });
        btnChecklist.setOnClickListener(v -> {
            editor.setNumbers();
            Toast.makeText(this, "Danh sách số", Toast.LENGTH_SHORT).show();
        });

        btnTextSize.setOnClickListener(v -> {
            currentHeading = (currentHeading + 1) % 6;
            editor.setHeading(currentHeading + 1);
            Toast.makeText(this, "Cỡ chữ H" + (currentHeading + 1), Toast.LENGTH_SHORT).show();
        });

        btnAlign.setOnClickListener(v -> {
            String[] alignments = { "Căn trái", "Căn giữa", "Căn phải" };
            new AlertDialog.Builder(this)
                    .setTitle("Chọn căn lề")
                    .setItems(alignments, (dialog, which) -> {
                        if (which == 0) {
                            editor.setAlignLeft();
                            Toast.makeText(this, "Đã căn trái", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            editor.setAlignCenter();
                            Toast.makeText(this, "Đã căn giữa", Toast.LENGTH_SHORT).show();
                        } else if (which == 2) {
                            editor.setAlignRight();
                            Toast.makeText(this, "Đã căn phải", Toast.LENGTH_SHORT).show();
                        }
                    })
                    .show();
        });

        btnTable.setOnClickListener(v -> {
            String[] options = { "Tạo bảng mới", "Xóa dòng hiện tại", "Xóa cột hiện tại", "Xóa toàn bộ bảng" };
            new AlertDialog.Builder(this)
                    .setTitle("Quản lý Bảng")
                    .setItems(options, (dialogInterface, index) -> {
                        if (index == 0) {
                            showCreateTableDialog();
                        } else if (index == 1) {
                            editor.evaluateJavascript(
                                    "(function(){" +
                                            "  var sel = window.getSelection();" +
                                            "  if(!sel || sel.rangeCount === 0) return;" +
                                            "  var node = sel.getRangeAt(0).startContainer;" +
                                            "  while(node && node.nodeName !== 'TR') node = node.parentNode;" +
                                            "  if(node && node.nodeName === 'TR') node.parentNode.removeChild(node);" +
                                            "  if(typeof window.RE !== 'undefined' && window.RE.callback) window.RE.callback();"
                                            +
                                            "})()",
                                    val -> runOnUiThread(
                                            () -> Toast.makeText(this, "Đã xóa dòng", Toast.LENGTH_SHORT).show()));
                        } else if (index == 2) {
                            editor.evaluateJavascript(
                                    "(function(){" +
                                            "  var sel = window.getSelection();" +
                                            "  if(!sel || sel.rangeCount === 0) return;" +
                                            "  var node = sel.getRangeAt(0).startContainer;" +
                                            "  while(node && node.nodeName !== 'TD' && node.nodeName !== 'TH') node = node.parentNode;"
                                            +
                                            "  if(node && (node.nodeName === 'TD' || node.nodeName === 'TH')) {" +
                                            "    var cellIndex = node.cellIndex;" +
                                            "    var table = node;" +
                                            "    while(table && table.nodeName !== 'TABLE') table = table.parentNode;" +
                                            "    if(table && table.nodeName === 'TABLE') {" +
                                            "      for(var i=0; i<table.rows.length; i++) {" +
                                            "        if(table.rows[i].cells.length > cellIndex) {" +
                                            "          table.rows[i].deleteCell(cellIndex);" +
                                            "        }" +
                                            "      }" +
                                            "    }" +
                                            "  }" +
                                            "  if(typeof window.RE !== 'undefined' && window.RE.callback) window.RE.callback();"
                                            +
                                            "})()",
                                    val -> runOnUiThread(
                                            () -> Toast.makeText(this, "Đã xóa cột", Toast.LENGTH_SHORT).show()));
                        } else if (index == 3) {
                            editor.evaluateJavascript(
                                    "(function(){" +
                                            "  var sel = window.getSelection();" +
                                            "  var node = sel && sel.rangeCount > 0 ? sel.getRangeAt(0).commonAncestorContainer : null;"
                                            +
                                            "  if (node && node.nodeType === 3) node = node.parentNode;" +
                                            "  while(node && node.tagName !== 'TABLE') node = node.parentNode;" +
                                            "  if(!node || node.tagName !== 'TABLE') {" +
                                            "    var tables = document.querySelectorAll('table');" +
                                            "    if(tables.length > 0) node = tables[tables.length - 1];" +
                                            "  }" +
                                            "  if(node && node.tagName === 'TABLE') node.parentNode.removeChild(node);"
                                            +
                                            "  if(typeof window.RE !== 'undefined' && window.RE.callback) window.RE.callback();"
                                            +
                                            "})()",
                                    val -> runOnUiThread(
                                            () -> Toast.makeText(this, "Đã xóa bảng", Toast.LENGTH_SHORT).show()));
                        }
                    })
                    .show();
        });

        // Color buttons
        View.OnClickListener colorClick = v -> {
            String hex = (String) v.getTag();
            selectedNoteColor = hex;
            try {
                findViewById(android.R.id.content).setBackgroundColor(Color.parseColor(hex));
            } catch (Exception ignored) {
            }
        };

        colorYellow.setTag("#F3D986");
        colorYellow.setOnClickListener(colorClick);
        colorGreen.setTag("#DDE0A8");
        colorGreen.setOnClickListener(colorClick);
        colorCyan.setTag("#B7D6C6");
        colorCyan.setOnClickListener(colorClick);
        colorBlue.setTag("#B5C7DE");
        colorBlue.setOnClickListener(colorClick);
        colorPurple.setTag("#D5C9E3");
        colorPurple.setOnClickListener(colorClick);
        colorPink.setTag("#EDC9CF");
        colorPink.setOnClickListener(colorClick);
        colorGray.setTag("#D9D5D2");
        colorGray.setOnClickListener(colorClick);
        colorBlack.setTag("#2E2A27");
        colorBlack.setOnClickListener(colorClick);
    }

    // ─── Insert Link Dialog ────────────────────────────────────────────────────
    private void showInsertLinkDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chèn đường Link");

        final EditText input = new EditText(this);
        input.setHint("https://...");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, padding / 2);
        builder.setView(input);

        builder.setPositiveButton("Thêm", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                if (!url.startsWith("http"))
                    url = "https://" + url;
                final String finalUrl = url;
                String js = "var marker = document.getElementById('cursor_marker_link');" +
                        "if(marker) {" +
                        "  var a = document.createElement('a');" +
                        "  a.href = '" + finalUrl + "';" +
                        "  a.innerText = '" + finalUrl + "';" +
                        "  marker.parentNode.replaceChild(a, marker);" +
                        "  if(typeof RE !== 'undefined' && RE.callback) RE.callback();" +
                        "} else {" +
                        "  document.execCommand('createLink', false, '" + finalUrl + "');" +
                        "}";
                editor.evaluateJavascript(js, null);
                editor.evaluateJavascript(
                        "var m; while(m = document.getElementById('cursor_marker_link')) { m.parentNode.removeChild(m); }",
                        null);
                Toast.makeText(this, "Đã chèn link", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    // ─── Tags Dialog ──────────────────────────────────────────────────────────
    private void showTagsDialog() {
        if (allTags.isEmpty()) {
            Toast.makeText(this, "Chưa có tag nào!", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] tagNames = new String[allTags.size()];
        boolean[] checkedItems = new boolean[allTags.size()];
        for (int i = 0; i < allTags.size(); i++) {
            tagNames[i] = allTags.get(i).tagName;
            checkedItems[i] = selectedTagIds.contains(allTags.get(i).tagId);
        }

        new AlertDialog.Builder(this)
                .setTitle("Chọn Tags")
                .setMultiChoiceItems(tagNames, checkedItems, (dialog, which, isChecked) -> {
                    if (isChecked)
                        selectedTagIds.add(allTags.get(which).tagId);
                    else
                        selectedTagIds.remove(allTags.get(which).tagId);
                })
                .setPositiveButton("Xong", (dialog, which) -> renderTags())
                .setNeutralButton("Tạo Tag mới", (dialog, which) -> showCreateTagDialog())
                .show();
    }

    private void showCreateTagDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Tạo Tag mới");
        final EditText input = new EditText(this);
        input.setHint("Nhập tên tag");
        builder.setView(input);

        builder.setPositiveButton("Tạo", (dialog, which) -> {
            String tagName = input.getText().toString().trim();
            if (!tagName.isEmpty()) {
                Executors.newSingleThreadExecutor().execute(() -> {
                    Tag newTag = new Tag();
                    newTag.userId = sessionUserId;
                    newTag.tagName = tagName;
                    long newId = AppDatabase.getInstance(getApplicationContext()).tagDao().insertTag(newTag);
                    selectedTagIds.add((int) newId);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Đã tạo tag mới!", Toast.LENGTH_SHORT).show();
                        loadTags();
                    });
                });
            }
        });
        builder.show();
    }

    private void showCreateTableDialog() {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(40, 20, 40, 20);

        TextView tvRowLabel = new TextView(this);
        tvRowLabel.setText("Số dòng:");
        tvRowLabel.setPadding(0, 8, 0, 4);
        dialogLayout.addView(tvRowLabel);

        EditText edtRows = new EditText(this);
        edtRows.setHint("VD: 3");
        edtRows.setInputType(InputType.TYPE_CLASS_NUMBER);
        dialogLayout.addView(edtRows);

        TextView tvColLabel = new TextView(this);
        tvColLabel.setText("Số cột:");
        tvColLabel.setPadding(0, 12, 0, 4);
        dialogLayout.addView(tvColLabel);

        EditText edtCols = new EditText(this);
        edtCols.setHint("VD: 3");
        edtCols.setInputType(InputType.TYPE_CLASS_NUMBER);
        dialogLayout.addView(edtCols);

        new AlertDialog.Builder(this)
                .setTitle("Tạo bảng")
                .setView(dialogLayout)
                .setPositiveButton("Tạo", (dialog, which) -> {
                    int rows = 2, cols = 2;
                    try {
                        String rStr = edtRows.getText().toString().trim();
                        if (!rStr.isEmpty())
                            rows = Integer.parseInt(rStr);
                    } catch (Exception ignored) {
                    }
                    try {
                        String cStr = edtCols.getText().toString().trim();
                        if (!cStr.isEmpty())
                            cols = Integer.parseInt(cStr);
                    } catch (Exception ignored) {
                    }
                    if (rows < 1)
                        rows = 1;
                    if (cols < 1)
                        cols = 1;

                    StringBuilder tableHtml = new StringBuilder();
                    tableHtml.append(
                            "<table style='width:100%;border-collapse:collapse;margin:10px 0;border:1px solid #666666;'>");
                    for (int r = 0; r < rows; r++) {
                        tableHtml.append("<tr>");
                        for (int c = 0; c < cols; c++) {
                            tableHtml.append(
                                    "<td style='border:1px solid #666666;padding:8px;min-width:40px;'>&nbsp;</td>");
                        }
                        tableHtml.append("</tr>");
                    }
                    tableHtml.append("</table><br>");

                    String escaped = tableHtml.toString().replace("'", "\\'");
                    int finalRows = rows, finalCols = cols;
                    editor.evaluateJavascript(
                            "(function(){" +
                                    "  var html = '" + escaped + "';" +
                                    "  document.execCommand('insertHTML', false, html);" +
                                    "})()",
                            value -> runOnUiThread(() -> Toast
                                    .makeText(this, "Đã chèn bảng " + finalRows + "x" + finalCols, Toast.LENGTH_SHORT)
                                    .show()));
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ─── Date/Time Picker ─────────────────────────────────────────────────────
    private void showDateTimePicker() {
        final Calendar c = Calendar.getInstance();
        new DatePickerDialog(this,
                (view, year, month, day) -> new TimePickerDialog(this,
                        (v2, hour, minute) -> {
                            Calendar cal = Calendar.getInstance();
                            cal.set(year, month, day, hour, minute, 0);
                            if (cal.getTimeInMillis() < System.currentTimeMillis()) {
                                Toast.makeText(this, "Vui lòng chọn thời gian trong tương lai!", Toast.LENGTH_SHORT)
                                        .show();
                                return;
                            }
                            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
                            selectedReminderTime = sdf.format(cal.getTime());
                            selectedReminderMillis = cal.getTimeInMillis();
                            tvRemindMe.setText("⏰ " + selectedReminderTime);
                            tvRemindMe.setTextColor(Color.parseColor("#D3C08D"));
                        },
                        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show(),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ─── Load Tags ────────────────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        loadTags();
    }

    private void loadTags() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<Tag> tags = AppDatabase.getInstance(getApplicationContext())
                    .tagDao().getAllTags(sessionUserId);
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
            TextView empty = new TextView(this);
            empty.setText("Chưa có tag nào.");
            empty.setTextColor(0xFF8A7F73);
            layoutTagsContainer.addView(empty);
            return;
        }
        for (Tag tag : allTags) {
            if (!selectedTagIds.contains(tag.tagId))
                continue;
            TextView chip = new TextView(this);
            chip.setText(tag.tagName);
            chip.setTextSize(14f);
            chip.setTextColor(0xFF3A312B);
            chip.setPadding(28, 16, 28, 16);
            chip.setBackgroundResource(R.drawable.bg_btn_yellow);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMarginEnd(16);
            chip.setLayoutParams(p);
            chip.setOnClickListener(v -> {
                selectedTagIds.remove(tag.tagId);
                renderTags();
            });
            layoutTagsContainer.addView(chip);
        }
    }

    // ─── Save Note ────────────────────────────────────────────────────────────
    private void saveNote() {
        String title = edtTitle.getText().toString().trim();
        String subtitle = edtSubtitle.getText().toString().trim();
        String contentHtml = editor.getHtml() != null ? editor.getHtml().trim() : "";

        if (title.isEmpty() && subtitle.isEmpty() && contentHtml.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập ghi chú", Toast.LENGTH_SHORT).show();
            return;
        }

        Note note = new Note();
        note.userId = sessionUserId;
        note.title = title;
        note.subtitle = subtitle;
        note.content = contentHtml;
        note.color = selectedNoteColor.isEmpty() ? "#FFFFFF" : selectedNoteColor;
        note.createdAt = getCurrentTime();
        note.updatedAt = getCurrentTime();
        note.isPinned = 0;
        note.isDeleted = 0;

        final String finalContent = contentHtml;
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            int noteId;

            if (existingNoteId != -1) {
                noteId = existingNoteId;
                note.noteId = noteId;
                db.noteDao().update(note);
                db.tagDao().deleteTagsOfNote(noteId);
            } else {
                long insertedId = db.noteDao().insert(note);
                noteId = (int) insertedId;
            }

            for (Integer tagId : selectedTagIds) {
                NoteTag nt = new NoteTag();
                nt.noteId = noteId;
                nt.tagId = tagId;
                db.tagDao().insertNoteTag(nt);
            }

            if (selectedReminderTime != null && selectedReminderMillis > System.currentTimeMillis()) {
                Reminder reminder = new Reminder();
                reminder.noteId = noteId;
                reminder.userId = sessionUserId;
                reminder.title = title.isEmpty() ? "Ghi chú không tên" : title;
                reminder.reminderTime = selectedReminderTime;
                reminder.isDone = 0;
                reminder.createdAt = getCurrentTime();
                db.reminderDao().insert(reminder);
                scheduleExactAlarm(noteId, reminder.title, selectedReminderMillis);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "Lưu ghi chú thành công!", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    @SuppressLint("ScheduleExactAlarm")
    private void scheduleExactAlarm(int noteId, String title, long timeInMillis) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra(ReminderReceiver.KEY_NOTE_ID, noteId);
        intent.putExtra(ReminderReceiver.KEY_TITLE, "Nhắc nhở: " + title);
        intent.putExtra(ReminderReceiver.KEY_MESSAGE, "Đến giờ kiểm tra ghi chú: " + title);

        PendingIntent pending = PendingIntent.getBroadcast(
                this, noteId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pending);
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pending);
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pending);
            }
        }
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}