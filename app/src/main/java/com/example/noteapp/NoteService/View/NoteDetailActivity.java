package com.example.noteapp.NoteService.View;

import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.MediaService.Entity.AudioRecording;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.R;
import com.example.noteapp.ReminderService.Entity.Reminder;
import com.example.noteapp.TagService.Entity.Tag;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoteDetailActivity extends AppCompatActivity {

    private TextView txtBack, txtHeaderTitle, txtTitle, txtTags, txtCreatedAt,
            txtSubtitle, txtReminder, txtLink, btnTTS, txtAudioHeader;
    private WebView webViewContent;
    private ImageView imgNote;
    private Button btnEdit, btnDelete, btnExportPdf, btnTranslate;
    private LinearLayout layoutAudioList;

    private int noteId = -1;
    private static final int REQUEST_EDIT = 1001;

    // TextToSpeech
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean isSpeaking = false;

    // Dịch thuật
    private String currentContent = "";         // HTML gốc
    private String translatedContent = null;    // HTML đã dịch
    private boolean isTranslated = false;       // Trạng thái toggle
    private String currentLang = "vi";         // vi hoặc en

    // MediaPlayer cho ghi âm
    private MediaPlayer mediaPlayer;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    // Tránh load 2 lần khi onResume + onActivityResult cùng được gọi
    private boolean pendingReload = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        txtBack        = findViewById(R.id.txtBack);
        txtHeaderTitle = findViewById(R.id.txtHeaderTitle);
        txtTitle       = findViewById(R.id.txtTitle);
        txtTags        = findViewById(R.id.txtTags);
        txtCreatedAt   = findViewById(R.id.txtCreatedAt);
        txtSubtitle    = findViewById(R.id.txtSubtitle);
        webViewContent = findViewById(R.id.webViewContent);
        txtReminder    = findViewById(R.id.txtReminder);
        txtLink        = findViewById(R.id.txtLink);
        imgNote        = findViewById(R.id.imgNote);
        btnEdit        = findViewById(R.id.btnEdit);
        btnDelete      = findViewById(R.id.btnDelete);
        btnExportPdf   = findViewById(R.id.btnExportPdf);
        btnTranslate   = findViewById(R.id.btnTranslate);
        btnTTS         = findViewById(R.id.btnTTS);
        txtAudioHeader = findViewById(R.id.txtAudioHeader);
        layoutAudioList = findViewById(R.id.layoutAudioList);

        txtBack.setOnClickListener(v -> finish());

        noteId = getIntent().getIntExtra("note_id", -1);
        if (noteId == -1) {
            Toast.makeText(this, "Không tìm thấy ghi chú", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateNoteActivity.class);
            intent.putExtra("note_id", noteId);
            pendingReload = true;
            startActivityForResult(intent, REQUEST_EDIT);
        });

        btnDelete.setOnClickListener(v -> deleteNote());
        btnExportPdf.setOnClickListener(v -> exportNoteToPdf());
        btnTranslate.setOnClickListener(v -> toggleTranslate());
        btnTTS.setOnClickListener(v -> toggleTTS());

        // Khởi tạo TextToSpeech
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(new Locale("vi", "VN"));
            }
        });

        loadNoteDetail(noteId);
    }

    // ─── onActivityResult (từ edit) ───────────────────────────────────────────
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT) {
            pendingReload = false; // onResume sẽ xử lý load
        }
    }

    // ─── onResume ─────────────────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        // Chỉ reload khi từ edit quay lại (pendingReload) hoặc lần đầu
        if (noteId != -1) {
            loadNoteDetail(noteId);
        }
        pendingReload = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (mediaPlayer != null) { mediaPlayer.release(); mediaPlayer = null; }
        executor.shutdown();
    }

    // ─── Load data ────────────────────────────────────────────────────────────

    private void loadNoteDetail(int id) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            Note note              = db.noteDao().getNoteById(id);
            List<Tag> tags         = db.tagDao().getTagsByNoteId(id);
            Reminder reminder      = db.reminderDao().getReminderByNoteId(id);
            List<AudioRecording> recordings = db.audioRecordingDao().getRecordingsByNoteId(id);

            runOnUiThread(() -> {
                if (note == null) {
                    Toast.makeText(this, "Ghi chú không tồn tại", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                String title    = note.title    != null ? note.title    : "";
                String subtitle = note.subtitle != null ? note.subtitle : "";
                String content  = note.content  != null ? note.content  : "";
                String created  = note.createdAt != null ? note.createdAt : "";

                // Reset trạng thái dịch mỗi khi load
                currentContent    = content;
                translatedContent = null;
                isTranslated      = false;
                currentLang       = "vi";
                btnTranslate.setText("🌐 Dịch");

                txtHeaderTitle.setText(title.isEmpty() ? "Ghi chú" : title);
                txtTitle.setText(title);
                txtCreatedAt.setText(created);

                if (subtitle.isEmpty()) txtSubtitle.setVisibility(View.GONE);
                else { txtSubtitle.setVisibility(View.VISIBLE); txtSubtitle.setText(subtitle); }

                // Màu nền
                if (note.color != null && !note.color.isEmpty()) {
                    try {
                        int parsedColor = Color.parseColor(note.color);
                        boolean isNight = (getResources().getConfiguration().uiMode &
                                android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                        if (isNight) {
                            float[] hsl = new float[3];
                            androidx.core.graphics.ColorUtils.colorToHSL(parsedColor, hsl);
                            hsl[1] *= 0.4f;
                            hsl[2] = 0.15f + (hsl[2] * 0.1f);
                            parsedColor = androidx.core.graphics.ColorUtils.HSLToColor(hsl);
                        }
                        findViewById(R.id.rootLayoutDetail).setBackgroundColor(parsedColor);
                    } catch (Exception ignored) {}
                }

                // Tags
                if (tags != null && !tags.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < tags.size(); i++) {
                        sb.append(tags.get(i).tagName);
                        if (i < tags.size() - 1) sb.append(", ");
                    }
                    txtTags.setText(sb.toString());
                    txtTags.setVisibility(View.VISIBLE);
                } else {
                    txtTags.setVisibility(View.GONE);
                }

                imgNote.setVisibility(View.GONE);

                // Nội dung
                renderContent(content);

                // Nhắc nhở
                if (reminder != null && reminder.reminderTime != null && !reminder.reminderTime.isEmpty()) {
                    txtReminder.setVisibility(View.VISIBLE);
                    txtReminder.setText("⏰ Nhắc nhở: " + reminder.reminderTime
                            + (reminder.isDone == 1 ? " ✓" : ""));
                } else {
                    txtReminder.setVisibility(View.GONE);
                }

                txtLink.setVisibility(View.GONE);

                // Danh sách ghi âm
                renderAudioList(recordings);
            });
        });
    }

    /** Render nội dung HTML vào WebView */
    private void renderContent(String content) {
        if (content != null && !content.isEmpty()) {
            boolean isNight = (getResources().getConfiguration().uiMode &
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            String textColor = isNight ? "#EEEEEE" : "#111111";
            String styledHtml = "<html><head>"
                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\" />"
                    + "<style>"
                    + "body { font-family: sans-serif; font-size: 16px; color: " + textColor + "; line-height: 1.6; word-wrap: break-word; white-space: pre-wrap; }"
                    + "img { max-width: 100%; height: auto; }"
                    + "table { width: 100%; border-collapse: collapse; margin-top: 10px; margin-bottom: 10px; border: 1px solid #666666; }"
                    + "th, td { border: 1px solid #666666; padding: 10px; text-align: left; }"
                    + "tr:nth-child(even) { background-color: rgba(0,0,0,0.05); }"
                    + "ul, ol { padding-left: 20px; }"
                    + "a { color: #2074D4; }"
                    + "</style></head><body style=\"margin:0;padding:0;\">"
                    + content + "</body></html>";
            webViewContent.loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null);
            webViewContent.setBackgroundColor(Color.TRANSPARENT);
            webViewContent.setVisibility(View.VISIBLE);
        } else {
            webViewContent.setVisibility(View.GONE);
        }
    }

    // ─── Danh sách ghi âm ────────────────────────────────────────────────────

    private void renderAudioList(List<AudioRecording> recordings) {
        layoutAudioList.removeAllViews();
        if (recordings == null || recordings.isEmpty()) {
            txtAudioHeader.setVisibility(View.GONE);
            return;
        }
        txtAudioHeader.setVisibility(View.VISIBLE);
        for (AudioRecording rec : recordings) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 10, 0, 10);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView tvInfo = new TextView(this);
            File f = new File(rec.filePath != null ? rec.filePath : "");
            String name = f.getName();
            long secs = rec.durationMs / 1000;
            tvInfo.setText("🎙 " + name + "  (" + formatDuration(secs) + ")");
            tvInfo.setTextColor(getResources().getColor(R.color.text_primary_app, getTheme()));
            tvInfo.setTextSize(14f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tvInfo.setLayoutParams(lp);

            TextView btnPlay = new TextView(this);
            btnPlay.setText("▶");
            btnPlay.setTextSize(20f);
            btnPlay.setPadding(12, 0, 12, 0);
            btnPlay.setTextColor(Color.parseColor("#5A8BDB"));
            btnPlay.setOnClickListener(v -> playRecording(rec.filePath, btnPlay));

            TextView btnDel = new TextView(this);
            btnDel.setText("🗑");
            btnDel.setTextSize(18f);
            btnDel.setPadding(12, 0, 0, 0);
            btnDel.setOnClickListener(v -> {
                new android.app.AlertDialog.Builder(this)
                        .setTitle("Xóa ghi âm?")
                        .setMessage("Bạn có chắc muốn xóa ghi âm này?")
                        .setPositiveButton("Xóa", (d, w) -> {
                            executor.execute(() -> {
                                AppDatabase.getInstance(getApplicationContext())
                                        .audioRecordingDao().delete(rec);
                                if (rec.filePath != null) new File(rec.filePath).delete();
                                runOnUiThread(() -> loadNoteDetail(noteId));
                            });
                        })
                        .setNegativeButton("Hủy", null).show();
            });

            row.addView(tvInfo);
            row.addView(btnPlay);
            row.addView(btnDel);

            // Divider
            View divider = new View(this);
            divider.setBackgroundColor(Color.parseColor("#22000000"));
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.addView(row);
            container.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            layoutAudioList.addView(container);
        }
    }

    private void playRecording(String filePath, TextView btnPlay) {
        if (filePath == null || !new File(filePath).exists()) {
            Toast.makeText(this, "File ghi âm không tồn tại", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
            btnPlay.setText("▶");
            return;
        }
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(filePath);
            mediaPlayer.prepare();
            mediaPlayer.start();
            btnPlay.setText("⏹");
            mediaPlayer.setOnCompletionListener(mp -> {
                btnPlay.setText("▶");
                mp.release();
                mediaPlayer = null;
            });
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi phát ghi âm: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String formatDuration(long secs) {
        return String.format(Locale.getDefault(), "%d:%02d", secs / 60, secs % 60);
    }

    // ─── TextToSpeech ─────────────────────────────────────────────────────────

    private void toggleTTS() {
        if (!ttsReady) {
            Toast.makeText(this, "TextToSpeech chưa sẵn sàng", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isSpeaking) {
            tts.stop();
            isSpeaking = false;
            btnTTS.setText("🔊 Đọc");
            return;
        }
        String displayedContent = isTranslated && translatedContent != null
                ? translatedContent : currentContent;
        String plainText = htmlToPlainText(displayedContent);
        if (plainText.isEmpty()) {
            Toast.makeText(this, "Không có nội dung để đọc", Toast.LENGTH_SHORT).show();
            return;
        }

        // Đặt ngôn ngữ phù hợp với nội dung
        Locale locale = isTranslated ? Locale.ENGLISH : new Locale("vi", "VN");
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.getDefault());
        }

        tts.speak(plainText, TextToSpeech.QUEUE_FLUSH, null, "NOTE_TTS");
        isSpeaking = true;
        btnTTS.setText("⏹ Dừng");

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String u) {}
            @Override public void onDone(String u) {
                runOnUiThread(() -> { isSpeaking = false; btnTTS.setText("🔊 Đọc"); });
            }
            @Override public void onError(String u) {
                runOnUiThread(() -> { isSpeaking = false; btnTTS.setText("🔊 Đọc"); });
            }
        });
    }

    private String htmlToPlainText(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?i)<br\\s*/?>|</p>|</div>|</li>|</tr>", " ")
                       .replaceAll("<[^>]+>", "")
                       .replace("&nbsp;", " ").replace("&amp;", "&")
                       .replace("&lt;", "<").replace("&gt;", ">")
                       .replaceAll("\\s+", " ").trim();
        return s;
    }

    // ─── Dịch ghi chú ────────────────────────────────────────────────────────

    private void toggleTranslate() {
        if (isTranslated) {
            // Quay về tiếng Việt
            isTranslated = false;
            currentLang = "vi";
            btnTranslate.setText("🌐 Dịch");
            renderContent(currentContent);
            return;
        }

        // Cần dịch sang tiếng Anh
        if (translatedContent != null) {
            // Đã có bản dịch rồi, hiển thị luôn
            isTranslated = true;
            currentLang = "en";
            btnTranslate.setText("🌐 Gốc");
            renderContent(translatedContent);
            return;
        }

        // Gọi API dịch
        String plainText = htmlToPlainText(currentContent);
        if (plainText.isEmpty()) {
            Toast.makeText(this, "Không có nội dung để dịch", Toast.LENGTH_SHORT).show();
            return;
        }

        btnTranslate.setEnabled(false);
        btnTranslate.setText("⏳ Đang dịch...");

        executor.execute(() -> {
            String translated = callMyMemoryApi(plainText, "vi", "en");
            runOnUiThread(() -> {
                btnTranslate.setEnabled(true);
                if (translated != null) {
                    // Wrap trong HTML đơn giản (plain text)
                    translatedContent = "<p>" + translated.replace("\n", "<br>") + "</p>";
                    isTranslated = true;
                    currentLang = "en";
                    btnTranslate.setText("🌐 Gốc");
                    renderContent(translatedContent);
                } else {
                    btnTranslate.setText("🌐 Dịch");
                    Toast.makeText(this, "Dịch thất bại. Kiểm tra kết nối mạng.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    /** Gọi MyMemory Free API – không cần API key */
    private String callMyMemoryApi(String text, String from, String to) {
        try {
            // Giới hạn 500 ký tự mỗi lần gọi để tránh lỗi
            String truncated = text.length() > 500 ? text.substring(0, 500) : text;
            String encoded   = URLEncoder.encode(truncated, "UTF-8");
            String urlStr    = "https://api.mymemory.translated.net/get?q=" + encoded
                    + "&langpair=" + from + "|" + to;

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            int code = conn.getResponseCode();
            if (code != 200) return null;

            InputStream is = conn.getInputStream();
            byte[] buf = new byte[4096];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = is.read(buf)) != -1) sb.append(new String(buf, 0, n, "UTF-8"));
            is.close();
            conn.disconnect();

            JSONObject json = new JSONObject(sb.toString());
            return json.getJSONObject("responseData").getString("translatedText");
        } catch (Exception e) {
            return null;
        }
    }

    // ─── Delete Note ─────────────────────────────────────────────────────────

    private void deleteNote() {
        if (noteId == -1) return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("Xóa Ghi Chú")
                .setMessage("Bạn có chắc muốn chuyển ghi chú này vào thùng rác?")
                .setPositiveButton("Xóa", (dialog, which) -> {
                    executor.execute(() -> {
                        AppDatabase.getInstance(getApplicationContext())
                                .noteDao().softDeleteNote(noteId, System.currentTimeMillis());
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Đã chuyển vào thùng rác", Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    });
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    // ─── Export PDF ───────────────────────────────────────────────────────────

    private void exportNoteToPdf() {
        if (noteId == -1) return;
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            Note note = db.noteDao().getNoteById(noteId);
            if (note == null) return;
            String title   = note.title   != null ? note.title   : "Ghi chú";
            String content = note.content != null ? note.content : "";
            String created = note.createdAt != null ? note.createdAt : "";
            String html = "<html><head><style>"
                    + "body{font-family:sans-serif;padding:20px;line-height:1.6;color:#111}"
                    + "h1{color:#333;margin-bottom:5px}"
                    + "p.date{color:#888;font-size:14px;margin-top:0;margin-bottom:20px}"
                    + "img{max-width:100%;height:auto}"
                    + "table{width:100%;border-collapse:collapse;margin-bottom:20px}"
                    + "th,td{border:1px solid #ddd;padding:8px}"
                    + "</style></head><body>"
                    + "<h1>" + title + "</h1>"
                    + "<p class='date'>" + created + "</p>"
                    + content + "</body></html>";
            runOnUiThread(() -> {
                WebView pdfWebView = new WebView(this);
                pdfWebView.getSettings().setJavaScriptEnabled(false);
                pdfWebView.getSettings().setLoadWithOverviewMode(true);
                pdfWebView.getSettings().setUseWideViewPort(true);
                pdfWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
                pdfWebView.setWebViewClient(new android.webkit.WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        android.print.PrintManager pm =
                                (android.print.PrintManager) getSystemService(PRINT_SERVICE);
                        String jobName = title.replaceAll("[^a-zA-Z0-9_-]", "_") + "_Document";
                        android.print.PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(jobName);
                        pm.print(jobName, adapter, new android.print.PrintAttributes.Builder().build());
                    }
                });
            });
        });
    }

    // ─── Utilities ────────────────────────────────────────────────────────────

    private List<String> extractAllLinks(String html) {
        List<String> links = new java.util.ArrayList<>();
        if (html == null) return links;
        Pattern p = Pattern.compile("<a[^>]+href\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");
        Matcher m = p.matcher(html);
        while (m.find()) {
            String href = m.group(1);
            if (href != null && !href.isEmpty()) links.add(href);
        }
        return links;
    }
}