package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterRestoreBinding;
import com.github.catvod.utils.Path;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RestoreAdapter extends RecyclerView.Adapter<RestoreAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<File> mItems;

    public RestoreAdapter(OnClickListener listener) {
        this.mItems = new ArrayList<>();
        this.mListener = listener;
        this.addAll();
    }

    public interface OnClickListener {

        void onItemClick(File item);

        void onDeleteClick(File item);
    }

    public void addAll() {
        File[] files = Path.tv().listFiles();
        if (files == null || files.length == 0) return;
        for (File file : files) if (file.getName().startsWith("tv") && file.getName().endsWith(".bk.gz")) mItems.add(file);
        Collections.sort(mItems, Collections.reverseOrder());
        notifyDataSetChanged();
    }

    public void remove(File item) {
        int position = mItems.indexOf(item);
        if (position == -1) return;
        Path.clear(item);
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterRestoreBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File item = mItems.get(position);
        holder.binding.text.setText(item.getName());
        holder.binding.text.setOnClickListener(v -> mListener.onItemClick(item));
        holder.binding.delete.setOnClickListener(v -> mListener.onDeleteClick(item));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterRestoreBinding binding;

        public ViewHolder(@NonNull AdapterRestoreBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}