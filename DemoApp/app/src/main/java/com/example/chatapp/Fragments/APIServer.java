package com.example.chatapp.Fragments;

import com.example.chatapp.Notifications.MyResponse;
import com.example.chatapp.Notifications.Sender;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public interface APIServer {
    @Headers({
                    "Content-Type:application/json",
                    "Authorization:key=AAAAVHKz72c:APA91bFIU5VIK_IxNNNV4hXcEBhF4R9nINqJZxIVqOJWWqPenGpADL75kBcqrqaF9HU9dGcbaSxe0SLarXIkh2rbog09TSeCAl4DPMRjZ6poP80RuzzQEgu-7YMwYWsm8ebE5MyZQkoi"
            })

    @POST("fcm/send")
    Call<MyResponse> sendNotification(@Body Sender body);
}
