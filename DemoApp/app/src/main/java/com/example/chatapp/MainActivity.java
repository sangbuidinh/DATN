package com.example.chatapp;


import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import com.meetme.chatapp.R;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.chatapp.Fragments.ProfileFragment;
import com.example.chatapp.Model.User;
import com.example.chatapp.SharedPreferencesManager.SharedPreferencesManager;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


import java.util.HashMap;
import java.util.Objects;

import com.example.chatapp.Fragments.ChatsFragment;
import com.example.chatapp.Fragments.UsersFragment;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity {

    CircleImageView profileImage;
    TextView userName;
    TabLayout tabLayout;
    ViewPager2 viewPager2;
    ViewPagerApapter viewPagerApapter;
    FirebaseUser firebaseUser;
    FirebaseAuth firebaseAuth;
    DatabaseReference reference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);


        // Thiết lập toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("");

        // Lấy doi tuong tu layout và cài đặt thông tin người dùng
        profileImage = findViewById(R.id.profileImage);
        userName = findViewById(R.id.userName);
        tabLayout = findViewById(R.id.tabLayout);
        viewPager2 = findViewById(R.id.viewPager2);



        viewPagerApapter = new ViewPagerApapter(MainActivity.this);
        viewPagerApapter.addFragment(new ChatsFragment(), "Trò chuyện");
        viewPagerApapter.addFragment(new UsersFragment(), "Liên hệ");
        viewPagerApapter.addFragment(new ProfileFragment(), "Cá nhân");
        viewPager2.setAdapter(viewPagerApapter);

        new TabLayoutMediator(tabLayout, viewPager2,
                (tab, position) -> tab.setText(viewPagerApapter.getPageTitle(position)))
                .attach();

        // Lấy thông tin người dùng Firebase và reference tới database
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
        reference = FirebaseDatabase.getInstance().getReference("Users").child(firebaseUser.getUid());

        // Tạo một ValueEventListener để lắng nghe sự thay đổi dữ liệu
        ValueEventListener userListener = (new ValueEventListener()  {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        User user = dataSnapshot.getValue(User.class);
                        // Xử lý trường hợp user là null
                        if (user == null) {
                            // Hiển thị thông báo lỗi hoặc xử lý khi không có dữ liệu user
                            Toast.makeText(MainActivity.this, "Không tìm thấy thông tin người dùng.", Toast.LENGTH_SHORT).show();
                            // Thoát khỏi phương thức nếu không có dữ liệu để tránh NullPointerException
                            navigateToStartActivity();
                            return;
                        }
                        // Nếu user không null, cập nhật giao diện
                        userName.setText(user.getUserName());

                        if ("default".equals(user.getImageURL())) {
                            profileImage.setImageResource(R.mipmap.ic_launcher);
                        } else {
                            Glide.with(getApplicationContext()).load(user.getImageURL()).into(profileImage);
                        }
                    } else {
                        // Xử lý trường hợp không có dữ liệu dataSnapshot
                        Toast.makeText(MainActivity.this, "Không tìm thấy dữ liệu trong dataSnapshot.", Toast.LENGTH_SHORT).show();
                        navigateToStartActivity();
                    }
                }

//            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Không thể đọc dữ liệu từ database.", Toast.LENGTH_SHORT).show();
                navigateToStartActivity();
            }
        });
        //Lắng nghe sự thay đổi dữ liệu
        reference.addValueEventListener(userListener);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.logout) {
            navigateToStartActivity();
            finish();
            SharedPreferencesManager.clearUsername(getApplicationContext());
            return true;
        }
        return false;
    }

    // Hàm hỗ trợ chuyển hướng người dùng dang xuat va sang StartActivity và kết thúc MainActivity.
    private void navigateToStartActivity() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(MainActivity.this, StartActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish(); // Kết thúc MainActivity
    }
    private void status(String status) {
        FirebaseUser fuser = FirebaseAuth.getInstance().getCurrentUser();
        if (fuser != null) {
            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users").child(fuser.getUid());
            ref.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    User user = dataSnapshot.getValue(User.class);
                    if (user != null && !user.getStatus().equals(status)) {
                        HashMap<String, Object> hashMap = new HashMap<>();
                        hashMap.put("status", status);
                        ref.updateChildren(hashMap);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("updateUserStatus", "Failed to update user status", error.toException());
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        status("online");
    }

    @Override
    protected void onPause() {
        super.onPause();
        status("offline");
    }


}