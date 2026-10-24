package com.kongbai.kculauncher.launcher.keycodes;

import androidx.annotation.Nullable;

public final class MinecraftKeyBindingMapper {
    private MinecraftKeyBindingMapper() {}

    public static final String BINDING_HOTBAR_1 = "key_key.hotbar.1";
    public static final String BINDING_HOTBAR_2 = "key_key.hotbar.2";
    public static final String BINDING_HOTBAR_3 = "key_key.hotbar.3";
    public static final String BINDING_HOTBAR_4 = "key_key.hotbar.4";
    public static final String BINDING_HOTBAR_5 = "key_key.hotbar.5";
    public static final String BINDING_HOTBAR_6 = "key_key.hotbar.6";
    public static final String BINDING_HOTBAR_7 = "key_key.hotbar.7";
    public static final String BINDING_HOTBAR_8 = "key_key.hotbar.8";
    public static final String BINDING_HOTBAR_9 = "key_key.hotbar.9";
    public static final String BINDING_SWAP_HANDS = "key_key.swapHands";
    public static final String BINDING_DROP = "key_key.drop";

    public static final String BINDING_CHAT = "key_key.chat";

    public static int mapBindingToKeycode(@Nullable String key, int defaultKeycode) {
        if (key == null) return defaultKeycode;

        if (key.startsWith("key.")) {
            //新版MC键绑定映射
            Short glfwKeycode = MinecraftKeyBindingMapper.getGlfwKeycode(key);
            if (glfwKeycode == null) return defaultKeycode;
            return (int) glfwKeycode;
        } else {
            int lwjgl2Keycode;
            try {
                //MC旧版本直接存了LWJGL2的键值
                lwjgl2Keycode = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                return defaultKeycode;
            }
            return lwjgl2Keycode;
        }
    }

    /**
     * Minecraft 绑定按键映射表
     *
     * @param keybinding 绑定的按键
     * @return 对应的GLFW按键
     */
    public static @Nullable Short getGlfwKeycode(String keybinding) {
        switch (keybinding) {
            case "key.keyboard.unknown": return KCUKeycodes.KEY_UNKNOWN;
            case "key.mouse.left": return LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT;
            case "key.mouse.right": return LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT;
            case "key.mouse.middle": return LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_MIDDLE;
            case "key.mouse.4": return LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_4;
            case "key.mouse.5": return LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_5;
            case "key.mouse.6": return LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_6;
            case "key.mouse.7": return LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_7;
            case "key.mouse.8": return LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_8;
            case "key.keyboard.0": return KCUKeycodes.KEY_0;
            case "key.keyboard.1": return KCUKeycodes.KEY_1;
            case "key.keyboard.2": return KCUKeycodes.KEY_2;
            case "key.keyboard.3": return KCUKeycodes.KEY_3;
            case "key.keyboard.4": return KCUKeycodes.KEY_4;
            case "key.keyboard.5": return KCUKeycodes.KEY_5;
            case "key.keyboard.6": return KCUKeycodes.KEY_6;
            case "key.keyboard.7": return KCUKeycodes.KEY_7;
            case "key.keyboard.8": return KCUKeycodes.KEY_8;
            case "key.keyboard.9": return KCUKeycodes.KEY_9;
            case "key.keyboard.a": return KCUKeycodes.KEY_A;
            case "key.keyboard.b": return KCUKeycodes.KEY_B;
            case "key.keyboard.c": return KCUKeycodes.KEY_C;
            case "key.keyboard.d": return KCUKeycodes.KEY_D;
            case "key.keyboard.e": return KCUKeycodes.KEY_E;
            case "key.keyboard.f": return KCUKeycodes.KEY_F;
            case "key.keyboard.g": return KCUKeycodes.KEY_G;
            case "key.keyboard.h": return KCUKeycodes.KEY_H;
            case "key.keyboard.i": return KCUKeycodes.KEY_I;
            case "key.keyboard.j": return KCUKeycodes.KEY_J;
            case "key.keyboard.k": return KCUKeycodes.KEY_K;
            case "key.keyboard.l": return KCUKeycodes.KEY_L;
            case "key.keyboard.m": return KCUKeycodes.KEY_M;
            case "key.keyboard.n": return KCUKeycodes.KEY_N;
            case "key.keyboard.o": return KCUKeycodes.KEY_O;
            case "key.keyboard.p": return KCUKeycodes.KEY_P;
            case "key.keyboard.q": return KCUKeycodes.KEY_Q;
            case "key.keyboard.r": return KCUKeycodes.KEY_R;
            case "key.keyboard.s": return KCUKeycodes.KEY_S;
            case "key.keyboard.t": return KCUKeycodes.KEY_T;
            case "key.keyboard.u": return KCUKeycodes.KEY_U;
            case "key.keyboard.v": return KCUKeycodes.KEY_V;
            case "key.keyboard.w": return KCUKeycodes.KEY_W;
            case "key.keyboard.x": return KCUKeycodes.KEY_X;
            case "key.keyboard.y": return KCUKeycodes.KEY_Y;
            case "key.keyboard.z": return KCUKeycodes.KEY_Z;
            case "key.keyboard.f1": return KCUKeycodes.KEY_F1;
            case "key.keyboard.f2": return KCUKeycodes.KEY_F2;
            case "key.keyboard.f3": return KCUKeycodes.KEY_F3;
            case "key.keyboard.f4": return KCUKeycodes.KEY_F4;
            case "key.keyboard.f5": return KCUKeycodes.KEY_F5;
            case "key.keyboard.f6": return KCUKeycodes.KEY_F6;
            case "key.keyboard.f7": return KCUKeycodes.KEY_F7;
            case "key.keyboard.f8": return KCUKeycodes.KEY_F8;
            case "key.keyboard.f9": return KCUKeycodes.KEY_F9;
            case "key.keyboard.f10": return KCUKeycodes.KEY_F10;
            case "key.keyboard.f11": return KCUKeycodes.KEY_F11;
            case "key.keyboard.f12": return KCUKeycodes.KEY_F12;
            case "key.keyboard.f13": return KCUKeycodes.KEY_F13;
            case "key.keyboard.f14": return KCUKeycodes.KEY_F14;
            case "key.keyboard.f15": return KCUKeycodes.KEY_F15;
            case "key.keyboard.f16": return KCUKeycodes.KEY_F16;
            case "key.keyboard.f17": return KCUKeycodes.KEY_F17;
            case "key.keyboard.f18": return KCUKeycodes.KEY_F18;
            case "key.keyboard.f19": return KCUKeycodes.KEY_F19;
            case "key.keyboard.f20": return KCUKeycodes.KEY_F20;
            case "key.keyboard.f21": return KCUKeycodes.KEY_F21;
            case "key.keyboard.f22": return KCUKeycodes.KEY_F22;
            case "key.keyboard.f23": return KCUKeycodes.KEY_F23;
            case "key.keyboard.f24": return KCUKeycodes.KEY_F24;
            case "key.keyboard.num.lock": return KCUKeycodes.KEY_NUMLOCK;
            case "key.keyboard.keypad.0": return KCUKeycodes.KEY_KP0;
            case "key.keyboard.keypad.1": return KCUKeycodes.KEY_KP1;
            case "key.keyboard.keypad.2": return KCUKeycodes.KEY_KP2;
            case "key.keyboard.keypad.3": return KCUKeycodes.KEY_KP3;
            case "key.keyboard.keypad.4": return KCUKeycodes.KEY_KP4;
            case "key.keyboard.keypad.5": return KCUKeycodes.KEY_KP5;
            case "key.keyboard.keypad.6": return KCUKeycodes.KEY_KP6;
            case "key.keyboard.keypad.7": return KCUKeycodes.KEY_KP7;
            case "key.keyboard.keypad.8": return KCUKeycodes.KEY_KP8;
            case "key.keyboard.keypad.9": return KCUKeycodes.KEY_KP9;
            case "key.keyboard.down": return KCUKeycodes.KEY_DOWN;
            case "key.keyboard.left": return KCUKeycodes.KEY_LEFT;
            case "key.keyboard.right": return KCUKeycodes.KEY_RIGHT;
            case "key.keyboard.up": return KCUKeycodes.KEY_UP;
            case "key.keyboard.apostrophe": return KCUKeycodes.KEY_APOSTROPHE;
            case "key.keyboard.backslash": return KCUKeycodes.KEY_BACKSLASH;
            case "key.keyboard.comma": return KCUKeycodes.KEY_COMMA;
            case "key.keyboard.equal": return KCUKeycodes.KEY_EQUAL;
            case "key.keyboard.grave.accent": return KCUKeycodes.KEY_GRAVE;
            case "key.keyboard.left.bracket": return KCUKeycodes.KEY_LEFTBRACE;
            case "key.keyboard.minus": return KCUKeycodes.KEY_MINUS;
            case "key.keyboard.semicolon": return KCUKeycodes.KEY_SEMICOLON;
            case "key.keyboard.slash": return KCUKeycodes.KEY_SLASH;
            case "key.keyboard.space": return KCUKeycodes.KEY_SPACE;
            case "key.keyboard.tab": return KCUKeycodes.KEY_TAB;
            case "key.keyboard.left.alt": return KCUKeycodes.KEY_LEFTALT;
            case "key.keyboard.left.control": return KCUKeycodes.KEY_LEFTCTRL;
            case "key.keyboard.left.shift": return KCUKeycodes.KEY_LEFTSHIFT;
            case "key.keyboard.right.alt": return KCUKeycodes.KEY_RIGHTALT;
            case "key.keyboard.right.control": return KCUKeycodes.KEY_RIGHTCTRL;
            case "key.keyboard.right.shift": return KCUKeycodes.KEY_RIGHTSHIFT;
            case "key.keyboard.enter": return KCUKeycodes.KEY_ENTER;
            case "key.keyboard.escape": return KCUKeycodes.KEY_ESC;
            case "key.keyboard.backspace": return KCUKeycodes.KEY_BACKSPACE;
            case "key.keyboard.delete": return KCUKeycodes.KEY_DELETE;
            case "key.keyboard.end": return KCUKeycodes.KEY_END;
            case "key.keyboard.home": return KCUKeycodes.KEY_HOME;
            case "key.keyboard.insert": return KCUKeycodes.KEY_INSERT;
            case "key.keyboard.page.down": return KCUKeycodes.KEY_PAGEDOWN;
            case "key.keyboard.page.up": return KCUKeycodes.KEY_PAGEUP;
            case "key.keyboard.caps.lock": return KCUKeycodes.KEY_CAPSLOCK;
            case "key.keyboard.pause": return KCUKeycodes.KEY_PAUSE;
            case "key.keyboard.scroll.lock": return KCUKeycodes.KEY_SCROLLLOCK;
            default: return null;
        }
    }
}
