package com.kongbai.kculauncher.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.kongbai.kculauncher.KCUApp;

/**
 * 设备联网状态判断。
 *
 * 【为什么先判联网再发请求】
 * 没网时发起请求有两种坏结果：一是请求要等到超时才失败，界面长时间转圈，
 * 玩家以为功能坏了；二是部分系统会立刻抛出无法连接，若没有兜底会直接崩。
 * 先本地判一次，没网就立刻告诉玩家，既不发无效请求，也不用等超时。
 *
 * 【为什么判不出来按"有网"处理】
 * 这个判断是本地操作，正常情况下很可靠。真判不出来时若按"没网"处理，
 * 会把网络正常的用户挡在门外 —— 误伤的代价远大于多发一次请求，
 * 而请求本身有超时兜底，不会无限等下去。
 */
public final class KcuNetwork {

    private KcuNetwork() {
    }

    /**
     * 当前是否联网。
     *
     * 只有在明确判断出"没有可用网络"时才返回 false；
     * 判断本身失败时返回 true，交由后续请求的超时兜底，避免误伤。
     */
    public static boolean isOnline() {
        try {
            Context context = KCUApp.getAppContext();
            if (context == null) {
                return true;
            }
            Object service = context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (!(service instanceof ConnectivityManager)) {
                return true;
            }
            ConnectivityManager manager = (ConnectivityManager) service;
            NetworkInfo info = manager.getActiveNetworkInfo();
            // 没有活动网络，或网络存在但没连上，都算没网
            return info != null && info.isConnected();
        } catch (Throwable e) {
            return true;
        }
    }
}
