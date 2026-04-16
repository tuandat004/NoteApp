package com.example.noteapp.NoteService.View;

import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.ColorUtils;

import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.MediaService.Entity.AudioRecording;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.R;
import com.example.noteapp.ReminderService.Entity.Reminder;
import com.example.noteapp.TagService.Entity.Tag;

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

public class NoteDetailActivity extends AppCompatActivity {

    // ─── Views ───────────────────────────────────────────────────────────────
    private TextView txtBack, txtHeaderTitle, txtTitle, txtSubtitle, txtTags, txtCreatedAt;
    private TextView txtReminder, txtLink, btnTTS, btnTranslate, btnExportPdf, btnDelete, btnEdit, btnMoreOptions;
    private TextView txtAudioHeader;
    private WebView webViewContent;
    private ImageView imgNote;
    private LinearLayout layoutAudioList;

    // ─── State ────────────────────────────────────────────────────────────────
    private int noteId = -1;

    // TTS
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean isSpeaking = false;

    // Translate
    private String currentContent = "";
    private String translatedContent = null;
    private boolean isTranslated = false;
    private String sourceLang = "vi";      // ngôn ngữ gốc của note
    private String targetLang = "en";     // sẽ toggle

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
                        inlinePlayer.setOnCompletionListener(mp -> { mp.release(); inlinePlayer = null; });
                        Toast.makeText(NoteDetailActivity.this, "Đang phát ghi âm...", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {}
                }
            });
        }
        @android.webkit.JavascriptInterface
        public void delete(String path, String domId) {
            runOnUiThread(() -> {
                Toast.makeText(NoteDetailActivity.this, "Không thể xoá khi đang xem", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    // ─── onCreate ─────────────────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_note_detail);

        txtBack         = findViewById(R.id.txtBack);
        txtHeaderTitle  = findViewById(R.id.txtHeaderTitle);
        txtTitle        = findViewById(R.id.txtTitle);
        txtSubtitle     = findViewById(R.id.txtSubtitle);
        txtTags         = findViewById(R.id.txtTags);
        txtCreatedAt    = findViewById(R.id.txtCreatedAt);
        txtReminder     = findViewById(R.id.txtReminder);
        txtLink         = findViewById(R.id.txtLink);
        imgNote         = findViewById(R.id.imgNote);
        webViewContent  = findViewById(R.id.webViewContent);
        txtAudioHeader  = findViewById(R.id.txtAudioHeader);
        layoutAudioList = findViewById(R.id.layoutAudioList);
        btnTTS          = findViewById(R.id.btnTTS);
        btnTranslate    = findViewById(R.id.btnTranslate);
        btnExportPdf    = findViewById(R.id.btnExportPdf);
        btnDelete       = findViewById(R.id.btnDelete);
        btnEdit         = findViewById(R.id.btnEdit);
        btnMoreOptions  = findViewById(R.id.btnMoreOptions);

        txtBack.setOnClickListener(v -> finish());

        noteId = getIntent().getIntExtra("note_id", -1);
        if (noteId == -1) { Toast.makeText(this, "Không tìm thấy ghi chú", Toast.LENGTH_SHORT).show(); finish(); return; }

        // Edit – dùng TextView giờ nên cast khác
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateNoteActivity.class);
            intent.putExtra("note_id", noteId);
            startActivity(intent);
        });

        btnDelete.setOnClickListener(v -> deleteNote());
        btnExportPdf.setOnClickListener(v -> exportNoteToPdf());
        btnTTS.setOnClickListener(v -> toggleTTS());
        btnTranslate.setOnClickListener(v -> showTranslateMenu());

        // More options popup
        btnMoreOptions.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, v);
            popup.getMenu().add(0, 1, 0, "📋 Sao chép nội dung");
            popup.getMenu().add(0, 2, 0, "📄 Xuất PDF");
            popup.getMenu().add(0, 3, 0, "🗑 Xóa ghi chú");
            popup.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) copyNoteContent();
                else if (item.getItemId() == 2) exportNoteToPdf();
                else if (item.getItemId() == 3) deleteNote();
                return true;
            });
            popup.show();
        });

        // TTS init
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(new Locale.Builder().setLanguage("vi").setRegion("VN").build());
            }
        });

        loadNoteDetail(noteId);
    }



    // Track if first load was done in onCreate to avoid double-load in onResume
    private boolean initialLoadDone = false;

    @Override
    protected void onResume() {
        super.onResume();
        if (!initialLoadDone) { initialLoadDone = true; return; }
        if (noteId != -1) loadNoteDetail(noteId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
        if (inlinePlayer != null) { inlinePlayer.release(); inlinePlayer = null; }
        executor.shutdown();
    }

    // ─── Load data ────────────────────────────────────────────────────────────
    private void loadNoteDetail(int id) {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            Note note = db.noteDao().getNoteById(id);
            List<Tag> tags = db.tagDao().getTagsByNoteId(id);
            Reminder reminder = db.reminderDao().getReminderByNoteId(id);
            List<AudioRecording> recordings = db.audioRecordingDao().getRecordingsByNoteId(id);

            runOnUiThread(() -> {
                if (note == null) { Toast.makeText(this, "Ghi chú không tồn tại", Toast.LENGTH_SHORT).show(); finish(); return; }

                String title    = note.title    != null ? note.title    : "";
                String subtitle = note.subtitle != null ? note.subtitle : "";
                String content  = note.content  != null ? note.content  : "";
                String created  = note.createdAt != null ? note.createdAt : "";

                // Reset translate state
                currentContent = content; translatedContent = null; isTranslated = false;
                btnTranslate.setText("🌐 Dịch");

                // AppBar title
                txtHeaderTitle.setText(title.isEmpty() ? "Chi tiết Ghi chú" : title);
                txtTitle.setText(title);
                txtCreatedAt.setText("🕐 " + created);

                if (subtitle.isEmpty()) txtSubtitle.setVisibility(View.GONE);
                else { txtSubtitle.setVisibility(View.VISIBLE); txtSubtitle.setText(subtitle); }

                // Background color
                applyNoteColor(note.color);

                // Tags
                if (tags != null && !tags.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < tags.size(); i++) {
                        sb.append("🏷 ").append(tags.get(i).tagName);
                        if (i < tags.size() - 1) sb.append("  ");
                    }
                    txtTags.setText(sb.toString());
                    txtTags.setVisibility(View.VISIBLE);
                } else txtTags.setVisibility(View.GONE);

                imgNote.setVisibility(View.GONE);
                renderContent(content);

                // Reminder
                if (reminder != null && reminder.reminderTime != null && !reminder.reminderTime.isEmpty()) {
                    txtReminder.setVisibility(View.VISIBLE);
                    txtReminder.setText("⏰ Nhắc nhở: " + reminder.reminderTime + (reminder.isDone == 1 ? " ✓" : ""));
                } else txtReminder.setVisibility(View.GONE);

                txtLink.setVisibility(View.GONE);
                
                // Hide audio list UI as it's now embedded in html
                txtAudioHeader.setVisibility(View.GONE);
                layoutAudioList.setVisibility(View.GONE);
            });
        });
    }

    private void applyNoteColor(String color) {
        if (color == null || color.isEmpty()) return;
        try {
            int parsed = Color.parseColor(color);
            boolean isNight = (getResources().getConfiguration().uiMode &
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            if (isNight) {
                float[] hsl = new float[3];
                ColorUtils.colorToHSL(parsed, hsl);
                hsl[1] *= 0.4f; hsl[2] = 0.15f + hsl[2] * 0.1f;
                parsed = ColorUtils.HSLToColor(hsl);
            }
            // Apply to scroll area only
            View scroll = findViewById(R.id.rootLayoutDetail);
            if (scroll != null) scroll.setBackgroundColor(parsed);
        } catch (Exception ignored) {}
    }

    private void renderContent(String content) {
        if (content != null && !content.isEmpty()) {
            String finalContent = content.replace("onclick=\"window.AndroidAudio.play('", "onclick=\"event.stopPropagation(); event.preventDefault(); window.AndroidAudio.play('");
            finalContent = finalContent.replace("onclick=\"window.AndroidAudio.delete('", "onclick=\"event.stopPropagation(); event.preventDefault(); window.AndroidAudio.delete('");

            boolean isNight = (getResources().getConfiguration().uiMode &
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            String textColor = isNight ? "#EEEEEE" : "#1A1A1A";
            String html = "<html><head>"
                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\"/>"
                    + "<style>"
                    + "body{font-family:sans-serif;font-size:16px;color:" + textColor + ";line-height:1.75;word-wrap:break-word;margin:0;padding:0;}"
                    + "img{max-width:100%;height:auto;display:block;margin:10px 0;border-radius:8px;}"
                    + "table{width:100%;border-collapse:collapse;margin:12px 0;border:1px solid #888;}"
                    + "th,td{border:1px solid #888;padding:10px;text-align:left;}"
                    + "tr:nth-child(even){background:rgba(0,0,0,0.04);}"
                    + "ul,ol{padding-left:22px;}"
                    + "a{color:#2074D4;}"
                    + ".app-audio-record > div:last-child { display: none !important; }" // Hide delete button in detail view
                    + "h1,h2,h3,h4,h5,h6{margin:12px 0 6px;}"
                    + "</style></head><body>" + finalContent + "</body></html>";
            webViewContent.getSettings().setJavaScriptEnabled(true);
            webViewContent.addJavascriptInterface(new AudioBridge(), "AndroidAudio");
            webViewContent.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
            webViewContent.setBackgroundColor(Color.TRANSPARENT);
            webViewContent.setVisibility(View.VISIBLE);
        } else webViewContent.setVisibility(View.GONE);
    }

    // ─── TTS (improved: skip URLs and audio) ───────────────────────────────────
    private void toggleTTS() {
        if (!ttsReady) { Toast.makeText(this, "TextToSpeech chưa sẵn sàng", Toast.LENGTH_SHORT).show(); return; }
        if (isSpeaking) {
            tts.stop(); isSpeaking = false; btnTTS.setText("🔊 Đọc"); return;
        }
        String displayedContent = isTranslated && translatedContent != null ? translatedContent : currentContent;
        String plainText = htmlToCleanText(displayedContent);
        if (plainText.isEmpty()) { Toast.makeText(this, "Không có nội dung để đọc", Toast.LENGTH_SHORT).show(); return; }

        Locale locale = isTranslated ? Locale.ENGLISH : new Locale.Builder().setLanguage("vi").setRegion("VN").build();
        int result = tts.setLanguage(locale);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED)
            tts.setLanguage(Locale.getDefault());

        tts.speak(plainText, TextToSpeech.QUEUE_FLUSH, null, "NOTE_TTS");
        isSpeaking = true; btnTTS.setText("⏹ Dừng");

        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String u) {}
            @Override public void onDone(String u) { runOnUiThread(() -> { isSpeaking = false; btnTTS.setText("🔊 Đọc"); }); }
            @SuppressWarnings("deprecation")
            @Override public void onError(String u) { runOnUiThread(() -> { isSpeaking = false; btnTTS.setText("🔊 Đọc"); }); }
        });
    }

    /** HTML → clean plain text: bỏ URL, bỏ audio, chỉ giữ text */
    private String htmlToCleanText(String html) {
        if (html == null) return "";
        // Loại bỏ thẻ âm thanh đặc biệt
        String s = html.replaceAll("(?is)<div[^>]*class=\"[^\"]*app-audio-record[^\"]*\"[^>]*>.*?</div>", "");
        // Remove link tags and image tags
        s = s.replaceAll("(?i)<a[^>]+href=['\"][^'\"]*['\"][^>]*>https?://[^<]*</a>", "")
             .replaceAll("(?i)<a[^>]*>.*?</a>", "")
             .replaceAll("(?i)<img[^>]*/?>", "")
             .replaceAll("(?i)<br\\s*/?>|</p>|</div>|</li>|</tr>", " ")
             .replaceAll("<[^>]+>", "") // remove all remaining tags
             .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
             .replaceAll("https?://\\S+", "") // remove remaining raw URLs
             .replaceAll("\\s+", " ").trim();
        return s;
    }

    // ─── Dịch với Placeholder ────────────────────────────────────────────────
    // Danh sách ngôn ngữ hỗ trợ
    private static final String[] LANG_NAMES = {
        "🇻🇳 Tiếng Việt", "🇺🇸 English", "🇨🇳 中文", "🇯🇵 日本語", "🇰🇷 한국어",
        "🇫🇷 Français", "🇩🇪 Deutsch", "🇪🇸 Español", "🇮🇹 Italiano",
        "🇵🇹 Português", "🇷🇺 Русский", "🇹🇭 ภาษาไทย", "🇮🇩 Bahasa Indonesia",
        "🇸🇦 العربية", "🇮🇳 हिन्दी", "🇳🇱 Nederlands", "🇵🇱 Polski",
        "🇹🇷 Türkçe", "🇺🇦 Українська", "🇲🇾 Melayu"
    };
    private static final String[] LANG_CODES = {
        "vi", "en", "zh", "ja", "ko",
        "fr", "de", "es", "it",
        "pt", "ru", "th", "id",
        "ar", "hi", "nl", "pl",
        "tr", "uk", "ms"
    };

    private void showTranslateMenu() {
        android.content.SharedPreferences prefs =
                getSharedPreferences("translate_prefs", MODE_PRIVATE);
        // Khôi phục lựa chọn lần trước
        int savedFrom = prefs.getInt("lang_from_idx", 0); // default: vi
        int savedTo   = prefs.getInt("lang_to_idx", 1);   // default: en

        // Bọc trong mảng để có thể thay đổi trong lambda
        int[] fromIdx = {savedFrom};
        int[] toIdx   = {savedTo};

        android.view.LayoutInflater inf = android.view.LayoutInflater.from(this);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(8));

        // ── Hàng "Từ" ──
        android.widget.TextView lblFrom = new android.widget.TextView(this);
        lblFrom.setText("Ngôn ngữ nguồn:");
        lblFrom.setTextColor(0xFF888888);
        lblFrom.setTextSize(12f);
        root.addView(lblFrom);

        android.widget.Spinner spFrom = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adFrom = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, LANG_NAMES);
        adFrom.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spFrom.setAdapter(adFrom);
        spFrom.setSelection(fromIdx[0]);
        android.widget.LinearLayout.LayoutParams spLP = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        spLP.setMargins(0, dp(4), 0, dp(12));
        spFrom.setLayoutParams(spLP);
        root.addView(spFrom);

        // ── Nút hoán đổi ──
        android.widget.Button btnSwap = new android.widget.Button(this);
        btnSwap.setText("⇅  Hoán đổi");
        btnSwap.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFEDE9FE));
        btnSwap.setTextColor(0xFF7C3AED);
        btnSwap.setAllCaps(false);
        android.widget.LinearLayout.LayoutParams swapLP = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        swapLP.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        swapLP.setMargins(0, 0, 0, dp(12));
        btnSwap.setLayoutParams(swapLP);
        root.addView(btnSwap);

        // ── Hàng "Sang" ──
        android.widget.TextView lblTo = new android.widget.TextView(this);
        lblTo.setText("Ngôn ngữ đích:");
        lblTo.setTextColor(0xFF888888);
        lblTo.setTextSize(12f);
        root.addView(lblTo);

        android.widget.Spinner spTo = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> adTo = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, LANG_NAMES);
        adTo.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTo.setAdapter(adTo);
        spTo.setSelection(toIdx[0]);
        android.widget.LinearLayout.LayoutParams spToLP = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        spToLP.setMargins(0, dp(4), 0, 0);
        spTo.setLayoutParams(spToLP);
        root.addView(spTo);

        // Swap logic
        btnSwap.setOnClickListener(v -> {
            int tmpF = spFrom.getSelectedItemPosition();
            int tmpT = spTo.getSelectedItemPosition();
            spFrom.setSelection(tmpT);
            spTo.setSelection(tmpF);
        });

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🌐 Chọn ngôn ngữ dịch")
                .setView(root)
                .setPositiveButton("Dịch ngay", (d, w) -> {
                    int fi = spFrom.getSelectedItemPosition();
                    int ti = spTo.getSelectedItemPosition();
                    if (fi == ti) {
                        Toast.makeText(this, "Vui lòng chọn hai ngôn ngữ khác nhau", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // Lưu lựa chọn
                    prefs.edit().putInt("lang_from_idx", fi).putInt("lang_to_idx", ti).apply();
                    translateContent(LANG_CODES[fi], LANG_CODES[ti]);
                })
                .setNeutralButton(isTranslated ? "Quay về bản gốc" : null, (d, w) -> {
                    isTranslated = false; translatedContent = null;
                    btnTranslate.setText("🌐 Dịch");
                    renderContent(currentContent);
                })
                .setNegativeButton("Hủy", null)
                .create();
        dialog.show();
    }





    private void translateContent(String from, String to) {
        if (currentContent == null || currentContent.isEmpty()) return;
        
        btnTranslate.setText("⏳...");
        btnTranslate.setEnabled(false);

        executor.execute(() -> {
            // Dùng local list để tránh race condition nếu dịch nhiều lần liên tiếp
            java.util.List<String> lph = new java.util.ArrayList<>();
            String templated = currentContent;
            
            // Xử lý Audio block
            java.util.regex.Matcher mAudio = java.util.regex.Pattern.compile("(?is)<div[^>]*class=\"[^\"]*app-audio-record[^\"]*\"[^>]*>.*?</div>").matcher(templated);
            StringBuffer sb = new StringBuffer();
            while (mAudio.find()) {
                lph.add(mAudio.group());
                mAudio.appendReplacement(sb, "[[AUDIO_" + (lph.size() - 1) + "]]");
            }
            mAudio.appendTail(sb);
            templated = sb.toString();

            // Xử lý Image block
            java.util.regex.Matcher mImg = java.util.regex.Pattern.compile("(?i)<img[^>]*>").matcher(templated);
            sb = new StringBuffer();
            while (mImg.find()) {
                lph.add(mImg.group());
                mImg.appendReplacement(sb, "[[IMG_" + (lph.size() - 1) + "]]");
            }
            mImg.appendTail(sb);
            templated = sb.toString();

            // Xử lý Link block
            java.util.regex.Matcher mLink = java.util.regex.Pattern.compile("(?i)<a[^>]*>.*?</a>").matcher(templated);
            sb = new StringBuffer();
            while (mLink.find()) {
                lph.add(mLink.group());
                mLink.appendReplacement(sb, "[[LINK_" + (lph.size() - 1) + "]]");
            }
            mLink.appendTail(sb);
            templated = sb.toString();

            // Lấy text thuần để dịch, vì MemoryAPI có khuynh hướng dịch cả href nếu để nguyên
            String plainTextToTranslate = htmlToCleanTextForTranslation(templated);

            String translated = callMyMemoryApi(plainTextToTranslate, from, to);
            
            if (!isDestroyed()) {
                runOnUiThread(() -> {
                    btnTranslate.setEnabled(true);
                    if (translated != null) {
                        // Phục hồi lại các placeholder vào nội dung đã dịch
                        String resultHtml = translated.replace("\n", "<br>");
                        for (int i = 0; i < lph.size(); i++) {
                            resultHtml = resultHtml.replace("[[AUDIO_" + i + "]]", lph.get(i));
                            resultHtml = resultHtml.replace("[[IMG_" + i + "]]", lph.get(i));
                            resultHtml = resultHtml.replace("[[LINK_" + i + "]]", lph.get(i));
                        }
                        
                        translatedContent = resultHtml;
                        isTranslated = true;
                        btnTranslate.setText("🌐 Gốc");
                        renderContent(translatedContent);
                        Toast.makeText(this, "✅ Đã dịch " + from.toUpperCase() + " → " + to.toUpperCase(), Toast.LENGTH_SHORT).show();
                    } else {
                        btnTranslate.setText("🌐 Dịch");
                        Toast.makeText(this, "Dịch thất bại. Kiểm tra kết nối mạng.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // Helper text extract for translation logic (giữ lại placeholder)
    private String htmlToCleanTextForTranslation(String html) {
        String s = html
                .replaceAll("(?i)<br\\s*/?>|</p>|</div>|</li>|</tr>", " \n ")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("https?://\\S+", "") 
                .trim();
        return s;
    }

    private String callMyMemoryApi(String text, String from, String to) {
        try {
            String truncated = text.length() > 500 ? text.substring(0, 500) : text;
            String encoded = URLEncoder.encode(truncated, "UTF-8");
            String urlStr = "https://api.mymemory.translated.net/get?q=" + encoded + "&langpair=" + from + "|" + to;
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000); conn.setReadTimeout(8000);
            if (conn.getResponseCode() != 200) return null;
            InputStream is = conn.getInputStream();
            byte[] buf = new byte[4096];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = is.read(buf)) != -1) sb.append(new String(buf, 0, n, "UTF-8"));
            is.close(); conn.disconnect();
            JSONObject json = new JSONObject(sb.toString());
            return json.getJSONObject("responseData").getString("translatedText");
        } catch (Exception e) { return null; }
    }

    // ─── Copy content ─────────────────────────────────────────────────────────
    private void copyNoteContent() {
        executor.execute(() -> {
            Note note = AppDatabase.getInstance(getApplicationContext()).noteDao().getNoteById(noteId);
            if (note == null) return;
            String plain = htmlToCleanText(note.content != null ? note.content : "");
            String full = (note.title != null ? note.title + "\n" : "") + plain;
            runOnUiThread(() -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                android.content.ClipData cd = android.content.ClipData.newPlainText("note", full);
                cm.setPrimaryClip(cd);
                Toast.makeText(this, "✅ Đã sao chép nội dung", Toast.LENGTH_SHORT).show();
            });
        });
    }

    // ─── Delete ───────────────────────────────────────────────────────────────
    private void deleteNote() {
        if (noteId == -1) return;
        new AlertDialog.Builder(this)
                .setTitle("Xóa Ghi Chú")
                .setMessage("Chuyển ghi chú này vào thùng rác?")
                .setPositiveButton("Xóa", (dialog, which) -> executor.execute(() -> {
                    AppDatabase.getInstance(getApplicationContext()).noteDao().softDeleteNote(noteId, System.currentTimeMillis());
                    runOnUiThread(() -> { Toast.makeText(this, "✅ Đã chuyển vào thùng rác", Toast.LENGTH_SHORT).show(); finish(); });
                }))
                .setNegativeButton("Hủy", null).show();
    }

    // ─── Export PDF (Fixed: WebView must be in hierarchy) ─────────────────────
    private void exportNoteToPdf() {
        if (noteId == -1) return;
        executor.execute(() -> {
            Note note = AppDatabase.getInstance(getApplicationContext()).noteDao().getNoteById(noteId);
            if (note == null) return;
            String title = note.title != null ? note.title : "Ghi chú";
            String content = note.content != null ? note.content : "";
            String created = note.createdAt != null ? note.createdAt : "";
            String html = "<html><head><style>"
                    + "body{font-family:sans-serif;padding:24px;line-height:1.7;color:#111}"
                    + "h1{color:#1a1a2e;margin-bottom:4px;font-size:22px}"
                    + "p.date{color:#888;font-size:13px;margin-top:0;margin-bottom:20px;border-bottom:1px solid #eee;padding-bottom:10px}"
                    + "img{max-width:100%;height:auto;border-radius:8px;margin:8px 0}"
                    + "table{width:100%;border-collapse:collapse;margin:12px 0}"
                    + "th,td{border:1px solid #ddd;padding:8px;text-align:left;}"
                    + "tr:nth-child(even){background:#f9f9f9}"
                    + ".app-audio-record{display:none}"
                    + "a{color:#7C3AED}"
                    + "</style></head><body>"
                    + "<h1>" + title + "</h1>"
                    + "<p class='date'>📅 " + created + "</p>"
                    + content + "</body></html>";
            if (!isDestroyed()) {
                runOnUiThread(() -> {
                    // FIX: must add WebView to the real view hierarchy for print to work
                    android.widget.FrameLayout root = (android.widget.FrameLayout) getWindow().getDecorView();
                    WebView pdfView = new WebView(this);
                    pdfView.setVisibility(View.INVISIBLE);
                    android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
                    root.addView(pdfView, lp);
                    pdfView.getSettings().setJavaScriptEnabled(false);
                    pdfView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
                    final String jobName = title.replaceAll("[^a-zA-Z0-9_\\-]", "_");
                    pdfView.setWebViewClient(new android.webkit.WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) {
                            try {
                                android.print.PrintManager pm =
                                        (android.print.PrintManager) getSystemService(PRINT_SERVICE);
                                if (pm != null) {
                                    pm.print(jobName,
                                            view.createPrintDocumentAdapter(jobName),
                                            new android.print.PrintAttributes.Builder().build());
                                }
                            } catch (Exception e) {
                                Toast.makeText(NoteDetailActivity.this,
                                        "Lỗi xuất PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            } finally {
                                // Remove the invisible WebView after handing off to print
                                root.postDelayed(() -> root.removeView(pdfView), 3000);
                            }
                        }
                    });
                });
            }
        });
    }


    private int dp(int val) {
        return (int)(val * getResources().getDisplayMetrics().density);
    }
}