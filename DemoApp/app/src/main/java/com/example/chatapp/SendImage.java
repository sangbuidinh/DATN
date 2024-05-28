package com.example.chatapp;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.chatapp.Fragments.APIServer;
import com.example.chatapp.Notifications.Client;
import com.example.chatapp.Notifications.Data;
import com.example.chatapp.Notifications.MyResponse;
import com.example.chatapp.Notifications.Sender;
import com.example.chatapp.Notifications.Token;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.meetme.chatapp.R;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SendImage extends AppCompatActivity {

    ImageView iv_sendImage;
    ProgressBar progressBar;
    Button btn_sendImage;
    FirebaseUser fuser;
    APIServer apiServer;
    StorageReference storageReference;
    DatabaseReference messagesRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send_image);
        EdgeToEdge.enable(this);

        iv_sendImage = findViewById(R.id.iv_sendImage);
        progressBar = findViewById(R.id.pb_sendImage);
        btn_sendImage = findViewById(R.id.btn_sendImage);
        fuser = FirebaseAuth.getInstance().getCurrentUser();
        apiServer = Client.getClient("https://fcm.googleapis.com/").create(APIServer.class);
        // Nhận dữ liệu từ Intent
        Intent intent = getIntent();
        Uri imageUri = Uri.parse(intent.getStringExtra("imageUrl"));
        String receiverId = intent.getStringExtra("receiverId");
        String receiverName = intent.getStringExtra("receiverName");
        String senderId = intent.getStringExtra("senderId");

        // Hiển thị hình ảnh
        Glide.with(this).load(imageUri).into(iv_sendImage);

        // Firebase
        storageReference = FirebaseStorage.getInstance().getReference("message_images");
        messagesRef = FirebaseDatabase.getInstance().getReference("Messages");

        btn_sendImage.setOnClickListener(v -> uploadImage(imageUri, receiverId, receiverName, senderId));

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void uploadImage(Uri imageUri, String receiverId, String receiverName, String senderId) {
        if (imageUri != null) {
            progressBar.setVisibility(ProgressBar.VISIBLE);
            final StorageReference fileRef = storageReference.child(System.currentTimeMillis() + "." + getFileExtension(imageUri));

            fileRef.putFile(imageUri)
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            throw Objects.requireNonNull(task.getException());
                        }
                        return fileRef.getDownloadUrl();
                    })
                    .addOnSuccessListener(uri -> {
                        String imageUrl = uri.toString();
                        sendMessage(senderId, receiverId, imageUrl, "image", receiverName);
                        Log.d("sendimagetest1", "imageUrl: " + imageUrl);
                        Log.d("sendimagetest1", "receiverId: " + receiverId);
                        Log.d("sendimagetest1", "receiverName: " + receiverName);
                        Log.d("sendimagetest1", "senderId: " + senderId);

                        progressBar.setVisibility(ProgressBar.INVISIBLE);
                        Toast.makeText(SendImage.this, "Hình ảnh đã được gửi thành công!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(ProgressBar.INVISIBLE);
                        Toast.makeText(SendImage.this, "Tải ảnh lên thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            Toast.makeText(this, "Không có ảnh nào được chọn!", Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileExtension(Uri uri) {
        ContentResolver contentResolver = getContentResolver();
        MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
        return mimeTypeMap.getExtensionFromMimeType(contentResolver.getType(uri));
    }

    private void sendMessage(String senderId, String receiverId, String imageUrl, String type, String receiverName) {
        DatabaseReference messagesRef = FirebaseDatabase.getInstance().getReference("Chats");
        HashMap<String, Object> messageMap = new HashMap<>();
        messageMap.put("sender", senderId);
        messageMap.put("receiver", receiverId);
        messageMap.put("message", imageUrl); // Đối với hình ảnh, URL hình ảnh được lưu trữ ở đây
        messageMap.put("type", type); // "image" hoặc "text" tùy thuộc vào loại tin nhắn
        messagesRef.push().setValue(messageMap);

        sendNotification(receiverId, receiverName, "đã gửi 1 hình ảnh cho bạn");
    }

    private void sendNotification(String receiver, String username, String message) {
        DatabaseReference tokens = FirebaseDatabase.getInstance().getReference("Tokens");
        Query query = tokens.orderByKey().equalTo(receiver);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Token token = snapshot.getValue(Token.class);
                    Data data = new Data(fuser.getUid(), R.mipmap.ic_launcher, username + ": " + message, "Tin nhắn mới", receiver);
                    Sender sender = new Sender(data, Objects.requireNonNull(token).getToken());

                    apiServer.sendNotification(sender).enqueue(new Callback<MyResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<MyResponse> call, @NonNull Response<MyResponse> response) {
                            if (!response.isSuccessful() || response.body() == null || response.body().success != 1) {
                                Toast.makeText(getApplicationContext(), "Failed to send notification", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<MyResponse> call, @NonNull Throwable throwable) {
                            Toast.makeText(getApplicationContext(), "Failed to send notification", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("sendNotification", "Failed to get token", error.toException());
            }
        });
    }
}
