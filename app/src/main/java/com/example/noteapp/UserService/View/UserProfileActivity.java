package com.example.noteapp.UserService.View;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import android.widget.Switch;

import com.bumptech.glide.Glide;
import com.example.noteapp.AccountService.View.LoginActivity;
import com.example.noteapp.Data.AppDatabase;
import com.example.noteapp.R;
import com.example.noteapp.UserService.DAO.UserDao;
import com.example.noteapp.UserService.Entity.User;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Executors;

import android.net.Uri;
import android.provider.MediaStore;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import de.hdodenhof.circleimageview.CircleImageView;

public class UserProfileActivity extends AppCompatActivity {

    private LinearLayout btnBack;
    private CircleImageView imgProfileAvatar;
    private TextView tvUsername;
    private EditText edtFullName, edtEmail, edtPassword;
    private Button btnSaveProfile, btnLogout;

    private UserDao userDao;
    private int sessionUserId;
    private String currentAvatarUri = null;
    private User currentUser = null;
    private android.widget.ImageView eyePassword;
    private boolean isShowPass = false;
    private Switch switchDarkMode;
    private final java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        // Copy ảnh vào internal storage → path bền vững, không bị revoke sau reinstall
                        String localPath = copyUriToInternalStorage(uri);
                        if (localPath != null) {
                            currentAvatarUri = localPath;
                            Glide.with(this).load(localPath)
                                    .skipMemoryCache(true)
                                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                    .error(android.R.drawable.ic_menu_myplaces)
                                    .into(imgProfileAvatar);
                        } else {
                            Toast.makeText(this, "Không thể tải ảnh", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
    );

    /** Copy ảnh từ content URI vào app's private files/avatars/ và trả về absolute path */
    private String copyUriToInternalStorage(Uri uri) {
        try {
            java.io.File avatarDir = new java.io.File(getFilesDir(), "avatars");
            if (!avatarDir.exists()) avatarDir.mkdirs();
            java.io.File dest = new java.io.File(avatarDir, "avatar_" + sessionUserId + ".jpg");
            try (java.io.InputStream in = getContentResolver().openInputStream(uri);
                 java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
                if (in == null) return null;
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            }
            return dest.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        userDao = AppDatabase.getInstance(this).userDao();
        
        SharedPreferences prefs = getSharedPreferences("USER", MODE_PRIVATE);
        sessionUserId = prefs.getInt("user_id", -1);

        if (sessionUserId == -1) {
            Toast.makeText(this, "Vui lòng đăng nhập lại", Toast.LENGTH_SHORT).show();
            handleLogout();
            return;
        }

        btnBack = findViewById(R.id.btnBack);
        imgProfileAvatar = findViewById(R.id.imgProfileAvatar);
        tvUsername = findViewById(R.id.tvUsername);
        edtFullName = findViewById(R.id.edtFullName);

        edtEmail = findViewById(R.id.edtEmail);
        edtPassword = findViewById(R.id.edtPassword);
        eyePassword = findViewById(R.id.eyePassword);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnLogout = findViewById(R.id.btnLogout);

        btnBack.setOnClickListener(v -> finish());
        
        imgProfileAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            pickImageLauncher.launch(intent);
        });

        eyePassword.setOnClickListener(v -> {
            isShowPass = !isShowPass;
            if (isShowPass) {
                edtPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                eyePassword.setAlpha(1f);
            } else {
                edtPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                eyePassword.setAlpha(0.4f);
            }
            edtPassword.setSelection(edtPassword.length());
        });

        btnSaveProfile.setOnClickListener(v -> saveProfile());
        
        switchDarkMode = findViewById(R.id.switchDarkMode);
        boolean isDark = prefs.getBoolean("dark_mode", false);
        switchDarkMode.setChecked(isDark);
        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });
        
        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất không?")
                .setPositiveButton("Đăng xuất", (dialog, which) -> handleLogout())
                .setNegativeButton("Hủy", null)
                .show();
        });

        loadUserProfile();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }

    private void loadUserProfile() {
        executor.execute(() -> {
            User user = userDao.getUserById(sessionUserId);
            currentUser = user;
            runOnUiThread(() -> {
                if (user != null) {
                    tvUsername.setText("@" + user.username);
                    edtFullName.setText(user.fullName != null ? user.fullName : "");

                    edtEmail.setText(user.email != null ? user.email : "");
                    currentAvatarUri = user.avatar;
                    
                    if (user.avatar != null && !user.avatar.isEmpty()) {
                        try {
                            Glide.with(this)
                                    .load(user.avatar)
                                    .skipMemoryCache(true)
                                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                                    .error(android.R.drawable.ic_menu_myplaces)
                                    .placeholder(android.R.drawable.ic_menu_myplaces)
                                    .into(imgProfileAvatar);
                        } catch (Exception e) {
                            imgProfileAvatar.setImageResource(android.R.drawable.ic_menu_myplaces);
                        }
                    }
                }
            });
        });
    }

    private void saveProfile() {
        String fullName = edtFullName.getText().toString().trim();
        String newPassword = edtPassword.getText().toString().trim();

        if (!newPassword.isEmpty() && (currentUser == null || !"google".equals(currentUser.loginType))) {
            if (newPassword.length() < 8 || !newPassword.matches(".*[A-Z].*")) {
                Toast.makeText(this, "Mật khẩu mới phải có ít nhất 8 ký tự và chứa tối thiểu 1 chữ cái viết hoa.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        btnSaveProfile.setEnabled(false);
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());

        executor.execute(() -> {
            User user = userDao.getUserById(sessionUserId);
            if (user != null) {
                String phone = user.phone != null ? user.phone : "";
                userDao.updateProfile(sessionUserId, fullName, phone, currentAvatarUri, now);
                
                if (!newPassword.isEmpty()) {
                    userDao.updatePassword(user.email, newPassword, now);
                }
                
                SharedPreferences.Editor editor = getSharedPreferences("USER", MODE_PRIVATE).edit();
                editor.putString("full_name", fullName);
                editor.apply();

                runOnUiThread(() -> {
                    btnSaveProfile.setEnabled(true);
                    Toast.makeText(UserProfileActivity.this, "Cập nhật thành công!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            }
        });
    }

    private void handleLogout() {
        // Clear Session
        getSharedPreferences("USER", MODE_PRIVATE).edit().clear().apply();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
