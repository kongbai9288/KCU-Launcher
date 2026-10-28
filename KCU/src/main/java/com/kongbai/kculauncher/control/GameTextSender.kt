package com.kongbai.kculauncher.control

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.kongbai.kculauncher.game.sdl.SdlBridge
import com.kongbai.kculauncher.launcher.bridge.KCUBridge
import com.kongbai.kculauncher.launcher.keycodes.KCUKeycodes
import com.kongbai.kculauncher.launcher.keycodes.MinecraftKeyBindingMapper
import org.libsdl.app.SDLActivity

/**
 * 向游戏提交文本：光标模式下提交到游戏内已打开的文本框，其余情况先打开聊天栏再提交并回车。
 * SDL 渲染路径（MC 26.3+）不注册 GLFW 字符回调，字符须经 SDL 文本输入通道提交，
 * 该通道仅在游戏内文本框聚焦时激活，须待其就绪后再提交
 */
object GameTextSender {

    private const val TAG = "KCU"
    private const val CHAT_OPEN_DELAY_MS = 150L
    private const val CHANNEL_WAIT_TIMEOUT_MS = 1000L
    private const val CHANNEL_POLL_INTERVAL_MS = 16L

    private val handler = Handler(Looper.getMainLooper())

    @JvmStatic
    @JvmOverloads
    fun send(menu: GameMenu, text: String, onDropped: Runnable? = null) {
        if (menu.cursorMode == KCUBridge.CursorEnabled) {
            commitText(menu, text)
            return
        }
        // 非光标模式下要先按 T 打开聊天栏；空文本没有意义，直接不动作
        if (text.isEmpty()) {
            return
        }
        menu.input.sendBoundKeyEvent(
            menu.gameOption,
            MinecraftKeyBindingMapper.BINDING_CHAT,
            KCUKeycodes.KEY_T,
            true
        )
        menu.input.sendBoundKeyEvent(
            menu.gameOption,
            MinecraftKeyBindingMapper.BINDING_CHAT,
            KCUKeycodes.KEY_T,
            false
        )
        whenTextInputReady({
            commitText(menu, text)
            menu.input.sendKeyEvent(KCUKeycodes.KEY_ENTER, true)
            menu.input.sendKeyEvent(KCUKeycodes.KEY_ENTER, false)
        }, onDropped)
    }

    private fun commitText(menu: GameMenu, text: String) {
        if (isSdlRenderActive()) {
            Log.i(TAG, "commit text via SDL, length=" + text.length)
            SDLActivity.commitTextInput(text)
        } else {
            text.forEach { menu.input.sendChar(it) }
        }
    }

    /**
     * 待文本可提交后执行：SDL 渲染路径轮询 native 通道激活状态，GLFW 路径按固定延时等聊天栏打开。
     * 判定用 native 真实状态，Java 侧激活镜像受软键盘 hint 影响可能缺失
     */
    private fun whenTextInputReady(action: () -> Unit, onDropped: Runnable? = null) {
        if (!isSdlRenderActive()) {
            handler.postDelayed(action, CHAT_OPEN_DELAY_MS)
            return
        }
        var waited = 0L
        val poll = object : Runnable {
            override fun run() {
                if (SdlBridge.isNativeTextInputActive()) {
                    action()
                    return
                }
                if (waited >= CHANNEL_WAIT_TIMEOUT_MS) {
                    Log.w(TAG, "native text input channel not active after " + CHANNEL_WAIT_TIMEOUT_MS + "ms, text dropped")
                    // 文本被丢弃必须让调用方知道：按下按钮却静默什么都不发生，
                    // 玩家只会以为按钮坏了
                    onDropped?.run()
                    return
                }
                waited += CHANNEL_POLL_INTERVAL_MS
                handler.postDelayed(this, CHANNEL_POLL_INTERVAL_MS)
            }
        }
        poll.run()
    }

    private fun isSdlRenderActive(): Boolean = SdlBridge.sdlEnabled && SdlBridge.isSdlRenderActive()
}
