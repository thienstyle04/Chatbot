package com.example.chatbox;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import model.Message;

// 1. SỬA: Dùng RecyclerView.ViewHolder làm kiểu chung để chấp nhận mọi loại View
public class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final int VIEW_TYPE_LOADING = 3;

    private final List<Message> messageList;

    public MessageAdapter(List<Message> messageList) {
        this.messageList = messageList != null ? messageList : new ArrayList<>();
    }

    public void addLoading() {
        messageList.add(new Message(true));
        notifyItemInserted(messageList.size() - 1);
    }

    public void removeLoading() {
        if (!messageList.isEmpty()) {
            int lastIndex = messageList.size() - 1;
            if (messageList.get(lastIndex).isLoading()) {
                messageList.remove(lastIndex);
                notifyItemRemoved(lastIndex);
            }
        }
    }

    public void addOldMessages(List<Message> oldMessages) {
        if (oldMessages == null || oldMessages.isEmpty()) return;
        messageList.addAll(0, oldMessages);
        notifyItemRangeInserted(0, oldMessages.size());
    }

    @Override
    public int getItemViewType(int position) {
        Message msg = messageList.get(position);
        if (msg.isLoading()) return VIEW_TYPE_LOADING;
        return msg.isSentByUser() ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    // 2. SỬA: Kiểu trả về là RecyclerView.ViewHolder
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        if (viewType == VIEW_TYPE_LOADING) {
            View view = inflater.inflate(R.layout.item_loading, parent, false);
            return new LoadingViewHolder(view);
        } else if (viewType == VIEW_TYPE_SENT) {
            View view = inflater.inflate(R.layout.item_message_sent, parent, false);
            return new SentMessageViewHolder(view);
        } else if (viewType == VIEW_TYPE_RECEIVED) {
            View view = inflater.inflate(R.layout.item_message_received, parent, false);
            return new ReceivedMessageViewHolder(view);
        }
        throw new IllegalArgumentException("Invalid View Type");
    }

    @Override
    // 3. SỬA: Tham số đầu vào là RecyclerView.ViewHolder
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof LoadingViewHolder) {
            // Loading tự chạy animation, không cần bind dữ liệu
            return;
        }

        Message message = messageList.get(position);
        if (holder instanceof SentMessageViewHolder) {
            ((SentMessageViewHolder) holder).bind(message);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            ((ReceivedMessageViewHolder) holder).bind(message);
        }
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    // --- ViewHolder Classes ---

    static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(@NonNull View itemView) {
            super(itemView);
            View dot1 = itemView.findViewById(R.id.dot1);
            View dot2 = itemView.findViewById(R.id.dot2);
            View dot3 = itemView.findViewById(R.id.dot3);

            animateDot(dot1, 0);
            animateDot(dot2, 200);
            animateDot(dot3, 400);
        }

        private void animateDot(View dot, long delay) {
            ObjectAnimator animator = ObjectAnimator.ofFloat(dot, "translationY", 0f, -15f);
            animator.setDuration(400);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setStartDelay(delay);
            animator.start();
        }
    }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        SentMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_message_body);
        }
        void bind(Message message) { messageText.setText(message.getText()); }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        TextView messageText;
        ReceivedMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.text_message_body);
        }
        void bind(Message message) { messageText.setText(message.getText()); }
    }
}