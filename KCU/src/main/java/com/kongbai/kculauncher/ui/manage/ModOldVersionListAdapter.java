package com.kongbai.kculauncher.ui.manage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.mod.LocalModFile;
import com.kongbai.kculauncher.library.component.KCUAdapter;
import com.kongbai.kculauncher.library.component.view.KCULinearLayout;
import com.kongbai.kculauncher.library.component.view.KCUTextView;
import com.kongbai.kculauncher.library.util.ConvertUtils;

import java.util.List;

public class ModOldVersionListAdapter extends KCUAdapter {

    private final ModRollbackDialog dialog;
    private final List<LocalModFile> list;
    private final ModRollbackDialog.Callback callback;

    public ModOldVersionListAdapter(Context context, ModRollbackDialog dialog, List<LocalModFile> list, ModRollbackDialog.Callback callback) {
        super(context);
        this.dialog = dialog;
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

    @SuppressLint("UseCompatLoadingForDrawables")
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
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        LocalModFile localModFile = list.get(i);
        viewHolder.parent.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onOldVersionSelect(localModFile);
        });
        viewHolder.version.setText(localModFile.getVersion());
        return view;
    }
}
