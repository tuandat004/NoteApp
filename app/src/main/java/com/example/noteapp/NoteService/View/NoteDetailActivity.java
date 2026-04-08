package com.example.noteapp.NoteService.View;

import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Color;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.R;
import com.example.noteapp.ReminderService.Entity.Reminder;
import com.example.noteapp.TagService.Entity.Tag;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoteDetailActivity extends AppCompatActivity {

    private TextView txtBack, txtHeaderTitle, txtTitle, txtTags, txtCreatedAt,
                     txtSubtitle, txtReminder, txtLink;
    private WebView webViewContent;
    private ImageView imgNote;
    private Button btnEdit, btnDelete, btnExportPdf;
    private int noteId = -1;
    private static final int REQUEST_EDIT = 1001;

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

        txtBack.setOnClickListener(v -> finish());

        noteId = getIntent().getIntExtra("note_id", -1);

        btnEdit.setVisibility(View.VISIBLE);
        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateNoteActivity.class);
            intent.putExtra("note_id", noteId);
            startActivityForResult(intent, REQUEST_EDIT);
        });

        btnDelete.setOnClickListener(v -> deleteNote());
        
        btnExportPdf.setOnClickListener(v -> exportNoteToPdf());

        if (noteId == -1) {
            Toast.makeText(this, "Không tìm thấy ghi chú", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadNoteDetail(noteId);
    }

    private void deleteNote() {
        if (noteId == -1) return;
        new android.app.AlertDialog.Builder(this)
            .setTitle("Xóa Ghi Chú")
            .setMessage("Bạn có chắc muốn chuyển ghi chú này vào thùng rác?")
            .setPositiveButton("Xóa", (dialog, which) -> {
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                    db.noteDao().softDeleteNote(noteId, System.currentTimeMillis());
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Đã chuyển vào thùng rác", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                });
            })
            .setNegativeButton("Hủy", null)
            .show();
    }

    private void exportNoteToPdf() {
        if (noteId == -1) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());
            Note note = db.noteDao().getNoteById(noteId);
            if (note == null) return;
            
            String title = note.title != null ? note.title : "Ghi chú";
            String createdAt = note.createdAt != null ? note.createdAt : "";
            // Keep images in the print HTML if possible
            String content = note.content != null ? note.content : "";
            
            String html = "<html><head><style>" +
                "body { font-family: sans-serif; padding: 20px; line-height: 1.6; color: #111; }" +
                "h1 { color: #333; margin-bottom: 5px; }" +
                "p.date { color: #888; font-size: 14px; margin-top: 0px; margin-bottom: 20px; }" +
                "img { max-width: 100%; height: auto; }" +
                "table { width: 100%; border-collapse: collapse; margin-bottom: 20px; }" +
                "th, td { border: 1px solid #ddd; padding: 8px; }" +
                "</style></head><body>" +
                "<h1>" + title + "</h1>" +
                "<p class='date'>" + createdAt + "</p>" +
                content +
                "</body></html>";
                
            runOnUiThread(() -> {
                WebView pdfWebView = new WebView(this);
                // Enable settings to load images if needed
                pdfWebView.getSettings().setJavaScriptEnabled(true);
                pdfWebView.getSettings().setLoadWithOverviewMode(true);
                pdfWebView.getSettings().setUseWideViewPort(true);
                
                pdfWebView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
                pdfWebView.setWebViewClient(new android.webkit.WebViewClient() {
                    @Override
                    public void onPageFinished(WebView view, String url) {
                        android.print.PrintManager printManager = (android.print.PrintManager) getSystemService(android.content.Context.PRINT_SERVICE);
                        String jobName = title.replaceAll("[^a-zA-Z0-9_-]", "_") + "_Document";
                        android.print.PrintDocumentAdapter printAdapter = view.createPrintDocumentAdapter(jobName);
                        printManager.print(jobName, printAdapter, new android.print.PrintAttributes.Builder().build());
                    }
                });
            });
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT) {
            // Reload lại nội dung sau khi chỉnh sửa
            loadNoteDetail(noteId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Đảm bảo reload khi quay lại từ CreateNoteActivity
        if (noteId != -1) {
            loadNoteDetail(noteId);
        }
    }

    private void loadNoteDetail(int noteId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(getApplicationContext());

            Note note           = db.noteDao().getNoteById(noteId);
            List<Tag> tags      = db.tagDao().getTagsByNoteId(noteId);
            Reminder reminder   = db.reminderDao().getReminderByNoteId(noteId);

            runOnUiThread(() -> {
                if (note == null) {
                    Toast.makeText(this, "Ghi chú không tồn tại", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                String title     = note.title     != null ? note.title     : "";
                String subtitle  = note.subtitle  != null ? note.subtitle  : "";
                String content   = note.content   != null ? note.content   : "";
                String createdAt = note.createdAt != null ? note.createdAt : "";

                txtHeaderTitle.setText(title.isEmpty() ? "Ghi chú" : title);
                txtTitle.setText(title);
                txtCreatedAt.setText(createdAt);

                // Subtitle
                if (subtitle.isEmpty()) {
                    txtSubtitle.setVisibility(View.GONE);
                } else {
                    txtSubtitle.setVisibility(View.VISIBLE);
                    txtSubtitle.setText(subtitle);
                }

                // Apply Note Background Color
                if (note.color != null && !note.color.isEmpty()) {
                    try {
                        int parsedColor = Color.parseColor(note.color);
                        boolean isNightMode = (getResources().getConfiguration().uiMode & 
                                               android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                                              == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                        if (isNightMode) {
                            float[] hsl = new float[3];
                            androidx.core.graphics.ColorUtils.colorToHSL(parsedColor, hsl);
                            hsl[1] *= 0.4f; // Reduce saturation by 60%
                            hsl[2] = 0.15f + (hsl[2] * 0.1f); // Make very dark
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

                // Hide external image viewer as images are embedded in WebView
                imgNote.setVisibility(View.GONE);
                
                // Content (Render in WebView)
                if (!content.isEmpty()) {
                    String styledHtml = "<html><head>" +
                        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\" />" +
                        "<style>" +
                        "body { font-family: sans-serif; font-size: 16px; color: #111111; line-height: 1.6; word-wrap: break-word; white-space: pre-wrap; }" +
                        "img { max-width: 100%; height: auto; }" +
                        "table { width: 100%; border-collapse: collapse; margin-top: 10px; margin-bottom: 10px; border: 1px solid #666666; }" +
                        "th, td { border: 1px solid #666666; padding: 10px; text-align: left; }" +
                        "tr:nth-child(even) { background-color: #F9F9F9; }" +
                        "ul, ol { padding-left: 20px; }" +
                        "</style></head><body style=\"margin:0; padding:0;\">" + 
                        content + "</body></html>";
                    webViewContent.loadDataWithBaseURL(null, styledHtml, "text/html", "utf-8", null);
                    webViewContent.setBackgroundColor(Color.TRANSPARENT);
                    webViewContent.setVisibility(View.VISIBLE);
                } else {
                    webViewContent.setVisibility(View.GONE);
                }

                // Reminder
                if (reminder != null && reminder.reminderTime != null && !reminder.reminderTime.isEmpty()) {
                    txtReminder.setVisibility(View.VISIBLE);
                    txtReminder.setText("⏰ Nhắc nhở: " + reminder.reminderTime
                        + (reminder.isDone == 1 ? " ✓" : ""));
                } else {
                    txtReminder.setVisibility(View.GONE);
                }

                // Hide external link viewer as links are embedded in WebView
                txtLink.setVisibility(View.GONE);
            });
        });
    }

    private String extractFirstImageSrc(String html) {
        if (html == null) return null;
        Pattern p = Pattern.compile("<img[^>]+src\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>");
        Matcher m = p.matcher(html);
        return m.find() ? m.group(1) : null;
    }

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