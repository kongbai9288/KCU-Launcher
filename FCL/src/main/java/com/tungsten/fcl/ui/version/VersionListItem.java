package com.tungsten.fcl.ui.version;

import android.graphics.drawable.Drawable;

import com.tungsten.fcl.setting.Profile;
import com.tungsten.fcl.util.KcuVersionMeta;
import com.tungsten.fclcore.fakefx.beans.property.BooleanProperty;
import com.tungsten.fclcore.fakefx.beans.property.SimpleBooleanProperty;

public class VersionListItem {

    private final Profile profile;
    private final String version;
    private final boolean isModpack;
    private final BooleanProperty selected = new SimpleBooleanProperty();
    private final String libraries;
    private final String tag;
    private final Drawable drawable;
    private final int modCount;
    private final boolean pinned;
    /** 导入版本的展示文本：无 kcu_meta 时为 null（沿用原显示），有则取 display（未探测为“未知”） */
    private final String displayVersion;

    /**
     * 仅对导入版本（版本目录存在 kcu_meta.json）返回展示文本，其余返回 null。
     * 构造时读一次，不在 onBind 里读，避免列表滚动反复 IO。
     */
    private static String resolveDisplayVersion(Profile profile, String id) {
        try {
            java.io.File dir = profile.getRepository().getVersionRoot(id);
            if (!KcuVersionMeta.file(dir).isFile()) {
                return null;
            }
            return KcuVersionMeta.read(dir).display;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public String getDisplayVersion() {
        return displayVersion;
    }

    public VersionListItem(Profile profile, String id, String libraries, String tag, Drawable drawable, int modCount, boolean pinned) {
        this.profile = profile;
        this.version = id;
        this.libraries = libraries;
        this.drawable = drawable;
        this.tag = tag;
        this.modCount = modCount;
        this.pinned = pinned;
        this.isModpack = profile.getRepository().isModpack(id);
        this.displayVersion = resolveDisplayVersion(profile, id);

        selected.set(id.equals(profile.getSelectedVersion()));
    }

    public Profile getProfile() {
        return profile;
    }

    public String getVersion() {
        return version;
    }

    public String getLibraries() {
        return libraries;
    }

    public String getTag() {
        return tag;
    }

    public Drawable getDrawable() {
        return drawable;
    }

    public int getModCount() {
        return modCount;
    }

    public boolean isPinned() {
        return pinned;
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public boolean canUpdate() {
        return isModpack;
    }

}
