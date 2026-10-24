package com.kongbai.kculauncher.control;

import android.view.KeyEvent;
import android.view.View;

import androidx.annotation.Nullable;

import com.kongbai.kculauncher.mio.ui.view.CursorView;
import com.kongbai.kculauncher.launcher.bridge.KCUBridge;
import com.kongbai.kculauncher.launcher.bridge.KCUBridgeCallback;
import com.kongbai.kculauncher.library.component.KCUActivity;
import com.kongbai.kculauncher.library.component.view.KCUImageView;

public interface MenuCallback {

    void setup(KCUActivity activity, KCUBridge fclBridge);

    View getLayout();

    @Nullable
    KCUBridge getBridge();

    KCUBridgeCallback getCallbackBridge();

    KCUInput getInput();

    CursorView getCursor();

    int getCursorMode();

    void onPause();

    void onResume();

    void onGraphicOutput();

    void onCursorModeChange(int mode);

    void onLog(String log);

    void onExit(int exitCode);

}
