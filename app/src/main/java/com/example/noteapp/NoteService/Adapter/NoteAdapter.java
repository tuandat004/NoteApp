package com.example.noteapp.NoteService.Adapter;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

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

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private final Context context;
    private List<Note> list = new ArrayList<>();
    private String highlightKeyword = "";

    public NoteAdapter(Context context) {
        this.context = context;
    }

    /** Dùng DiffUtil để chỉ redraw những item thực sự thay đổi → giảm lag */
    public void setData(List<Note> newList) {
        if (newList == null) newList = new ArrayList<>();
        final List<Note> oldList = this.list;
        final List<Note> finalNewList = newList;
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return oldList.size(); }
            @Override public int getNewListSize() { return finalNewList.size(); }
            @Override public boolean areItemsTheSame(int oldPos, int newPos) {
                return oldList.get(oldPos).noteId == finalNewList.get(newPos).noteId;
            }
            @Override public boolean areContentsTheSame(int oldPos, int newPos) {
                Note o = oldList.get(oldPos), n = finalNewList.get(newPos);
                return safeEq(o.title, n.title)
                        && safeEq(o.content, n.content)
                        && safeEq(o.color, n.color)
                        && o.isLocked == n.isLocked
                        && safeEq(o.updatedAt, n.updatedAt);
            }
            private boolean safeEq(String a, String b) {
                if (a == null && b == null) return true;
                if (a == null || b == null) return false;
                return a.equals(b);
            }
        });
        this.list = finalNewList;
        result.dispatchUpdatesTo(this);
    }

    public void setHighlightKeyword(String keyword) {
        this.highlightKeyword = keyword == null ? "" : keyword.trim();
    }

    // ─── Interfaces ───────────────────────────────────────────────────────────

    public interface OnNoteClickListener { void onNoteClick(Note note); }
    public interface OnNoteLongClickListener { void onNoteLongClick(Note note); }

    private OnNoteClickListener noteClickListener;
    private OnNoteLongClickListener noteLongClickListener;

    public void setOnNoteClickListener(OnNoteClickListener l) { this.noteClickListener = l; }
    public void setOnNoteLongClickListener(OnNoteLongClickListener l) { this.noteLongClickListener = l; }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note note = list.get(position);

        // Background Color + accent bar
        int noteColor = -1;
        if (note.color != null && !note.color.isEmpty()) {
            try {
                noteColor = Color.parseColor(note.color);
                holder.cardView.setCardBackgroundColor(getFadedColorForDarkMode(noteColor));
            } catch (Exception e) {
                holder.cardView.setCardBackgroundColor(getSurfaceColor());
            }
        } else {
            holder.cardView.setCardBackgroundColor(getSurfaceColor());
        }
        // Accent bar: use a slightly darker shade of the note color
        if (holder.accentBar != null) {
            if (noteColor != -1) {
                float[] hsl = new float[3];
                ColorUtils.colorToHSL(noteColor, hsl);
                hsl[1] = Math.min(1f, hsl[1] + 0.2f);
                hsl[2] = Math.max(0f, hsl[2] - 0.15f);
                holder.accentBar.setBackgroundColor(ColorUtils.HSLToColor(hsl));
            } else {
                holder.accentBar.setBackgroundColor(0xFF7C3AED);
            }
        }

        // Title
        String rawTitle = (note.title != null && !note.title.isEmpty())
                ? note.title : "Không có tiêu đề";
        holder.tvTitle.setText(highlight(rawTitle, highlightKeyword));

        // Lock icon
        if (holder.imgLock != null) {
            holder.imgLock.setVisibility(note.isLocked == 1 ? View.VISIBLE : View.GONE);
        }

        // Time
        holder.tvTime.setText(getRelativeTime(note.updatedAt != null ? note.updatedAt : note.createdAt));

        // Content preview
        if (note.content != null && !note.content.isEmpty()) {
            String plain = extractPlainTextFast(note.content);
            holder.tvContent.setText(highlight(plain, highlightKeyword));
        } else {
            holder.tvContent.setText("");
        }

        // First link - show domain only
        String firstLink = extractFirstLinkFast(note.content);
        if (firstLink != null && holder.txtLink != null) {
            holder.txtLink.setVisibility(View.VISIBLE);
            String display = firstLink.replaceAll("https?://", "").replaceAll("/.*$", "");
            holder.txtLink.setText("🔗 " + display);
        } else if (holder.txtLink != null) {
            holder.txtLink.setVisibility(View.GONE);
        }

        // Click
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

        // Long click
        holder.itemView.setOnLongClickListener(v -> {
            if (noteLongClickListener != null) {
                noteLongClickListener.onNoteLongClick(note);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() { return list == null ? 0 : list.size(); }

    // ─── Color helpers ────────────────────────────────────────────────────────

    private int getSurfaceColor() {
        android.util.TypedValue tv = new android.util.TypedValue();
        context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorSurface, tv, true);
        return tv.data;
    }

    private int getFadedColorForDarkMode(int color) {
        boolean isNight = (context.getResources().getConfiguration().uiMode &
                android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (!isNight) return color;
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[1] *= 0.4f;
        hsl[2] = 0.15f + (hsl[2] * 0.1f);
        return ColorUtils.HSLToColor(hsl);
    }

    // ─── Text helpers ─────────────────────────────────────────────────────────

    private CharSequence highlight(String original, String keyword) {
        if (original == null) return "";
        if (keyword == null || keyword.isEmpty()) return original;
        SpannableString span = new SpannableString(original);
        String lowerOrig = removeAccentsLocal(original.toLowerCase());
        String lowerKw   = removeAccentsLocal(keyword.toLowerCase());
        int idx = lowerOrig.indexOf(lowerKw);
        while (idx >= 0) {
            int end = idx + keyword.length();
            if (end <= original.length()) {
                span.setSpan(new BackgroundColorSpan(Color.parseColor("#F3D986")),
                        idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            idx = lowerOrig.indexOf(lowerKw, idx + 1);
        }
        return span;
    }

    private String removeAccentsLocal(String s) {
        if (s == null) return "";
        String n = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD);
        return n.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace("đ", "d").replace("Đ", "d");
    }

    private String getRelativeTime(String dateStr) {
        if (dateStr == null) return "Vừa xong";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(dateStr);
            long diff = System.currentTimeMillis() - date.getTime();
            long days    = TimeUnit.MILLISECONDS.toDays(diff);
            long hours   = TimeUnit.MILLISECONDS.toHours(diff);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            if (days > 7)  return sdf.format(date);
            if (days > 1)  return days + " ngày trước";
            if (days == 1) return "Hôm qua";
            if (hours > 0) return hours + " giờ trước";
            if (minutes > 0) return minutes + " phút trước";
            return "Vừa xong";
        } catch (ParseException e) { return dateStr; }
    }

    private String extractPlainTextFast(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?i)<a[^>]*>.*?</a>", "")
                       .replaceAll("(?i)<img[^>]*>", "")
                       .replaceAll("(?i)<br\\s*/?>|</p>|</div>", " ");
        StringBuilder sb = new StringBuilder();
        boolean inTag = false;
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<')       inTag = true;
            else if (c == '>') inTag = false;
            else if (!inTag) {
                sb.append(c);
                if (++count > 500) break;
            }
        }
        return sb.toString().trim()
                .replaceAll("\\s+", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&")
                .replace("&lt;", "<").replace("&gt;", ">");
    }

    private String extractFirstImageFast(String html) {
        if (html == null) return null;
        int imgIdx = html.indexOf("<img");
        if (imgIdx == -1) return null;
        int srcIdx = html.indexOf("src=", imgIdx);
        if (srcIdx == -1 || srcIdx > imgIdx + 2000) return null;
        int q1 = html.indexOf("\"", srcIdx), q2 = html.indexOf("'", srcIdx);
        if (q1 == -1 && q2 == -1) return null;
        int sq = (q1 != -1 && q2 != -1) ? Math.min(q1, q2) : (q1 != -1 ? q1 : q2);
        int eq = html.indexOf((q1 == sq ? "\"" : "'"), sq + 1);
        return eq == -1 ? null : html.substring(sq + 1, eq);
    }

    private String extractFirstLinkFast(String html) {
        if (html == null) return null;
        int aIdx = html.indexOf("<a");
        if (aIdx == -1) return null;
        int hIdx = html.indexOf("href=", aIdx);
        if (hIdx == -1 || hIdx > aIdx + 2000) return null;
        int q1 = html.indexOf("\"", hIdx), q2 = html.indexOf("'", hIdx);
        if (q1 == -1 && q2 == -1) return null;
        int sq = (q1 != -1 && q2 != -1) ? Math.min(q1, q2) : (q1 != -1 ? q1 : q2);
        int eq = html.indexOf((q1 == sq ? "\"" : "'"), sq + 1);
        return eq == -1 ? null : html.substring(sq + 1, eq);
    }

    // ─── ViewHolder ───────────────────────────────────────────────────────────

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvTime, tvContent, txtLink;
        ImageView imgLock;
        View accentBar;
        androidx.cardview.widget.CardView cardView;

        NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle   = itemView.findViewById(R.id.tvTitle);
            tvTime    = itemView.findViewById(R.id.tvTime);
            tvContent = itemView.findViewById(R.id.tvContent);
            txtLink   = itemView.findViewById(R.id.txtLink);
            imgLock   = itemView.findViewById(R.id.imgLock);
            accentBar = itemView.findViewById(R.id.accentBar);
            cardView  = itemView.findViewById(R.id.cardNote);
        }
    }
}