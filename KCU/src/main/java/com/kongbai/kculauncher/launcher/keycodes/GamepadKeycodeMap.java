package com.kongbai.kculauncher.launcher.keycodes;

import android.view.KeyEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class GamepadKeycodeMap {
    public static final HashMap<Integer, Integer> KEY_MAP = new HashMap<>();
    private static final int MOUSE_LEFT = 1000;
    private static final int MOUSE_MIDDLE = 1001;
    private static final int MOUSE_RIGHT = 1002;
    private static final int MOUSE_SCROLL_UP = 1003;
    private static final int MOUSE_SCROLL_DOWN = 1004;
    public static final int LEFT_JOYSTICK_RIGHT = 2000;
    public static final int LEFT_JOYSTICK_UP = 2002;
    public static final int LEFT_JOYSTICK_LEFT = 2004;
    public static final int LEFT_JOYSTICK_DOWN = 2006;

    private static void add(int key, int value) {
        KEY_MAP.put(key, value);
    }

    static {
        add(KeyEvent.KEYCODE_BUTTON_A, KCUKeycodes.KEY_SPACE);
        add(KeyEvent.KEYCODE_BUTTON_B, KCUKeycodes.KEY_Q);
        add(KeyEvent.KEYCODE_BUTTON_X, KCUKeycodes.KEY_E);
        add(KeyEvent.KEYCODE_BUTTON_Y, KCUKeycodes.KEY_F);
        add(KeyEvent.KEYCODE_BUTTON_L1, MOUSE_SCROLL_UP);
        add(KeyEvent.KEYCODE_BUTTON_R1, MOUSE_SCROLL_DOWN);
        add(KeyEvent.KEYCODE_BUTTON_L2, MOUSE_LEFT);
        add(KeyEvent.KEYCODE_BUTTON_R2, MOUSE_RIGHT);
        add(KeyEvent.KEYCODE_BUTTON_THUMBL, KCUKeycodes.KEY_LEFTCTRL);
        add(KeyEvent.KEYCODE_BUTTON_THUMBR, KCUKeycodes.KEY_LEFTSHIFT);
        add(KeyEvent.KEYCODE_BUTTON_START, KCUKeycodes.KEY_ESC);
        add(KeyEvent.KEYCODE_BUTTON_SELECT, KCUKeycodes.KEY_TAB);
        add(KeyEvent.KEYCODE_DPAD_UP, KCUKeycodes.KEY_LEFTSHIFT);
        add(KeyEvent.KEYCODE_DPAD_DOWN, KCUKeycodes.KEY_O);
        add(KeyEvent.KEYCODE_DPAD_LEFT, KCUKeycodes.KEY_J);
        add(KeyEvent.KEYCODE_DPAD_RIGHT, KCUKeycodes.KEY_K);
        add(LEFT_JOYSTICK_UP, KCUKeycodes.KEY_W);
        add(LEFT_JOYSTICK_DOWN, KCUKeycodes.KEY_S);
        add(LEFT_JOYSTICK_LEFT, KCUKeycodes.KEY_A);
        add(LEFT_JOYSTICK_RIGHT, KCUKeycodes.KEY_D);
    }

    public static int convert(Map<Integer, Integer> keyMap, int key) {
        return Optional.ofNullable(keyMap.get(key)).orElse(-1);
    }
}
