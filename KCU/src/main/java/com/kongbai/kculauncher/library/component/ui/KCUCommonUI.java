package com.kongbai.kculauncher.library.component.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;

import com.kongbai.kculauncher.core.task.Task;

public abstract class KCUCommonUI extends KCUBaseUI {

    public KCUCommonUI(Context context, @LayoutRes int id) {
        super(context);
        setContentView(id);
    }

    @Override
    public boolean isShowing() {
        View contentView = getContentView();
        if (contentView == null) return false;
        return contentView.isShown();
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }



    @Override
    public void onBackPressed() {
        super.onBackPressed();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

}
