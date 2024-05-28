package com.example.chatapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.meetme.chatapp.R;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.chatapp.Repository.MainRespository;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.permissionx.guolindev.PermissionX;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class SignInWithPhoneNo extends AppCompatActivity {

    EditText phone, otp;
    Button login, verOtp;
    FirebaseAuth mAuth;
    String verificationId;
    ProgressBar bar;
    private MainRespository mainRespository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_in_with_phone_no);

        mainRespository = MainRespository.getInstance(getApplicationContext());
        phone = findViewById(R.id.phone);
        otp = findViewById(R.id.otp);
        login = findViewById(R.id.login);
        verOtp = findViewById(R.id.verOtp);
        bar = findViewById(R.id.bar);

        mAuth = FirebaseAuth.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Login");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        login.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TextUtils.isEmpty(phone.getText().toString())){
                    Toast.makeText(SignInWithPhoneNo.this, "Không để trống", Toast.LENGTH_SHORT).show();
                } else {
                    String number = phone.getText().toString().trim();
                    bar.setVisibility(View.VISIBLE);
                    sendCode(number);
                }
            }
        });

        verOtp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (TextUtils.isEmpty(otp.getText().toString())){
                    Toast.makeText(SignInWithPhoneNo.this, "Không để trống", Toast.LENGTH_SHORT).show();
                } else {
                    verCode(otp.getText().toString());
                }
            }
        });

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void sendCode(String phoneNumber) {
        // Truy vấn cơ sở dữ liệu để tìm người dùng hiện có với số điện thoại
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("Users");
        Query query = usersRef.orderByChild("phoneNo").equalTo(phoneNumber);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Số điện thoại đã tồn tại, tiếp tục gửi OTP
                    PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                            .setPhoneNumber("+84" + phoneNumber)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(SignInWithPhoneNo.this)
                            .setCallbacks(mCallbacks)
                            .build();
                    PhoneAuthProvider.verifyPhoneNumber(options);
                } else {
                    // Số điện thoại chưa tồn tại, hiển thị thông báo
                    bar.setVisibility(View.GONE);
                    Toast.makeText(SignInWithPhoneNo.this, "Số điện thoại chưa được đăng ký", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Xử lý lỗi
                bar.setVisibility(View.GONE);
                Toast.makeText(SignInWithPhoneNo.this, "Lỗi kiểm tra số điện thoại", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks
            mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        @Override
        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
            String phoneNumber = phone.getText().toString();
            DatabaseReference databaseReference = FirebaseDatabase.getInstance().getReference();
            databaseReference.child(phoneNumber).addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot.exists()){
                        Toast.makeText(SignInWithPhoneNo.this, "Số điện thoại đã tồn tại", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    signInByCredential(credential);
                }
            });
        }

        @Override
        public void onVerificationFailed(@NonNull FirebaseException e) {
            bar.setVisibility(View.GONE);
            Toast.makeText(SignInWithPhoneNo.this, "Gửi OTP gặp lỗi: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCodeSent(@NonNull String verId,
                               @NonNull PhoneAuthProvider.ForceResendingToken token) {
            super.onCodeSent(verId, token);
            // Lưu lại verificationId để sử dụng sau này
            SignInWithPhoneNo.this.verificationId = verId;
            verOtp.setEnabled(true);
            bar.setVisibility(View.GONE);
        }
    };

    private void verCode(String code) {
        // Kiểm tra lại để chắc chắn verificationId không phải là null
        if (verificationId != null && !verificationId.isEmpty() && code != null) {
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
            signInByCredential(credential);
        } else {
            Toast.makeText(SignInWithPhoneNo.this, "Verification ID hoặc code là null.", Toast.LENGTH_SHORT).show();
        }
    }

    private void signInByCredential(PhoneAuthCredential credential) {
        String txt_phone = phone.getText().toString();
        if (TextUtils.isEmpty(txt_phone)){
            Toast.makeText(this, "Không được để trống", Toast.LENGTH_SHORT).show();
        } else {
            PermissionX.init(SignInWithPhoneNo.this)
                    .permissions(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)
                    .request((allGranted, grantedList, deniedList) -> {
                        if (allGranted) {
                            mainRespository.loginByPhone(credential, this, () -> {
                                Intent intent = new Intent(SignInWithPhoneNo.this, MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                                finish();
                            });
                        } else {
                            Toast.makeText(SignInWithPhoneNo.this, "Cần cấp quyền CAMERA và RECORD_AUDIO để sử dụng ứng dụng", Toast.LENGTH_LONG).show();
                            FirebaseAuth.getInstance().signOut();
                        }
                    });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            startActivity(new Intent(SignInWithPhoneNo.this, MainActivity.class));
            finish();
        }
    }
}
