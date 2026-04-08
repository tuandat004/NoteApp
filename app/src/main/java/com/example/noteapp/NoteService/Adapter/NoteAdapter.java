package com.example.noteapp.NoteService.Adapter;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.BackgroundColorSpan;
import android.graphics.Color;
import androidx.core.graphics.ColorUtils;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.noteapp.NoteService.Entity.Note;
import com.example.noteapp.NoteService.View.NoteDetailActivity;
import com.example.noteapp.R;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private Context context;
    private List<Note> list = new ArrayList<>();
    private String highlightKeyword = "";

    public NoteAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<Note> notes) {
        this.list = notes;
        notifyDataSetChanged();
    }

    public void setHighlightKeyword(String keyword) {
        this.highlightKeyword = keyword == null ? "" : keyword.trim();
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    public interface OnNoteClickListener {
        void onNoteClick(Note note);
    }

    public interface OnNoteLongClickListener {
        void onNoteLongClick(Note note);
    }

    private OnNoteClickListener noteClickListener;
    private OnNoteLongClickListener noteLongClickListener;

    public void setOnNoteClickListener(OnNoteClickListener listener) {
        this.noteClickListener = listener;
    }

    public void setOnNoteLongClickListener(OnNoteLongClickListener listener) {
        this.noteLongClickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = list.get(position);

        // Background Color Binding
        if (note.color != null && !note.color.isEmpty()) {
            try {
                int parsedColor = Color.parseColor(note.color);
                int fadedColor = getFadedColorForDarkMode(parsedColor);
                holder.cardView.setCardBackgroundColor(fadedColor);
            } catch (Exception e) {
                holder.cardView.setCardBackgroundColor(getSurfaceColor());
            }
        } else {
            holder.cardView.setCardBackgroundColor(getSurfaceColor());
        }

        // 1. Title
        String rawTitle = note.title != null && !note.title.isEmpty() ? note.title : "Không có tiêu đề";
        holder.txtTitle.setText(highlight(rawTitle, highlightKeyword));

        // 2. Time relative format
        holder.txtTime.setText(getRelativeTime(note.createdAt));

        // 3. Extract content plain text (strip img/link tags for clean preview)
        if (note.content != null && !note.content.isEmpty()) {
            String plain = extractPlainTextFast(note.content);
            holder.txtContent.setText(highlight(plain, highlightKeyword));
        } else {
            holder.txtContent.setText("");
        }

        // 4. Extract first image (supports base64 & URL)
        String firstImageSrc = extractFirstImageFast(note.content);
        if (firstImageSrc != null) {
            holder.imgThumb.setVisibility(View.VISIBLE);
            if (firstImageSrc.startsWith("data:")) {
                try {
                    String b64 = firstImageSrc.substring(firstImageSrc.indexOf(",") + 1);
                    byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                    Glide.with(context)
                        .asBitmap()
                        .load(bytes)
                        .centerCrop()
                        .into(holder.imgThumb);
                } catch (Exception e) {
                    holder.imgThumb.setVisibility(View.GONE);
                }
            } else {
                Glide.with(context).load(firstImageSrc).centerCrop().into(holder.imgThumb);
            }
        } else {
            holder.imgThumb.setVisibility(View.GONE);
        }

        // 5. Extract first link
        String firstLink = extractFirstLinkFast(note.content);
        if (firstLink != null) {
            holder.txtLink.setVisibility(View.VISIBLE);
            String display = firstLink.replaceAll("https?://", "").replaceAll("/.*$", "");
            holder.txtLink.setText("🔗 " + display);
        } else {
            holder.txtLink.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (noteClickListener != null) {
                noteClickListener.onNoteClick(note);
            } else {
                Intent intent = new Intent(context, NoteDetailActivity.class);
                intent.putExtra("note_id", note.noteId);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (noteLongClickListener != null) {
                noteLongClickListener.onNoteLongClick(note);
                return true;
            }
            return false;
        });
    }

    private int getSurfaceColor() {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true);
        return typedValue.data;
    }

    private int getFadedColorForDarkMode(int color) {
        boolean isNightMode = (context.getResources().getConfiguration().uiMode & 
                               android.content.res.Configuration.UI_MODE_NIGHT_MASK) 
                              == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (!isNightMode) return color;
        
        // Convert to HSL, reduce lightness and saturation for dark mode
        float[] hsl = new float[3];
        androidx.core.graphics.ColorUtils.colorToHSL(color, hsl);
        hsl[1] *= 0.4f; // Reduce saturation by 60%
        hsl[2] = 0.15f + (hsl[2] * 0.1f); // Make very dark, keeping a hint of original brightness
        return androidx.core.graphics.ColorUtils.HSLToColor(hsl);
    }

    @Override
    public int getItemCount() {
        return list == null ? 0 : list.size();
    }

    private CharSequence highlight(String original, String keyword) {
        if (original == null) return "";
        if (keyword == null || keyword.isEmpty()) return original;
        
        SpannableString spannable = new SpannableString(original);
        String lowerOriginal = removeAccentsLocal(original.toLowerCase());
        String lowerKeyword = removeAccentsLocal(keyword.toLowerCase());
        
        int index = lowerOriginal.indexOf(lowerKeyword);
        while (index >= 0) {
            int endIndex = index + keyword.length();
            if (endIndex <= original.length()) {
                spannable.setSpan(new BackgroundColorSpan(Color.parseColor("#F3D986")), index, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            index = lowerOriginal.indexOf(lowerKeyword, index + 1);
        }
        return spannable;
    }

    private String removeAccentsLocal(String s) {
        if (s == null) return "";
        String normalized = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "").replace("đ", "d").replace("Đ", "d");
    }

    private String getRelativeTime(String dateStr) {
        if (dateStr == null) return "Vừa xong";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(dateStr);
            long diff = System.currentTimeMillis() - date.getTime();
            
            long days = TimeUnit.MILLISECONDS.toDays(diff);
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);

            if (days > 7) return sdf.format(date);
            if (days > 1) return days + " ngày trước";
            if (days == 1) return "Hôm qua";
            if (hours > 0) return hours + " giờ trước";
            if (minutes > 0) return minutes + " phút trước";
            return "Vừa xong";
        } catch (ParseException e) {
            return dateStr;
        }
    }

    private String extractPlainTextFast(String html) {
        if (html == null) return "";
        // X\u00f3a to\u00e0n b\u1ed9 th\u1ebb <a> v\u00e0 n\u1ed9i dung b\u00ean trong (v\u00ec ta \u0111\u00e3 hi\u1ec3n th\u1ecb link \u1edf widget ri\u00eang)
        String noLink = html.replaceAll("(?i)<a[^>]*>.*?</a>", "");
        // X\u00f3a to\u00e0n b\u1ed9 th\u1ebb <img>
        String noImg = noLink.replaceAll("(?i)<img[^>]*>", "");
        // C\u00e1c th\u1ebb br, div, p thay b\u1eb1ng d\u1ea5u c\u00e1ch \u0111\u1ec3 line break
        String spaced = noImg.replaceAll("(?i)<br\\s*/?>|</p>|</div>", " ");

        StringBuilder sb = new StringBuilder();
        boolean inTag = false;
        int textCount = 0;
        for (int i = 0; i < spaced.length(); i++) {
            char c = spaced.charAt(i);
            if (c == '<') {
                inTag = true;
            } else if (c == '>') {
                inTag = false;
            } else if (!inTag) {
                sb.append(c);
                textCount++;
                if (textCount > 500) break; // preview only needs short text
            }
        }
        String result = sb.toString().trim().replaceAll("\\s+", " ");
        return result.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
    }

    private String extractFirstImageFast(String html) {
        if (html == null) return null;
        int imgIdx = html.indexOf("<img");
        if (imgIdx == -1) return null;
        int srcIdx = html.indexOf("src=", imgIdx);
        if (srcIdx == -1 || srcIdx > imgIdx + 2000) return null;
        
        int quoteIdx1 = html.indexOf("\"", srcIdx);
        int quoteIdx2 = html.indexOf("'", srcIdx);
        if (quoteIdx1 == -1 && quoteIdx2 == -1) return null;
        
        int startQuote;
        if (quoteIdx1 != -1 && quoteIdx2 != -1) {
            startQuote = Math.min(quoteIdx1, quoteIdx2);
        } else {
            startQuote = quoteIdx1 != -1 ? quoteIdx1 : quoteIdx2;
        }
        
        int endQuote = html.indexOf((quoteIdx1 == startQuote ? "\"" : "'"), startQuote + 1);
        if (endQuote == -1) return null;
        return html.substring(startQuote + 1, endQuote);
    }

    private String extractFirstLinkFast(String html) {
        if (html == null) return null;
        int aIdx = html.indexOf("<a");
        if (aIdx == -1) return null;
        int hrefIdx = html.indexOf("href=", aIdx);
        if (hrefIdx == -1 || hrefIdx > aIdx + 2000) return null;
        
        int quoteIdx1 = html.indexOf("\"", hrefIdx);
        int quoteIdx2 = html.indexOf("'", hrefIdx);
        if (quoteIdx1 == -1 && quoteIdx2 == -1) return null;
        
        int startQuote;
        if (quoteIdx1 != -1 && quoteIdx2 != -1) {
            startQuote = Math.min(quoteIdx1, quoteIdx2);
        } else {
            startQuote = quoteIdx1 != -1 ? quoteIdx1 : quoteIdx2;
        }
        
        int endQuote = html.indexOf((quoteIdx1 == startQuote ? "\"" : "'"), startQuote + 1);
        if (endQuote == -1) return null;
        return html.substring(startQuote + 1, endQuote);
    }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView txtTitle, txtTime, txtContent, txtLink;
        ImageView imgThumb;
        com.google.android.material.card.MaterialCardView cardView;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            txtTitle = itemView.findViewById(R.id.txtTitle);
            txtTime = itemView.findViewById(R.id.txtTime);
            txtContent = itemView.findViewById(R.id.txtContent);
            txtLink = itemView.findViewById(R.id.txtLink);
            imgThumb = itemView.findViewById(R.id.imgThumb);
            cardView = (com.google.android.material.card.MaterialCardView) itemView;
        }
    }
}