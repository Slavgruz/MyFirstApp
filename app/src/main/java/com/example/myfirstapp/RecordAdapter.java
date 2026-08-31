package com.example.myfirstapp;

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

public class RecordAdapter extends RecyclerView.Adapter<RecordAdapter.ViewHolder> {
    
    private List<Reading> readings;
    private MeterDatabase db;
    private OnItemClickListener listener;
    
    public interface OnItemClickListener {
        void onItemClick(Reading reading);
    }
    
    public RecordAdapter(List<Reading> readings, MeterDatabase db, OnItemClickListener listener) {
        this.readings = readings;
        this.db = db;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_record, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Reading reading = readings.get(position);
        
        Meter meter = db.getMeter(reading.getMeterId());
        if (meter != null) {
            holder.tvResource.setText(meter.getResource());
            
            Address address = db.getAddress(meter.getAddressId());
            if (address != null) {
                holder.tvAddress.setText(address.getFullAddress());
            }
        }
        
        String dateStr = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(new Date(reading.getDatetime()));
        holder.tvDate.setText(dateStr);
        
        holder.tvReading.setText(reading.getConfirmedReading());
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(reading);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return readings != null ? readings.size() : 0;
    }
    
    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvResource, tvAddress, tvDate, tvReading;
        
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvResource = itemView.findViewById(R.id.tvResource);
            tvAddress = itemView.findViewById(R.id.tvAddress);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvReading = itemView.findViewById(R.id.tvReading);
        }
    }
}