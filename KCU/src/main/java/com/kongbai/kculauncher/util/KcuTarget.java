package com.kongbai.kculauncher.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.kongbai.kculauncher.KCUApp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 规则命中判断：版本号与客户端类型。
 *
 * 【为什么规则要分版本和客户端】
 * 同一个策略不可能对所有版本、所有设备一刀切。某个问题只出现在旧版本上，
 * 或者只影响某一种设备，如果只能全局开关，就会连带影响用得好好的那些人。
 * 因此每条规则都写清楚它管哪些版本、哪些客户端，只有都匹配才生效。
 *
 * 【版本号书写方案】
 * 采用三段式，与常见游戏版本的写法一致：主.次.修订，例如 1.20.1。
 * 支持三种写法：
 *   1.20.1        只匹配这一个版本
 *   1.20.x        匹配这一段内的所有修订版
 *   1.20.0-1.21.4 匹配闭区间内的所有版本
 * 不写或写 * 表示所有版本。
 *
 * 【客户端类型书写方案】
 * 采用 平台-架构 两段，例如 android-arm64。
 * 只写平台（android）表示该平台下所有架构。
 * 不写或写 * 表示所有客户端。
 */
public final class KcuTarget {

    private KcuTarget() {
    }

    /**
     * 比较两个版本，a 低于 b 返回负数，相等返回 0，高于返回正数。
     *
     * 供需要判断"当前版本是否太低"的场景使用。
     */
    public static int compareVersions(String a, String b) {
        return compare(parts(a), parts(b));
    }

    /** 解析成整数序列，便于逐段比较；无法识别的段按 0 处理 */
    private static int[] parts(String version) {
        List<Integer> out = new ArrayList<>();
        if (version != null) {
            String text = version.trim();
            // 去掉可能带上的前缀，例如 v1.20.1
            if (!text.isEmpty() && (text.charAt(0) == 'v' || text.charAt(0) == 'V')) {
                text = text.substring(1);
            }
            // 只取数字与点，忽略其后的后缀（例如 1.20.1-pre1 里的 -pre1）
            StringBuilder num = new StringBuilder();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '.') {
                    out.add(parseOrZero(num.toString()));
                    num.setLength(0);
                } else if (c >= '0' && c <= '9') {
                    num.append(c);
                } else {
                    break;
                }
            }
            out.add(parseOrZero(num.toString()));
        }
        // 补齐到三段，位数不同也能逐段比
        while (out.size() < 3) {
            out.add(0);
        }
        int[] arr = new int[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }

    private static int parseOrZero(String text) {
        try {
            return Integer.parseInt(text);
        } catch (Throwable e) {
            return 0;
        }
    }

    /** 逐段比较：a 小于 b 返回负数，相等返回 0，大于返回正数 */
    private static int compare(int[] a, int[] b) {
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return 0;
    }

    /**
     * 判断当前版本是否落在规则写的范围内。
     *
     * 规则本身写得无法识别时返回不匹配：
     * 一条谁都看不懂的规则如果当成匹配，会把限制套到所有人头上，
     * 而当成不匹配最多是这条规则没生效，后者安全得多。
     */
    public static boolean versionMatches(String pattern, String current) {
        if (pattern == null || pattern.trim().isEmpty() || "*".equals(pattern.trim())) {
            return true;
        }
        String text = pattern.trim();
        try {
            // 区间写法：低-高
            int dash = text.indexOf('-');
            // 注意 1.20.1-pre1 这类带后缀的本身含短横线，只在看起来像区间时才按区间处理
            if (dash > 0 && isRange(text, dash)) {
                String low = text.substring(0, dash);
                String high = text.substring(dash + 1);
                int[] c = parts(current);
                return compare(c, parts(low)) >= 0 && compare(c, parts(high)) <= 0;
            }
            // 通配写法：某一段是 x
            if (text.toLowerCase(Locale.ROOT).contains("x")) {
                int[] c = parts(current);
                String[] seg = text.split("\\.");
                for (int i = 0; i < seg.length && i < c.length; i++) {
                    String one = seg[i].trim().toLowerCase(Locale.ROOT);
                    if ("x".equals(one) || "*".equals(one)) {
                        continue;
                    }
                    if (parseOrZero(one) != c[i]) {
                        return false;
                    }
                }
                return true;
            }
            // 精确写法
            return compare(parts(current), parts(text)) == 0;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 短横线处看起来像版本区间的标志：后面紧跟数字 */
    private static boolean isRange(String text, int dash) {
        return dash + 1 < text.length() && Character.isDigit(text.charAt(dash + 1));
    }

    /**
     * 当前应用版本号。
     *
     * 取不到时返回空串而不是占位符：版本号要参与区间比较，
     * 混入非数字内容会让比较结果不可预期，空串则会被当成"没有版本"，
     * 由调用方决定如何处理。
     */
    public static String currentVersion() {
        try {
            Context context = KCUApp.getAppContext();
            if (context == null) {
                return "";
            }
            String name = context.getPackageName();
            PackageManager pm = context.getPackageManager();
            PackageInfo info = Build.VERSION.SDK_INT >= 33
                    ? pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
                    : pm.getPackageInfo(name, 0);
            String version = info.versionName;
            return version == null ? "" : version.trim();
        } catch (Throwable e) {
            return "";
        }
    }

    /** 当前客户端标识：平台-架构 */
    public static String currentClient() {
        return ("android-" + primaryAbi()).toLowerCase(Locale.ROOT);
    }

    /** 设备首选 ABI；取不到时按 unknown，避免与任何真实架构意外相同 */
    private static String primaryAbi() {
        String abi = "";
        try {
            String[] abis = Build.SUPPORTED_ABIS;
            abi = abis != null && abis.length > 0 ? abis[0] : "";
        } catch (Throwable e) {
            abi = "";
        }
        if (abi == null || abi.isEmpty()) {
            abi = "unknown";
        }
        return abi;
    }

    /**
     * 同一架构的常见简称。
     *
     * 只列确实通用的那几种：设备上报的是 arm64-v8a、armeabi-v7a 这种完整写法，
     * 而规则里习惯写成 arm64、arm 这种简称。列多了容易与别的架构混淆，
     * 因此宁可少列，也不要凭猜测写进不属于这个架构的名字。
     */
    private static List<String> abiAliases(String abi) {
        List<String> out = new ArrayList<>();
        if ("arm64-v8a".equals(abi)) {
            out.add("arm64");
        } else if ("armeabi-v7a".equals(abi)) {
            out.add("armeabi");
            out.add("arm");
        } else if ("armeabi".equals(abi)) {
            out.add("arm");
        } else if ("x86_64".equals(abi)) {
            out.add("x64");
        }
        return out;
    }

    /**
     * 当前客户端的所有等价写法。
     *
     * 【为什么要列一套等价写法，而不是只比一种】
     * 设备上报的 ABI 是完整写法（arm64-v8a），规则里却习惯写简称（android-arm64）。
     * 只比完整写法的话，照着说明写出来的规则在真机上一条都匹配不上，
     * 而且匹配不上不会有任何报错 —— 规则安静地不生效，
     * 看上去像是远端没下发、或者代码漏了判断，很难查到是写法对不上。
     * 因此把同一个设备的几种常见写法都列出来，命中任意一种即算匹配。
     */
    private static List<String> clientAliases() {
        List<String> out = new ArrayList<>();
        String abi = primaryAbi().toLowerCase(Locale.ROOT);
        out.add("android-" + abi);
        for (String alias : abiAliases(abi)) {
            String one = "android-" + alias;
            if (!out.contains(one)) {
                out.add(one);
            }
        }
        // 只写平台时匹配该平台下所有架构
        if (!out.contains("android")) {
            out.add("android");
        }
        return out;
    }

    /**
     * 判断当前客户端是否匹配。
     *
     * 规则写 android 表示匹配所有安卓架构，写 android-arm64 只匹配这一种，
     * 写 android-arm64-v8a 这种完整写法同样能匹配。
     * 无法识别时按不匹配处理，理由同版本号：宁可规则不生效也不乱套。
     */
    public static boolean clientMatches(String pattern) {
        if (pattern == null || pattern.trim().isEmpty() || "*".equals(pattern.trim())) {
            return true;
        }
        try {
            String want = pattern.trim().toLowerCase(Locale.ROOT);
            for (String alias : clientAliases()) {
                if (want.equals(alias)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable e) {
            return false;
        }
    }
}
