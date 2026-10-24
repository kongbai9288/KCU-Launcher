package com.kongbai.kculauncher.library.component.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;

import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.library.component.KCUActivity;

public abstract class KCUBaseUI {

    private static Runnable defaultBackEvent;

    private final Context context;
    private final KCUActivity activity;

    private View contentView;

    public KCUBaseUI(Context context) {
        this.context = context;
        this.activity = (KCUActivity) context;
    }

    public static void setDefaultBackEvent(Runnable defaultBackEvent) {
        KCUBaseUI.defaultBackEvent = defaultBackEvent;
    }

    public Context getContext() {
        return context;
    }

    public KCUActivity getActivity() {
        return activity;
    }

    public void setContentView(@LayoutRes int id) {
        contentView = LayoutInflater.from(context).inflate(id, null);
    }

    public View getContentView() {
        return contentView;
    }

    @NonNull
    public final <T extends View> T findViewById(int id) {
        return contentView.findViewById(id);
    }

    public abstract boolean isShowing();


    public void onCreate() {

    }



    public void onBackPressed() {
        if (defaultBackEvent != null && isShowing()) {
            Schedulers.androidUIThread().execute(defaultBackEvent);
        }
    }

    public void onPause() {

    }

    public void onResume() {

    }

}
