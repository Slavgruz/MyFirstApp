package com.example.myfirstapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class SeparatorAdapter extends RecyclerView.Adapter<SeparatorAdapter.ViewHolder> {
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_separator, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Разделитель статичен, ничего не делаем
    }
    
    @Override
    public int getItemCount() {
        return 1;
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvSeparator;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSeparator = itemView.findViewById(R.id.tvSeparator);
        }
    }
}