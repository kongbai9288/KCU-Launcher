package com.kongbai.kculauncher.ui.download.common;

import android.animation.AnimatorInflater;
import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.kongbai.kculauncher.mio.util.AnimUtil;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.mod.ModLoaderType;
import com.kongbai.kculauncher.core.mod.RemoteMod;
import com.kongbai.kculauncher.library.component.KCUAdapter;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.view.KCULinearLayout;
import com.kongbai.kculauncher.library.component.view.KCUTextView;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;

public class ModVersionAdapter extends KCUAdapter {

    private final List<RemoteMod.Version> list;
    private final Callback callback;

    public ModVersionAdapter(Context context, List<RemoteMod.Version> list, Callback callback) {
        super(context);
        this.list = list;
        this.callback = callback;
    }

    private static class ViewHolder {
        KCULinearLayout parent;
        KCUTextView name;
        KCUTextView tag;
        KCUTextView date;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int i) {
        return list.get(i);
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        final ViewHolder viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder();
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_mod_version, null);
            viewHolder.parent = view.findViewById(R.id.parent);
            viewHolder.name = view.findViewById(R.id.name);
            viewHolder.tag = view.findViewById(R.id.tag);
            viewHolder.date = view.findViewById(R.id.date);
            viewHolder.parent.setStateListAnimator(AnimatorInflater.loadStateListAnimator(getContext(), com.kongbai.kculauncher.R.xml.anim_scale));
            view.setTag(viewHolder);
            // 仅首次创建时播放滑入动画：convertView 复用（滚动/布局变化重绑定）时不重播，
            // 避免截图等异步加载完成触发布局变化导致列表动画重复
            AnimUtil.playTranslationX(view, ThemeEngine.getInstance().getTheme().getAnimationSpeed() * 30L, -100f, 0f).start();
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        RemoteMod.Version version = list.get(i);
        viewHolder.parent.setOnClickListener(v -> callback.onItemSelect(version));
        viewHolder.name.setText(version.name());
        viewHolder.tag.setText(getTag(getContext(), version));
        viewHolder.date.setText(FORMATTER.format(version.datePublished()));
        return view;
    }

    public static String getTag(Context context, RemoteMod.Version version) {
        StringBuilder stringBuilder = new StringBuilder();
        switch (version.versionType()) {
            case Beta:
            case Alpha:
                stringBuilder.append(context.getString(R.string.version_game_snapshot));
                break;
            default:
                stringBuilder.append(context.getString(R.string.version_game_release));
                break;
        }
        for (ModLoaderType modLoaderType : version.loaders()) {
            switch (modLoaderType) {
                case FORGE:
                    stringBuilder.append("   ").append(context.getString(R.string.install_installer_forge));
                    break;
                case NEO_FORGED:
                    stringBuilder.append("   ").append(context.getString(R.string.install_installer_neoforge));
                    break;
                case FABRIC:
                    stringBuilder.append("   ").append(context.getString(R.string.install_installer_fabric));
                    break;
                case LITE_LOADER:
                    stringBuilder.append("   ").append(context.getString(R.string.install_installer_liteloader));
                    break;
                case QUILT:
                    stringBuilder.append("   ").append(context.getString(R.string.install_installer_quilt));
                    break;
            }
        }
        return stringBuilder.toString();
    }

    public interface Callback {
        void onItemSelect(RemoteMod.Version version);
    }

    @SuppressLint("ConstantLocale")
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());
}
