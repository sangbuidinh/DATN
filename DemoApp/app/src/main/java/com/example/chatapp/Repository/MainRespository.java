package com.example.chatapp.Repository;

import android.content.Context;
import android.util.Log;

import com.example.chatapp.remote.FirebaseClient;
import com.example.chatapp.utils.DataModel;
import com.example.chatapp.utils.DataModelType;
import com.example.chatapp.utils.ErrorCallback;
import com.example.chatapp.utils.NewEventCallBack;
import com.example.chatapp.utils.SuccessCallBack;
import com.example.chatapp.webrtc.MyPeerConnectionObserver;
import com.example.chatapp.webrtc.WebRTCClient;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.gson.Gson;

import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;

import java.util.HashMap;

public class MainRespository implements WebRTCClient.Listener {
   public Listener listener;
    private final Gson gson = new Gson();
    private final FirebaseClient firebaseClient;
    private String currentUserId;
    private void updateCurrentUserId(String userId) {
        this.currentUserId = userId;
    }
    private SurfaceViewRenderer remoteView;
    private String target;
    private WebRTCClient webRTCClient;
    private MainRespository(Context context) {
        this.firebaseClient = new FirebaseClient(context);
    }
    private static MainRespository instance;

    public static MainRespository getInstance(Context context) {
        if (instance == null) {
            instance = new MainRespository(context);
        }
        return instance;
    }

    public void login(String email, String password, Context context, SuccessCallBack callback) {
        Log.d("LoginCheck", "Logging in with email: " + email);
        firebaseClient.login(email, password, () -> {
            String userIdromFirebase = firebaseClient.getCurrentUserId();
            updateCurrentUserId(userIdromFirebase);
            Log.d("LoginCheck", "Current user name set after login: " + userIdromFirebase);

//                if (userNameFromFirebase != null && !userNameFromFirebase.isEmpty()) {
//            initWebRTCClient(context, userIdromFirebase);
//                } else {
//                    Log.e("LoginCheck", "Username from Firebase is null or empty after login.");
//                }
            callback.onSuccess();
        });
    }

    public void loginByPhone(PhoneAuthCredential credential, Context context, SuccessCallBack callback) {
        Log.d("LoginCheck", "Logging in with phone credential");
        firebaseClient.signInByPhone(credential, () -> {
            String userIdFromFirebase = firebaseClient.getCurrentUserId();
            updateCurrentUserId(userIdFromFirebase);
            Log.d("LoginCheck", "Current user name set after phone login: " + userIdFromFirebase);
//            initWebRTCClient(context, userIdFromFirebase);
            callback.onSuccess();
        });
    }

    public void initWebRTCClient(Context context, String userIdromFirebase) {
        if (webRTCClient != null) {
            webRTCClient.closeConnection(); // Đảm bảo đóng kết nối cũ nếu có
        }
        webRTCClient = new WebRTCClient(context, new MyPeerConnectionObserver() {
            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d("WebRTC", "Stream received with " + mediaStream.videoTracks.size() + " video tracks and " + mediaStream.audioTracks.size() + " audio tracks.");
                Log.d("WebRTCClient", "Stream added with video track count: " + mediaStream.videoTracks.size());
                super.onAddStream(mediaStream);
                try {
                    if (!mediaStream.videoTracks.isEmpty()) {
                        mediaStream.videoTracks.get(0).addSink(remoteView);
                        Log.d("WebRTCClient", "Video track added to remote view.");
                    }
                } catch (Exception e) {
                    Log.e("WebRTCClient", "Error adding stream to remoteView", e);
                }
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                super.onConnectionChange(newState);
                Log.d("WebRTCClient", "Connection state changed to: " + newState);
                if (newState == PeerConnection.PeerConnectionState.CONNECTED && listener != null) {
                    listener.webrtcConnected();
                }
                if (newState == PeerConnection.PeerConnectionState.CLOSED || newState == PeerConnection.PeerConnectionState.DISCONNECTED) {
                    if (listener != null) {
                        listener.webrtcClosed();
                    }
                }
            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                Log.d("WebRTCClient", "Ice candidate received: " + iceCandidate.sdp);
                webRTCClient.sendIceCandidate(iceCandidate, target);
            }
        }, userIdromFirebase);
        webRTCClient.listener = MainRespository.this;
    }


    public void initLocalView(SurfaceViewRenderer view){
        webRTCClient.initLocalSurfaceView(view);
    }
    public void initRemoteView(SurfaceViewRenderer view){
        webRTCClient.initRemoteSurfaceView(view);
        this.remoteView = view;
    }
    public void startCall(String target){
        webRTCClient.call(target);
    }
    public void switchCamera(){
        webRTCClient.swichCamera();
    }
    public void toggleAudio(Boolean shouldBeMuted){
        webRTCClient.toggleAudio(shouldBeMuted);
        Log.d("MainRespository", "Toggling audio: " + shouldBeMuted);

    }
    public void toggleVideo(Boolean shouldBeMuted){
        webRTCClient.toggleVideo(shouldBeMuted);

    }
    public void sendCallRequest(String targetUserId, ErrorCallback errorCallback) {
        if (currentUserId == null || currentUserId.isEmpty()) {
            Log.e("SendCallRequest", "currentUserId is null or empty.");
            errorCallback.onError();
            return;
        }
        Log.d("SendCallRequest", "Sending call request from " + currentUserId + " to " + targetUserId);
        firebaseClient.sendMessageToOtherUser(
                new DataModel(targetUserId, currentUserId, null, DataModelType.StartCall), errorCallback
        );
    }

    public void clearLatestEvent(String userId) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId);
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("latest_event", null); // Cập nhật latest_event thành null hoặc bạn có thể dùng ""
        userRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.d("Firebase", "Latest event cleared successfully.");
            } else {
                Log.e("Firebase", "Failed to clear latest event.", task.getException());
            }
        });
    }


    public void endCall(){
        webRTCClient.closeConnection();
    }
    public void subscribeForLatestEvent(NewEventCallBack callBack){
        firebaseClient.observeInComingLatestEvent(model -> {
            Log.d("FirebaseEvent", "Received event: " + model.getType());
            switch (model.getType()){

                case Offer:
                    this.target = model.getSender();
                    webRTCClient.OnRemoteSessionReceived(new SessionDescription(
                            SessionDescription.Type.OFFER, model.getData()
                    ));
                    webRTCClient.answer(model.getSender());
                    break;
                case Answer:
                    this.target = model.getSender();
                    webRTCClient.OnRemoteSessionReceived(new SessionDescription(
                            SessionDescription.Type.ANSWER, model.getData()
                    ));
                    break;
                case IceCandidate:
                        try {
                            IceCandidate candidate = gson.fromJson(model.getData(), IceCandidate.class);
                            webRTCClient.addIceCandidate(candidate);
                        } catch (Exception e){
                            e.printStackTrace();
                        }
                    break;
                case StartCall:
                    this.target = model.getSender();
                    callBack.onNewEventReceived(model);
                    break;
            }
        });
    }

    @Override
    public void onTransferDataToOtherPeer(DataModel model) {
        firebaseClient.sendMessageToOtherUser(model, ()->{});
    }
    public interface Listener{
        void webrtcConnected();
        void webrtcClosed();
    }
    //(tag:WebRTCClient | tag:MainRespository | tag:SendCallRequest | tag:FirebaseEvent | tag:FirebaseClient | tag:SetupFeatures | tag:CallInitiation | tag:CallRequest | tag:CallReceived | tag:runOnUiThread )
}
