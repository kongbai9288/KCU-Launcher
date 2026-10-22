package com.mio.promo

import android.content.Context
import androidx.fragment.app.FragmentActivity

object QuarkPromo {

    /**
     * 已停用。
     * 原由 UpdateChecker 在拉取远程版本表成功后调用，缓存上游网盘推广链接。
     * 上游更新源已停用，此处不再写入任何值。
     */
    @JvmStatic
    fun updateNetdiskUrl(context: Context, url: String?) = Unit

    /**
     * 已停用：启动流程不再被推广拦截，直接放行。
     * 原实现按启动次数掷骰子弹窗引导跳转外部网盘链接，并在中签时取消本次启动。
     */
    fun interceptLaunch(activity: FragmentActivity, onProceed: () -> Unit) {
        onProceed()
    }

}
