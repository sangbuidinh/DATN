package com.example.chatapp.Notification;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.chatapp.MessagesActivity;

public class NotificationHelper {
    private static final String CHANNEL_ID = "CALL_NOTIFICATION_CHANNEL";
    private static final int NOTIFICATION_ID = 1;

    @SuppressLint({"ObsoleteSdkInt", "MissingPermission"})
    public static void displayIncomingCallNotification(Context context, String callerName) {
        // Tạo một Intent để mở Activity khi nhấn vào thông báo
        Intent intent = new Intent(context, MessagesActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        @SuppressLint("UnspecifiedImmutableFlag") PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        // Tạo và cấu hình NotificationChannel, yêu cầu cho Android 8.0 (API level 26) trở lên
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Incoming Calls";
            String description = "Notifications for incoming calls";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        // Xây dựng và cấu hình thông báo
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.sym_call_incoming)
                .setContentTitle("Cuộc gọi đến")
                .setContentText("Bạn có một cuộc gọi từ " + callerName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true) // Để thông báo hiển thị trên màn hình đang tắt
                .setAutoCancel(true);

        // Hiển thị thông báo
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }


}
