package com.kongbai.kculauncher.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.kongbai.kculauncher.KCUApp;

/**
 * 设备联网状态判断：直接读手机自身的网络状态，不发任何请求去探测。
 *
 * 【为什么是读状态而不是发请求探测】
 * 发请求探测既要多等一次超时，又会在没网时白白产生一次失败请求；
 * 而系统在连接变化时就已经维护好状态，直接读既快又不产生任何流量。
 * 这里全程只做本地查询，不发一个包。
 *
 * 【判定口径：既没有可用的移动数据，也没有连上的 WiFi，才算没网】
 * 两种通道分别检查，只有两条路都不可用时才判为没网。
 * 任何一条通着就认为能上网，避免把只开着流量、或只连着 WiFi 的用户误判成没网。
 *
 * 【为什么判断本身失败时按"有网"处理】
 * 真判不出来时若按"没网"处理，会把网络正常的用户挡在门外 ——
 * 误伤的代价远大于多发一次请求，而请求本身有超时兜底，不会无限等下去。
 */
public final class KcuNetwork {

    /** 网络类型，用于给出更明确的提示 */
    public enum Kind {
        /** 两条路都不可用 */
        NONE,
        WIFI,
        MOBILE,
        /** 其他通道（以太网、蓝牙共享等），能连上就算可用 */
        OTHER
    }

    private KcuNetwork() {
    }

    private static ConnectivityManager manager() {
        try {
            Context context = KCUApp.getAppContext();
            if (context == null) {
                return null;
            }
            Object service = context.getSystemService(Context.CONNECTIVITY_SERVICE);
            return service instanceof ConnectivityManager ? (ConnectivityManager) service : null;
        } catch (Throwable e) {
            return null;
        }
    }

    /** 某类网络是否已连上；查不到按"没连上"处理 */
    private static boolean connectedOf(ConnectivityManager manager, int type) {
        try {
            NetworkInfo info = manager.getNetworkInfo(type);
            return info != null && info.isConnected();
        } catch (Throwable e) {
            return false;
        }
    }

    /** 是否连上了 WiFi */
    public static boolean isWifiConnected() {
        ConnectivityManager manager = manager();
        if (manager == null) {
            return false;
        }
        return connectedOf(manager, ConnectivityManager.TYPE_WIFI);
    }

    /** 是否连上了移动数据 */
    public static boolean isMobileConnected() {
        ConnectivityManager manager = manager();
        if (manager == null) {
            return false;
        }
        return connectedOf(manager, ConnectivityManager.TYPE_MOBILE);
    }

    /**
     * 当前网络类型。
     *
     * 判断本身失败时返回 OTHER 而不是 NONE：
     * 判不出来就当没网会把正常用户挡在门外，代价更大。
     */
    public static Kind kind() {
        try {
            if (isWifiConnected()) {
                return Kind.WIFI;
            }
            if (isMobileConnected()) {
                return Kind.MOBILE;
            }
            ConnectivityManager manager = manager();
            if (manager == null) {
                // 查不到状态，不敢判成没网
                return Kind.OTHER;
            }
            NetworkInfo active = manager.getActiveNetworkInfo();
            if (active != null && active.isConnected()) {
                return Kind.OTHER;
            }
            return Kind.NONE;
        } catch (Throwable e) {
            return Kind.OTHER;
        }
    }

    /**
     * 当前是否联网：移动数据与 WiFi 两条路只要有一条通着就算有网。
     *
     * 只有在两条路都明确不可用时才返回 false。
     */
    public static boolean isOnline() {
        return kind() != Kind.NONE;
    }
}
