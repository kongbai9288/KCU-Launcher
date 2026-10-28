package com.kongbai.kculauncher.control.keyboard;

import static android.content.Context.INPUT_METHOD_SERVICE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kongbai.kculauncher.control.GameMenu;
import com.kongbai.kculauncher.game.sdl.SdlBridge;

import org.libsdl.app.SDLActivity;

/**
 * From PojavLauncher
 * This class is intended for sending characters used in chat via the virtual keyboard
 */
public class TouchCharInput extends androidx.appcompat.widget.AppCompatEditText {

    public static final String TEXT_FILLER = "                              ";

    public TouchCharInput(@NonNull Context context) {
        this(context, null);
    }

    public TouchCharInput(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, androidx.appcompat.R.attr.editTextStyle);
    }

    public TouchCharInput(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    private GameMenu menu;
    private boolean isDoingInternalChanges = false;
    private CharacterSenderStrategy characterSender;

    /**
     * 上一次已经提交到游戏的有效文本（不含占位空格），用于与当前文本比对差值。
     *
     * 【为什么不再按 lengthBefore / lengthAfter 直接发送】
     * 输入框里始终垫着一段占位空格（TEXT_FILLER），它是为了让部分输入法肯开始工作。
     * 逐字输入时新字符追加在占位空格之后，按增量发送没有问题；
     * 但复制或粘贴时，不少输入法会一次性替换掉输入框里的全部内容，
     * 此时回调给出的是整段文本的替换区间，按原逻辑就会把占位空格
     * 连同真实内容一起发给游戏，表现就是粘贴出来的内容前面莫名其妙多出空格。
     *
     * 这里改为：先剥掉前导空格得到"有效文本"，再与上次已提交的文本比对，
     * 只补发多出来的部分、只回退少掉的部分。占位空格不参与比对，也就不会被发出去。
     */
    private String lastCommitted = "";

    /** 剥掉前导空格；占位空格与玩家自己敲在开头的空格都不参与提交 */
    private static String stripLeadingSpaces(CharSequence text) {
        if (text == null) {
            return "";
        }
        int i = 0;
        while (i < text.length() && text.charAt(i) == ' ') {
            ++i;
        }
        return text.subSequence(i, text.length()).toString();
    }

    /**
     * We take the new chars, and send them to the game.
     * If less chars are present, remove some.
     * The text is always cleaned up.
     */
    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        if (isDoingInternalChanges)
            return;
        if (characterSender != null) {
            String effective = stripLeadingSpaces(text);

            int common = 0;
            int limit = Math.min(lastCommitted.length(), effective.length());
            while (common < limit && lastCommitted.charAt(common) == effective.charAt(common)) {
                ++common;
            }

            for (int i = lastCommitted.length(); i > common; --i) {
                characterSender.sendBackspace();
            }
            for (int i = common; i < effective.length(); ++i) {
                characterSender.sendChar(effective.charAt(i));
            }
            lastCommitted = effective;
        }

        // Reset the keyboard state
        if (text.length() < 1)
            clear();
    }

    /**
     * When we change from app to app, the keyboard gets disabled.
     * So, we disable the object
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        disable();
    }

    /**
     * Intercepts the back key to disable focus
     * Does not affect the rest of the activity.
     */
    @Override
    public boolean onKeyPreIme(final int keyCode, final KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
            disable();
        }
        return super.onKeyPreIme(keyCode, event);
    }

    /**
     * Toggle on and off the soft keyboard, depending of the state
     */
    public void switchKeyboardState() {
        // 仅当游戏运行在 SDL 渲染路径（MC 26.3+）时由 SDL 输入框接管；
        // 仅手柄子系统初始化 SDL 时（如 MC 26.2 挂 Controlify）游戏输入仍走 GLFW 桥，
        // 委托给 SDL 通道只会被拒绝，须回落到启动器侧输入
        if (SdlBridge.getSdlEnabled() && SdlBridge.isSdlRenderActive()) {
            if (SDLActivity.isSDLEditKeyboardShown()) {
                SDLActivity.disableSDLEditKeyboard();
            } else {
                SDLActivity.enableSDLEditKeyboard();
            }
            return;
        }
        InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
        if (hasFocus()) {
            inputMethodManager.hideSoftInputFromWindow(getWindowToken(), 0);
            clear();
            disable();
            if (menu != null && menu.getInput().getFocusableView() != null) {
                if (!menu.getMenuSetting().isPhysicalMouseMode()) {
                    menu.getInput().getFocusableView().requestFocus();
                    menu.getInput().getFocusableView().requestPointerCapture();
                }
            }
        } else {
            if (menu != null && menu.getInput().getFocusableView() != null) {
                if (!menu.getMenuSetting().isPhysicalMouseMode()) {
                    menu.getInput().getFocusableView().releasePointerCapture();
                    menu.getInput().getFocusableView().clearFocus();
                }
            }
            enable();
            inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }
    }


    /**
     * Clear the EditText from any leftover inputs
     * It does not affect the in-game input
     */
    @SuppressLint("SetTextI18n")
    public void clear() {
        isDoingInternalChanges = true;
        setText(TEXT_FILLER);
        setSelection(TEXT_FILLER.length());
        // 输入框被清空后，游戏侧也不再保留上次提交的内容，比对基准必须一起归零，
        // 否则下次输入会先按旧基准回退，把游戏里已经存在的内容删掉
        lastCommitted = "";
        isDoingInternalChanges = false;
    }

    /**
     * Regain ability to exist, take focus and have some text being input
     */
    public void enable() {
        setEnabled(true);
        setFocusable(true);
        setVisibility(VISIBLE);
        requestFocus();
        sActiveInput = this;
    }

    /**
     * Lose ability to exist, take focus and have some text being input
     */
    public void disable() {
        clear();
        setVisibility(GONE);
        clearFocus();
        setEnabled(false);
        if (sActiveInput == this) {
            sActiveInput = null;
        }
    }

    // 当前激活的字符输入控件，供 SDL 输入法接管时统一关闭（见 SDLActivity）
    private static TouchCharInput sActiveInput;

    public static void disableActiveInput() {
        if (sActiveInput != null) {
            sActiveInput.disable();
        }
    }

    /**
     * 收起当前正在使用的输入框（关掉软键盘）。
     *
     * @return true 表示确实收起了一个正在使用的输入框；
     *         false 表示当前没有可收起的输入 —— 调用方据此判断这次按下要不要直接干活，
     *         避免玩家在没有输入法的情况下白白多点一次
     */
    public static boolean dismissActiveInput() {
        if (sActiveInput == null) {
            return false;
        }
        if (sActiveInput.getVisibility() != VISIBLE || !sActiveInput.isEnabled()) {
            return false;
        }
        sActiveInput.disable();
        return true;
    }

    /**
     * Send the enter key.
     */
    private void sendEnter() {
        characterSender.sendEnter();
        clear();
    }

    /**
     * Just sets the char sender that should be used.
     */
    public void setCharacterSender(GameMenu gameMenu, CharacterSenderStrategy characterSender) {
        this.menu = gameMenu;
        this.characterSender = characterSender;
    }

    /**
     * This function deals with anything that has to be executed when the constructor is called
     */
    private void setup() {
        setOnEditorActionListener((textView, i, keyEvent) -> {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
            sendEnter();
            clear();
            disable();
            if (menu != null && menu.getInput().getFocusableView() != null) {
                if (!menu.getMenuSetting().isPhysicalMouseMode()) {
                    menu.getInput().getFocusableView().requestFocus();
                    menu.getInput().getFocusableView().requestPointerCapture();
                }
            }
            return false;
        });
        clear();
        disable();
    }

    public void hide() {
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(getWindowToken(), 0);
        sendEnter();
        clear();
        disable();
    }

}