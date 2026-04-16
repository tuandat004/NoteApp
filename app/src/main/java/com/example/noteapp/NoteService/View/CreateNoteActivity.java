package com.example.noteapp.NoteService.View;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.AccelerateDecelerateInterpolator;
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
import com.example.noteapp.MediaService.Entity.AudioRecording;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.NoteService.Entity.NoteTag;
import com.example.noteapp.R;
import com.example.noteapp.ReminderService.Entity.Reminder;
import com.example.noteapp.ReminderService.Worker.ReminderReceiver;
import com.example.noteapp.TagService.Entity.Tag;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jp.wasabeef.richeditor.RichEditor;

public class CreateNoteActivity extends AppCompatActivity {

    // ─── Views ────────────────────────────────────────────────────────────────
    private Button btnSave, btnAddTag;
    private TextView txtCancel, tvRemindMe, tvAddImage, tvInsertLink, tvRecordAudio, tvVoiceInput;
    private TextView btnBold, btnItalic, btnUnderline, btnBullets, btnChecklist,
            btnTextSize, btnAlign, btnTable, btnHideToolbar;
    private EditText edtTitle, edtSubtitle;
    private LinearLayout layoutTagsContainer, layoutFormattingToolbar, layoutLinksContainer;
    private View colorDefault, colorYellow, colorGreen, colorCyan, colorBlue, colorPurple,
            colorPink, colorOrange, colorGray, colorBlack;
    private RelativeLayout layoutImagePreview;
    private ImageView imgPreview;
    private TextView btnRemoveImage, txtImageName;
    private RichEditor editor;

    // ─── State ────────────────────────────────────────────────────────────────
    private int currentHeading = 0;
    private final List<Tag> allTags = new ArrayList<>();
    private final Set<Integer> selectedTagIds = new HashSet<>();
    private int sessionUserId;
    private String selectedReminderTime = null;
    private long selectedReminderMillis = 0;
    private int existingNoteId = -1;
    private String selectedNoteColor = "";

    // ─── Ghi âm ──────────────────────────────────────────────────────────────
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private boolean isRecordingPaused = false;
    private String currentRecordingPath = null;
    private long recordingStartTime = 0;
    private long recordingPausedAt = 0;
    private long totalPausedMs = 0;
    private String pendingRecordingPath = null;
    private long pendingRecordingDuration = 0;
    private MediaPlayer inlinePlayer;

    private class AudioBridge {
        @android.webkit.JavascriptInterface
        public void play(String path) {
            runOnUiThread(() -> {
                if (inlinePlayer != null && inlinePlayer.isPlaying()) {
                    inlinePlayer.stop();
                    inlinePlayer.release();
                    inlinePlayer = null;
                } else {
                    try {
                        inlinePlayer = new MediaPlayer();
                        inlinePlayer.setDataSource(path);
                        inlinePlayer.prepare();
                        inlinePlayer.start();
                        inlinePlayer.setOnCompletionListener(mp -> {
                            mp.release();
                            inlinePlayer = null;
                        });
                        Toast.makeText(CreateNoteActivity.this, "Đang phát ghi âm...", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                    }
                }
            });
        }

        @android.webkit.JavascriptInterface
        public void delete(String path, String domId) {
            runOnUiThread(() -> {
                new AlertDialog.Builder(CreateNoteActivity.this)
                        .setTitle("Xóa ghi âm")
                        .setMessage("Bạn có chắc chắn muốn xóa tệp ghi âm này?")
                        .setPositiveButton("Xóa", (dialog, which) -> {
                            try {
                                new File(path).delete();
                                editor.evaluateJavascript(
                                        "var el = document.getElementById('" + domId + "'); if(el) el.remove();", null);
                                Toast.makeText(CreateNoteActivity.this, "Đã xóa ghi âm!", Toast.LENGTH_SHORT).show();
                            } catch (Exception e) {
                            }
                        })
                        .setNegativeButton("Hủy", null)
                        .show();
            });
        }
    }

    // Dialog ghi âm
    private Dialog audioDialog;
    private TextView tvRecordStatus, tvRecordTimer, tvMainRecordIcon, tvPauseIcon;
    private LinearLayout layoutWaveform, btnMainRecord, btnPauseRecord;
    private TextView btnReRecord, btnCancelRecord, btnConfirmRecord;
    private View recordingDot;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private final Handler waveHandler = new Handler(Looper.getMainLooper());
    private Runnable waveRunnable;
    private final List<View> waveBars = new ArrayList<>();

    // ─── Speech-to-Text ───────────────────────────────────────────────────────
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    // ─── Color name map ───────────────────────────────────────────────────────
    private static final Map<String, String> COLOR_NAMES = new HashMap<>();
    static {
        COLOR_NAMES.put("#F7F3EF", "Mặc định");
        COLOR_NAMES.put("#F3D986", "Vàng");
        COLOR_NAMES.put("#DDE0A8", "Xanh lá nhạt");
        COLOR_NAMES.put("#B7D6C6", "Xanh ngọc");
        COLOR_NAMES.put("#B5C7DE", "Xanh dương");
        COLOR_NAMES.put("#D5C9E3", "Tím nhạt");
        COLOR_NAMES.put("#EDC9CF", "Hồng nhạt");
        COLOR_NAMES.put("#F5C9A0", "Cam nhạt");
        COLOR_NAMES.put("#D9D5D2", "Xám");
        COLOR_NAMES.put("#2E2A27", "Tối");
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                Boolean audioGranted = results.getOrDefault(Manifest.permission.RECORD_AUDIO, false);
                if (!Boolean.TRUE.equals(audioGranted))
                    Toast.makeText(this, "Cần cấp quyền micro để sử dụng tính năng này", Toast.LENGTH_SHORT).show();
            });

    private final ActivityResultLauncher<String> requestNotifLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted)
                    Toast.makeText(this, "Quyền thông báo bị từ chối", Toast.LENGTH_SHORT).show();
            });

    // ─── onCreate ─────────────────────────────────────────────────────────────
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
        tvRecordAudio = findViewById(R.id.tvRecordAudio);
        tvVoiceInput = findViewById(R.id.tvVoiceInput);
        layoutFormattingToolbar = findViewById(R.id.layoutFormattingToolbar);
        layoutLinksContainer = findViewById(R.id.layoutLinksContainer);
        layoutImagePreview = findViewById(R.id.layoutImagePreview);
        imgPreview = findViewById(R.id.imgPreview);
        btnRemoveImage = findViewById(R.id.btnRemoveImage);
        txtImageName = findViewById(R.id.txtImageName);

        btnBold = findViewById(R.id.btnBold);
        btnItalic = findViewById(R.id.btnItalic);
        btnUnderline = findViewById(R.id.btnUnderline);
        btnBullets = findViewById(R.id.btnBullets);
        btnChecklist = findViewById(R.id.btnChecklist);
        btnTextSize = findViewById(R.id.btnTextSize);
        btnAlign = findViewById(R.id.btnAlign);
        btnTable = findViewById(R.id.btnTable);
        btnHideToolbar = findViewById(R.id.btnHideToolbar);

        colorDefault = findViewById(R.id.colorDefault);
        colorYellow = findViewById(R.id.colorYellow);
        colorGreen = findViewById(R.id.colorGreen);
        colorCyan = findViewById(R.id.colorCyan);
        colorBlue = findViewById(R.id.colorBlue);
        colorPurple = findViewById(R.id.colorPurple);
        colorPink = findViewById(R.id.colorPink);
        colorOrange = findViewById(R.id.colorOrange);
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
            if (txtHeaderTitle != null)
                txtHeaderTitle.setText("Chỉnh sửa Ghi chú");
            loadExistingNote(existingNoteId);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecordingIfActive();
        cleanupAudioDialog();
        if (inlinePlayer != null) {
            inlinePlayer.release();
            inlinePlayer = null;
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        timerHandler.removeCallbacksAndMessages(null);
        waveHandler.removeCallbacksAndMessages(null);
        executor.shutdown();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadTags();
    }

    // ─── Load existing note ───────────────────────────────────────────────────
    private void loadExistingNote(int noteId) {
        executor.execute(() -> {
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
                        tvRemindMe.setTextColor(Color.parseColor("#C8961A"));
                    }
                });
            }
        });
    }

    // ─── Permissions ─────────────────────────────────────────────────────────
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private boolean checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsLauncher.launch(new String[] { Manifest.permission.RECORD_AUDIO });
            return false;
        }
        return true;
    }

    // ─── Editor setup ────────────────────────────────────────────────────────
    private void setupEditor() {
        editor.setEditorFontSize(17);
        boolean isNight = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        editor.setEditorFontColor(isNight ? Color.parseColor("#EEEEEE") : Color.parseColor("#2E2A27"));
        editor.setPadding(16, 16, 16, 16);
        editor.setPlaceholder("Nội dung ghi chú...");
        editor.setLongClickable(true);
        editor.setEditorBackgroundColor(Color.TRANSPARENT);
        editor.setBackgroundColor(Color.TRANSPARENT);
        try {
            android.webkit.WebView webView = (android.webkit.WebView) editor;
            webView.getSettings().setJavaScriptEnabled(true);
            webView.setBackgroundColor(Color.TRANSPARENT);
            webView.addJavascriptInterface(new AudioBridge(), "AndroidAudio");
        } catch (Exception e) {
        }

        editor.postDelayed(() -> editor.evaluateJavascript(
                "document.body.style.userSelect='auto'; document.body.style.webkitUserSelect='auto';" +
                        "document.addEventListener('selectionchange', function() { var s=window.getSelection(); if(s&&s.rangeCount>0) { window.lastRange = s.getRangeAt(0).cloneRange(); } });",
                null), 500);
    }

    private void setupEditorFocusListener() {
        View rootView = getWindow().getDecorView().getRootView();
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private boolean wasKeyboardVisible = false;

            @Override
            public void onGlobalLayout() {
                android.graphics.Rect r = new android.graphics.Rect();
                rootView.getWindowVisibleDisplayFrame(r);
                int screenH = rootView.getRootView().getHeight();
                int keyPadH = screenH - r.bottom;
                boolean isKeyVisible = keyPadH > screenH * 0.15;
                if (isKeyVisible != wasKeyboardVisible) {
                    wasKeyboardVisible = isKeyVisible;
                    layoutFormattingToolbar.setVisibility(isKeyVisible ? View.VISIBLE : View.GONE);
                }
            }
        });
        btnHideToolbar.setOnClickListener(v -> {
            layoutFormattingToolbar.setVisibility(View.GONE);
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            View focus = getCurrentFocus();
            if (focus != null)
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        });
    }

    // ─── Image picker (auto-resize + cursor insert) ───────────────────────────
    private void setupImagePicker() {
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null)
                insertImageAtCursor(uri);
        });
    }

    private void insertImageAtCursor(Uri uri) {
        executor.execute(() -> {
            try {
                String fileName = getFileNameFromUri(uri);
                InputStream is = getContentResolver().openInputStream(uri);
                Bitmap original = BitmapFactory.decodeStream(is);
                is.close();
                if (original == null) {
                    runOnUiThread(() -> Toast.makeText(this, "Không đọc được ảnh", Toast.LENGTH_SHORT).show());
                    return;
                }

                // Auto-resize: max width = screen width * 0.85
                DisplayMetrics dm = getResources().getDisplayMetrics();
                int maxPx = (int) (dm.widthPixels * 0.85f);
                Bitmap scaled = original;
                if (original.getWidth() > maxPx) {
                    float ratio = (float) maxPx / original.getWidth();
                    scaled = Bitmap.createScaledBitmap(original, maxPx, (int) (original.getHeight() * ratio), true);
                }

                ByteArrayOutputStream bao = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, bao);
                String base64 = android.util.Base64.encodeToString(bao.toByteArray(), android.util.Base64.NO_WRAP);
                String dataUrl = "data:image/jpeg;base64," + base64;

                // JS: insert at cursor marker or current caret position
                String js = "(function(){"
                        + "var img=document.createElement('img');"
                        + "img.src='" + dataUrl + "';"
                        + "img.alt='" + fileName + "';"
                        + "img.style.maxWidth='100%';"
                        + "img.style.height='auto';"
                        + "img.style.display='block';"
                        + "img.style.margin='8px 0';"
                        + "img.style.borderRadius='6px';"
                        + "var marker=document.getElementById('cursor_marker_img');"
                        + "var targetNode = img;"
                        + "if(marker){"
                        + "  var p = marker.parentNode; p.insertBefore(img, marker); p.insertBefore(document.createElement('br'), marker);"
                        + "  var s = document.createElement('span'); s.innerHTML='&#8203;'; p.insertBefore(s, marker);"
                        + "  p.removeChild(marker); targetNode = s;"
                        + "} else {"
                        + "  var sel=window.getSelection();"
                        + "  if(sel&&sel.rangeCount>0&&sel.isCollapsed){"
                        + "    var range=sel.getRangeAt(0); range.deleteContents(); range.insertNode(img);"
                        + "    var br=document.createElement('br'); range.insertNode(br); range.setStartAfter(br);"
                        + "    var s=document.createElement('span'); s.innerHTML='&#8203;'; range.insertNode(s);"
                        + "    targetNode = s;"
                        + "  } else { document.body.appendChild(img); }"
                        + "}"
                        + "var sel=window.getSelection(); if(sel && targetNode.parentNode) { var r=document.createRange(); r.selectNodeContents(targetNode); r.collapse(false); sel.removeAllRanges(); sel.addRange(r); }"
                        + "if(typeof RE!=='undefined'&&RE.callback)RE.callback();"
                        + "})()";

                runOnUiThread(() -> {
                    editor.evaluateJavascript(js, null);
                    Toast.makeText(this, "✅ Đã chèn ảnh: " + fileName, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Lỗi khi thêm ảnh!", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String getFileNameFromUri(Uri uri) {
        String name = "image.jpg";
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
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
        btnSave.setOnClickListener(v -> {
            applyHaptic(v);
            saveNote();
        });
        txtCancel.setOnClickListener(v -> finish());
        btnAddTag.setOnClickListener(v -> {
            applyHaptic(v);
            showTagsDialog();
        });

        layoutImagePreview.setVisibility(View.GONE);
        btnRemoveImage.setVisibility(View.GONE);

        // Add Image → focus editor đặt marker cursor → chọn ảnh
        tvAddImage.setOnClickListener(v -> {
            applyHaptic(v);
            editor.focusEditor();
            String js = "(function(){ if(window.lastRange){ var m=document.createElement('span'); m.id='cursor_marker_img'; window.lastRange.deleteContents(); window.lastRange.insertNode(m); } })();";
            editor.evaluateJavascript(js, null);
            editor.postDelayed(() -> pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()), 200);
        });

        tvInsertLink.setOnClickListener(v -> {
            applyHaptic(v);
            editor.focusEditor();
            String js = "(function(){ if(window.lastRange){ var m=document.createElement('span'); m.id='cursor_marker_link'; window.lastRange.deleteContents(); window.lastRange.insertNode(m); } })();";
            editor.evaluateJavascript(js, null);
            showInsertLinkDialog();
        });

        tvRecordAudio.setOnClickListener(v -> {
            applyHaptic(v);
            if (!checkAndRequestAudioPermission())
                return;
            editor.focusEditor();
            String js = "(function(){ if(window.lastRange){ var m=document.createElement('span'); m.id='cursor_marker_audio'; window.lastRange.deleteContents(); window.lastRange.insertNode(m); } })();";
            editor.evaluateJavascript(js, null);
            showAudioRecorderDialog();
        });

        // Nhập giọng nói
        tvVoiceInput.setOnClickListener(v -> {
            applyHaptic(v);
            toggleVoiceInput();
        });

        // Màu nền
        View tvNoteColor = findViewById(R.id.tvNoteColor);
        View layoutColorPicker = findViewById(R.id.layoutColorPicker);
        if (tvNoteColor != null && layoutColorPicker != null) {
            tvNoteColor.setOnClickListener(v -> {
                applyHaptic(v);
                boolean visible = layoutColorPicker.getVisibility() == View.VISIBLE;
                layoutColorPicker.setVisibility(visible ? View.GONE : View.VISIBLE);
            });
        }

        tvRemindMe.setOnClickListener(v -> {
            applyHaptic(v);
            showDateTimePicker();
        });

        // Formatting
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

        btnBold.setOnClickListener(v -> editor.setBold());
        btnItalic.setOnClickListener(v -> editor.setItalic());
        btnUnderline.setOnClickListener(v -> editor.setUnderline());
        btnBullets.setOnClickListener(v -> editor.setBullets());
        btnChecklist.setOnClickListener(v -> editor.setNumbers());

        btnTextSize.setOnClickListener(v -> {
            currentHeading = (currentHeading + 1) % 6;
            editor.setHeading(currentHeading + 1);
            Toast.makeText(this, "Cỡ chữ H" + (currentHeading + 1), Toast.LENGTH_SHORT).show();
        });

        btnAlign.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Chọn căn lề")
                .setItems(new String[] { "Căn trái", "Căn giữa", "Căn phải" }, (d, w) -> {
                    if (w == 0)
                        editor.setAlignLeft();
                    else if (w == 1)
                        editor.setAlignCenter();
                    else
                        editor.setAlignRight();
                }).show());

        btnTable.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Quản lý Bảng")
                .setItems(new String[] { "Tạo bảng mới", "Xóa dòng", "Xóa cột", "Xóa bảng" }, (d, w) -> {
                    if (w == 0)
                        showCreateTableDialog();
                    else if (w == 1)
                        runTableJs("row");
                    else if (w == 2)
                        runTableJs("col");
                    else
                        runTableJs("table");
                }).show());

        // Color click listener với tên màu
        View.OnClickListener colorClick = v -> {
            String hex = (String) v.getTag();
            if (hex == null)
                return;
            applyHaptic(v);
            selectedNoteColor = hex;
            try {
                findViewById(android.R.id.content).setBackgroundColor(Color.parseColor(hex));
            } catch (Exception ignored) {
            }
            String name = COLOR_NAMES.getOrDefault(hex, hex);
            Toast.makeText(this, "🎨 Màu nền hiện tại: " + name, Toast.LENGTH_SHORT).show();
            // Highlight selected circle
            animateColorSelect(v);
        };

        colorDefault.setTag("#F7F3EF");
        colorDefault.setOnClickListener(colorClick);
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
        colorOrange.setTag("#F5C9A0");
        colorOrange.setOnClickListener(colorClick);
        colorGray.setTag("#D9D5D2");
        colorGray.setOnClickListener(colorClick);
        colorBlack.setTag("#2E2A27");
        colorBlack.setOnClickListener(colorClick);
    }

    /** Touch feedback haptics + scale animation */
    private void applyHaptic(View v) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
    }

    private void animateColorSelect(View v) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.4f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.4f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(scaleX, scaleY);
        set.setDuration(250);
        set.setInterpolator(new AccelerateDecelerateInterpolator());
        set.start();
    }

    // ─── Table JS ─────────────────────────────────────────────────────────────
    private void runTableJs(String action) {
        String js;
        if (action.equals("row")) {
            js = "(function(){var sel=window.getSelection();if(!sel||sel.rangeCount===0)return;"
                    + "var node=sel.getRangeAt(0).startContainer;"
                    + "while(node&&node.nodeName!=='TR')node=node.parentNode;"
                    + "if(node&&node.nodeName==='TR')node.parentNode.removeChild(node);"
                    + "if(typeof window.RE!=='undefined'&&window.RE.callback)window.RE.callback();})()";
        } else if (action.equals("col")) {
            js = "(function(){var sel=window.getSelection();if(!sel||sel.rangeCount===0)return;"
                    + "var node=sel.getRangeAt(0).startContainer;"
                    + "while(node&&node.nodeName!=='TD'&&node.nodeName!=='TH')node=node.parentNode;"
                    + "if(node){var ci=node.cellIndex,t=node;while(t&&t.nodeName!=='TABLE')t=t.parentNode;"
                    + "if(t)for(var i=0;i<t.rows.length;i++)if(t.rows[i].cells.length>ci)t.rows[i].deleteCell(ci);}"
                    + "if(typeof window.RE!=='undefined'&&window.RE.callback)window.RE.callback();})()";
        } else {
            js = "(function(){var sel=window.getSelection();"
                    + "var node=sel&&sel.rangeCount>0?sel.getRangeAt(0).commonAncestorContainer:null;"
                    + "if(node&&node.nodeType===3)node=node.parentNode;"
                    + "while(node&&node.tagName!=='TABLE')node=node.parentNode;"
                    + "if(!node){var t=document.querySelectorAll('table');if(t.length>0)node=t[t.length-1];}"
                    + "if(node&&node.tagName==='TABLE')node.parentNode.removeChild(node);"
                    + "if(typeof window.RE!=='undefined'&&window.RE.callback)window.RE.callback();})()";
        }
        editor.evaluateJavascript(js, null);
    }

    // ─── Audio Recorder Dialog ────────────────────────────────────────────────
    private void showAudioRecorderDialog() {
        audioDialog = new Dialog(this);
        audioDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        audioDialog.setContentView(R.layout.dialog_audio_recorder);
        audioDialog.setCanceledOnTouchOutside(false);
        if (audioDialog.getWindow() != null) {
            audioDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            audioDialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        tvRecordStatus = audioDialog.findViewById(R.id.tvRecordStatus);
        tvRecordTimer = audioDialog.findViewById(R.id.tvRecordTimer);
        tvMainRecordIcon = audioDialog.findViewById(R.id.tvMainRecordIcon);
        tvPauseIcon = audioDialog.findViewById(R.id.tvPauseIcon);
        layoutWaveform = audioDialog.findViewById(R.id.layoutWaveform);
        btnMainRecord = audioDialog.findViewById(R.id.btnMainRecord);
        btnPauseRecord = audioDialog.findViewById(R.id.btnPauseRecord);
        btnReRecord = audioDialog.findViewById(R.id.btnReRecord);
        btnCancelRecord = audioDialog.findViewById(R.id.btnCancelRecord);
        btnConfirmRecord = audioDialog.findViewById(R.id.btnConfirmRecord);
        recordingDot = audioDialog.findViewById(R.id.recordingDot);

        buildWaveformBars();
        setupDialogButtons();
        audioDialog.show();
    }

    private void buildWaveformBars() {
        waveBars.clear();
        layoutWaveform.removeAllViews();
        int barCount = 28;
        int dp4 = dp(4);
        int dp20 = dp(20);
        for (int i = 0; i < barCount; i++) {
            View bar = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(5), dp20);
            lp.setMargins(dp(3), 0, dp(3), 0);
            bar.setLayoutParams(lp);
            bar.setBackgroundResource(R.drawable.bg_waveform_bar);
            bar.setAlpha(0.3f);
            layoutWaveform.addView(bar);
            waveBars.add(bar);
        }
    }

    private void setupDialogButtons() {
        // Main Record button
        btnMainRecord.setOnClickListener(v -> {
            applyHaptic(v);
            if (!isRecording) {
                startDialogRecording();
            } else {
                stopDialogRecording();
            }
        });

        // Pause
        btnPauseRecord.setOnClickListener(v -> {
            applyHaptic(v);
            if (!isRecording)
                return;
            if (!isRecordingPaused) {
                pauseRecording();
            } else {
                resumeRecording();
            }
        });

        // Re-record
        btnReRecord.setOnClickListener(v -> {
            applyHaptic(v);
            stopRecordingIfActive();
            deleteCurrentRecording();
            resetDialogUI();
        });

        // Cancel
        btnCancelRecord.setOnClickListener(v -> {
            stopRecordingIfActive();
            deleteCurrentRecording();
            cleanupAudioDialog();
        });

        // Confirm
        btnConfirmRecord.setOnClickListener(v -> {
            applyHaptic(v);
            if (isRecording)
                stopDialogRecording();
            if (currentRecordingPath == null) {
                Toast.makeText(this, "Chưa có file ghi âm", Toast.LENGTH_SHORT).show();
                return;
            }
            long dur = recordingStartTime > 0
                    ? (System.currentTimeMillis() - recordingStartTime) - totalPausedMs
                    : 0;
            if (dur < 1000) {
                Toast.makeText(this, "Ghi âm quá ngắn", Toast.LENGTH_SHORT).show();
                return;
            }
            finalizeRecording(currentRecordingPath, dur);
            cleanupAudioDialog();
        });
    }

    @SuppressWarnings("deprecation")
    private void startDialogRecording() {
        try {
            File dir = getFilesDir();
            String fileName = "rec_" + System.currentTimeMillis() + ".m4a";
            currentRecordingPath = new File(dir, fileName).getAbsolutePath();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                mediaRecorder = new MediaRecorder(this);
            else
                mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(currentRecordingPath);
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            isRecordingPaused = false;
            recordingStartTime = System.currentTimeMillis();
            totalPausedMs = 0;
            tvMainRecordIcon.setText("⏹");
            tvRecordStatus.setText("Đang ghi âm...");
            tvRecordStatus.setTextColor(Color.parseColor("#FF4444"));
            startTimerAndWave();
            startDotBlink();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi ghi âm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            isRecording = false;
            mediaRecorder = null;
        }
    }

    private void stopDialogRecording() {
        if (mediaRecorder == null)
            return;
        try {
            mediaRecorder.stop();
        } catch (Exception ignored) {
        }
        mediaRecorder.release();
        mediaRecorder = null;
        isRecording = false;
        isRecordingPaused = false;
        timerHandler.removeCallbacksAndMessages(null);
        waveHandler.removeCallbacksAndMessages(null);
        tvMainRecordIcon.setText("⏺");
        tvRecordStatus.setText("Đã dừng – Nhấn ✓ Lưu để hoàn tất");
        tvRecordStatus.setTextColor(Color.parseColor("#4CAF50"));
        recordingDot.setAlpha(0f);
        flattenWave();
    }

    private void pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mediaRecorder != null) {
            try {
                mediaRecorder.pause();
            } catch (Exception ignored) {
            }
        }
        isRecordingPaused = true;
        recordingPausedAt = System.currentTimeMillis();
        timerHandler.removeCallbacksAndMessages(null);
        waveHandler.removeCallbacksAndMessages(null);
        tvPauseIcon.setText("▶");
        tvRecordStatus.setText("Tạm dừng");
        tvRecordStatus.setTextColor(Color.parseColor("#FFA726"));
        flattenWave();
    }

    private void resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mediaRecorder != null) {
            try {
                mediaRecorder.resume();
            } catch (Exception ignored) {
            }
        }
        totalPausedMs += System.currentTimeMillis() - recordingPausedAt;
        isRecordingPaused = false;
        tvPauseIcon.setText("⏸");
        tvRecordStatus.setText("Đang ghi âm...");
        tvRecordStatus.setTextColor(Color.parseColor("#FF4444"));
        startTimerAndWave();
    }

    private void startTimerAndWave() {
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRecording || isRecordingPaused)
                    return;
                long elapsed = (System.currentTimeMillis() - recordingStartTime - totalPausedMs) / 1000;
                tvRecordTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", elapsed / 60, elapsed % 60));
                timerHandler.postDelayed(this, 500);
            }
        };
        timerHandler.post(timerRunnable);

        waveRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isRecording || isRecordingPaused)
                    return;
                animateWave();
                waveHandler.postDelayed(this, 120);
            }
        };
        waveHandler.post(waveRunnable);
    }

    private void animateWave() {
        int barCount = waveBars.size();
        if (barCount == 0)
            return;
        float amp = 0.5f;
        try {
            if (mediaRecorder != null)
                amp = Math.min(1f, mediaRecorder.getMaxAmplitude() / 32768f * 2f);
        } catch (Exception ignored) {
        }
        final float finalAmp = Math.max(0.15f, amp);
        for (int i = 0; i < barCount; i++) {
            View bar = waveBars.get(i);
            float rand = 0.2f + (float) Math.random() * finalAmp;
            int targetH = dp(8 + (int) (44 * rand));
            bar.setAlpha(0.4f + rand * 0.6f);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bar.getLayoutParams();
            lp.height = targetH;
            bar.setLayoutParams(lp);
        }
    }

    private void flattenWave() {
        for (View bar : waveBars) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) bar.getLayoutParams();
            lp.height = dp(8);
            bar.setLayoutParams(lp);
            bar.setAlpha(0.2f);
        }
    }

    private void startDotBlink() {
        ObjectAnimator blink = ObjectAnimator.ofFloat(recordingDot, "alpha", 1f, 0f, 1f);
        blink.setDuration(900);
        blink.setRepeatCount(ObjectAnimator.INFINITE);
        blink.start();
        recordingDot.setTag(blink);
    }

    private void resetDialogUI() {
        tvRecordTimer.setText("00:00");
        tvRecordStatus.setText("Sẵn sàng ghi âm");
        tvRecordStatus.setTextColor(Color.parseColor("#BBBBBB"));
        tvMainRecordIcon.setText("⏺");
        tvPauseIcon.setText("⏸");
        recordingDot.setAlpha(0f);
        currentRecordingPath = null;
        flattenWave();
    }

    private void deleteCurrentRecording() {
        if (currentRecordingPath != null) {
            new File(currentRecordingPath).delete();
            currentRecordingPath = null;
        }
    }

    private void cleanupAudioDialog() {
        timerHandler.removeCallbacksAndMessages(null);
        waveHandler.removeCallbacksAndMessages(null);
        if (audioDialog != null && audioDialog.isShowing())
            audioDialog.dismiss();
        audioDialog = null;
        if (editor != null) {
            editor.evaluateJavascript("var m=document.getElementById('cursor_marker_audio');if(m)m.remove();", null);
        }
    }

    private void finalizeRecording(String path, long durationMs) {
        String fileName = new File(path).getName();
        long secs = durationMs / 1000;
        String durStr = String.format(Locale.getDefault(), "%d:%02d", secs / 60, secs % 60);

        String domId = "audio_" + System.currentTimeMillis();
        String htmlBlock = String.format(Locale.getDefault(),
                "<div contenteditable=\"false\" class=\"app-audio-record\" id=\"%s\" data-path=\"%s\" data-duration=\"%d\" "
                        +
                        "style=\"display:flex; align-items:center; background:#EFEEFC; border-radius:8px; padding:12px; margin:12px 0; border:1px solid #D5D1EB; user-select:none;\">"
                        +
                        "<div onclick=\"event.stopPropagation(); event.preventDefault(); window.AndroidAudio.play('%s')\" style=\"display:flex; align-items:center; flex:1; cursor:pointer;\">"
                        +
                        "<span style=\"font-size:24px; margin-right:12px;\">▶️</span>" +
                        "<div style=\"display:flex; flex-direction:column;\">" +
                        "<b style=\"color:#3A312B; font-size:14px; margin-bottom:2px;\">%s</b>" +
                        "<span style=\"color:#5F5650; font-size:12px;\">Thời lượng: %s</span>" +
                        "</div></div>" +
                        "<div onclick=\"event.stopPropagation(); event.preventDefault(); window.AndroidAudio.delete('%s', '%s')\" style=\"padding:8px 12px; font-size:18px; cursor:pointer; color:red;\">❌</div>"
                        +
                        "</div><br>",
                domId, path, durationMs, path, fileName, durStr, path, domId);

        String b64 = android.util.Base64.encodeToString(htmlBlock.getBytes(), android.util.Base64.NO_WRAP);
        String js = "(function(){" +
                "var html=decodeURIComponent(escape(window.atob('" + b64 + "')));" +
                "var temp=document.createElement('div'); temp.innerHTML=html;" +
                "var node=temp.firstChild;" +
                "var marker=document.getElementById('cursor_marker_audio');" +
                "var targetNode = node;" +
                "if(marker){" +
                "  var p = marker.parentNode; p.insertBefore(node, marker); p.insertBefore(document.createElement('br'), marker);"
                +
                "  var s = document.createElement('span'); s.innerHTML='&#8203;'; p.insertBefore(s, marker);" +
                "  p.removeChild(marker); targetNode = s;" +
                "}else{" +
                "  var sel=window.getSelection();" +
                "  if(sel&&sel.rangeCount>0&&sel.isCollapsed){" +
                "    var r=sel.getRangeAt(0); r.deleteContents(); r.insertNode(node);" +
                "    var br=document.createElement('br'); r.insertNode(br); r.setStartAfter(br);" +
                "    var s=document.createElement('span'); s.innerHTML='&#8203;'; r.insertNode(s);" +
                "    targetNode = s;" +
                "  }else{ document.body.appendChild(node); }" +
                "}" +
                "var sel=window.getSelection(); if(sel && targetNode.parentNode) { var r=document.createRange(); r.selectNodeContents(targetNode); r.collapse(false); sel.removeAllRanges(); sel.addRange(r); }"
                +
                "if(typeof RE!=='undefined'&&RE.callback)RE.callback();" +
                "})()";
        editor.evaluateJavascript(js, null);

        // Vẫn lưu vào DB để quản lý (phòng trường hợp mất)
        if (existingNoteId != -1) {
            saveRecordingToDb(existingNoteId, path, durationMs);
        } else {
            pendingRecordingPath = path;
            pendingRecordingDuration = durationMs;
        }

        Toast.makeText(this, "✅ Đã chèn ghi âm vào nội dung", Toast.LENGTH_SHORT).show();
    }

    private void stopRecordingIfActive() {
        if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (Exception ignored) {
            }
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
        }
    }

    private void saveRecordingToDb(int noteId, String filePath, long durationMs) {
        executor.execute(() -> {
            AudioRecording rec = new AudioRecording();
            rec.noteId = noteId;
            rec.filePath = filePath;
            rec.durationMs = durationMs;
            rec.createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            AppDatabase.getInstance(getApplicationContext()).audioRecordingDao().insert(rec);
        });
    }

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }

    // ─── Voice Input (Speech-to-Text) ─────────────────────────────────────────
    private void toggleVoiceInput() {
        if (!checkAndRequestAudioPermission())
            return;
        if (isListening)
            stopVoiceInput();
        else
            startVoiceInput();
    }

    private String lastVoicePartial = "";

    private void startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Thiết bị không hỗ trợ nhận dạng giọng nói", Toast.LENGTH_SHORT).show();
            return;
        }
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        lastVoicePartial = "";
        // ĐÃ SỬA: KHÔNG gọi editor.focusEditor() ở đây vì nó giữ focus WebView
        // và gây xung đột với IME / microphone access

        // Tạo dialog overlay "Đang nghe"
        android.app.Dialog listenDialog = new android.app.Dialog(this);
        listenDialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        android.widget.LinearLayout ll = new android.widget.LinearLayout(this);
        ll.setOrientation(android.widget.LinearLayout.VERTICAL);
        ll.setPadding(dp(24), dp(24), dp(24), dp(20));
        ll.setGravity(android.view.Gravity.CENTER);
        ll.setBackgroundResource(R.drawable.bg_manage_tag_box);

        android.widget.TextView tvIcon = new android.widget.TextView(this);
        tvIcon.setText("🎤");
        tvIcon.setTextSize(48f);
        tvIcon.setGravity(android.view.Gravity.CENTER);
        ll.addView(tvIcon);

        android.widget.TextView tvListenStatus = new android.widget.TextView(this);
        tvListenStatus.setText("Đang nghe...");
        tvListenStatus.setTextSize(16f);
        tvListenStatus.setGravity(android.view.Gravity.CENTER);
        tvListenStatus.setTextColor(0xFF7C3AED);
        android.widget.LinearLayout.LayoutParams tvLP = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        tvLP.setMargins(0, dp(12), 0, dp(16));
        tvListenStatus.setLayoutParams(tvLP);
        ll.addView(tvListenStatus);

        android.widget.Button btnCancelListen = new android.widget.Button(this);
        btnCancelListen.setText("Hủy");
        btnCancelListen.setAllCaps(false);
        btnCancelListen.setOnClickListener(v -> stopVoiceInput());
        ll.addView(btnCancelListen);
        listenDialog.setContentView(ll);
        if (listenDialog.getWindow() != null)
            listenDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        listenDialog.show();

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle p) {
                isListening = true;
                tvVoiceInput.setText("🔴 Đang nghe...");
                tvVoiceInput.setTextColor(Color.parseColor("#D32F2F"));
                tvListenStatus.setText("Hãy nói...");
            }

            @Override
            public void onResults(Bundle results) {
                if (listenDialog.isShowing()) listenDialog.dismiss();
                List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String ext = matches.get(0).trim();
                    if (!ext.isEmpty()) {
                        ext = ext.substring(0, 1).toUpperCase() + ext.substring(1);
                    }
                    String escaped = ext.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
                    editor.evaluateJavascript(
                            "document.execCommand('insertText', false, '" + escaped + " ');", null);
                    Toast.makeText(CreateNoteActivity.this, "📝 " + ext, Toast.LENGTH_SHORT).show();
                }
                resetVoiceButton();
            }

            @Override
            public void onError(int error) {
                if (listenDialog.isShowing()) listenDialog.dismiss();
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    // Cho user thử lại thay vì chỉ hiện toast
                    new androidx.appcompat.app.AlertDialog.Builder(CreateNoteActivity.this)
                            .setTitle("Không nhận diện được")
                            .setMessage("Không nghe thấy gì. Bạn muốn thử lại?")
                            .setPositiveButton("Thử lại", (d, w) -> startVoiceInput())
                            .setNegativeButton("Hủy", null)
                            .show();
                } else {
                    Toast.makeText(CreateNoteActivity.this,
                            "Lỗi nhận giọng: " + getVoiceErrorMsg(error), Toast.LENGTH_SHORT).show();
                }
                resetVoiceButton();
            }

            @Override public void onEndOfSpeech() {
                tvListenStatus.setText("⏳ Đang xử lý...");
                tvVoiceInput.setText("⏳ Xử lý...");
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onPartialResults(Bundle b) {
                List<String> partial = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (partial != null && !partial.isEmpty()) {
                    String text = partial.get(0);
                    tvListenStatus.setText("🔴 " + text);
                    tvVoiceInput.setText("🔴 " + text);
                }
            }
            @Override public void onEvent(int i, Bundle b) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        // Thử vi-VN trước, fallback theo locale hệ thống nếu fail
        String langTag = "vi-VN";
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag);
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
        speechRecognizer.startListening(intent);
    }


    private void stopVoiceInput() {
        if (speechRecognizer != null)
            speechRecognizer.stopListening();
        resetVoiceButton();
    }

    private void resetVoiceButton() {
        isListening = false;
        tvVoiceInput.setText("🎤 Giọng nói");
        tvVoiceInput.setTextColor(Color.parseColor("#5F5650"));
    }

    private String getVoiceErrorMsg(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:
                return "Lỗi audio";
            case SpeechRecognizer.ERROR_CLIENT:
                return "Lỗi client";
            case SpeechRecognizer.ERROR_NETWORK:
                return "Lỗi mạng";
            case SpeechRecognizer.ERROR_NO_MATCH:
                return "Không nhận diện được";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                return "Hết thời gian";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                return "Đang bận";
            default:
                return "Lỗi " + error;
        }
    }

    // ─── Insert Link ──────────────────────────────────────────────────────────
    private void showInsertLinkDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chèn đường Link");
        final EditText input = new EditText(this);
        input.setHint("https://...");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        int p = dp(16);
        input.setPadding(p, p / 2, p, p / 2);
        builder.setView(input);
        builder.setPositiveButton("Thêm", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                if (!url.startsWith("http"))
                    url = "https://" + url;
                final String finalUrl = url;
                String js = "var marker=document.getElementById('cursor_marker_link');"
                        + "var targetNode;"
                        + "if(marker){"
                        + "  var a=document.createElement('a');"
                        + "  a.href='" + finalUrl + "';"
                        + "  a.innerText='" + finalUrl + "';"
                        + "  marker.parentNode.replaceChild(a,marker);"
                        + "  var s = document.createElement('span'); s.innerHTML='&#8203;'; a.parentNode.insertBefore(s, a.nextSibling);"
                        + "  targetNode = s;"
                        + "} else {"
                        + "  document.execCommand('createLink',false,'" + finalUrl + "');"
                        + "  var sel = window.getSelection();"
                        + "  if(sel && sel.rangeCount>0) { "
                        + "     var r = sel.getRangeAt(0); var s = document.createElement('span'); s.innerHTML='&#8203;'; r.collapse(false); r.insertNode(s); targetNode = s;"
                        + "  }"
                        + "}"
                        + "if (targetNode) {"
                        + "  var sel=window.getSelection(); if(sel && targetNode.parentNode) { var r=document.createRange(); r.selectNodeContents(targetNode); r.collapse(false); sel.removeAllRanges(); sel.addRange(r); }"
                        + "}"
                        + "if(typeof RE!=='undefined'&&RE.callback)RE.callback();";
                editor.evaluateJavascript(js, null);
            }
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    // ─── Tags ─────────────────────────────────────────────────────────────────
    private void showTagsDialog() {
        // Build dialog with existing tags + inline create
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("🏷 Chọn / Tạo Tag");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(8));

        // Input để tạo tag mới
        LinearLayout createRow = new LinearLayout(this);
        createRow.setOrientation(LinearLayout.HORIZONTAL);
        createRow.setPadding(0, 0, 0, dp(12));
        createRow.setGravity(Gravity.CENTER_VERTICAL);

        final EditText edtNewTag = new EditText(this);
        edtNewTag.setHint("Tạo tag mới...");
        edtNewTag.setTextSize(14f);
        LinearLayout.LayoutParams etLP = new LinearLayout.LayoutParams(0, dp(44), 1f);
        edtNewTag.setLayoutParams(etLP);
        edtNewTag.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_input));
        edtNewTag.setPadding(dp(12), 0, dp(12), 0);

        TextView btnCreate = new TextView(this);
        btnCreate.setText("＋ Tạo");
        btnCreate.setTextColor(Color.parseColor("#3A312B"));
        btnCreate.setTextSize(13f);
        btnCreate.setPadding(dp(14), dp(10), dp(14), dp(10));
        btnCreate.setBackground(ContextCompat.getDrawable(this, R.drawable.bg_btn_yellow));
        LinearLayout.LayoutParams btnLP = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(44));
        btnLP.setMarginStart(dp(8));
        btnCreate.setLayoutParams(btnLP);

        createRow.addView(edtNewTag);
        createRow.addView(btnCreate);
        root.addView(createRow);

        // Separator
        View sep = new View(this);
        sep.setBackgroundColor(Color.parseColor("#E0D8D0"));
        root.addView(sep, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));

        b.setView(root);

        // Danh sách tag checkboxes
        if (!allTags.isEmpty()) {
            String[] names = new String[allTags.size()];
            boolean[] checked = new boolean[allTags.size()];
            for (int i = 0; i < allTags.size(); i++) {
                names[i] = allTags.get(i).tagName;
                checked[i] = selectedTagIds.contains(allTags.get(i).tagId);
            }
            b.setMultiChoiceItems(names, checked, (d, w, c) -> {
                if (c)
                    selectedTagIds.add(allTags.get(w).tagId);
                else
                    selectedTagIds.remove(allTags.get(w).tagId);
            });
        } else {
            TextView empty = new TextView(this);
            empty.setText("Chưa có tag nào. Hãy tạo tag đầu tiên!");
            empty.setTextColor(Color.parseColor("#A09890"));
            empty.setPadding(0, dp(12), 0, dp(4));
            root.addView(empty);
        }

        b.setPositiveButton("✓ Xong", (d, w) -> renderTags());
        b.setNegativeButton("Hủy", null);

        AlertDialog dialog = b.create();
        dialog.show();

        // Xử lý tạo tag mới ngay trong dialog
        btnCreate.setOnClickListener(v -> {
            String tagName = edtNewTag.getText().toString().trim();
            if (tagName.isEmpty()) {
                edtNewTag.setError("Nhập tên tag");
                return;
            }
            executor.execute(() -> {
                Tag t = new Tag();
                t.userId = sessionUserId;
                t.tagName = tagName;
                long id = AppDatabase.getInstance(getApplicationContext()).tagDao().insertTag(t);
                runOnUiThread(() -> {
                    edtNewTag.setText("");
                    selectedTagIds.add((int) id);
                    Toast.makeText(this, "✅ Đã tạo tag: " + tagName, Toast.LENGTH_SHORT).show();
                    loadTags(); // refresh list
                    dialog.dismiss();
                    showTagsDialog(); // reopen với list mới
                });
            });
        });
    }

    private void showCreateTableDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24), dp(16), dp(24), dp(16));
        TextView tvR = new TextView(this);
        tvR.setText("Số dòng:");
        layout.addView(tvR);
        EditText edtR = new EditText(this);
        edtR.setHint("3");
        edtR.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(edtR);
        TextView tvC = new TextView(this);
        tvC.setText("Số cột:");
        layout.addView(tvC);
        EditText edtC = new EditText(this);
        edtC.setHint("3");
        edtC.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(edtC);
        new AlertDialog.Builder(this).setTitle("Tạo bảng").setView(layout)
                .setPositiveButton("Tạo", (d, w) -> {
                    int rows = 2, cols = 2;
                    try {
                        rows = Integer.parseInt(edtR.getText().toString().trim());
                    } catch (Exception ignored) {
                    }
                    try {
                        cols = Integer.parseInt(edtC.getText().toString().trim());
                    } catch (Exception ignored) {
                    }
                    if (rows < 1)
                        rows = 1;
                    if (cols < 1)
                        cols = 1;
                    StringBuilder html = new StringBuilder(
                            "<table style='width:100%;border-collapse:collapse;margin:10px 0;border:1px solid #666666;'>");
                    for (int r = 0; r < rows; r++) {
                        html.append("<tr>");
                        for (int c = 0; c < cols; c++)
                            html.append("<td style='border:1px solid #666666;padding:8px;min-width:40px;'>&nbsp;</td>");
                        html.append("</tr>");
                    }
                    html.append("</table><br>");
                    String escaped = html.toString().replace("'", "\\'");
                    int fr = rows, fc = cols;
                    editor.evaluateJavascript("document.execCommand('insertHTML',false,'" + escaped + "');",
                            v2 -> runOnUiThread(() -> Toast
                                    .makeText(this, "Đã chèn bảng " + fr + "×" + fc, Toast.LENGTH_SHORT).show()));
                })
                .setNegativeButton("Hủy", null).show();
    }

    // ─── Date/Time Picker ─────────────────────────────────────────────────────
    private void showDateTimePicker() {
        final Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, (view, y, m, d) -> new TimePickerDialog(this, (v2, h, min) -> {
            Calendar cal = Calendar.getInstance();
            cal.set(y, m, d, h, min, 0);
            if (cal.getTimeInMillis() < System.currentTimeMillis()) {
                Toast.makeText(this, "Chọn thời gian trong tương lai!", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedReminderTime = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(cal.getTime());
            selectedReminderMillis = cal.getTimeInMillis();
            tvRemindMe.setText("⏰ " + selectedReminderTime);
            tvRemindMe.setTextColor(Color.parseColor("#C8961A"));
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show(),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ─── Tags rendering ───────────────────────────────────────────────────────
    private void loadTags() {
        executor.execute(() -> {
            List<Tag> tags = AppDatabase.getInstance(getApplicationContext()).tagDao().getAllTags(sessionUserId);
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
            empty.setText("Chưa có tag");
            empty.setTextColor(0xFF8A7F73);
            empty.setTextSize(13f);
            layoutTagsContainer.addView(empty);
            return;
        }
        for (Tag tag : allTags) {
            if (!selectedTagIds.contains(tag.tagId))
                continue;
            TextView chip = new TextView(this);
            chip.setText("✕ " + tag.tagName);
            chip.setTextSize(13f);
            chip.setTextColor(0xFF3A312B);
            chip.setPadding(dp(14), dp(10), dp(14), dp(10));
            chip.setBackgroundResource(R.drawable.bg_btn_yellow);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMarginEnd(dp(8));
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

        if (isRecording)
            stopRecordingIfActive();

        Note note = new Note();
        note.userId = sessionUserId;
        note.title = title;
        note.subtitle = subtitle;
        note.content = contentHtml;
        note.color = selectedNoteColor.isEmpty() ? "#F7F3EF" : selectedNoteColor;
        note.createdAt = getCurrentTime();
        note.updatedAt = getCurrentTime();
        note.isPinned = 0;
        note.isDeleted = 0;

        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            int noteId;
            if (existingNoteId != -1) {
                noteId = existingNoteId;
                note.noteId = noteId;
                db.noteDao().update(note);
                db.tagDao().deleteTagsOfNote(noteId);
            } else {
                noteId = (int) db.noteDao().insert(note);
            }

            for (Integer tagId : selectedTagIds) {
                NoteTag nt = new NoteTag();
                nt.noteId = noteId;
                nt.tagId = tagId;
                db.tagDao().insertNoteTag(nt);
            }

            if (selectedReminderTime != null && selectedReminderMillis > System.currentTimeMillis()) {
                Reminder rem = new Reminder();
                rem.noteId = noteId;
                rem.userId = sessionUserId;
                rem.title = title.isEmpty() ? "Ghi chú không tên" : title;
                rem.reminderTime = selectedReminderTime;
                rem.isDone = 0;
                rem.createdAt = getCurrentTime();
                db.reminderDao().insert(rem);
                scheduleExactAlarm(noteId, rem.title, subtitle, selectedReminderMillis);
            }

            if (pendingRecordingPath != null) {
                saveRecordingToDb(noteId, pendingRecordingPath, pendingRecordingDuration);
                pendingRecordingPath = null;
                pendingRecordingDuration = 0;
            }

            if (!isDestroyed()) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "✅ Lưu ghi chú thành công!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }
        });
    }

    @SuppressLint("ScheduleExactAlarm")
    private void scheduleExactAlarm(int noteId, String title, String subtitle, long timeInMillis) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this, ReminderReceiver.class);
        intent.putExtra(ReminderReceiver.KEY_NOTE_ID, noteId);
        intent.putExtra(ReminderReceiver.KEY_TITLE, title);
        intent.putExtra(ReminderReceiver.KEY_SUBTITLE, subtitle);
        intent.putExtra(ReminderReceiver.KEY_MESSAGE, subtitle.isEmpty()
                ? "Đến giờ xem ghi chú của bạn!"
                : subtitle);
        PendingIntent pending = PendingIntent.getBroadcast(this, noteId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms())
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pending);
                else
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pending);
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pending);
            }
        }
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
    }
}