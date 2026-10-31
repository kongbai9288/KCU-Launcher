package com.kongbai.kculauncher.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.kongbai.kculauncher.KCUApp;
import com.kongbai.kculauncher.core.download.BMCLAPIDownloadProvider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 下载页那几个下载用的多源配置。
 *
 * 一个文件可以不止一个地址：官方地址是一个，镜像地址是一个，
 * 玩家自己填的也算。下载时同时从几个源拉同一个文件，谁先下完用谁的。
 *
 * 【为什么只管下载页那几个】
 * 我的世界本体、依赖库这些有自己的下载体系（含缓存、断点、校验、
 * 依赖顺序），插一脚进去风险不划算。下载页的模组 / 资源包 / 世界 /
 * 光影包是单个文件落地，最适合这么干。
 *
 * 【为什么镜像地址不是写死的】
 * 项目里本来就有现成的地址替换表，直接复用它。
 * 自己再抄一份，将来上游改了镜像地址，这里就会一直用旧地址。
 */
public final class KcuDownloadSources {

    private static final String TAG = "KCU";

    /** 官方源：文件原本的地址，不加任何改写 */
    public static final String OFFICIAL = "official";
    /** 镜像源：按项目自带的地址替换表改写 */
    public static final String BMCL = "bmcl";

    private static final String PREF = "kcu_download_sources";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_CUSTOM = "custom";

    /** 自定义源的规则分隔符：一行一条，原前缀与新前缀之间用这个隔开 */
    private static final char SEP = '\t';
    private static final char LINE = '\n';

    private static volatile BMCLAPIDownloadProvider mirror;

    private KcuDownloadSources() {
    }

    private static SharedPreferences prefs() {
        Context context = KCUApp.getAppContext();
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /**
     * 当前启用的源。
     *
     * 默认官方与镜像都开：玩家什么都没配过时就能直接享受加速，
     * 不需要先去设置里找开关。
     */
    public static Set<String> enabled() {
        Set<String> out = new LinkedHashSet<>();
        try {
            String raw = prefs().getString(KEY_ENABLED, null);
            if (raw != null && !raw.isEmpty()) {
                for (String one : raw.split(",")) {
                    String id = one.trim();
                    if (!id.isEmpty()) {
                        out.add(id);
                    }
                }
                return out;
            }
        } catch (Throwable e) {
            // 读不到就按默认来
        }
        out.add(OFFICIAL);
        out.add(BMCL);
        return out;
    }

    /** 是否启用某个源；同步写入，避免刚勾完进程被终止时丢掉 */
    public static void setEnabled(String id, boolean on) {
        if (id == null || id.isEmpty()) {
            return;
        }
        Set<String> set = enabled();
        if (on) {
            set.add(id);
        } else {
            set.remove(id);
        }
        try {
            prefs().edit().putString(KEY_ENABLED, join(set)).commit();
        } catch (Throwable ignored) {
        }
    }

    private static String join(Set<String> set) {
        StringBuilder sb = new StringBuilder();
        for (String one : set) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(one);
        }
        return sb.toString();
    }

    public static final class Rule {
        /** 只对以这个前缀开头的地址生效 */
        public final String from;
        /** 把前缀换成这个 */
        public final String to;

        Rule(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }

    /**
     * 玩家自己填的源。
     *
     * 存的是"前缀替换"规则而不是整串地址：同一个文件在不同源上
     * 只有前面那段不一样，让玩家为每个文件都填一遍地址不现实。
     */
    public static List<Rule> custom() {
        List<Rule> out = new ArrayList<>();
        String raw;
        try {
            raw = prefs().getString(KEY_CUSTOM, null);
        } catch (Throwable e) {
            return out;
        }
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split(String.valueOf(LINE))) {
            if (line.isEmpty()) {
                continue;
            }
            int at = line.indexOf(SEP);
            if (at <= 0 || at >= line.length() - 1) {
                // 缺一半的规则没法用，跳过而不是让整份配置失效
                continue;
            }
            out.add(new Rule(line.substring(0, at), line.substring(at + 1)));
        }
        return out;
    }

    /** 同步写入，理由同 setEnabled */
    public static void saveCustom(List<Rule> rules) {
        StringBuilder sb = new StringBuilder();
        if (rules != null) {
            for (Rule rule : rules) {
                if (rule == null || rule.from == null || rule.to == null) {
                    continue;
                }
                if (rule.from.isEmpty() || rule.to.isEmpty()) {
                    continue;
                }
                // 前缀里出现分隔符会把配置串味，直接丢掉这条
                if (rule.from.indexOf(SEP) >= 0 || rule.to.indexOf(SEP) >= 0
                        || rule.from.indexOf(LINE) >= 0 || rule.to.indexOf(LINE) >= 0) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append(LINE);
                }
                sb.append(rule.from).append(SEP).append(rule.to);
            }
        }
        try {
            prefs().edit().putString(KEY_CUSTOM, sb.toString()).commit();
        } catch (Throwable ignored) {
        }
    }

    /** 按项目自带的替换表改写地址；改不了就原样返回 */
    private static String injectMirror(String origin) {
        try {
            if (mirror == null) {
                synchronized (KcuDownloadSources.class) {
                    if (mirror == null) {
                        mirror = new BMCLAPIDownloadProvider("https://bmclapi2.bangbang93.com");
                    }
                }
            }
            return mirror.injectURL(origin);
        } catch (Throwable e) {
            Log.w(TAG, "Failed to build mirror url");
            return origin;
        }
    }

    /**
     * 排出这个文件的候选地址。
     *
     * 官方源永远在第一个：它是文件原本的地址，一定可用（只是可能慢）。
     * 其余源改写不出来就不加入 —— 拿一个跟原来一模一样的地址去并发
     * 毫无意义，还平白多占一份流量。
     */
    public static List<String> candidates(String origin) {
        List<String> out = new ArrayList<>();
        if (origin == null || origin.isEmpty()) {
            return out;
        }
        Set<String> on = enabled();

        if (on.contains(OFFICIAL)) {
            out.add(origin);
        }
        if (on.contains(BMCL)) {
            String injected = injectMirror(origin);
            if (!injected.equals(origin) && !out.contains(injected)) {
                out.add(injected);
            }
        }
        try {
            for (Rule rule : custom()) {
                if (!origin.startsWith(rule.from)) {
                    continue;
                }
                String converted = rule.to + origin.substring(rule.from.length());
                if (!out.contains(converted)) {
                    out.add(converted);
                }
            }
        } catch (Throwable e) {
            // 自定义规则有问题不影响已经排好的那几个
            Log.w(TAG, "Failed to apply custom source");
        }
        return out;
    }
}
