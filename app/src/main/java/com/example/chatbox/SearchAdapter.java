package com.example.chatbox;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import model.Message;

public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.SearchViewHolder> {
    private List<Message> list;
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(Message message);
    }

    public SearchAdapter(List<Message> list, OnItemClickListener listener) {
        this.list = list;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SearchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
        return new SearchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchViewHolder holder, int position) {
        Message msg = list.get(position);
        holder.tvContent.setText(msg.getText());

        // Format ngày tháng đẹp
        try {
            long time = Long.parseLong(msg.getTimestamp());
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault());
            holder.tvTime.setText(sdf.format(new Date(time)));
        } catch (Exception e) {
            holder.tvTime.setText("Unknown date");
        }

        holder.itemView.setOnClickListener(v -> listener.onItemClick(msg));
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class SearchViewHolder extends RecyclerView.ViewHolder {
        TextView tvContent, tvTime;
        public SearchViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tvResultContent);
            tvTime = itemView.findViewById(R.id.tvResultTime);
        }
    }
}