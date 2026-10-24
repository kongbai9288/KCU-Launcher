package com.kongbai.kculauncher.launcher.bridge;

public interface KCUBridgeCallback {

    void onCursorModeChange(int mode);
    void onLog(String log);
    void onExit(int code);

}
