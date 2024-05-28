package com.example.chatapp.remote;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.example.chatapp.SharedPreferencesManager.SharedPreferencesManager;
import com.example.chatapp.utils.DataModel;
import com.example.chatapp.utils.ErrorCallback;
import com.example.chatapp.utils.NewEventCallBack;
import com.example.chatapp.utils.SuccessCallBack;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.gson.Gson;

import java.util.Objects;

public class FirebaseClient {
    private final Gson gson = new Gson();
    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
    private static final String LASTER_EVENT_FIELD_NAME = "latest_event";
    private FirebaseAuth firebaseAuth;
    private Context context;
    private String currentUserId;

    public FirebaseClient(Context context) {
        this.context = context;
        this.currentUserId = SharedPreferencesManager.getUsername(context);
        if (this.currentUserId == null) {
            this.firebaseAuth = FirebaseAuth.getInstance();
            if (firebaseAuth.getCurrentUser() != null) {
                this.currentUserId = firebaseAuth.getCurrentUser().getUid();
                SharedPreferencesManager.saveUsername(context, this.currentUserId);
            } else {
                Log.e("FirebaseClient", "No user logged in.");
            }
        }
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    // Phương thức đăng nhập bằng email và mật khẩu
    public void login(String email, String password, SuccessCallBack callback) {
        firebaseAuth = FirebaseAuth.getInstance();
        firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        currentUserId = Objects.requireNonNull(firebaseAuth.getCurrentUser()).getUid();
                        SharedPreferencesManager.saveUsername(context, currentUserId);
                        callback.onSuccess();
                    } else {
                        Exception exception = task.getException();
                        String errorMessage = "Xác thực không thành công";
                        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                            errorMessage = "Sai email hoặc mật khẩu";
                        } else if (exception instanceof FirebaseAuthInvalidUserException) {
                            errorMessage = "Tài khoản không tồn tại";
                        }
                        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // Phương thức đăng nhập bằng số điện thoại
    public void signInByPhone(PhoneAuthCredential credential, SuccessCallBack callback) {
        FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        currentUserId = Objects.requireNonNull(FirebaseAuth.getInstance().getCurrentUser()).getUid();
                        SharedPreferencesManager.saveUsername(context, currentUserId);
                        callback.onSuccess();
                    } else {
                        handleAuthenticationException(task.getException(), context);
                    }
                });
    }

    private void handleAuthenticationException(Exception exception, Context context) {
        String errorMessage = "Xác thực không thành công";
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            errorMessage = "Mã OTP không đúng";
        } else if (exception instanceof FirebaseAuthInvalidUserException) {
            errorMessage = "Tài khoản không tồn tại";
        }
        Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();
    }

    public void sendMessageToOtherUser(DataModel dataModel, ErrorCallback errorCallback) {
        DatabaseReference userRef = dbRef.child("Users").child(dataModel.getTarget());
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String latestEventString = snapshot.child("latest_event").getValue(String.class);
                Log.d("FirebaseClient", "latestEventString: " + latestEventString);
                if (snapshot.exists()) {
                    userRef.child(LASTER_EVENT_FIELD_NAME).setValue(gson.toJson(dataModel));
                } else {
                    errorCallback.onError();
                    Log.e("SendCallRequest", "No such user: " + dataModel.getTarget());
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                errorCallback.onError();
            }
        });
    }

    public void observeInComingLatestEvent(NewEventCallBack callBack) {
        if (currentUserId == null) {
            Log.e("FirebaseClient", "currentUserId is null. Cannot observe incoming events.");
            return;
        }

        dbRef.child("Users").child(currentUserId).child(LASTER_EVENT_FIELD_NAME).addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        Log.d("FirebaseClient", "Data changed: " + snapshot.getValue());
                        try {
                            String data = Objects.requireNonNull(snapshot.getValue()).toString();
                            DataModel dataModel = gson.fromJson(data, DataModel.class);
                            callBack.onNewEventReceived(dataModel);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Log.w("FirebaseClient", "Data does not exist at this location.");
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.e("FirebaseClient", "Error: " + error.getMessage());
                    }
                }
        );
    }
}
