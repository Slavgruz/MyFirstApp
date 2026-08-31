package com.example.myfirstapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class DigitAdapter extends RecyclerView.Adapter<DigitAdapter.ViewHolder> {
    
    private List<DigitItem> digits;
    
    public DigitAdapter(List<DigitItem> digits) {
        this.digits = digits;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_digit, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DigitItem item = digits.get(position);
        holder.tvDigit.setText(String.valueOf(item.getValue()));
    }
    
    @Override
    public int getItemCount() {
        return digits.size();
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDigit;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDigit = itemView.findViewById(R.id.tvDigit);
        }
    }
}