package com.example.chatapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
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

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class SignUpWithPhoneNo extends AppCompatActivity {
    EditText phone, otp, userName;
    Button register, verOtp;
    FirebaseAuth mAuth;
    String verificationId;
    ProgressBar bar;
    DatabaseReference reference;
    String txt_userName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_sign_up_with_phone_no);

        phone = findViewById(R.id.phone);
        otp = findViewById(R.id.otp);
        register = findViewById(R.id.register);
        verOtp = findViewById(R.id.verOtp);
        bar = findViewById(R.id.bar);
        userName = findViewById(R.id.userName);

        mAuth = FirebaseAuth.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("Register");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        register.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                txt_userName = userName.getText().toString();
                if (TextUtils.isEmpty(phone.getText().toString())){
                    Toast.makeText(SignUpWithPhoneNo.this, "Không để trống", Toast.LENGTH_SHORT).show();
                } else {
                    String number = phone.getText().toString();
                    bar.setVisibility(View.GONE);
                    sendCode(number);
                }
            }
        });

        verOtp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                txt_userName = userName.getText().toString();
                if (TextUtils.isEmpty(otp.getText().toString())){
                    Toast.makeText(SignUpWithPhoneNo.this, "Không để trống", Toast.LENGTH_SHORT).show();
                }else {
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
                    // Số điện thoại đã tồn tại, thông báo cho người dùng
                    Toast.makeText(SignUpWithPhoneNo.this, "Bạn không thể đăng ký bằng Số điện thoại này", Toast.LENGTH_SHORT).show();
                } else {
                    // Không tìm thấy số điện thoại, tiếp tục gửi OTP
                    PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth)
                            .setPhoneNumber("+84" + phoneNumber)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(SignUpWithPhoneNo.this)
                            .setCallbacks(mCallbacks)
                            .build();
                    PhoneAuthProvider.verifyPhoneNumber(options);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Xử lý lỗi
                Toast.makeText(SignUpWithPhoneNo.this, "Lỗi kiểm tra số điện thoại", Toast.LENGTH_SHORT).show();
            }
        });
    }
    private final PhoneAuthProvider.OnVerificationStateChangedCallbacks
            mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

        @Override
        public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
            Log.d("txtname" ,  txt_userName);
            signInByCredential(credential, txt_userName);
        }

        @Override
        public void onVerificationFailed(@NonNull FirebaseException e) {
            Toast.makeText(SignUpWithPhoneNo.this, "Gửi OTP gặp lỗi", Toast.LENGTH_SHORT).show();

        }

        @Override
        public void onCodeSent(@NonNull String verId,
                               @NonNull PhoneAuthProvider.ForceResendingToken token) {
            super.onCodeSent(verId, token);
            // Lưu lại verificationId để sử dụng sau này
            SignUpWithPhoneNo.this.verificationId = verId;
            verOtp.setEnabled(true);
            bar.setVisibility(View.VISIBLE);
        }

    };

    private void verCode(String code) {
        // Kiểm tra lại để chắc chắn verificationId không phải là null
        if (verificationId != null && !verificationId.isEmpty() && code != null) {
            PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
            Log.d("txtname" ,  txt_userName);
            signInByCredential(credential, txt_userName);
        } else {
            Toast.makeText(SignUpWithPhoneNo.this, "Verification ID hoặc code là null.", Toast.LENGTH_SHORT).show();
        }
    }
    private void signInByCredential(PhoneAuthCredential credential, String userName) {
        FirebaseAuth firebaseAuth = FirebaseAuth.getInstance();
        firebaseAuth.signInWithCredential(credential)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()){
                            // Tạo user thành công, chuẩn bị dữ liệu người dùng để lưu vào Realtime Database
                            FirebaseUser firebaseUser = mAuth.getCurrentUser();
                            assert firebaseUser != null;
                            String userid = firebaseUser.getUid();
                            String mPhone = phone.getText().toString();
                            reference = FirebaseDatabase.getInstance().getReference("Users").child(userid);
                            HashMap<String, String> hashMap = new HashMap<>();
                            hashMap.put("id", userid);
                            hashMap.put("userName", userName);
                            hashMap.put("phoneNo", mPhone);
                            hashMap.put("imageURL", "default");
                            hashMap.put("status", "offline");
                            hashMap.put("search", userName.toLowerCase());
                            // Lưu dữ liệu người dùng vào database
                            reference.setValue(hashMap).addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> task) {
                                    // Nếu việc lưu dữ liệu thành công, chuyển đến giao diện Chính
                                    if (task.isSuccessful()){
                                        // chạy khi dữ liệu người dùng đã được lưu thành công
                                        Intent intent = new Intent(SignUpWithPhoneNo.this, MainActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);
                                        finish(); // Đóng RegisterActivity hiện tại
                                    }
                                }
                            });
                        }else {
                            if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                Toast.makeText(SignUpWithPhoneNo.this, "Mã OTP không đúng", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(SignUpWithPhoneNo.this, "Đăng nhập thất bại", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
    }


    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser!=null){
            startActivity(new Intent(SignUpWithPhoneNo.this, MainActivity.class));
            finish();
        }
    }
}