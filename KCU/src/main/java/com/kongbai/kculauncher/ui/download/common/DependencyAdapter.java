package com.kongbai.kculauncher.ui.download.common;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.util.ModTranslations;
import com.kongbai.kculauncher.core.mod.RemoteMod;
import com.kongbai.kculauncher.library.component.KCUAdapter;
import com.kongbai.kculauncher.library.util.LocaleUtils;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.view.KCULinearLayout;
import com.kongbai.kculauncher.library.component.view.KCUTextView;
import com.kongbai.kculauncher.library.util.ConvertUtils;

import java.util.List;

public class DependencyAdapter extends KCUAdapter {

    private final DownloadPage downloadPage;
    private final List<RemoteMod> list;
    private final Callback callback;

    public DependencyAdapter(Context context, DownloadPage page, List<RemoteMod> list, Callback callback) {
        super(context);
        this.downloadPage = page;
        this.list = list;
        this.callback = callback;
    }

    private static class ViewHolder {
        KCULinearLayout parent;
        KCUTextView name;
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
            viewHolder.name = new KCUTextView(getContext());
            ((KCULinearLayout) view).addView(viewHolder.parent, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            int padding = ConvertUtils.dip2px(getContext(), 10);
            viewHolder.parent.setPadding(padding, padding, padding, padding);
            viewHolder.parent.setBackground(getContext().getDrawable(R.drawable.bg_container_transparent_clickable));
            viewHolder.parent.addView(viewHolder.name, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            viewHolder.name.setSingleLine(true);
            viewHolder.name.setAutoTint(true);
            viewHolder.name.setTextColor(ThemeEngine.getInstance().getTheme().getAutoTint());
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        RemoteMod remoteMod = list.get(i);
        viewHolder.parent.setOnClickListener(v -> callback.onItemSelect(remoteMod));
        ModTranslations.Mod mod = ModTranslations.getTranslationsByRepositoryType(downloadPage.repository.getType()).getModByCurseForgeId(remoteMod.getSlug());
        viewHolder.name.setText(getContext().getString(R.string.mods_dependency) + ": " + (mod != null && LocaleUtils.isChinese(getContext()) ? mod.getDisplayName() : remoteMod.getTitle()));
        return view;
    }

    public interface Callback {
        void onItemSelect(RemoteMod mod);
    }
}
