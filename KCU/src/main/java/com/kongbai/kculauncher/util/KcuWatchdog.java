package com.kongbai.kculauncher.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;

import com.kongbai.kculauncher.core.util.Logging;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * 智能守卫：探测主线程是否长时间失去响应，超过设定秒数就自行结束进程。
 *
 * 做法：守卫线程每秒往主线程队列投一个心跳，主线程执行时把时刻记下来；
 * 守卫线程发现时刻已经有很久没被更新，就认为主线程已经不处理任何消息了。
 *
 * 只在游戏前台期间生效（由 JVMActivity 开停）。这样做的原因是：
 * 正常浏览界面时主线程偶尔忙一阵是正常的，那时退出只会误伤；
 * 真正会把整台设备拖住的是游戏跑起来之后。
 *
 * 判定的是"主线程连续这么久一个消息都没处理"，不是"主线程有点忙"。
 * 正常游玩时主线程一直在处理输入与生命周期消息，不会触发。
 */
public final class KcuWatchdog {

    /** 与设置页共用同一份偏好文件 */
    public static final String PREFS_NAME = "launcher";
    public static final String KEY_ENABLED = "watchdogEnabled";
    public static final String KEY_TIMEOUT = "watchdogTimeoutSeconds";
    public static final String KEY_LAST_EXIT = "watchdogLastExitAt";

    /** 小于这个值不予启用：太短会把正常的加载停顿误判成卡死 */
    public static final int MIN_TIMEOUT_SEC = 10;
    public static final int[] TIMEOUT_CHOICES = {15, 30, 45, 60};
    /** 选项里默认选中的位置（30 秒） */
    public static final int DEFAULT_CHOICE_INDEX = 1;

    private static final long TICK_INTERVAL_MS = 1000L;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 主线程执行到它时记下时刻；用它是否还被更新判断主线程是否还在处理消息 */
    private static final Runnable TICK = new Runnable() {
        @Override
        public void run() {
            lastTick.set(SystemClock.elapsedRealtime());
        }
    };

    private static final AtomicLong lastTick = new AtomicLong(0L);

    private static volatile boolean running = false;
    private static Thread thread;

    /** 应用级上下文，start 时记下，供退出前记录标记用（与进程同生命周期，不持有界面） */
    private static volatile Context appContext;

    private KcuWatchdog() {
    }

    public static boolean isEnabled(Context context) {
        if (context == null) {
            return false;
        }
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    /**
     * 取设定的秒数。读到不认识的值（含小于下限）时按下限处理：
     * 宁可晚一点退出，也不能把正常的停顿当成卡死
     */
    public static int getTimeoutSeconds(Context context) {
        int value = prefs(context).getInt(KEY_TIMEOUT, TIMEOUT_CHOICES[DEFAULT_CHOICE_INDEX]);
        if (value < MIN_TIMEOUT_SEC) {
            return MIN_TIMEOUT_SEC;
        }
        return value;
    }

    /**
     * 开始守卫。只在开关打开时才真正启动；已经启动过则不动。
     * 调用点负责在游戏进入前台时开、离开前台时停。
     */
    public static synchronized void start(Context context) {
        if (context == null || !isEnabled(context)) {
            return;
        }
        if (running) {
            return;
        }
        running = true;
        appContext = context.getApplicationContext();
        lastTick.set(0L);
        final int timeoutSec = getTimeoutSeconds(context);
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                loop(timeoutSec);
            }
        }, "kcu-watchdog");
        // 守护线程：进程要退出时不能因为它还在跑而拖住
        thread.setDaemon(true);
        thread.start();
    }

    /** 停止守卫。重复调用无害 */
    public static synchronized void stop() {
        running = false;
        Thread t = thread;
        thread = null;
        if (t != null) {
            t.interrupt();
        }
        appContext = null;
    }

    public static synchronized boolean isRunning() {
        return running;
    }

    private static void loop(int timeoutSec) {
        final long timeoutMs = timeoutSec * 1000L;
        // 守卫启动的时刻。主线程从一开始就不处理任何消息时，一次心跳都收不到，
        // 此时只能拿启动时刻作为基准，否则这一路永远判不出卡死
        final long startedAt = SystemClock.elapsedRealtime();
        while (running) {
            mainHandler.post(TICK);
            try {
                Thread.sleep(TICK_INTERVAL_MS);
            } catch (InterruptedException e) {
                // stop() 触发的正常退出
                return;
            }
            long now = SystemClock.elapsedRealtime();
            long last = lastTick.get();
            // 收不到任何心跳时按启动时刻算：游戏一启动主线程就卡住正是最该救的情况，
            // 不能因为"还没收到过心跳"就一直放过
            long idle = (last == 0L) ? (now - startedAt) : (now - last);
            if (idle > timeoutMs) {
                Logging.LOG.log(Level.WARNING,
                        "Watchdog: main thread unresponsive for over " + timeoutSec
                                + "s, killing process to release the device");
                recordExit();
                // 主线程已经不处理消息了，finish 之类的正常退出路径根本执行不到，
                // 只能直接结束进程
                Process.killProcess(Process.myPid());
                return;
            }
        }
    }

    /**
     * 记下这次是守卫触发的退出，供下次启动判断是否要提示。
     * 用 commit 同步写：随后进程立刻就没了，异步写可能来不及落盘。
     * 整段兜住异常：记录失败也必须照常退出，不能因此把守卫卡在这里
     */
    private static void recordExit() {
        try {
            Context context = appContext;
            if (context == null) {
                return;
            }
            prefs(context).edit()
                    .putLong(KEY_LAST_EXIT, System.currentTimeMillis())
                    .commit();
        } catch (Throwable t) {
            Logging.LOG.log(Level.WARNING, "Watchdog: failed to record last exit", t);
        }
    }

    /**
     * 取走"上次是否由守卫触发退出"的标记并清掉（只提示一次，不反复弹）
     */
    public static long takeLastExitAt(Context context) {
        if (context == null) {
            return 0L;
        }
        try {
            SharedPreferences p = prefs(context);
            long at = p.getLong(KEY_LAST_EXIT, 0L);
            if (at != 0L) {
                p.edit().remove(KEY_LAST_EXIT).commit();
            }
            return at;
        } catch (Throwable t) {
            Logging.LOG.log(Level.WARNING, "Watchdog: failed to read last exit", t);
            return 0L;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
