package com.example.chatapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import com.meetme.chatapp.R;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chatapp.Adapter.MessageAdapter;
import com.example.chatapp.Fragments.APIServer;
import com.example.chatapp.Model.Chat;
import com.example.chatapp.Model.User;
import com.example.chatapp.Notifications.Client;
import com.example.chatapp.Notifications.Data;
import com.example.chatapp.Notifications.MyResponse;
import com.example.chatapp.Notifications.Sender;
import com.example.chatapp.Notifications.Token;
import com.example.chatapp.Repository.MainRespository;
import com.example.chatapp.SharedPreferencesManager.SharedPreferencesManager;
import com.meetme.chatapp.databinding.ActivityMessagesBinding;
import com.example.chatapp.remote.FirebaseClient;
import com.example.chatapp.utils.DataModelType;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.StorageTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import de.hdodenhof.circleimageview.CircleImageView;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MessagesActivity extends AppCompatActivity implements MainRespository.Listener {

    CircleImageView profileImage;
    TextView userName;
    FirebaseUser fuser;
    DatabaseReference reference;
    ImageButton btn_Send, btn_send_image;
    EditText text_Send;
    MessageAdapter messageAdapter;
    List<Chat> mChat;

    RecyclerView recyclerView;
    Intent intent;
    ValueEventListener seenListener;
    ValueEventListener messageListener;
    DatabaseReference messagesRef;
    String userid;

    APIServer apiServer;
    public boolean notify = false;
    private ActivityMessagesBinding views;
    private MainRespository mainRespository;
    private Boolean isCameraMuted = false;
    private Boolean isMicrophoneMuted = false;
    private StorageTask upLoadTask;
    private final Handler callTimeoutHandler = new Handler();
    private Runnable callTimeoutRunnable;
    private FirebaseClient firebaseClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        views = ActivityMessagesBinding.inflate(getLayoutInflater());
        setContentView(R.layout.activity_messages);
        setContentView(views.getRoot());

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setTitle("");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MessagesActivity.this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            }
        });

        apiServer = Client.getClient("https://fcm.googleapis.com/").create(APIServer.class);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setHasFixedSize(true);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
        linearLayoutManager.setStackFromEnd(true);
        recyclerView.setLayoutManager(linearLayoutManager);

        profileImage = findViewById(R.id.profileImage);
        userName = findViewById(R.id.userName);
        btn_Send = findViewById(R.id.btn_Send);
        btn_send_image = findViewById(R.id.btn_send_image);
        text_Send = findViewById(R.id.text_Send);

        text_Send.setOnFocusChangeListener((view, b) -> {
            if (b) {
                btn_send_image.setVisibility(View.GONE);
            } else {
                btn_send_image.setVisibility(View.VISIBLE);
            }
        });

        firebaseClient = new FirebaseClient(this);

        mainRespository = MainRespository.getInstance(getApplicationContext());
        fuser = FirebaseAuth.getInstance().getCurrentUser();
        if (fuser != null) {
            String userIdFromFirebase = fuser.getUid();
            mainRespository.initWebRTCClient(getApplicationContext(), userIdFromFirebase);
        } else {
            Log.e("WebRTCInit", "No user logged in.");
        }

        intent = getIntent();
        if (intent != null && intent.getStringExtra("userid") != null) {
            userid = intent.getStringExtra("userid");
        } else {
            userid = SharedPreferencesManager.getUsername(getApplicationContext());
        }
        Log.d("usersid", "userid: " + userid);

        if (userid != null) {
            init();
            setupMessageListener(userid);
            loadReceiverInfo();
        } else {
            Toast.makeText(this, "Thông tin người dùng không hợp lệ.", Toast.LENGTH_SHORT).show();
        }

        btn_Send.setOnClickListener(view -> {
            notify = true;
            String msg = text_Send.getText().toString();
            String imageUrl = "";
            if (!msg.isEmpty()) {
                sendMessage(fuser.getUid(), userid, msg, imageUrl);
                text_Send.setText("");
            } else {
                Toast.makeText(MessagesActivity.this, "Bạn không thể gửi tin nhắn trống", Toast.LENGTH_SHORT).show();
            }
        });

        btn_send_image.setOnClickListener(view -> mGetContent.launch("image/*"));
        seenMessage(userid);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.messagesactivity), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void loadReceiverInfo() {
        reference = FirebaseDatabase.getInstance().getReference("Users").child(userid);
        reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                User user = dataSnapshot.getValue(User.class);
                if (user != null) {
                    userName.setText(user.getUserName());
                    if (user.getImageURL().equals("default")) {
                        profileImage.setImageResource(R.mipmap.ic_launcher);
                    } else {
                        Glide.with(getApplicationContext()).load(user.getImageURL()).into(profileImage);
                    }
                    setupMessageListener(user.getImageURL());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("DatabaseError", "Failed to load user info: " + error.getMessage());
            }
        });
    }

    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    if (uri != null) {
                        if (upLoadTask != null && upLoadTask.isInProgress()) {
                            Toast.makeText(getApplicationContext(), "Đang tải ảnh...", Toast.LENGTH_SHORT).show();
                        } else {
                            String imageUrl = uri.toString();
                            String receiverId = userid;
                            String receiverName = userName.getText().toString();
                            String senderId = fuser.getUid();
                            Intent sendImageIntent = new Intent(MessagesActivity.this, SendImage.class);
                            sendImageIntent.putExtra("imageUrl", imageUrl);
                            sendImageIntent.putExtra("receiverId", receiverId);
                            sendImageIntent.putExtra("receiverName", receiverName);
                            sendImageIntent.putExtra("senderId", senderId);

                            startActivity(sendImageIntent);
                        }
                    }
                }
            });

    private void seenMessage(String userId) {
        reference = FirebaseDatabase.getInstance().getReference("Chats");
        seenListener = reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Chat chat = snapshot.getValue(Chat.class);
                    if (chat != null && chat.getReceiver() != null && chat.getSender() != null) {
                        if (chat.getReceiver().equals(fuser.getUid()) && chat.getSender().equals(userId)) {
                            HashMap<String, Object> hashMap = new HashMap<>();
                            hashMap.put("isseen", true);
                            snapshot.getRef().updateChildren(hashMap);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

    private void sendMessage(String sender, String receiver, String message, String imageUrl) {
        final String userid = intent.getStringExtra("userid");
        DatabaseReference reference = FirebaseDatabase.getInstance().getReference();

        boolean isImage = !imageUrl.isEmpty();
        String type = isImage ? "image" : "text";

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("sender", sender);
        hashMap.put("receiver", receiver);
        hashMap.put("message", message);
        hashMap.put("imageUrl", imageUrl);
        hashMap.put("type", type);
        hashMap.put("isseen", false);

        reference.child("Chats").push().setValue(hashMap);

        updateChatList(fuser.getUid(), userid);

        String notificationMessage = isImage ? "đã gửi một hình ảnh" : message;

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(fuser.getUid());
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                User user = dataSnapshot.getValue(User.class);
                if (notify && user != null) {
                    sendNotification(receiver, user.getUserName(), notificationMessage);
                    notify = false;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
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
                            if (response.isSuccessful() && response.body() != null && response.body().success != 1) {
                                Toast.makeText(MessagesActivity.this, "Failed to send notification", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<MyResponse> call, @NonNull Throwable throwable) {
                            Toast.makeText(MessagesActivity.this, "Failed to send notification", Toast.LENGTH_SHORT).show();
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

    private void updateChatList(String senderId, String receiverId) {
        DatabaseReference chatRefSender = FirebaseDatabase.getInstance().getReference("Chatlist")
                .child(senderId)
                .child(receiverId);
        chatRefSender.child("id").setValue(receiverId);

        DatabaseReference chatRefReceiver = FirebaseDatabase.getInstance().getReference("Chatlist")
                .child(receiverId)
                .child(senderId);
        chatRefReceiver.child("id").setValue(senderId);
    }

    private void setupMessageListener(String receiverImageURL) {
        mChat = new ArrayList<>();
        reference = FirebaseDatabase.getInstance().getReference("Chats");

        messageListener = reference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                mChat.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Chat chat = snapshot.getValue(Chat.class);
                    if (chat != null && chat.getReceiver() != null && chat.getSender() != null) {
                        if ((chat.getReceiver().equals(fuser.getUid()) && chat.getSender().equals(userid)) ||
                                (chat.getReceiver().equals(userid) && chat.getSender().equals(fuser.getUid()))) {
                            mChat.add(chat);
                        }
                    }
                }
                messageAdapter = new MessageAdapter(MessagesActivity.this, mChat, receiverImageURL);
                recyclerView.setAdapter(messageAdapter);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("DatabaseError", "Data fetching cancelled: " + error.getMessage());
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void currentUser(String userId) {
        SharedPreferences sharedPreferences = getSharedPreferences("PREFS", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("current_user", userId);
        editor.apply();
    }

    @SuppressLint("SetTextI18n")
    private void init() {
        if (userid != null && fuser != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(userid);
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    User user = dataSnapshot.getValue(User.class);
                    if (user != null) {
                        setupMessageAndCallFeatures(userid);
//                        readMessages(fuser.getUid(), userid, user.getImageURL());
                    } else {
                        Toast.makeText(MessagesActivity.this, "Không tìm thấy thông tin người dùng.", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Toast.makeText(MessagesActivity.this, "Lỗi khi truy cập dữ liệu người dùng.", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            Toast.makeText(this, "Thông tin người dùng không hợp lệ.", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("SetTextI18n")
    private void setupMessageAndCallFeatures(String userIdOfOtherUser) {
        Log.d("SetupFeatures", "Preparing to set up features for communication with " + userIdOfOtherUser);

        String currentUserName = SharedPreferencesManager.getUsername(getApplicationContext());
        if (currentUserName == null || currentUserName.isEmpty()) {
            Log.e("CallInitiation", "Current user's username is null or empty.");
            Toast.makeText(this, "Current user information not available.", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("CallInitiation", "Current user's username: " + currentUserName);
        Log.d("CallInitiation", "Attempting to initiate call from " + currentUserName + " to " + userIdOfOtherUser);

        views.callBtn.setOnClickListener(v -> {
            Toast.makeText(this, "Đã gửi yêu cầu gọi đi.", Toast.LENGTH_SHORT).show();
            notifyCallRequest();
            mainRespository.sendCallRequest(userIdOfOtherUser, () -> {
                Log.e("CallRequest", "Failed to find user: " + userIdOfOtherUser);
                Toast.makeText(MessagesActivity.this, "Không tìm thấy người dùng", Toast.LENGTH_SHORT).show();
            });
        });

        mainRespository.initLocalView(views.localView);
        mainRespository.initRemoteView(views.remoteView);
        mainRespository.listener = this;

        mainRespository.subscribeForLatestEvent(data -> {
            reference = FirebaseDatabase.getInstance().getReference("Users").child(userid);
            reference.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    User user = snapshot.getValue(User.class);
                    if (data.getType() == DataModelType.StartCall) {
                        Log.d("CallReceived", "Received StartCall event from: " + data.getSender());
                        runOnUiThread(() -> {
                            assert user != null;
                            views.incomingNameTV.setText(user.getUserName() + " đang gọi tới bạn.");
                            views.incomingCallLayout.setVisibility(View.VISIBLE);

                            views.acceptButton.setOnClickListener(v -> {
                                mainRespository.startCall(data.getSender());
                                views.incomingCallLayout.setVisibility(View.GONE);
                                handleCallEnd();
                            });

                            views.rejectButton.setOnClickListener(v -> {
                                views.incomingCallLayout.setVisibility(View.GONE);
                                handleCallEnd();
                            });
                        });
                        if (!isActivityVisible()) {
//                            NotificationHelper.displayIncomingCallNotification(getApplicationContext(), data.getSender());
                        }
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });
            Log.d("CallActivity", "Received data: type = " + data.getType());
        });
        views.switchCameraButton.setOnClickListener(view -> {
            mainRespository.switchCamera();
        });
        views.micButton.setOnClickListener(v -> {
            if (isMicrophoneMuted) {
                views.micButton.setImageResource(R.drawable.ic_baseline_mic_off_24);
            } else {
                views.micButton.setImageResource(R.drawable.ic_baseline_mic_24);
            }
            mainRespository.toggleAudio(isMicrophoneMuted);
            isMicrophoneMuted = !isMicrophoneMuted;
            Log.d("WebRTCClient", "Mute button clicked: " + isMicrophoneMuted);
        });

        views.videoButton.setOnClickListener(v -> {
            if (isCameraMuted) {
                views.videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24);
            } else {
                views.videoButton.setImageResource(R.drawable.ic_baseline_videocam_24);
            }
            mainRespository.toggleVideo(isCameraMuted);
            isCameraMuted = !isCameraMuted;
        });
        views.endCallButton.setOnClickListener(view -> {
            mainRespository.endCall();
            finish();
        });
    }

    private void notifyCallRequest() {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(userid);

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                User user = dataSnapshot.getValue(User.class);
                if (user != null) {
                    // Send notification to the recipient
                    sendNotificationCall(userid, user.getUserName(), "gửi yêu cầu gọi");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("sendNotification", "Failed to get user data", error.toException());
            }
        });
    }

    private void sendNotificationCall(String receiver, String username, String message) {
        DatabaseReference tokens = FirebaseDatabase.getInstance().getReference("Tokens");
        Query query = tokens.orderByKey().equalTo(receiver);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Token token = snapshot.getValue(Token.class);
                    if (token != null) {
                        Data data = new Data(fuser.getUid(), R.mipmap.ic_launcher, username + ": " + message, "Cuộc gọi mới", receiver);
                        Sender sender = new Sender(data, token.getToken());

                        apiServer.sendNotification(sender).enqueue(new Callback<MyResponse>() {
                            @Override
                            public void onResponse(@NonNull Call<MyResponse> call, @NonNull Response<MyResponse> response) {
                                if (!response.isSuccessful() || response.body() == null || response.body().success != 1) {
                                    Toast.makeText(MessagesActivity.this, "Failed to send notification", Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(@NonNull Call<MyResponse> call, @NonNull Throwable throwable) {
                                Toast.makeText(MessagesActivity.this, "Failed to send notification", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("sendNotification", "Failed to get token", error.toException());
            }
        });
    }

    private void handleCallTimeout() {
        if (views.incomingCallLayout.getVisibility() == View.VISIBLE) {
            if (fuser != null) {
                mainRespository.clearLatestEvent(fuser.getUid());
            }
            runOnUiThread(() -> {
                Toast.makeText(this, "No response, call cancelled.", Toast.LENGTH_SHORT).show();
                views.incomingCallLayout.setVisibility(View.GONE);
            });
        }
    }

    private boolean isActivityVisible() {
        return !isFinishing() && !isDestroyed();
    }

    @Override
    public void webrtcConnected() {
        callTimeoutRunnable = this::handleCallTimeout;
        callTimeoutHandler.postDelayed(callTimeoutRunnable, 15000);

        runOnUiThread(() -> {
            views.incomingCallLayout.setVisibility(View.GONE);
            findViewById(R.id.toolbar).setVisibility(View.GONE);
            findViewById(R.id.recyclerView).setVisibility(View.GONE);
            findViewById(R.id.bottom).setVisibility(View.GONE);
            views.callLayout.setVisibility(View.VISIBLE);
            Log.d("CallUI", "Call layout set to visible");
        });
    }

    @Override
    public void webrtcClosed() {
        callTimeoutHandler.removeCallbacks(callTimeoutRunnable);
        if (fuser != null) {
            handleCallEnd();
        }
        runOnUiThread(this::finish);
    }

    private void handleCallEnd() {
        if (fuser != null) {
            mainRespository.clearLatestEvent(fuser.getUid());
        }
    }

    private void status(String status) {
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














//package com.example.chatapp;
//
//import android.annotation.SuppressLint;
//import android.content.Context;
//import android.content.Intent;
//import android.content.SharedPreferences;
//import android.graphics.Rect;
//import android.net.Uri;
//import android.os.Bundle;
//import android.os.Handler;
//import android.util.Log;
//import android.view.MotionEvent;
//import android.view.View;
//import android.view.inputmethod.InputMethodManager;
//import android.widget.EditText;
//import android.widget.ImageButton;
//import android.widget.TextView;
//import android.widget.Toast;
//import com.meetme.chatapp.R;
//import androidx.activity.EdgeToEdge;
//import androidx.activity.result.ActivityResultCallback;
//import androidx.activity.result.ActivityResultLauncher;
//import androidx.activity.result.contract.ActivityResultContracts;
//import androidx.annotation.NonNull;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.appcompat.widget.Toolbar;
//import androidx.core.graphics.Insets;
//import androidx.core.view.ViewCompat;
//import androidx.core.view.WindowInsetsCompat;
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//
//import com.bumptech.glide.Glide;
//import com.example.chatapp.Adapter.MessageAdapter;
//import com.example.chatapp.Fragments.APIServer;
//import com.example.chatapp.Model.Chat;
//import com.example.chatapp.Model.User;
//import com.example.chatapp.Notifications.Client;
//import com.example.chatapp.Notifications.Data;
//import com.example.chatapp.Notifications.MyResponse;
//import com.example.chatapp.Notifications.Sender;
//import com.example.chatapp.Notifications.Token;
//import com.example.chatapp.Repository.MainRespository;
//import com.example.chatapp.SharedPreferencesManager.SharedPreferencesManager;
//import com.meetme.chatapp.databinding.ActivityMessagesBinding;
//import com.example.chatapp.remote.FirebaseClient;
//import com.example.chatapp.utils.DataModelType;
//import com.google.firebase.auth.FirebaseAuth;
//import com.google.firebase.auth.FirebaseUser;
//import com.google.firebase.database.DataSnapshot;
//import com.google.firebase.database.DatabaseError;
//import com.google.firebase.database.DatabaseReference;
//import com.google.firebase.database.FirebaseDatabase;
//import com.google.firebase.database.Query;
//import com.google.firebase.database.ValueEventListener;
//import com.google.firebase.storage.StorageTask;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Objects;
//
//import de.hdodenhof.circleimageview.CircleImageView;
//import retrofit2.Call;
//import retrofit2.Callback;
//import retrofit2.Response;
//
//public class MessagesActivity extends AppCompatActivity implements MainRespository.Listener {
//
//    CircleImageView profileImage;
//    TextView userName;
//    FirebaseUser fuser;
//    DatabaseReference reference;
//    ImageButton btn_Send, btn_send_image;
//    EditText text_Send;
//    MessageAdapter messageAdapter;
//    List<Chat> mChat;
//
//    RecyclerView recyclerView;
//    Intent intent;
//    ValueEventListener seenListener;
//    String userid;
//
//    APIServer apiServer;
//    public boolean notify = false;
//    private ActivityMessagesBinding views;
//    private MainRespository mainRespository;
//    private Boolean isCameraMuted = false;
//    private Boolean isMicrophoneMuted = false;
//    private StorageTask upLoadTask;
//    private final Handler callTimeoutHandler = new Handler();
//    private Runnable callTimeoutRunnable;
//    private FirebaseClient firebaseClient;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        EdgeToEdge.enable(this);
//        views = ActivityMessagesBinding.inflate(getLayoutInflater());
//        setContentView(R.layout.activity_messages);
//        setContentView(views.getRoot());
//
//        Toolbar toolbar = findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);
//        Objects.requireNonNull(getSupportActionBar()).setTitle("");
//        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
//        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                startActivity(new Intent(MessagesActivity.this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
//            }
//        });
//
//        apiServer = Client.getClient("https://fcm.googleapis.com/").create(APIServer.class);
//
//        recyclerView = findViewById(R.id.recyclerView);
//        recyclerView.setHasFixedSize(true);
//        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getApplicationContext());
//        linearLayoutManager.setStackFromEnd(true);
//        recyclerView.setLayoutManager(linearLayoutManager);
//
//        profileImage = findViewById(R.id.profileImage);
//        userName = findViewById(R.id.userName);
//        btn_Send = findViewById(R.id.btn_Send);
//        btn_send_image = findViewById(R.id.btn_send_image);
//        text_Send = findViewById(R.id.text_Send);
//
//        text_Send.setOnFocusChangeListener((view, b) -> {
//            if (b) {
//                btn_send_image.setVisibility(View.GONE);
//            } else {
//                btn_send_image.setVisibility(View.VISIBLE);
//            }
//        });
//
//        firebaseClient = new FirebaseClient(this);
//
//        mainRespository = MainRespository.getInstance(getApplicationContext());
//        fuser = FirebaseAuth.getInstance().getCurrentUser();
//        if (fuser != null) {
//            String userIdFromFirebase = fuser.getUid();
//            mainRespository.initWebRTCClient(getApplicationContext(), userIdFromFirebase);
//        } else {
//            Log.e("WebRTCInit", "No user logged in.");
//        }
//
//
//        intent = getIntent();
//        if (intent != null && intent.getStringExtra("userid") != null) {
//            userid = intent.getStringExtra("userid");
//        } else {
//            userid = SharedPreferencesManager.getUsername(getApplicationContext());
//        }
//        Log.d("usersid", "userid: " + userid);
//
//        if (userid != null) {
//            init();
//            fuser = FirebaseAuth.getInstance().getCurrentUser();
//            reference = FirebaseDatabase.getInstance().getReference("Users").child(userid);
//            reference.addValueEventListener(new ValueEventListener() {
//                @Override
//                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                    User user = dataSnapshot.getValue(User.class);
//                    assert user != null;
//                    userName.setText(user.getUserName());
//                    if (user.getImageURL().equals("default")) {
//                        profileImage.setImageResource(R.mipmap.ic_launcher);
//                    } else {
//                        Glide.with(getApplicationContext()).load(user.getImageURL()).into(profileImage);
//                    }
////                    readMessages(fuser.getUid(), userid, user.getImageURL());
//                }
//
//                @Override
//                public void onCancelled(@NonNull DatabaseError error) {
//                }
//            });
//        } else {
//            Toast.makeText(this, "Thông tin người dùng không hợp lệ.", Toast.LENGTH_SHORT).show();
//        }
//
//        btn_Send.setOnClickListener(view -> {
//            notify = true;
//            String msg = text_Send.getText().toString();
//            String imageUrl = "";
//            if (!msg.isEmpty()) {
//                sendMessage(fuser.getUid(), userid, msg, imageUrl);
//                text_Send.setText("");
//            } else {
//                Toast.makeText(MessagesActivity.this, "Bạn không thể gửi tin nhắn trống", Toast.LENGTH_SHORT).show();
//            }
//        });
//
//
//        btn_send_image.setOnClickListener(view -> mGetContent.launch("image/*"));
//        seenMessage(userid);
//
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.messagesactivity), (v, insets) -> {
//            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
//            return insets;
//        });
//    }
//
//    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
//            new ActivityResultCallback<Uri>() {
//                @Override
//                public void onActivityResult(Uri uri) {
//                    if (uri != null) {
//                        if (upLoadTask != null && upLoadTask.isInProgress()) {
//                            Toast.makeText(getApplicationContext(), "Đang tải ảnh...", Toast.LENGTH_SHORT).show();
//                        } else {
//                            String imageUrl = uri.toString();
//                            String receiverId = userid;
//                            String receiverName = userName.getText().toString();
//                            String senderId = fuser.getUid();
//                            Intent sendImageIntent = new Intent(MessagesActivity.this, SendImage.class);
//                            sendImageIntent.putExtra("imageUrl", imageUrl);
//                            sendImageIntent.putExtra("receiverId", receiverId);
//                            sendImageIntent.putExtra("receiverName", receiverName);
//                            sendImageIntent.putExtra("senderId", senderId);
//
//                            startActivity(sendImageIntent);
//                        }
//                    }
//                }
//            });
//
//    private void seenMessage(String userId) {
//        reference = FirebaseDatabase.getInstance().getReference("Chats");
//        seenListener = reference.addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
//                    Chat chat = snapshot.getValue(Chat.class);
//                    if (chat != null && chat.getReceiver() != null && chat.getSender() != null) {
//                        if (chat.getReceiver().equals(fuser.getUid()) && chat.getSender().equals(userId)) {
//                            HashMap<String, Object> hashMap = new HashMap<>();
//                            hashMap.put("isseen", true);
//                            snapshot.getRef().updateChildren(hashMap);
//                        }
//                    }
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//            }
//        });
//    }
//
//    private void sendMessage(String sender, String receiver, String message, String imageUrl) {
//        final String userid = intent.getStringExtra("userid");
//        DatabaseReference reference = FirebaseDatabase.getInstance().getReference();
//
//        boolean isImage = !imageUrl.isEmpty();
//        String type = isImage ? "image" : "text";
//
//        HashMap<String, Object> hashMap = new HashMap<>();
//        hashMap.put("sender", sender);
//        hashMap.put("receiver", receiver);
//        hashMap.put("message", message);
//        hashMap.put("imageUrl", imageUrl);
//        hashMap.put("type", type);
//        hashMap.put("isseen", false);
//
//        reference.child("Chats").push().setValue(hashMap);
//
//        updateChatList(fuser.getUid(), userid);
//
//        String notificationMessage = isImage ? "đã gửi một hình ảnh" : message;
//
//        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(fuser.getUid());
//        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                User user = dataSnapshot.getValue(User.class);
//                if (notify && user != null) {
//                    sendNotification(receiver, user.getUserName(), notificationMessage);
//                    notify = false;
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//            }
//        });
//    }
//
//    private void sendNotification(String receiver, String username, String message) {
//        DatabaseReference tokens = FirebaseDatabase.getInstance().getReference("Tokens");
//        Query query = tokens.orderByKey().equalTo(receiver);
//        query.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
//                    Token token = snapshot.getValue(Token.class);
//                    Data data = new Data(fuser.getUid(), R.mipmap.ic_launcher, username + ": " + message, "Tin nhắn mới", receiver);
//                    Sender sender = new Sender(data, Objects.requireNonNull(token).getToken());
//
//                    apiServer.sendNotification(sender).enqueue(new Callback<MyResponse>() {
//                        @Override
//                        public void onResponse(@NonNull Call<MyResponse> call, @NonNull Response<MyResponse> response) {
//                            if (response.isSuccessful() && response.body() != null && response.body().success != 1) {
//                                Toast.makeText(MessagesActivity.this, "Failed to send notification", Toast.LENGTH_SHORT).show();
//                            }
//                        }
//
//                        @Override
//                        public void onFailure(@NonNull Call<MyResponse> call, @NonNull Throwable throwable) {
//                            Toast.makeText(MessagesActivity.this, "Failed to send notification", Toast.LENGTH_SHORT).show();
//                        }
//                    });
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e("sendNotification", "Failed to get token", error.toException());
//            }
//        });
//    }
//
//    private void updateChatList(String senderId, String receiverId) {
//        DatabaseReference chatRefSender = FirebaseDatabase.getInstance().getReference("Chatlist")
//                .child(senderId)
//                .child(receiverId);
//        chatRefSender.child("id").setValue(receiverId);
//
//        DatabaseReference chatRefReceiver = FirebaseDatabase.getInstance().getReference("Chatlist")
//                .child(receiverId)
//                .child(senderId);
//        chatRefReceiver.child("id").setValue(senderId);
//    }
//
//    private void readMessages(String myId, String userId, String imageURL) {
//        mChat = new ArrayList<>();
//        reference = FirebaseDatabase.getInstance().getReference("Chats");
//
//        reference.addValueEventListener(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
////                mChat.clear();
//                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
//                    Chat chat = snapshot.getValue(Chat.class);
//                    if (chat != null && chat.getReceiver() != null && chat.getSender() != null) {
//                        if ((chat.getReceiver().equals(myId) && chat.getSender().equals(userId)) ||
//                                (chat.getReceiver().equals(userId) && chat.getSender().equals(myId))) {
//                            mChat.add(chat);
//                        }
//                    }
//                }
//                messageAdapter = new MessageAdapter(MessagesActivity.this, mChat, imageURL);
//                recyclerView.setAdapter(messageAdapter);
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e("DatabaseError", "Data fetching cancelled: " + error.getMessage());
//            }
//        });
//    }
//
//    @Override
//    public boolean dispatchTouchEvent(MotionEvent event) {
//        if (event.getAction() == MotionEvent.ACTION_DOWN) {
//            View v = getCurrentFocus();
//            if (v instanceof EditText) {
//                Rect outRect = new Rect();
//                v.getGlobalVisibleRect(outRect);
//                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
//                    v.clearFocus();
//                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
//                    imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
//                }
//            }
//        }
//        return super.dispatchTouchEvent(event);
//    }
//
//    private void currentUser(String userId) {
//        SharedPreferences sharedPreferences = getSharedPreferences("PREFS", MODE_PRIVATE);
//        SharedPreferences.Editor editor = sharedPreferences.edit();
//        editor.putString("current_user", userId);
//        editor.apply();
//    }
//
//    @SuppressLint("SetTextI18n")
//    private void init() {
//        if (userid != null && fuser != null) {
//            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(userid);
//            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
//                @Override
//                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                    User user = dataSnapshot.getValue(User.class);
//                    if (user != null) {
//                        setupMessageAndCallFeatures(userid);
//                    readMessages(fuser.getUid(), userid, user.getImageURL());
//
//                    } else {
//                        Toast.makeText(MessagesActivity.this, "Không tìm thấy thông tin người dùng.", Toast.LENGTH_SHORT).show();
//                    }
//                }
//
//                @Override
//                public void onCancelled(@NonNull DatabaseError error) {
//                    Toast.makeText(MessagesActivity.this, "Lỗi khi truy cập dữ liệu người dùng.", Toast.LENGTH_SHORT).show();
//                }
//            });
//        } else {
//            Toast.makeText(this, "Thông tin người dùng không hợp lệ.", Toast.LENGTH_SHORT).show();
//        }
//    }
//    @SuppressLint("SetTextI18n")
//    private void setupMessageAndCallFeatures(String userIdOfOtherUser) {
//        Log.d("SetupFeatures", "Preparing to set up features for communication with " + userIdOfOtherUser);
//
//        String currentUserName = SharedPreferencesManager.getUsername(getApplicationContext());
//        if (currentUserName == null || currentUserName.isEmpty()) {
//            Log.e("CallInitiation", "Current user's username is null or empty.");
//            Toast.makeText(this, "Current user information not available.", Toast.LENGTH_SHORT).show();
//            return;
//        }
//
//        Log.d("CallInitiation", "Current user's username: " + currentUserName);
//        Log.d("CallInitiation", "Attempting to initiate call from " + currentUserName + " to " + userIdOfOtherUser);
//
//        views.callBtn.setOnClickListener(v -> {
//            Toast.makeText(this, "Đã gửi yêu cầu gọi đi.", Toast.LENGTH_SHORT).show();
//            notifyCallRequest();
//            mainRespository.sendCallRequest(userIdOfOtherUser, () -> {
//                Log.e("CallRequest", "Failed to find user: " + userIdOfOtherUser);
//                Toast.makeText(MessagesActivity.this, "Không tìm thấy người dùng", Toast.LENGTH_SHORT).show();
//            });
//        });
//
//        mainRespository.initLocalView(views.localView);
//        mainRespository.initRemoteView(views.remoteView);
//        mainRespository.listener = this;
//
//        mainRespository.subscribeForLatestEvent(data -> {
//            reference = FirebaseDatabase.getInstance().getReference("Users").child(userid);
//            reference.addValueEventListener(new ValueEventListener() {
//                @Override
//                public void onDataChange(@NonNull DataSnapshot snapshot) {
//                    User user = snapshot.getValue(User.class);
//                    if (data.getType() == DataModelType.StartCall) {
//                        Log.d("CallReceived", "Received StartCall event from: " + data.getSender());
//                        runOnUiThread(() -> {
//                            assert user != null;
//                            views.incomingNameTV.setText(user.getUserName() + " đang gọi tới bạn.");
//                            views.incomingCallLayout.setVisibility(View.VISIBLE);
//
//                            views.acceptButton.setOnClickListener(v -> {
//                                mainRespository.startCall(data.getSender());
//                                views.incomingCallLayout.setVisibility(View.GONE);
//                                handleCallEnd();
//                            });
//
//                            views.rejectButton.setOnClickListener(v -> {
//                                views.incomingCallLayout.setVisibility(View.GONE);
//                                handleCallEnd();
//                            });
//                        });
//                        if (!isActivityVisible()) {
////                            NotificationHelper.displayIncomingCallNotification(getApplicationContext(), data.getSender());
//                        }
//                    }
//                }
//
//                @Override
//                public void onCancelled(@NonNull DatabaseError error) {
//                }
//            });
//            Log.d("CallActivity", "Received data: type = " + data.getType());
//        });
//        views.switchCameraButton.setOnClickListener(view -> {
//            mainRespository.switchCamera();
//        });
//        views.micButton.setOnClickListener(v->{
//            if (isMicrophoneMuted){
//                views.micButton.setImageResource(R.drawable.ic_baseline_mic_off_24);
//            }else {
//                views.micButton.setImageResource(R.drawable.ic_baseline_mic_24);
//            }
//            mainRespository.toggleAudio(isMicrophoneMuted);
//            isMicrophoneMuted=!isMicrophoneMuted;
//            Log.d("WebRTCClient", "Mute button clicked: " + isMicrophoneMuted);
//        });
//
//        views.videoButton.setOnClickListener(v->{
//            if (isCameraMuted){
//                views.videoButton.setImageResource(R.drawable.ic_baseline_videocam_off_24);
//            }else {
//                views.videoButton.setImageResource(R.drawable.ic_baseline_videocam_24);
//            }
//            mainRespository.toggleVideo(isCameraMuted);
//            isCameraMuted=!isCameraMuted;
//        });
//        views.endCallButton.setOnClickListener(view -> {
//            mainRespository.endCall();
//            finish();
//        });
//    }
//
//    private void notifyCallRequest() {
//        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(userid);
//        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                User user = dataSnapshot.getValue(User.class);
//                if (user != null) {
//                    // Send notification to the recipient
//                    sendNotificationCall(userid, user.getUserName(), "gửi yêu cầu gọi");
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e("sendNotification", "Failed to get user data", error.toException());
//            }
//        });
//    }
//
//    private void sendNotificationCall(String receiver, String username, String message) {
//        DatabaseReference tokens = FirebaseDatabase.getInstance().getReference("Tokens");
//        Query query = tokens.orderByKey().equalTo(receiver);
//        query.addListenerForSingleValueEvent(new ValueEventListener() {
//            @Override
//            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
//                    Token token = snapshot.getValue(Token.class);
//                    if (token != null) {
//                        Data data = new Data(fuser.getUid(), R.mipmap.ic_launcher, username + ": " + message, "Cuộc gọi mới", receiver);
//                        Sender sender = new Sender(data, token.getToken());
//
//                        apiServer.sendNotification(sender).enqueue(new Callback<MyResponse>() {
//                            @Override
//                            public void onResponse(@NonNull Call<MyResponse> call, @NonNull Response<MyResponse> response) {
//                                if (!response.isSuccessful() || response.body() == null || response.body().success != 1) {
//                                    Toast.makeText(MessagesActivity.this, "Failed to send notification", Toast.LENGTH_SHORT).show();
//                                }
//                            }
//
//                            @Override
//                            public void onFailure(@NonNull Call<MyResponse> call, @NonNull Throwable throwable) {
//                                Toast.makeText(MessagesActivity.this, "Failed to send notification", Toast.LENGTH_SHORT).show();
//                            }
//                        });
//                    }
//                }
//            }
//
//            @Override
//            public void onCancelled(@NonNull DatabaseError error) {
//                Log.e("sendNotification", "Failed to get token", error.toException());
//            }
//        });
//    }
//    private void handleCallTimeout() {
//        if (views.incomingCallLayout.getVisibility() == View.VISIBLE) {
//            if (fuser != null) {
//                mainRespository.clearLatestEvent(fuser.getUid());
//            }
//            runOnUiThread(() -> {
//                Toast.makeText(this, "No response, call cancelled.", Toast.LENGTH_SHORT).show();
//                views.incomingCallLayout.setVisibility(View.GONE);
//            });
//        }
//    }
//
//    private boolean isActivityVisible() {
//        return !isFinishing() && !isDestroyed();
//    }
//
//    @Override
//    public void webrtcConnected() {
//        callTimeoutRunnable = this::handleCallTimeout;
//        callTimeoutHandler.postDelayed(callTimeoutRunnable, 15000);
//
//        runOnUiThread(() -> {
//            views.incomingCallLayout.setVisibility(View.GONE);
//            findViewById(R.id.toolbar).setVisibility(View.GONE);
//            findViewById(R.id.recyclerView).setVisibility(View.GONE);
//            findViewById(R.id.bottom).setVisibility(View.GONE);
//            views.callLayout.setVisibility(View.VISIBLE);
//            Log.d("CallUI", "Call layout set to visible");
//        });
//    }
//
//    @Override
//    public void webrtcClosed() {
//        callTimeoutHandler.removeCallbacks(callTimeoutRunnable);
//        if (fuser != null) {
//            handleCallEnd();
//        }
//        runOnUiThread(this::finish);
//    }
//
//    private void handleCallEnd() {
//        if (fuser != null) {
//            mainRespository.clearLatestEvent(fuser.getUid());
//        }
//    }
//
//    private void status(String status) {
//        if (fuser != null) {
//            DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Users").child(fuser.getUid());
//            ref.addListenerForSingleValueEvent(new ValueEventListener() {
//                @Override
//                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
//                    User user = dataSnapshot.getValue(User.class);
//                    if (user != null && !user.getStatus().equals(status)) {
//                        HashMap<String, Object> hashMap = new HashMap<>();
//                        hashMap.put("status", status);
//                        ref.updateChildren(hashMap);
//                    }
//                }
//
//                @Override
//                public void onCancelled(@NonNull DatabaseError error) {
//                    Log.e("updateUserStatus", "Failed to update user status", error.toException());
//                }
//            });
//        }
//    }
//
//    @Override
//    protected void onResume() {
//        super.onResume();
//        status("online");
//    }
//
//    @Override
//    protected void onPause() {
//        super.onPause();
//        status("offline");
//    }
//}