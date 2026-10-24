package com.kongbai.kculauncher.activity;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.kongbai.kculauncher.control.GameMenu;
import com.kongbai.kculauncher.control.view.MenuView;
import com.kongbai.kculauncher.library.component.KCUActivity;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.view.KCUImageView;

public class ControllerActivity extends KCUActivity {

    private GameMenu menu;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        KCUImageView contentView = new KCUImageView(this);
        contentView.setBackground(ThemeEngine.getInstance().getTheme().getBackground(this));
        setContentView(contentView);

        menu = new GameMenu();
        menu.setup(this, null);

        addContentView(menu.getLayout(), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if ((event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN || event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP)) {
            DrawerLayout drawerLayout = (DrawerLayout) menu.getLayout();
            drawerLayout.openDrawer(GravityCompat.START, true);
            drawerLayout.openDrawer(GravityCompat.END, true);
        }
        return super.dispatchKeyEvent(event);
    }
}
