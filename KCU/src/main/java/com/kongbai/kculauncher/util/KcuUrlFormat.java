package com.kongbai.kculauncher.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 格式链接：用 § 包裹占位符，由玩家逐个填写后拼出真实地址。
 *
 * 【为什么用 § 而不是常见的花括号】
 * 地址里本身就可能含有 { } 和 $ ，用它们做占位符会和正常地址混淆；
 * § 不会出现在正常地址中，界定清楚，也不必转义。
 *
 * 【为什么先解析出占位符再让玩家填】
 * 若只给一个输入框让玩家自己把整条地址拼好，格式一复杂就极易写错，
 * 而且写错了没有任何提示，只会下载失败。解析出占位符后逐个填，
 * 每个填什么一目了然，也不会漏填。
 */
public final class KcuUrlFormat {

    private static final char MARK = '\u00a7';

    private KcuUrlFormat() {
    }

    /**
     * 取出格式里的占位符名称，按出现顺序、去重。
     *
     * 只认完整成对的一对 § ：单个 § 或没闭合的都忽略，
     * 免得玩家还没写完就被当成占位符去索要填写。
     */
    public static List<String> placeholders(String format) {
        List<String> out = new ArrayList<>();
        if (format == null || format.isEmpty()) {
            return out;
        }
        int i = 0;
        int len = format.length();
        while (i < len) {
            int open = format.indexOf(MARK, i);
            if (open < 0) {
                break;
            }
            int close = format.indexOf(MARK, open + 1);
            if (close < 0) {
                // 有开头没结尾，说明还没写完，忽略
                break;
            }
            String name = format.substring(open + 1, close).trim();
            if (!name.isEmpty() && !out.contains(name)) {
                out.add(name);
            }
            i = close + 1;
        }
        return out;
    }

    /**
     * 用已填的值替换占位符，得到最终地址。
     *
     * 未提供值的占位符原样保留：
     * 这样玩家能在结果里一眼看出是哪一处没填，
     * 而不是拿到一条看似完整、实则错误的地址去下载失败。
     */
    public static String apply(String format, Map<String, String> values) {
        if (format == null || format.isEmpty()) {
            return format;
        }
        Map<String, String> safe = values == null ? new LinkedHashMap<String, String>() : values;
        StringBuilder out = new StringBuilder();
        int i = 0;
        int len = format.length();
        while (i < len) {
            int open = format.indexOf(MARK, i);
            if (open < 0) {
                out.append(format, i, len);
                break;
            }
            out.append(format, i, open);
            int close = format.indexOf(MARK, open + 1);
            if (close < 0) {
                // 没闭合，剩下的原样保留
                out.append(format, open, len);
                break;
            }
            String name = format.substring(open + 1, close).trim();
            String value = safe.get(name);
            if (value == null || value.isEmpty()) {
                out.append(format, open, close + 1);
            } else {
                out.append(value);
            }
            i = close + 1;
        }
        return out.toString();
    }

    /** 是否含有完整成对的占位符 */
    public static boolean hasPlaceholder(String format) {
        return !placeholders(format).isEmpty();
    }
}
