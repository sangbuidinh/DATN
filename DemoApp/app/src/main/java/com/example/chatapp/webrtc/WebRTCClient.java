package com.example.chatapp.webrtc;

import android.content.Context;
import android.util.Log;

import com.example.chatapp.utils.DataModel;
import com.example.chatapp.utils.DataModelType;
import com.google.gson.Gson;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

public class WebRTCClient {
    private final Gson gson = new Gson();
    private final Context context;
    private final String userName;
    private final EglBase.Context eglBaseContext = EglBase.create().getEglBaseContext();
    private final PeerConnectionFactory peerConnectionFactory;
    private final PeerConnection peerConnection;
    private final List<PeerConnection.IceServer> iceServer = new ArrayList<>();
    private CameraVideoCapturer videoCapturer;
    private final VideoSource localVideoSource;
    private final AudioSource localAudioSource;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private final MediaConstraints mediaConstraints = new MediaConstraints();
    public Listener listener;

    public WebRTCClient(Context context, PeerConnection.Observer observer, String userName) {
        this.context = context;
        this.userName = userName;
        initPeerConnectionFactory();
        peerConnectionFactory = createPeerConnectionFactory();
        iceServer.add(PeerConnection.IceServer.builder("turn:a.relay.metered.ca:443?transport=tcp")
                .setUsername("83eebabf8b4cce9d5dbcb649")
                .setPassword("2D7JvfkOQtBdYW3R").createIceServer());
        peerConnection = createPeerConnection(observer);

        localVideoSource = peerConnectionFactory.createVideoSource(false);
        localAudioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
    }

    private void initPeerConnectionFactory() {
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(context)
                        .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                        .setEnableInternalTracer(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(options);
    }

    private PeerConnectionFactory createPeerConnectionFactory() {
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.disableEncryption = false;
        options.disableNetworkMonitor = false;
        return PeerConnectionFactory.builder()
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(eglBaseContext, true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(eglBaseContext))
                .setOptions(options).createPeerConnectionFactory();
    }

    private PeerConnection createPeerConnection(PeerConnection.Observer observer) {
        Log.d("WebRTCClient", "Creating peer connection with observer: " + observer);
        return peerConnectionFactory.createPeerConnection(iceServer, observer);
    }

    public void initSurfaceViewRenderer(SurfaceViewRenderer viewRenderer) {
        viewRenderer.setEnableHardwareScaler(true);
        viewRenderer.setMirror(true);
        viewRenderer.init(eglBaseContext, null);
    }

    public void initLocalSurfaceView(SurfaceViewRenderer view) {
        initSurfaceViewRenderer(view);
        startLocalVideoStreaming(view);
    }

//    private void startLocalVideoStreaming(SurfaceViewRenderer view) {
//        SurfaceTextureHelper helper = SurfaceTextureHelper.create(
//                Thread.currentThread().getName(), eglBaseContext
//        );
//        videoCapturer = getVideoCapturer();
//        Log.d("WebRTCClient", "videoCapturer: " + videoCapturer);
//        videoCapturer.initialize(helper, context, localVideoSource.getCapturerObserver());
//        videoCapturer.startCapture(480, 360, 15);
//
//        String localTrackId = "local_track";
//        localVideoTrack = peerConnectionFactory.createVideoTrack(
//                localTrackId + "_video", localVideoSource
//        );
//        localVideoTrack.addSink(view);
//        Log.d("WebRTCClient", "localVideoTrack: " + localVideoTrack);
//
//        localAudioTrack = peerConnectionFactory.createAudioTrack(localTrackId + "_audio", localAudioSource);
//
//        peerConnection.addTrack(localVideoTrack);
//        peerConnection.addTrack(localAudioTrack);
//    }
    private void startLocalVideoStreaming(SurfaceViewRenderer view) {
        SurfaceTextureHelper helper = SurfaceTextureHelper.create(
                Thread.currentThread().getName(), eglBaseContext
        );
        videoCapturer = getVideoCapturer();
        Log.d("WebRTCClient", "videoCapturer: " + videoCapturer);
        videoCapturer.initialize(helper, context, localVideoSource.getCapturerObserver());
        videoCapturer.startCapture(480, 360, 15);

        localVideoTrack = peerConnectionFactory.createVideoTrack("local_track_video", localVideoSource);
        localVideoTrack.addSink(view);
        Log.d("WebRTCClient", "localVideoTrack: " + localVideoTrack);

        localAudioTrack = peerConnectionFactory.createAudioTrack("local_track_audio", localAudioSource);

        peerConnection.addTrack(localVideoTrack);
        peerConnection.addTrack(localAudioTrack);
    }

    private CameraVideoCapturer getVideoCapturer() {
        Camera2Enumerator enumerator = new Camera2Enumerator(context);
        String[] devicesNames = enumerator.getDeviceNames();
        for (String device : devicesNames) {
            if (enumerator.isFrontFacing(device)) {
                return enumerator.createCapturer(device, null);
            }
        }
        throw new IllegalStateException("Front facing camera not found");
    }

    public void initRemoteSurfaceView(SurfaceViewRenderer view) {
        initSurfaceViewRenderer(view);
    }

//    public void call(String target) {
//        try {
//            peerConnection.createOffer(new MySdpObserver() {
//                @Override
//                public void onCreateSuccess(SessionDescription sessionDescription) {
//                    super.onCreateSuccess(sessionDescription);
//                    peerConnection.setLocalDescription(new MySdpObserver() {
//                        @Override
//                        public void onSetSuccess() {
//                            super.onSetSuccess();
//                            if (listener != null) {
//                                listener.onTransferDataToOtherPeer(new DataModel(
//                                        target, userName, sessionDescription.description, DataModelType.Offer
//                                ));
//                            }
//                        }
//                    }, sessionDescription);
//                }
//            }, mediaConstraints);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
//
//    public void answer(String target) {
//        try {
//            peerConnection.createAnswer(new MySdpObserver() {
//                @Override
//                public void onCreateSuccess(SessionDescription sessionDescription) {
//                    super.onCreateSuccess(sessionDescription);
//                    peerConnection.setLocalDescription(new MySdpObserver() {
//                        @Override
//                        public void onSetSuccess() {
//                            super.onSetSuccess();
//                            if (listener != null) {
//                                listener.onTransferDataToOtherPeer(new DataModel(
//                                        target, userName, sessionDescription.description, DataModelType.Answer
//                                ));
//                            }
//                        }
//                    }, sessionDescription);
//                }
//            }, mediaConstraints);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//    }
public void call(String target) {
    try {
        peerConnection.createOffer(new MySdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                peerConnection.setLocalDescription(new MySdpObserver() {
                    @Override
                    public void onSetSuccess() {
                        super.onSetSuccess();
                        if (listener != null) {
                            listener.onTransferDataToOtherPeer(new DataModel(
                                    target, userName, sessionDescription.description, DataModelType.Offer
                            ));
                        }
                    }
                }, sessionDescription);
            }
        }, mediaConstraints);
    } catch (Exception e) {
        e.printStackTrace();
    }
}

    public void answer(String target) {
        try {
            peerConnection.createAnswer(new MySdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    super.onCreateSuccess(sessionDescription);
                    peerConnection.setLocalDescription(new MySdpObserver() {
                        @Override
                        public void onSetSuccess() {
                            super.onSetSuccess();
                            if (listener != null) {
                                listener.onTransferDataToOtherPeer(new DataModel(
                                        target, userName, sessionDescription.description, DataModelType.Answer
                                ));
                            }
                        }
                    }, sessionDescription);
                }
            }, mediaConstraints);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void OnRemoteSessionReceived(SessionDescription sessionDescription) {
        peerConnection.setRemoteDescription(new MySdpObserver(), sessionDescription);
    }

    public void addIceCandidate(IceCandidate iceCandidate) {
        peerConnection.addIceCandidate(iceCandidate);
    }

    public void sendIceCandidate(IceCandidate iceCandidate, String target) {
        addIceCandidate(iceCandidate);
        if (listener != null) {
            listener.onTransferDataToOtherPeer(new DataModel(
                    target, userName, gson.toJson(iceCandidate), DataModelType.IceCandidate
            ));
        }
    }

    public void swichCamera() {
        videoCapturer.switchCamera(null);
    }

    public void toggleVideo(Boolean shouldBeMuted) {
        localVideoTrack.setEnabled(!shouldBeMuted);
    }

    public void toggleAudio(Boolean shouldBeMuted) {
        if (localAudioTrack != null) {
            localAudioTrack.setEnabled(!shouldBeMuted);
            Log.d("WebRTCClient", "Audio is now " + (shouldBeMuted ? "muted" : "unmuted"));
        }
    }

    public void closeConnection() {
        try {
            localVideoTrack.dispose();
            videoCapturer.stopCapture();
            videoCapturer.dispose();
            peerConnection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public interface Listener {
        void onTransferDataToOtherPeer(DataModel model);
    }
}
