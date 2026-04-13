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
import android.graphics.Color;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jp.wasabeef.richeditor.RichEditor;

public class CreateNoteActivity extends AppCompatActivity {

    private Button btnSave, btnAddTag;
    private TextView txtCancel, tvRemindMe, tvAddImage, tvInsertLink, tvRecordAudio, tvVoiceInput;
    private TextView btnBold, btnItalic, btnUnderline, btnBullets, btnChecklist,
            btnTextSize, btnAlign, btnTable, btnHideToolbar;
    private EditText edtTitle, edtSubtitle;
    private LinearLayout layoutTagsContainer, layoutFormattingToolbar, layoutLinksContainer;
    private View colorYellow, colorGreen, colorCyan, colorBlue, colorPurple,
            colorPink, colorGray, colorBlack;

    private RelativeLayout layoutImagePreview;
    private ImageView imgPreview;
    private TextView btnRemoveImage, txtImageName;

    private RichEditor editor;
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
    private String currentRecordingPath = null;
    private long recordingStartTime = 0;

    // ─── Nhập giọng nói ──────────────────────────────────────────────────────
    private SpeechRecognizer speechRecognizer;
    private boolean isListening = false;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;

    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), results -> {
                Boolean audioGranted = results.getOrDefault(Manifest.permission.RECORD_AUDIO, false);
                if (!Boolean.TRUE.equals(audioGranted)) {
                    Toast.makeText(this, "Cần cấp quyền micro để sử dụng tính năng này",
                            Toast.LENGTH_SHORT).show();
                }
            });

    private final ActivityResultLauncher<String> requestNotifLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!granted) {
                    Toast.makeText(this, "Quyền thông báo bị từ chối", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_note);

        btnSave             = findViewById(R.id.btnSave);
        txtCancel           = findViewById(R.id.txtCancel);
        edtTitle            = findViewById(R.id.edtTitle);
        edtSubtitle         = findViewById(R.id.edtSubtitle);
        editor              = findViewById(R.id.editor);
        btnAddTag           = findViewById(R.id.btnAddTag);
        layoutTagsContainer = findViewById(R.id.layoutTagsContainer);
        tvRemindMe          = findViewById(R.id.tvRemindMe);
        tvAddImage          = findViewById(R.id.tvAddImage);
        tvInsertLink        = findViewById(R.id.tvInsertLink);
        tvRecordAudio       = findViewById(R.id.tvRecordAudio);
        tvVoiceInput        = findViewById(R.id.tvVoiceInput);
        layoutFormattingToolbar = findViewById(R.id.layoutFormattingToolbar);
        layoutLinksContainer    = findViewById(R.id.layoutLinksContainer);

        layoutImagePreview = findViewById(R.id.layoutImagePreview);
        imgPreview         = findViewById(R.id.imgPreview);
        btnRemoveImage     = findViewById(R.id.btnRemoveImage);
        txtImageName       = findViewById(R.id.txtImageName);

        btnBold       = findViewById(R.id.btnBold);
        btnItalic     = findViewById(R.id.btnItalic);
        btnUnderline  = findViewById(R.id.btnUnderline);
        btnBullets    = findViewById(R.id.btnBullets);
        btnChecklist  = findViewById(R.id.btnChecklist);
        btnTextSize   = findViewById(R.id.btnTextSize);
        btnAlign      = findViewById(R.id.btnAlign);
        btnTable      = findViewById(R.id.btnTable);
        btnHideToolbar = findViewById(R.id.btnHideToolbar);

        colorYellow = findViewById(R.id.colorYellow);
        colorGreen  = findViewById(R.id.colorGreen);
        colorCyan   = findViewById(R.id.colorCyan);
        colorBlue   = findViewById(R.id.colorBlue);
        colorPurple = findViewById(R.id.colorPurple);
        colorPink   = findViewById(R.id.colorPink);
        colorGray   = findViewById(R.id.colorGray);
        colorBlack  = findViewById(R.id.colorBlack);

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
            if (txtHeaderTitle != null) txtHeaderTitle.setText("Chỉnh sửa Ghi chú");
            loadExistingNote(existingNoteId);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecordingIfActive();
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        executor.shutdown();
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
                            findViewById(android.R.id.content)
                                    .setBackgroundColor(Color.parseColor(selectedNoteColor));
                        } catch (Exception ignored) {}
                    }
                    editor.setHtml(fullContent);
                    if (tags != null) {
                        for (Tag t : tags) selectedTagIds.add(t.tagId);
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestNotifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private boolean checkAndRequestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
            return false;
        }
        return true;
    }

    // ─── Editor setup ─────────────────────────────────────────────────────────

    private void setupEditor() {
        editor.setEditorFontSize(18);
        editor.setEditorFontColor(Color.parseColor("#2E2A27"));
        editor.setPadding(14, 14, 14, 14);
        editor.setPlaceholder("Nội dung ghi chú... (Nhấn để nhập, toolbar định dạng sẽ hiện)");
        editor.setLongClickable(true);
        editor.postDelayed(() ->
                editor.evaluateJavascript(
                        "document.body.style.userSelect='auto'; document.body.style.webkitUserSelect='auto';",
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
            if (focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        });
    }

    // ─── Image picker ─────────────────────────────────────────────────────────

    private void setupImagePicker() {
        pickMedia = registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                try {
                    String fileName = getFileNameFromUri(uri);
                    InputStream is = getContentResolver().openInputStream(uri);
                    android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
                    is.close();
                    ByteArrayOutputStream bao = new ByteArrayOutputStream();
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, bao);
                    String base64  = android.util.Base64.encodeToString(bao.toByteArray(), android.util.Base64.NO_WRAP);
                    String dataUrl = "data:image/jpeg;base64," + base64;
                    String js = "var marker = document.getElementById('cursor_marker_img');"
                            + "if(marker) {"
                            + "  var img = document.createElement('img');"
                            + "  img.src = '" + dataUrl + "';"
                            + "  img.alt = '" + fileName + "';"
                            + "  img.style.maxWidth = '100%';"
                            + "  marker.parentNode.replaceChild(img, marker);"
                            + "  if(typeof RE !== 'undefined' && RE.callback) RE.callback();"
                            + "} else {"
                            + "  document.execCommand('insertImage', false, '" + dataUrl + "');"
                            + "}";
                    editor.evaluateJavascript(js, null);
                    editor.evaluateJavascript(
                            "var m; while(m = document.getElementById('cursor_marker_img')) { m.parentNode.removeChild(m); }",
                            null);
                    Toast.makeText(this, "Đã chèn ảnh: " + fileName, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Lỗi khi thêm ảnh!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private String getFileNameFromUri(Uri uri) {
        String name = "image.png";
        try {
            android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) name = cursor.getString(idx);
                cursor.close();
            }
        } catch (Exception ignored) {}
        return name;
    }

    // ─── Click listeners ──────────────────────────────────────────────────────

    private void setupClickListeners() {
        btnSave.setOnClickListener(v -> saveNote());
        txtCancel.setOnClickListener(v -> finish());
        btnAddTag.setOnClickListener(v -> showTagsDialog());

        btnRemoveImage.setVisibility(View.GONE);
        layoutImagePreview.setVisibility(View.GONE);

        tvAddImage.setOnClickListener(v -> {
            editor.focusEditor();
            editor.evaluateJavascript(
                    "document.execCommand('insertHTML', false, '<span id=\"cursor_marker_img\"></span>');", null);
            pickMedia.launch(new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build());
        });

        tvInsertLink.setOnClickListener(v -> {
            editor.focusEditor();
            editor.evaluateJavascript(
                    "document.execCommand('insertHTML', false, '<span id=\"cursor_marker_link\"></span>');", null);
            showInsertLinkDialog();
        });

        // Ghi âm
        tvRecordAudio.setOnClickListener(v -> toggleRecording());

        // Nhập bằng giọng nói
        tvVoiceInput.setOnClickListener(v -> toggleVoiceInput());

        // Note Color
        View tvNoteColor = findViewById(R.id.tvNoteColor);
        View layoutColorPicker = findViewById(R.id.layoutColorPicker);
        if (tvNoteColor != null && layoutColorPicker != null) {
            tvNoteColor.setOnClickListener(v ->
                    layoutColorPicker.setVisibility(
                            layoutColorPicker.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        }

        tvRemindMe.setOnClickListener(v -> showDateTimePicker());

        editor.setOnDecorationChangeListener((text, types) -> {
            boolean isBold      = types.contains(RichEditor.Type.BOLD);
            boolean isItalic    = types.contains(RichEditor.Type.ITALIC);
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

        btnAlign.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Chọn căn lề")
                        .setItems(new String[]{"Căn trái", "Căn giữa", "Căn phải"}, (d, w) -> {
                            if (w == 0) editor.setAlignLeft();
                            else if (w == 1) editor.setAlignCenter();
                            else editor.setAlignRight();
                        }).show());

        btnTable.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("Quản lý Bảng")
                        .setItems(new String[]{"Tạo bảng mới", "Xóa dòng", "Xóa cột", "Xóa bảng"}, (d, w) -> {
                            if (w == 0) showCreateTableDialog();
                            else if (w == 1) runTableJs("row");
                            else if (w == 2) runTableJs("col");
                            else runTableJs("table");
                        }).show());

        View.OnClickListener colorClick = v -> {
            String hex = (String) v.getTag();
            selectedNoteColor = hex;
            try { findViewById(android.R.id.content).setBackgroundColor(Color.parseColor(hex)); }
            catch (Exception ignored) {}
        };
        colorYellow.setTag("#F3D986"); colorYellow.setOnClickListener(colorClick);
        colorGreen.setTag("#DDE0A8");  colorGreen.setOnClickListener(colorClick);
        colorCyan.setTag("#B7D6C6");   colorCyan.setOnClickListener(colorClick);
        colorBlue.setTag("#B5C7DE");   colorBlue.setOnClickListener(colorClick);
        colorPurple.setTag("#D5C9E3"); colorPurple.setOnClickListener(colorClick);
        colorPink.setTag("#EDC9CF");   colorPink.setOnClickListener(colorClick);
        colorGray.setTag("#D9D5D2");   colorGray.setOnClickListener(colorClick);
        colorBlack.setTag("#2E2A27");  colorBlack.setOnClickListener(colorClick);
    }

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

    // ─── Ghi âm ──────────────────────────────────────────────────────────────

    private void toggleRecording() {
        if (!checkAndRequestAudioPermission()) return;
        if (isRecording) {
            stopRecording();
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        try {
            File dir = getFilesDir();
            String fileName = "rec_" + System.currentTimeMillis() + ".m4a";
            currentRecordingPath = new File(dir, fileName).getAbsolutePath();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(this);
            } else {
                mediaRecorder = new MediaRecorder();
            }
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(128000);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(currentRecordingPath);
            mediaRecorder.prepare();
            mediaRecorder.start();

            isRecording = true;
            recordingStartTime = System.currentTimeMillis();
            tvRecordAudio.setText("⏹ Dừng ghi âm");
            tvRecordAudio.setTextColor(Color.parseColor("#D32F2F"));
            Toast.makeText(this, "Đang ghi âm...", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi ghi âm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            mediaRecorder = null;
            isRecording = false;
        }
    }

    private void stopRecording() {
        if (mediaRecorder == null) return;
        try {
            mediaRecorder.stop();
        } catch (Exception ignored) {}
        mediaRecorder.release();
        mediaRecorder = null;

        long duration = System.currentTimeMillis() - recordingStartTime;
        isRecording = false;
        tvRecordAudio.setText("🎙 Ghi âm");
        tvRecordAudio.setTextColor(Color.parseColor("#7B726B"));

        if (duration < 1000) {
            // Quá ngắn → xóa file
            if (currentRecordingPath != null) new File(currentRecordingPath).delete();
            Toast.makeText(this, "Ghi âm quá ngắn, hãy thử lại", Toast.LENGTH_SHORT).show();
            currentRecordingPath = null;
            return;
        }

        // Lưu tạm đường dẫn để save cùng note
        final String path = currentRecordingPath;
        final long dur = duration;
        currentRecordingPath = null;

        // Nếu đang edit note hiện tại → lưu ngay vào DB
        if (existingNoteId != -1) {
            saveRecordingToDb(existingNoteId, path, dur);
        } else {
            // Ghi chú mới chưa lưu → giữ tạm trong memory, sẽ lưu khi save note
            pendingRecordingPath = path;
            pendingRecordingDuration = dur;
        }

        Toast.makeText(this, "✅ Đã ghi âm xong (" + (dur / 1000) + "s)", Toast.LENGTH_SHORT).show();
    }

    private void stopRecordingIfActive() {
        if (isRecording && mediaRecorder != null) {
            try { mediaRecorder.stop(); } catch (Exception ignored) {}
            mediaRecorder.release();
            mediaRecorder = null;
            isRecording = false;
        }
    }

    // Lưu tạm ghi âm chờ ghi chú được tạo xong
    private String pendingRecordingPath = null;
    private long pendingRecordingDuration = 0;

    private void saveRecordingToDb(int noteId, String filePath, long durationMs) {
        executor.execute(() -> {
            AudioRecording rec = new AudioRecording();
            rec.noteId = noteId;
            rec.filePath = filePath;
            rec.durationMs = durationMs;
            rec.createdAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date());
            AppDatabase.getInstance(getApplicationContext()).audioRecordingDao().insert(rec);
        });
    }

    // ─── Nhập bằng giọng nói ─────────────────────────────────────────────────

    private void toggleVoiceInput() {
        if (!checkAndRequestAudioPermission()) return;
        if (isListening) {
            stopVoiceInput();
        } else {
            startVoiceInput();
        }
    }

    private void startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Thiết bị không hỗ trợ nhận dạng giọng nói", Toast.LENGTH_SHORT).show();
            return;
        }

        if (speechRecognizer != null) { speechRecognizer.destroy(); }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                isListening = true;
                tvVoiceInput.setText("🔴 Đang nghe...");
                tvVoiceInput.setTextColor(Color.parseColor("#D32F2F"));
            }
            @Override public void onResults(Bundle results) {
                List<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    // Chèn text vào vị trí con trỏ trong editor
                    String escaped = text.replace("'", "\\'");
                    editor.evaluateJavascript(
                            "document.execCommand('insertText', false, '" + escaped + "');", null);
                    Toast.makeText(CreateNoteActivity.this,
                            "Đã nhận: " + text, Toast.LENGTH_SHORT).show();
                }
                resetVoiceButton();
            }
            @Override public void onError(int error) {
                String msg = getVoiceErrorMsg(error);
                Toast.makeText(CreateNoteActivity.this, "Lỗi: " + msg, Toast.LENGTH_SHORT).show();
                resetVoiceButton();
            }
            @Override public void onEndOfSpeech() { resetVoiceButton(); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float v) {}
            @Override public void onBufferReceived(byte[] b) {}
            @Override public void onPartialResults(Bundle b) {}
            @Override public void onEvent(int i, Bundle b) {}
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "vi-VN");
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        speechRecognizer.startListening(intent);
    }

    private void stopVoiceInput() {
        if (speechRecognizer != null) speechRecognizer.stopListening();
        resetVoiceButton();
    }

    private void resetVoiceButton() {
        isListening = false;
        tvVoiceInput.setText("🎤 Nhập giọng");
        tvVoiceInput.setTextColor(Color.parseColor("#7B726B"));
    }

    private String getVoiceErrorMsg(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO:              return "Lỗi audio";
            case SpeechRecognizer.ERROR_CLIENT:             return "Lỗi client";
            case SpeechRecognizer.ERROR_NETWORK:            return "Lỗi mạng";
            case SpeechRecognizer.ERROR_NO_MATCH:           return "Không nhận diện được";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:     return "Hết thời gian";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:    return "Đang bận";
            default: return "Lỗi không xác định (" + error + ")";
        }
    }

    // ─── Insert Link Dialog ───────────────────────────────────────────────────

    private void showInsertLinkDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Chèn đường Link");
        final EditText input = new EditText(this);
        input.setHint("https://...");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        input.setPadding(p, p / 2, p, p / 2);
        builder.setView(input);
        builder.setPositiveButton("Thêm", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                if (!url.startsWith("http")) url = "https://" + url;
                final String finalUrl = url;
                String js = "var marker = document.getElementById('cursor_marker_link');"
                        + "if(marker) {"
                        + "  var a = document.createElement('a');"
                        + "  a.href = '" + finalUrl + "';"
                        + "  a.innerText = '" + finalUrl + "';"
                        + "  marker.parentNode.replaceChild(a, marker);"
                        + "  if(typeof RE !== 'undefined' && RE.callback) RE.callback();"
                        + "} else {"
                        + "  document.execCommand('createLink', false, '" + finalUrl + "');"
                        + "}";
                editor.evaluateJavascript(js, null);
                editor.evaluateJavascript(
                        "var m; while(m = document.getElementById('cursor_marker_link')) { m.parentNode.removeChild(m); }",
                        null);
            }
        });
        builder.setNegativeButton("Hủy", null);
        builder.show();
    }

    // ─── Tags ─────────────────────────────────────────────────────────────────

    private void showTagsDialog() {
        if (allTags.isEmpty()) {
            Toast.makeText(this, "Chưa có tag nào!", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[allTags.size()];
        boolean[] checked = new boolean[allTags.size()];
        for (int i = 0; i < allTags.size(); i++) {
            names[i]   = allTags.get(i).tagName;
            checked[i] = selectedTagIds.contains(allTags.get(i).tagId);
        }
        new AlertDialog.Builder(this)
                .setTitle("Chọn Tags")
                .setMultiChoiceItems(names, checked, (d, w, c) -> {
                    if (c) selectedTagIds.add(allTags.get(w).tagId);
                    else selectedTagIds.remove(allTags.get(w).tagId);
                })
                .setPositiveButton("Xong", (d, w) -> renderTags())
                .setNeutralButton("Tạo Tag mới", (d, w) -> showCreateTagDialog())
                .show();
    }

    private void showCreateTagDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Tạo Tag mới");
        final EditText input = new EditText(this);
        input.setHint("Nhập tên tag");
        b.setView(input);
        b.setPositiveButton("Tạo", (d, w) -> {
            String tagName = input.getText().toString().trim();
            if (!tagName.isEmpty()) {
                executor.execute(() -> {
                    Tag t = new Tag();
                    t.userId = sessionUserId;
                    t.tagName = tagName;
                    long id = AppDatabase.getInstance(getApplicationContext()).tagDao().insertTag(t);
                    selectedTagIds.add((int) id);
                    runOnUiThread(() -> { Toast.makeText(this, "Đã tạo tag!", Toast.LENGTH_SHORT).show(); loadTags(); });
                });
            }
        });
        b.show();
    }

    private void showCreateTableDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);
        TextView tvR = new TextView(this); tvR.setText("Số dòng:"); layout.addView(tvR);
        EditText edtR = new EditText(this); edtR.setHint("VD: 3"); edtR.setInputType(InputType.TYPE_CLASS_NUMBER); layout.addView(edtR);
        TextView tvC = new TextView(this); tvC.setText("Số cột:"); layout.addView(tvC);
        EditText edtC = new EditText(this); edtC.setHint("VD: 3"); edtC.setInputType(InputType.TYPE_CLASS_NUMBER); layout.addView(edtC);
        new AlertDialog.Builder(this).setTitle("Tạo bảng").setView(layout)
                .setPositiveButton("Tạo", (d, w) -> {
                    int rows = 2, cols = 2;
                    try { rows = Integer.parseInt(edtR.getText().toString().trim()); } catch (Exception ignored) {}
                    try { cols = Integer.parseInt(edtC.getText().toString().trim()); } catch (Exception ignored) {}
                    if (rows < 1) rows = 1; if (cols < 1) cols = 1;
                    StringBuilder html = new StringBuilder("<table style='width:100%;border-collapse:collapse;margin:10px 0;border:1px solid #666666;'>");
                    for (int r = 0; r < rows; r++) {
                        html.append("<tr>");
                        for (int c = 0; c < cols; c++) html.append("<td style='border:1px solid #666666;padding:8px;min-width:40px;'>&nbsp;</td>");
                        html.append("</tr>");
                    }
                    html.append("</table><br>");
                    String escaped = html.toString().replace("'", "\\'");
                    int fr = rows, fc = cols;
                    editor.evaluateJavascript("(function(){document.execCommand('insertHTML',false,'" + escaped + "');})()",
                            v -> runOnUiThread(() -> Toast.makeText(this, "Đã chèn bảng " + fr + "x" + fc, Toast.LENGTH_SHORT).show()));
                })
                .setNegativeButton("Hủy", null).show();
    }

    // ─── Date/Time picker ─────────────────────────────────────────────────────

    private void showDateTimePicker() {
        final Calendar c = Calendar.getInstance();
        new DatePickerDialog(this,
                (view, y, m, d) -> new TimePickerDialog(this, (v2, h, min) -> {
                    Calendar cal = Calendar.getInstance();
                    cal.set(y, m, d, h, min, 0);
                    if (cal.getTimeInMillis() < System.currentTimeMillis()) {
                        Toast.makeText(this, "Chọn thời gian trong tương lai!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedReminderTime   = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(cal.getTime());
                    selectedReminderMillis = cal.getTimeInMillis();
                    tvRemindMe.setText("⏰ " + selectedReminderTime);
                    tvRemindMe.setTextColor(Color.parseColor("#D3C08D"));
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show(),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ─── Load Tags ────────────────────────────────────────────────────────────

    @Override
    protected void onResume() {
        super.onResume();
        loadTags();
    }

    private void loadTags() {
        executor.execute(() -> {
            List<Tag> tags = AppDatabase.getInstance(getApplicationContext())
                    .tagDao().getAllTags(sessionUserId);
            runOnUiThread(() -> { allTags.clear(); allTags.addAll(tags); renderTags(); });
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
            if (!selectedTagIds.contains(tag.tagId)) continue;
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
            chip.setOnClickListener(v -> { selectedTagIds.remove(tag.tagId); renderTags(); });
            layoutTagsContainer.addView(chip);
        }
    }

    // ─── Save Note ────────────────────────────────────────────────────────────

    private void saveNote() {
        String title       = edtTitle.getText().toString().trim();
        String subtitle    = edtSubtitle.getText().toString().trim();
        String contentHtml = editor.getHtml() != null ? editor.getHtml().trim() : "";

        if (title.isEmpty() && subtitle.isEmpty() && contentHtml.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập ghi chú", Toast.LENGTH_SHORT).show();
            return;
        }

        // Dừng ghi âm nếu đang ghi
        if (isRecording) stopRecording();

        Note note = new Note();
        note.userId    = sessionUserId;
        note.title     = title;
        note.subtitle  = subtitle;
        note.content   = contentHtml;
        note.color     = selectedNoteColor.isEmpty() ? "#FFFFFF" : selectedNoteColor;
        note.createdAt = getCurrentTime();
        note.updatedAt = getCurrentTime();
        note.isPinned  = 0;
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
                nt.tagId  = tagId;
                db.tagDao().insertNoteTag(nt);
            }

            if (selectedReminderTime != null && selectedReminderMillis > System.currentTimeMillis()) {
                Reminder rem = new Reminder();
                rem.noteId    = noteId;
                rem.userId    = sessionUserId;
                rem.title     = title.isEmpty() ? "Ghi chú không tên" : title;
                rem.reminderTime = selectedReminderTime;
                rem.isDone    = 0;
                rem.createdAt = getCurrentTime();
                db.reminderDao().insert(rem);
                scheduleExactAlarm(noteId, rem.title, selectedReminderMillis);
            }

            // Lưu ghi âm đang chờ (nếu ghi chú mới vừa được tạo)
            if (pendingRecordingPath != null) {
                saveRecordingToDb(noteId, pendingRecordingPath, pendingRecordingDuration);
                pendingRecordingPath = null;
                pendingRecordingDuration = 0;
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