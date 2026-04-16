package com.example.noteapp.CalendarService.View;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.noteapp.CalendarService.DAO.CalendarNoteDao;
import com.example.noteapp.CalendarService.Entity.CalendarNote;
import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.R;
import com.example.noteapp.ReminderService.Worker.ReminderReceiver;

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

public class CalendarActivity extends AppCompatActivity {

    private RecyclerView rvCalendar;
    private TextView tvMonthYear;
    private ImageButton btnPrevMonth, btnNextMonth, btnBack;

    private final Calendar currentMonth = Calendar.getInstance();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int sessionUserId;
    private CalendarNoteDao dao;

    // Days in grid (42 cells = 6 rows × 7 cols)
    private final List<CalendarDay> days = new ArrayList<>();
    private final Set<String> datesWithNotes = new HashSet<>();
    private CalendarDayAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        SharedPreferences prefs = getSharedPreferences("USER", MODE_PRIVATE);
        sessionUserId = prefs.getInt("user_id", -1);
        dao = AppDatabase.getInstance(this).calendarNoteDao();

        rvCalendar = findViewById(R.id.rvCalendar);
        tvMonthYear = findViewById(R.id.tvMonthYear);
        btnPrevMonth = findViewById(R.id.btnPrevMonth);
        btnNextMonth = findViewById(R.id.btnNextMonth);
        btnBack = findViewById(R.id.btnBack);

        btnBack.setOnClickListener(v -> finish());
        btnPrevMonth.setOnClickListener(v -> { currentMonth.add(Calendar.MONTH, -1); loadMonth(); });
        btnNextMonth.setOnClickListener(v -> { currentMonth.add(Calendar.MONTH, 1); loadMonth(); });

        adapter = new CalendarDayAdapter(days, this::onDayClick);
        rvCalendar.setLayoutManager(new GridLayoutManager(this, 7));
        rvCalendar.setAdapter(adapter);

        loadMonth();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMonth();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    // ── Load month grid ──────────────────────────────────────────────────────

    private void loadMonth() {
        int year = currentMonth.get(Calendar.YEAR);
        int month = currentMonth.get(Calendar.MONTH);
        String monthPrefix = String.format(Locale.getDefault(), "%04d-%02d", year, month + 1);
        String displayText = String.format(Locale.getDefault(),
                "Tháng %d, %d", month + 1, year);
        tvMonthYear.setText(displayText);

        executor.execute(() -> {
            List<String> marked = dao.getDatesWithNotes(sessionUserId, monthPrefix);
            if (!isDestroyed()) {
                runOnUiThread(() -> {
                    datesWithNotes.clear();
                    datesWithNotes.addAll(marked);
                    buildDays(year, month);
                    adapter.notifyDataSetChanged();
                });
            }
        });
    }

    private void buildDays(int year, int month) {
        days.clear();
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1);

        // Day of week of first day (Mon=0 ... Sun=6)
        int firstDow = cal.get(Calendar.DAY_OF_WEEK);
        int offset = (firstDow == Calendar.SUNDAY) ? 6 : firstDow - Calendar.MONDAY;
        for (int i = 0; i < offset; i++) days.add(new CalendarDay(0, false, false, ""));

        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        Calendar today = Calendar.getInstance();
        for (int d = 1; d <= maxDay; d++) {
            String dateStr = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, d);
            boolean isToday = (today.get(Calendar.YEAR) == year
                    && today.get(Calendar.MONTH) == month
                    && today.get(Calendar.DAY_OF_MONTH) == d);
            boolean hasNote = datesWithNotes.contains(dateStr);
            days.add(new CalendarDay(d, isToday, hasNote, dateStr));
        }

        // Pad to full rows
        while (days.size() % 7 != 0) days.add(new CalendarDay(0, false, false, ""));
    }

    // ── Day click ────────────────────────────────────────────────────────────

    private void onDayClick(CalendarDay day) {
        if (day.dayNumber == 0) return;
        showDayDialog(day.dateStr);
    }

    private void showDayDialog(String dateStr) {
        executor.execute(() -> {
            List<CalendarNote> notes = dao.getNotesByDate(sessionUserId, dateStr);
            if (!isDestroyed()) {
                runOnUiThread(() -> buildDayDialog(dateStr, notes));
            }
        });
    }

    private void buildDayDialog(String dateStr, List<CalendarNote> notes) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        String[] parts = dateStr.split("-");
        b.setTitle(String.format("📅 Ngày %s/%s/%s", parts[2], parts[1], parts[0]));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(4));

        if (notes.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Chưa có ghi chú nào trong ngày này.");
            empty.setTextColor(0xFF9CA3AF);
            empty.setPadding(0, dp(8), 0, dp(12));
            root.addView(empty);
        } else {
            for (CalendarNote n : notes) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(12), dp(10), dp(12), dp(10));
                row.setBackgroundResource(R.drawable.bg_input);
                LinearLayout.LayoutParams rLP = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rLP.setMargins(0, 0, 0, dp(8));
                row.setLayoutParams(rLP);

                LinearLayout textCol = new LinearLayout(this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                TextView tvTitle = new TextView(this);
                tvTitle.setText(n.title != null && !n.title.isEmpty() ? n.title : "(Không tiêu đề)");
                tvTitle.setTextColor(0xFF1E1B4B);
                tvTitle.setTextSize(14f);
                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                textCol.addView(tvTitle);

                if (n.reminderTime != null && !n.reminderTime.isEmpty()) {
                    TextView tvRemind = new TextView(this);
                    tvRemind.setText("⏰ " + n.reminderTime);
                    tvRemind.setTextColor(0xFF7C3AED);
                    tvRemind.setTextSize(12f);
                    textCol.addView(tvRemind);
                }

                row.addView(textCol);

                TextView btnEdit = new TextView(this);
                btnEdit.setText("✏");
                btnEdit.setTextSize(18f);
                btnEdit.setPadding(dp(8), 0, dp(4), 0);
                btnEdit.setOnClickListener(v -> showEditNoteDialog(dateStr, n));
                row.addView(btnEdit);

                root.addView(row);
            }
        }

        b.setView(root);
        b.setPositiveButton("➕ Thêm ghi chú", (d, w) -> showEditNoteDialog(dateStr, null));
        b.setNegativeButton("Đóng", null);
        b.show();
    }

    // ── Edit / Create note dialog ─────────────────────────────────────────────

    private void showEditNoteDialog(String dateStr, CalendarNote existing) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle(existing == null ? "➕ Thêm ghi chú" : "✏️ Sửa ghi chú");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(12), dp(20), dp(4));

        EditText edtTitle = new EditText(this);
        edtTitle.setHint("Tiêu đề...");
        edtTitle.setTextSize(15f);
        edtTitle.setBackgroundResource(R.drawable.bg_input);
        edtTitle.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams edLP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        edLP.setMargins(0, 0, 0, dp(10));
        edtTitle.setLayoutParams(edLP);
        if (existing != null && existing.title != null) edtTitle.setText(existing.title);
        root.addView(edtTitle);

        EditText edtContent = new EditText(this);
        edtContent.setHint("Nội dung ghi chú...");
        edtContent.setTextSize(14f);
        edtContent.setMinHeight(dp(100));
        edtContent.setGravity(android.view.Gravity.TOP);
        edtContent.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        edtContent.setBackgroundResource(R.drawable.bg_input);
        edtContent.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams ecLP = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ecLP.setMargins(0, 0, 0, dp(10));
        edtContent.setLayoutParams(ecLP);
        if (existing != null && existing.content != null) edtContent.setText(existing.content);
        root.addView(edtContent);

        // Reminder section
        String[] chosenReminder = {existing != null ? existing.reminderTime : null};
        TextView tvReminderBtn = new TextView(this);
        tvReminderBtn.setPadding(dp(12), dp(10), dp(12), dp(10));
        tvReminderBtn.setBackgroundResource(R.drawable.bg_action_chip);
        tvReminderBtn.setTextColor(0xFF7C3AED);
        tvReminderBtn.setTextSize(14f);
        updateReminderBtnText(tvReminderBtn, chosenReminder[0]);
        tvReminderBtn.setOnClickListener(v -> showReminderPicker(dateStr, tvReminderBtn, chosenReminder));
        root.addView(tvReminderBtn);

        // Delete reminder (if has one)
        if (existing != null && existing.reminderTime != null && !existing.reminderTime.isEmpty()) {
            TextView tvDelReminder = new TextView(this);
            tvDelReminder.setText("🗑 Xóa nhắc nhở");
            tvDelReminder.setTextColor(0xFFEF4444);
            tvDelReminder.setTextSize(13f);
            tvDelReminder.setPadding(0, dp(6), 0, 0);
            tvDelReminder.setOnClickListener(v -> {
                chosenReminder[0] = null;
                updateReminderBtnText(tvReminderBtn, null);
                Toast.makeText(this, "Đã xóa nhắc nhở", Toast.LENGTH_SHORT).show();
            });
            root.addView(tvDelReminder);
        }

        b.setView(root);

        b.setPositiveButton("💾 Lưu", (d, w) -> {
            String title = edtTitle.getText().toString().trim();
            String content = edtContent.getText().toString().trim();
            if (title.isEmpty() && content.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập tiêu đề hoặc nội dung", Toast.LENGTH_SHORT).show();
                return;
            }
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            CalendarNote note = existing != null ? existing : new CalendarNote();
            note.userId = sessionUserId;
            note.dateStr = dateStr;
            note.title = title;
            note.content = content;
            note.reminderTime = chosenReminder[0];
            note.updatedAt = now;
            if (existing == null) note.createdAt = now;
            executor.execute(() -> {
                if (existing == null) {
                    long id = dao.insert(note);
                    note.id = (int) id;
                } else {
                    dao.update(note);
                }
                // Schedule alarm if reminder set
                if (note.reminderTime != null && !note.reminderTime.isEmpty()) {
                    scheduleReminder(note);
                }
                if (!isDestroyed()) runOnUiThread(this::loadMonth);
            });
        });

        if (existing != null) {
            b.setNeutralButton("🗑 Xóa note", (d, w) -> {
                executor.execute(() -> {
                    dao.deleteById(existing.id);
                    if (!isDestroyed()) runOnUiThread(() -> {
                        Toast.makeText(this, "Đã xóa ghi chú", Toast.LENGTH_SHORT).show();
                        loadMonth();
                    });
                });
            });
        }

        b.setNegativeButton("Hủy", null);
        b.show();
    }

    private void updateReminderBtnText(TextView btn, String reminderTime) {
        if (reminderTime == null || reminderTime.isEmpty()) {
            btn.setText("🔔 Đặt nhắc nhở");
        } else {
            btn.setText("⏰ Nhắc: " + reminderTime);
        }
    }

    private void showReminderPicker(String dateStr, TextView btn, String[] chosenReminder) {
        String[] parts = dateStr.split("-");
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]) - 1;
        int day = Integer.parseInt(parts[2]);
        Calendar defCal = Calendar.getInstance();
        defCal.set(year, month, day);

        new DatePickerDialog(this, (view, y, m, d) ->
                new TimePickerDialog(this, (v, h, min) -> {
                    Calendar cal = Calendar.getInstance();
                    cal.set(y, m, d, h, min, 0);
                    if (cal.getTimeInMillis() < System.currentTimeMillis()) {
                        Toast.makeText(this, "Chọn thời gian trong tương lai!", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String reminderStr = String.format(Locale.getDefault(),
                            "%04d-%02d-%02d %02d:%02d", y, m + 1, d, h, min);
                    chosenReminder[0] = reminderStr;
                    updateReminderBtnText(btn, reminderStr);
                }, defCal.get(Calendar.HOUR_OF_DAY), defCal.get(Calendar.MINUTE), true).show(),
                defCal.get(Calendar.YEAR), defCal.get(Calendar.MONTH), defCal.get(Calendar.DAY_OF_MONTH)).show();
    }

    @SuppressLint("ScheduleExactAlarm")
    private void scheduleReminder(CalendarNote note) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date dt = sdf.parse(note.reminderTime);
            if (dt == null || dt.getTime() < System.currentTimeMillis()) return;
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(this, ReminderReceiver.class);
            intent.putExtra(ReminderReceiver.KEY_NOTE_ID, note.id + 100000);
            intent.putExtra(ReminderReceiver.KEY_TITLE, note.title != null ? note.title : "Lịch nhắc nhở");
            intent.putExtra(ReminderReceiver.KEY_MESSAGE, "Nhắc nhở từ lịch: " + note.dateStr);
            intent.putExtra(ReminderReceiver.KEY_SUBTITLE, note.dateStr);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                    this, note.id + 100000, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            if (am != null) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms())
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dt.getTime(), pi);
                    else
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dt.getTime(), pi);
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dt.getTime(), pi);
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi đặt nhắc nhở: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private int dp(int val) {
        return (int)(val * getResources().getDisplayMetrics().density);
    }

    // ── Data model ───────────────────────────────────────────────────────────

    public static class CalendarDay {
        public final int dayNumber;
        public final boolean isToday;
        public final boolean hasNote;
        public final String dateStr;
        CalendarDay(int dayNumber, boolean isToday, boolean hasNote, String dateStr) {
            this.dayNumber = dayNumber; this.isToday = isToday;
            this.hasNote = hasNote; this.dateStr = dateStr;
        }
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    interface OnDayClick { void onClick(CalendarDay day); }

    static class CalendarDayAdapter extends RecyclerView.Adapter<CalendarDayAdapter.VH> {
        private final List<CalendarDay> data;
        private final OnDayClick listener;
        CalendarDayAdapter(List<CalendarDay> data, OnDayClick listener) {
            this.data = data; this.listener = listener;
        }
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_calendar_day, parent, false);
            // Make it square per column
            v.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int)(parent.getResources().getDisplayMetrics().density * 54)));
            return new VH(v);
        }
        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            CalendarDay day = data.get(pos);
            if (day.dayNumber == 0) {
                h.tvDay.setText("");
                h.bgToday.setVisibility(View.GONE);
                h.dot.setVisibility(View.GONE);
                h.itemView.setOnClickListener(null);
                return;
            }
            h.tvDay.setText(String.valueOf(day.dayNumber));
            h.bgToday.setVisibility(day.isToday ? View.VISIBLE : View.GONE);
            h.tvDay.setTextColor(day.isToday
                    ? Color.WHITE
                    : h.itemView.getContext().getColor(R.color.text_primary_app));
            h.dot.setVisibility(day.hasNote ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> listener.onClick(day));
        }
        @Override public int getItemCount() { return data.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView tvDay; View bgToday, dot;
            VH(@NonNull View v) {
                super(v);
                tvDay = v.findViewById(R.id.tvDay);
                bgToday = v.findViewById(R.id.bgToday);
                dot = v.findViewById(R.id.dotIndicator);
            }
        }
    }
}
