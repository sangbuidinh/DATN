package com.example.chatapp.Adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.chatapp.Model.Chat;
import com.meetme.chatapp.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {
    public static final int MSG_TYPE_LEFT = 0;
    public static final int MSG_TYPE_RIGHT = 1;
    public static final int MSG_TYPE_IMAGE_LEFT = 2;
    public static final int MSG_TYPE_IMAGE_RIGHT = 3;

    private final Context mContext;
    private final List<Chat> mChat;
    private final String imageURL;

    FirebaseUser fusers;

    public MessageAdapter(Context mContext, List<Chat> mChat, String imageURL) {
        this.mChat = mChat;
        this.mContext = mContext;
        this.imageURL = imageURL;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == MSG_TYPE_RIGHT || viewType == MSG_TYPE_IMAGE_RIGHT) {
            return new ViewHolder(LayoutInflater.from(mContext).inflate(R.layout.chat_item_right, parent, false));
        } else {
            return new ViewHolder(LayoutInflater.from(mContext).inflate(R.layout.chat_item_left, parent, false));
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull MessageAdapter.ViewHolder holder, int position) {
        Chat chat = mChat.get(position);

        // Sử dụng kiểm tra type để xác định nếu tin nhắn là hình ảnh
        if ("image".equals(chat.getType())) {
            holder.show_message.setVisibility(View.GONE);
            holder.iv_showImage.setVisibility(View.VISIBLE);
            Glide.with(mContext).load(chat.getMessage()).into(holder.iv_showImage); // Sử dụng trường 'message' chứa URL
        } else {
            holder.iv_showImage.setVisibility(View.GONE);
            holder.show_message.setVisibility(View.VISIBLE);
            holder.show_message.setText(chat.getMessage());
        }

        // Thiết lập hình ảnh đại diện nếu có
        if (getItemViewType(position) == MSG_TYPE_LEFT || getItemViewType(position) == MSG_TYPE_IMAGE_LEFT) {
            if (imageURL != null && !imageURL.equals("default")) {
                Glide.with(mContext).load(imageURL).into(holder.profileImage);
            } else {
                holder.profileImage.setImageResource(R.mipmap.ic_launcher);
            }
        } else {
            // Ẩn hình ảnh đại diện đối với tin nhắn gửi đi (phải)
            holder.profileImage.setVisibility(View.GONE);
        }

        // Kiểm tra nếu là tin nhắn gửi đi (MSG_TYPE_RIGHT) để hiển thị trạng thái
        if (getItemViewType(position) == MSG_TYPE_RIGHT && position == mChat.size()-1) {
            if (chat.isIsseen()){
                holder.txt_seen.setText("Đã xem");
                holder.txt_seen.setVisibility(View.VISIBLE);
            } else {
                holder.txt_seen.setText("Đã gửi");
                holder.txt_seen.setVisibility(View.VISIBLE);
            }
        } else {
            // Ẩn trạng thái đối với tin nhắn nhận
            holder.txt_seen.setVisibility(View.GONE);
        }
    }



    @Override
    public int getItemCount() {
        return mChat.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder{
        public TextView show_message;
        public ImageView profileImage;
        public ImageView iv_showImage;
        public TextView txt_seen;
        public ViewHolder(View itemView){
            super(itemView);

            show_message = itemView.findViewById(R.id.show_message);
            profileImage = itemView.findViewById(R.id.profileImage);
            iv_showImage = itemView.findViewById(R.id.iv_showImage);
            txt_seen = itemView.findViewById(R.id.txt_seen);
        }
    }

//    @Override
//    public int getItemViewType(int position) {
//        fusers = FirebaseAuth.getInstance().getCurrentUser();
//        if (mChat.get(position).getSender().equals(fusers.getUid())){
//            return MSG_TYPE_RIGHT;
//        } else {
//            return MSG_TYPE_LEFT;
//        }
//    }
    @Override
    public int getItemViewType(int position) {
        Chat chat = mChat.get(position);
        fusers = FirebaseAuth.getInstance().getCurrentUser();

        // Kiểm tra chat.getReceiver() và chat.getImageUrl() không null trước khi so sánh
        boolean isImage = chat.getImageUrl() != null && !chat.getImageUrl().isEmpty() && !chat.getImageUrl().equals("default");

        if (isImage) {
            return chat.getSender().equals(fusers.getUid()) ? MSG_TYPE_IMAGE_RIGHT : MSG_TYPE_IMAGE_LEFT;
        } else {
            return chat.getSender().equals(fusers.getUid()) ? MSG_TYPE_RIGHT : MSG_TYPE_LEFT;
        }
    }


}
