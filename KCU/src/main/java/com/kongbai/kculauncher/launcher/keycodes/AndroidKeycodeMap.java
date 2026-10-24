package com.kongbai.kculauncher.launcher.keycodes;

import android.view.KeyEvent;

import java.util.Arrays;

public class AndroidKeycodeMap {

    private static final int[] ANDROID_KEYCODES = new int[99];
    private static final int[] FCL_KEYCODES = new int[99];

    private static int count = 0;

    private static void add(int androidKeycode, int fclKeycode) {
        ANDROID_KEYCODES[count] = androidKeycode;
        FCL_KEYCODES[count] = fclKeycode;
        count++;
    }

    public static int convertKeycode(int androidKeycode) {
        int index = Arrays.binarySearch(ANDROID_KEYCODES, androidKeycode);
        if (index >= 0)
            return FCL_KEYCODES[index];
        return KCUKeycodes.KEY_UNKNOWN;
    }

    static {
        add(KeyEvent.KEYCODE_HOME,                           KCUKeycodes.KEY_HOME);
        add(KeyEvent.KEYCODE_BACK,                           KCUKeycodes.KEY_ESC);

        add(KeyEvent.KEYCODE_0,                              KCUKeycodes.KEY_0);
        add(KeyEvent.KEYCODE_1,                              KCUKeycodes.KEY_1);
        add(KeyEvent.KEYCODE_2,                              KCUKeycodes.KEY_2);
        add(KeyEvent.KEYCODE_3,                              KCUKeycodes.KEY_3);
        add(KeyEvent.KEYCODE_4,                              KCUKeycodes.KEY_4);
        add(KeyEvent.KEYCODE_5,                              KCUKeycodes.KEY_5);
        add(KeyEvent.KEYCODE_6,                              KCUKeycodes.KEY_6);
        add(KeyEvent.KEYCODE_7,                              KCUKeycodes.KEY_7);
        add(KeyEvent.KEYCODE_8,                              KCUKeycodes.KEY_8);
        add(KeyEvent.KEYCODE_9,                              KCUKeycodes.KEY_9);

        add(KeyEvent.KEYCODE_POUND,                          KCUKeycodes.KEY_3);

        add(KeyEvent.KEYCODE_DPAD_UP,                        KCUKeycodes.KEY_UP);
        add(KeyEvent.KEYCODE_DPAD_DOWN,                      KCUKeycodes.KEY_DOWN);
        add(KeyEvent.KEYCODE_DPAD_LEFT,                      KCUKeycodes.KEY_LEFT);
        add(KeyEvent.KEYCODE_DPAD_RIGHT,                     KCUKeycodes.KEY_RIGHT);

        add(KeyEvent.KEYCODE_A,                              KCUKeycodes.KEY_A);
        add(KeyEvent.KEYCODE_B,                              KCUKeycodes.KEY_B);
        add(KeyEvent.KEYCODE_C,                              KCUKeycodes.KEY_C);
        add(KeyEvent.KEYCODE_D,                              KCUKeycodes.KEY_D);
        add(KeyEvent.KEYCODE_E,                              KCUKeycodes.KEY_E);
        add(KeyEvent.KEYCODE_F,                              KCUKeycodes.KEY_F);
        add(KeyEvent.KEYCODE_G,                              KCUKeycodes.KEY_G);
        add(KeyEvent.KEYCODE_H,                              KCUKeycodes.KEY_H);
        add(KeyEvent.KEYCODE_I,                              KCUKeycodes.KEY_I);
        add(KeyEvent.KEYCODE_J,                              KCUKeycodes.KEY_J);
        add(KeyEvent.KEYCODE_K,                              KCUKeycodes.KEY_K);
        add(KeyEvent.KEYCODE_L,                              KCUKeycodes.KEY_L);
        add(KeyEvent.KEYCODE_M,                              KCUKeycodes.KEY_M);
        add(KeyEvent.KEYCODE_N,                              KCUKeycodes.KEY_N);
        add(KeyEvent.KEYCODE_O,                              KCUKeycodes.KEY_O);
        add(KeyEvent.KEYCODE_P,                              KCUKeycodes.KEY_P);
        add(KeyEvent.KEYCODE_Q,                              KCUKeycodes.KEY_Q);
        add(KeyEvent.KEYCODE_R,                              KCUKeycodes.KEY_R);
        add(KeyEvent.KEYCODE_S,                              KCUKeycodes.KEY_S);
        add(KeyEvent.KEYCODE_T,                              KCUKeycodes.KEY_T);
        add(KeyEvent.KEYCODE_U,                              KCUKeycodes.KEY_U);
        add(KeyEvent.KEYCODE_V,                              KCUKeycodes.KEY_V);
        add(KeyEvent.KEYCODE_W,                              KCUKeycodes.KEY_W);
        add(KeyEvent.KEYCODE_X,                              KCUKeycodes.KEY_X);
        add(KeyEvent.KEYCODE_Y,                              KCUKeycodes.KEY_Y);
        add(KeyEvent.KEYCODE_Z,                              KCUKeycodes.KEY_Z);
        
        add(KeyEvent.KEYCODE_COMMA,                          KCUKeycodes.KEY_COMMA);
        add(KeyEvent.KEYCODE_PERIOD,                         KCUKeycodes.KEY_DOT);

        add(KeyEvent.KEYCODE_ALT_LEFT,                       KCUKeycodes.KEY_LEFTALT);
        add(KeyEvent.KEYCODE_ALT_RIGHT,                      KCUKeycodes.KEY_RIGHTALT);

        add(KeyEvent.KEYCODE_SHIFT_LEFT,                     KCUKeycodes.KEY_LEFTSHIFT);
        add(KeyEvent.KEYCODE_SHIFT_RIGHT,                    KCUKeycodes.KEY_RIGHTSHIFT);

        add(KeyEvent.KEYCODE_TAB,                            KCUKeycodes.KEY_TAB);
        add(KeyEvent.KEYCODE_SPACE,                          KCUKeycodes.KEY_SPACE);
        add(KeyEvent.KEYCODE_ENTER,                          KCUKeycodes.KEY_ENTER);
        add(KeyEvent.KEYCODE_DEL,                            KCUKeycodes.KEY_BACKSPACE);
        add(KeyEvent.KEYCODE_GRAVE,                          KCUKeycodes.KEY_GRAVE);
        add(KeyEvent.KEYCODE_MINUS,                          KCUKeycodes.KEY_MINUS);
        add(KeyEvent.KEYCODE_EQUALS,                         KCUKeycodes.KEY_EQUAL);
        add(KeyEvent.KEYCODE_LEFT_BRACKET,                   KCUKeycodes.KEY_LEFTBRACE);
        add(KeyEvent.KEYCODE_RIGHT_BRACKET,                  KCUKeycodes.KEY_RIGHTBRACE);
        add(KeyEvent.KEYCODE_BACKSLASH,                      KCUKeycodes.KEY_BACKSLASH);
        add(KeyEvent.KEYCODE_SEMICOLON,                      KCUKeycodes.KEY_SEMICOLON);
        add(KeyEvent.KEYCODE_APOSTROPHE,                     KCUKeycodes.KEY_APOSTROPHE);
        add(KeyEvent.KEYCODE_SLASH,                          KCUKeycodes.KEY_SLASH);
        add(KeyEvent.KEYCODE_AT,                             KCUKeycodes.KEY_2);

        add(KeyEvent.KEYCODE_PAGE_UP,                        KCUKeycodes.KEY_PAGEUP);
        add(KeyEvent.KEYCODE_PAGE_DOWN,                      KCUKeycodes.KEY_PAGEDOWN);

        add(KeyEvent.KEYCODE_ESCAPE,                         KCUKeycodes.KEY_ESC);

        add(KeyEvent.KEYCODE_CTRL_LEFT,                      KCUKeycodes.KEY_LEFTCTRL);
        add(KeyEvent.KEYCODE_CTRL_RIGHT,                     KCUKeycodes.KEY_RIGHTCTRL);

        add(KeyEvent.KEYCODE_CAPS_LOCK,                      KCUKeycodes.KEY_CAPSLOCK);
        add(KeyEvent.KEYCODE_BREAK,                          KCUKeycodes.KEY_PAUSE);
        add(KeyEvent.KEYCODE_MOVE_END,                       KCUKeycodes.KEY_END);
        add(KeyEvent.KEYCODE_INSERT,                         KCUKeycodes.KEY_INSERT);

        add(KeyEvent.KEYCODE_F1,                             KCUKeycodes.KEY_F1);
        add(KeyEvent.KEYCODE_F2,                             KCUKeycodes.KEY_F2);
        add(KeyEvent.KEYCODE_F3,                             KCUKeycodes.KEY_F3);
        add(KeyEvent.KEYCODE_F4,                             KCUKeycodes.KEY_F4);
        add(KeyEvent.KEYCODE_F5,                             KCUKeycodes.KEY_F5);
        add(KeyEvent.KEYCODE_F6,                             KCUKeycodes.KEY_F6);
        add(KeyEvent.KEYCODE_F7,                             KCUKeycodes.KEY_F7);
        add(KeyEvent.KEYCODE_F8,                             KCUKeycodes.KEY_F8);
        add(KeyEvent.KEYCODE_F9,                             KCUKeycodes.KEY_F9);
        add(KeyEvent.KEYCODE_F10,                            KCUKeycodes.KEY_F10);
        add(KeyEvent.KEYCODE_F11,                            KCUKeycodes.KEY_F11);
        add(KeyEvent.KEYCODE_F12,                            KCUKeycodes.KEY_F12);

        add(KeyEvent.KEYCODE_NUM_LOCK,                       KCUKeycodes.KEY_NUMLOCK);
        add(KeyEvent.KEYCODE_NUMPAD_0,                       KCUKeycodes.KEY_KP0);
        add(KeyEvent.KEYCODE_NUMPAD_1,                       KCUKeycodes.KEY_KP1);
        add(KeyEvent.KEYCODE_NUMPAD_2,                       KCUKeycodes.KEY_KP2);
        add(KeyEvent.KEYCODE_NUMPAD_3,                       KCUKeycodes.KEY_KP3);
        add(KeyEvent.KEYCODE_NUMPAD_4,                       KCUKeycodes.KEY_KP4);
        add(KeyEvent.KEYCODE_NUMPAD_5,                       KCUKeycodes.KEY_KP5);
        add(KeyEvent.KEYCODE_NUMPAD_6,                       KCUKeycodes.KEY_KP6);
        add(KeyEvent.KEYCODE_NUMPAD_7,                       KCUKeycodes.KEY_KP7);
        add(KeyEvent.KEYCODE_NUMPAD_8,                       KCUKeycodes.KEY_KP8);
        add(KeyEvent.KEYCODE_NUMPAD_9,                       KCUKeycodes.KEY_KP9);
        add(KeyEvent.KEYCODE_NUMPAD_DOT,                     KCUKeycodes.KEY_KPDOT);
        add(KeyEvent.KEYCODE_NUMPAD_COMMA,                   KCUKeycodes.KEY_KPCOMMA);
        add(KeyEvent.KEYCODE_NUMPAD_ENTER,                   KCUKeycodes.KEY_KPENTER);
        add(KeyEvent.KEYCODE_NUMPAD_EQUALS,                  KCUKeycodes.KEY_KPEQUAL);
    }

}
