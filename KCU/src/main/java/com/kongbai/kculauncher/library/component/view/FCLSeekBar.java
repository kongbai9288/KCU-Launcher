package com.kongbai.kculauncher.library.component.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.util.AttributeSet;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;

import com.kongbai.kculauncher.core.fakefx.beans.property.BooleanProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.IntegerProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.IntegerPropertyBase;
import com.kongbai.kculauncher.core.fakefx.beans.property.BooleanPropertyBase;
import com.kongbai.kculauncher.core.fakefx.beans.property.DoubleProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.DoublePropertyBase;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;

public class KCUSeekBar extends AppCompatSeekBar {

    private boolean fromUserOrSystem = false;
    private BooleanProperty visibilityProperty;
    private BooleanProperty disableProperty;
    private DoubleProperty percentProgressProperty;
    private IntegerProperty progressProperty;

    /** 主题刷新回调（registerEvent 注册，主题变化时全量执行） */
    private void refreshTheme() {
            int[][] state = {
                    {

                    }
            };
            int[] color = {
                    ThemeEngine.getInstance().getTheme().getDkColor()
            };
            setThumbTintList(new ColorStateList(state, color));
            setProgressTintList(new ColorStateList(state, color));
    }

    public void addProgressListener() {
        setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                fromUserOrSystem = true;
                progressProperty().set(i);
                percentProgressProperty().set((double) i / (double) getMax());
                fromUserOrSystem = false;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    public KCUSeekBar(@NonNull Context context) {
        super(context);
        ThemeEngine.getInstance().registerEvent(this, this::refreshTheme);
    }

    public KCUSeekBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        ThemeEngine.getInstance().registerEvent(this, this::refreshTheme);
    }

    public KCUSeekBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ThemeEngine.getInstance().registerEvent(this, this::refreshTheme);
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

    public final void setDisableValue(boolean disableValue) {
        disableProperty().set(disableValue);
    }

    public final boolean getDisableValue() {
        return disableProperty == null || disableProperty.get();
    }

    public final BooleanProperty disableProperty() {
        if (disableProperty == null) {
            disableProperty = new BooleanPropertyBase() {

                public void invalidated() {
                    Schedulers.androidUIThread().execute(() -> {
                        boolean disable = get();
                        setEnabled(!disable);
                    });
                }

                public Object getBean() {
                    return this;
                }

                public String getName() {
                    return "disable";
                }
            };
        }

        return disableProperty;
    }

    public final void setPercentProgressValue(double percentProgressValue) {
        percentProgressProperty().set(percentProgressValue);
    }

    public final double getPercentProgressValue() {
        return percentProgressProperty == null ? -1 : percentProgressProperty.get();
    }

    public final DoubleProperty percentProgressProperty() {
        if (percentProgressProperty == null) {
            percentProgressProperty = new DoublePropertyBase() {

                public void invalidated() {
                    Schedulers.androidUIThread().execute(() -> {
                        if (!fromUserOrSystem) {
                            double progress = get();
                            setProgress((int) (progress * getMax()));
                        }
                    });
                }

                public Object getBean() {
                    return this;
                }

                public String getName() {
                    return "percentProgress";
                }
            };
        }

        return percentProgressProperty;
    }

    public final void setProgressValue(int progressValue) {
        progressProperty().set(progressValue);
    }

    public final int getProgressValue() {
        return progressProperty == null ? -1 : progressProperty().get();
    }

    public final IntegerProperty progressProperty() {
        if (progressProperty == null) {
            progressProperty = new IntegerPropertyBase() {

                public void invalidated() {
                    Schedulers.androidUIThread().execute(() -> {
                        if (!fromUserOrSystem) {
                            int progress = get();
                            setProgress(progress);
                        }
                    });
                }

                public Object getBean() {
                    return this;
                }

                public String getName() {
                    return "progress";
                }
            };
        }

        return progressProperty;
    }

}
