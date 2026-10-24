package com.kongbai.kculauncher.ui.download.common;

import android.animation.AnimatorInflater;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.kongbai.kculauncher.mio.util.AnimUtil;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.library.component.KCUAdapter;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.view.KCULinearLayout;
import com.kongbai.kculauncher.library.component.view.KCUTextView;
import com.kongbai.kculauncher.library.util.ConvertUtils;

import java.util.List;

public class ModGameVersionAdapter extends KCUAdapter {

    private final List<String> list;
    private final Callback callback;

    public ModGameVersionAdapter(Context context, List<String> list, Callback callback) {
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
            viewHolder.version.setAutoTint(true);
            viewHolder.version.setTextColor(ThemeEngine.getInstance().getTheme().getAutoTint());
            viewHolder.parent.setStateListAnimator(AnimatorInflater.loadStateListAnimator(getContext(), com.kongbai.kculauncher.R.xml.anim_scale));
            view.setTag(viewHolder);
            // 仅首次创建时播放滑入动画：convertView 复用（滚动/布局变化重绑定）时不重播，
            // 避免截图等异步加载完成触发布局变化导致列表动画重复
            AnimUtil.playTranslationX(view, ThemeEngine.getInstance().getTheme().getAnimationSpeed() * 30L, -100f, 0f).start();
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        viewHolder.parent.setOnClickListener(v -> callback.onSelect(list.get(i)));
        viewHolder.version.setText((list.get(i).contains(getContext().getString(R.string.recommend_version)) ? "" : "Minecraft ") + list.get(i));
        return view;
    }

    public interface Callback {
        void onSelect(String version);
    }
}
