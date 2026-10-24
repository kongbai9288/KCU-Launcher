package com.kongbai.kculauncher.ui.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.control.download.ControllerVersion;
import com.kongbai.kculauncher.library.component.KCUAdapter;
import com.kongbai.kculauncher.library.component.view.KCULinearLayout;
import com.kongbai.kculauncher.library.component.view.KCUTextView;
import com.kongbai.kculauncher.library.util.ConvertUtils;

import java.util.ArrayList;

public class HistoricalListAdapter extends KCUAdapter {

    private ArrayList<ControllerVersion.VersionInfo> list;
    private Callback callback;

    public HistoricalListAdapter(Context context, ArrayList<ControllerVersion.VersionInfo> list, Callback callback) {
        super(context);
        this.list = list;
        this.callback = callback;
    }

    private static class ViewHolder {
        KCULinearLayout parent;
        KCUTextView version;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int i) {
        return list.get(i);
    }

    @SuppressLint({"UseCompatLoadingForDrawables", "SetTextI18n"})
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        final ViewHolder viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder();
            view = new KCULinearLayout(getContext());
            viewHolder.parent = new KCULinearLayout(getContext());
            viewHolder.version = new KCUTextView(getContext());
            ((KCULinearLayout) view).addView(viewHolder.parent, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            int padding = ConvertUtils.dip2px(getContext(), 10);
            viewHolder.parent.setPadding(padding, padding, padding, padding);
            viewHolder.parent.setBackground(getContext().getDrawable(R.drawable.bg_container_transparent_clickable));
            viewHolder.parent.addView(viewHolder.version, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            viewHolder.version.setSingleLine(true);
            viewHolder.version.setTextColor(Color.GRAY);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        viewHolder.parent.setOnClickListener(v -> callback.onSelect(list.get(i)));
        viewHolder.version.setText(list.get(i).getVersionName());
        return view;
    }

    public interface Callback {
        void onSelect(ControllerVersion.VersionInfo versionInfo);
    }
}
