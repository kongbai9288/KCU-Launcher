package com.kongbai.kculauncher.library.component.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import com.kongbai.kculauncher.core.fakefx.beans.property.BooleanProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.BooleanPropertyBase;
import com.kongbai.kculauncher.core.fakefx.beans.property.ObjectProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.ObjectPropertyBase;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;

public class KCUImageView extends AppCompatImageView {

    private ObjectProperty<Drawable> image;
    private boolean autoTint;
    private boolean useThemeColor;
    private BooleanProperty visibilityProperty;

    /** 主题刷新回调（registerEvent 注册，主题变化时全量执行） */
    private void refreshTheme() {
        if (autoTint) {
            int[][] state = {
                    {

                    }
            };
            int[] color = {
                    ThemeEngine.getInstance().getTheme().getAutoTint()
            };
            setImageTintList(new ColorStateList(state, color));
        }
        if (useThemeColor && getBackground() != null) {
            getBackground().setTint(ThemeEngine.getInstance().getTheme().getColor2());
        }
    }

    public KCUImageView(@NonNull Context context) {
        super(context);
        ThemeEngine.getInstance().registerEvent(this, this::refreshTheme);
    }

    public KCUImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.KCUImageView);
        autoTint = typedArray.getBoolean(R.styleable.FCLImageView_auto_src_tint, false);
        useThemeColor = typedArray.getBoolean(R.styleable.FCLImageView_use_theme_color, false);
        typedArray.recycle();
        ThemeEngine.getInstance().registerEvent(this, this::refreshTheme);
    }

    public KCUImageView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.KCUImageView);
        autoTint = typedArray.getBoolean(R.styleable.FCLImageView_auto_src_tint, false);
        useThemeColor = typedArray.getBoolean(R.styleable.FCLImageView_use_theme_color, false);
        typedArray.recycle();
        ThemeEngine.getInstance().registerEvent(this, this::refreshTheme);
    }

    public void setAutoTint(boolean autoTint) {
        this.autoTint = autoTint;
    }

    public boolean isAutoTint() {
        return autoTint;
    }

    public void setUseThemeColor(boolean useThemeColor) {
        this.useThemeColor = useThemeColor;
    }

    public boolean isUseThemeColor() {
        return useThemeColor;
    }

    public final void setImage(Drawable drawable) {
        imageProperty().set(drawable);
    }

    public final Drawable getImage() {
        return image == null ? null : image.get();
    }

    public final ObjectProperty<Drawable> imageProperty() {
        if (image == null) {
            image = new ObjectPropertyBase<Drawable>() {

                public void invalidated() {
                    Schedulers.androidUIThread().execute(() -> {
                        Drawable drawable = get();
                        setBackground(drawable);
                    });
                }

                public Object getBean() {
                    return this;
                }

                public String getName() {
                    return "image";
                }
            };
        }

        return this.image;
    }

    public final void setVisibilityValue(boolean visibility) {
        visibilityProperty().set(visibility);
    }

    public final boolean getVisibilityValue() {
        return visibilityProperty == null || visibilityProperty.get();
    }

    public final BooleanProperty visibilityProperty() {
        if (visibilityProperty == null) {
            visibilityProperty = new BooleanPropertyBase() {

                public void invalidated() {
                    Schedulers.androidUIThread().execute(() -> {
                        boolean visible = get();
                        setVisibility(visible ? VISIBLE : GONE);
                    });
                }

                public Object getBean() {
                    return this;
                }

                public String getName() {
                    return "visibility";
                }
            };
        }

        return visibilityProperty;
    }
}
