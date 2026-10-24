package com.kongbai.kculauncher.launcher.keycodes;

import android.util.Log;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mio
 */
public class LwjglKeycodeMap {
    private static final Map<Integer, Integer> KEY_MAP = new HashMap<>();

    private static void add(int lwjglKeycode, int fclKeycode) {
        KEY_MAP.put(fclKeycode, lwjglKeycode);
    }

    public static int convertKeycode(int fclKeycode) {
        Integer key = KEY_MAP.get(fclKeycode);
        if (key != null) {
            return key;
        }
        return LwjglGlfwKeycode.KEY_UNKNOWN;
    }

    static {
        add(LwjglGlfwKeycode.KEY_HOME, KCUKeycodes.KEY_HOME);
        add(LwjglGlfwKeycode.KEY_ESCAPE, KCUKeycodes.KEY_ESC);
        add(LwjglGlfwKeycode.KEY_0, KCUKeycodes.KEY_0);
        add(LwjglGlfwKeycode.KEY_1, KCUKeycodes.KEY_1);
        add(LwjglGlfwKeycode.KEY_2, KCUKeycodes.KEY_2);
        add(LwjglGlfwKeycode.KEY_3, KCUKeycodes.KEY_3);
        add(LwjglGlfwKeycode.KEY_4, KCUKeycodes.KEY_4);
        add(LwjglGlfwKeycode.KEY_5, KCUKeycodes.KEY_5);
        add(LwjglGlfwKeycode.KEY_6, KCUKeycodes.KEY_6);
        add(LwjglGlfwKeycode.KEY_7, KCUKeycodes.KEY_7);
        add(LwjglGlfwKeycode.KEY_8, KCUKeycodes.KEY_8);
        add(LwjglGlfwKeycode.KEY_9, KCUKeycodes.KEY_9);
        add(LwjglGlfwKeycode.KEY_UP, KCUKeycodes.KEY_UP);
        add(LwjglGlfwKeycode.KEY_DOWN, KCUKeycodes.KEY_DOWN);
        add(LwjglGlfwKeycode.KEY_LEFT, KCUKeycodes.KEY_LEFT);
        add(LwjglGlfwKeycode.KEY_RIGHT, KCUKeycodes.KEY_RIGHT);
        add(LwjglGlfwKeycode.KEY_A, KCUKeycodes.KEY_A);
        add(LwjglGlfwKeycode.KEY_B, KCUKeycodes.KEY_B);
        add(LwjglGlfwKeycode.KEY_C, KCUKeycodes.KEY_C);
        add(LwjglGlfwKeycode.KEY_D, KCUKeycodes.KEY_D);
        add(LwjglGlfwKeycode.KEY_E, KCUKeycodes.KEY_E);
        add(LwjglGlfwKeycode.KEY_F, KCUKeycodes.KEY_F);
        add(LwjglGlfwKeycode.KEY_G, KCUKeycodes.KEY_G);
        add(LwjglGlfwKeycode.KEY_H, KCUKeycodes.KEY_H);
        add(LwjglGlfwKeycode.KEY_I, KCUKeycodes.KEY_I);
        add(LwjglGlfwKeycode.KEY_J, KCUKeycodes.KEY_J);
        add(LwjglGlfwKeycode.KEY_K, KCUKeycodes.KEY_K);
        add(LwjglGlfwKeycode.KEY_L, KCUKeycodes.KEY_L);
        add(LwjglGlfwKeycode.KEY_M, KCUKeycodes.KEY_M);
        add(LwjglGlfwKeycode.KEY_N, KCUKeycodes.KEY_N);
        add(LwjglGlfwKeycode.KEY_O, KCUKeycodes.KEY_O);
        add(LwjglGlfwKeycode.KEY_P, KCUKeycodes.KEY_P);
        add(LwjglGlfwKeycode.KEY_Q, KCUKeycodes.KEY_Q);
        add(LwjglGlfwKeycode.KEY_R, KCUKeycodes.KEY_R);
        add(LwjglGlfwKeycode.KEY_S, KCUKeycodes.KEY_S);
        add(LwjglGlfwKeycode.KEY_T, KCUKeycodes.KEY_T);
        add(LwjglGlfwKeycode.KEY_U, KCUKeycodes.KEY_U);
        add(LwjglGlfwKeycode.KEY_V, KCUKeycodes.KEY_V);
        add(LwjglGlfwKeycode.KEY_W, KCUKeycodes.KEY_W);
        add(LwjglGlfwKeycode.KEY_X, KCUKeycodes.KEY_X);
        add(LwjglGlfwKeycode.KEY_Y, KCUKeycodes.KEY_Y);
        add(LwjglGlfwKeycode.KEY_Z, KCUKeycodes.KEY_Z);
        add(LwjglGlfwKeycode.KEY_COMMA, KCUKeycodes.KEY_COMMA);
        add(LwjglGlfwKeycode.KEY_PERIOD, KCUKeycodes.KEY_DOT);
        add(LwjglGlfwKeycode.KEY_LEFT_ALT, KCUKeycodes.KEY_LEFTALT);
        add(LwjglGlfwKeycode.KEY_RIGHT_ALT, KCUKeycodes.KEY_RIGHTALT);
        add(LwjglGlfwKeycode.KEY_LEFT_SHIFT, KCUKeycodes.KEY_LEFTSHIFT);
        add(LwjglGlfwKeycode.KEY_RIGHT_SHIFT, KCUKeycodes.KEY_RIGHTSHIFT);
        add(LwjglGlfwKeycode.KEY_TAB, KCUKeycodes.KEY_TAB);
        add(LwjglGlfwKeycode.KEY_SPACE, KCUKeycodes.KEY_SPACE);
        add(LwjglGlfwKeycode.KEY_ENTER, KCUKeycodes.KEY_ENTER);
        add(LwjglGlfwKeycode.KEY_BACKSPACE, KCUKeycodes.KEY_BACKSPACE);
        add(LwjglGlfwKeycode.KEY_GRAVE_ACCENT, KCUKeycodes.KEY_GRAVE);
        add(LwjglGlfwKeycode.KEY_MINUS, KCUKeycodes.KEY_MINUS);
        add(LwjglGlfwKeycode.KEY_EQUAL, KCUKeycodes.KEY_EQUAL);
        add(LwjglGlfwKeycode.KEY_LEFT_BRACKET, KCUKeycodes.KEY_LEFTBRACE);
        add(LwjglGlfwKeycode.KEY_RIGHT_BRACKET, KCUKeycodes.KEY_RIGHTBRACE);
        add(LwjglGlfwKeycode.KEY_BACKSLASH, KCUKeycodes.KEY_BACKSLASH);
        add(LwjglGlfwKeycode.KEY_SEMICOLON, KCUKeycodes.KEY_SEMICOLON);
        add(LwjglGlfwKeycode.KEY_APOSTROPHE, KCUKeycodes.KEY_APOSTROPHE);
        add(LwjglGlfwKeycode.KEY_SLASH, KCUKeycodes.KEY_SLASH);
        add(LwjglGlfwKeycode.KEY_PAGE_UP, KCUKeycodes.KEY_PAGEUP);
        add(LwjglGlfwKeycode.KEY_PAGE_DOWN, KCUKeycodes.KEY_PAGEDOWN);
        add(LwjglGlfwKeycode.KEY_ESCAPE, KCUKeycodes.KEY_ESC);
        add(LwjglGlfwKeycode.KEY_LEFT_CONTROL, KCUKeycodes.KEY_LEFTCTRL);
        add(LwjglGlfwKeycode.KEY_RIGHT_CONTROL, KCUKeycodes.KEY_RIGHTCTRL);
        add(LwjglGlfwKeycode.KEY_CAPS_LOCK, KCUKeycodes.KEY_CAPSLOCK);
        add(LwjglGlfwKeycode.KEY_PAUSE, KCUKeycodes.KEY_PAUSE);
        add(LwjglGlfwKeycode.KEY_END, KCUKeycodes.KEY_END);
        add(LwjglGlfwKeycode.KEY_INSERT, KCUKeycodes.KEY_INSERT);
        add(LwjglGlfwKeycode.KEY_F1, KCUKeycodes.KEY_F1);
        add(LwjglGlfwKeycode.KEY_F2, KCUKeycodes.KEY_F2);
        add(LwjglGlfwKeycode.KEY_F3, KCUKeycodes.KEY_F3);
        add(LwjglGlfwKeycode.KEY_F4, KCUKeycodes.KEY_F4);
        add(LwjglGlfwKeycode.KEY_F5, KCUKeycodes.KEY_F5);
        add(LwjglGlfwKeycode.KEY_F6, KCUKeycodes.KEY_F6);
        add(LwjglGlfwKeycode.KEY_F7, KCUKeycodes.KEY_F7);
        add(LwjglGlfwKeycode.KEY_F8, KCUKeycodes.KEY_F8);
        add(LwjglGlfwKeycode.KEY_F9, KCUKeycodes.KEY_F9);
        add(LwjglGlfwKeycode.KEY_F10, KCUKeycodes.KEY_F10);
        add(LwjglGlfwKeycode.KEY_F11, KCUKeycodes.KEY_F11);
        add(LwjglGlfwKeycode.KEY_F12, KCUKeycodes.KEY_F12);
        add(LwjglGlfwKeycode.KEY_NUM_LOCK, KCUKeycodes.KEY_NUMLOCK);
        add(LwjglGlfwKeycode.KEY_KP_0, KCUKeycodes.KEY_KP0);
        add(LwjglGlfwKeycode.KEY_KP_1, KCUKeycodes.KEY_KP1);
        add(LwjglGlfwKeycode.KEY_KP_2, KCUKeycodes.KEY_KP2);
        add(LwjglGlfwKeycode.KEY_KP_3, KCUKeycodes.KEY_KP3);
        add(LwjglGlfwKeycode.KEY_KP_4, KCUKeycodes.KEY_KP4);
        add(LwjglGlfwKeycode.KEY_KP_5, KCUKeycodes.KEY_KP5);
        add(LwjglGlfwKeycode.KEY_KP_6, KCUKeycodes.KEY_KP6);
        add(LwjglGlfwKeycode.KEY_KP_7, KCUKeycodes.KEY_KP7);
        add(LwjglGlfwKeycode.KEY_KP_8, KCUKeycodes.KEY_KP8);
        add(LwjglGlfwKeycode.KEY_KP_9, KCUKeycodes.KEY_KP9);
        add(LwjglGlfwKeycode.KEY_KP_DECIMAL, KCUKeycodes.KEY_KPDOT);
        add(LwjglGlfwKeycode.KEY_KP_SUBTRACT, KCUKeycodes.KEY_KPMINUS);
        add(LwjglGlfwKeycode.KEY_KP_MULTIPLY, KCUKeycodes.KEY_KPASTERISK);
        add(LwjglGlfwKeycode.KEY_KP_ADD, KCUKeycodes.KEY_KPPLUS);
        add(LwjglGlfwKeycode.KEY_KP_DIVIDE, KCUKeycodes.KEY_KPSLASH);
        add(LwjglGlfwKeycode.KEY_KP_ENTER, KCUKeycodes.KEY_KPENTER);
        add(LwjglGlfwKeycode.KEY_KP_EQUAL, KCUKeycodes.KEY_KPEQUAL);
        add(LwjglGlfwKeycode.KEY_DELETE, KCUKeycodes.KEY_DELETE);
    }
}