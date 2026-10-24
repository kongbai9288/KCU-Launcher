package com.kongbai.kculauncher.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.kongbai.kculauncher.core.auth.Account;

import java.util.UUID;

/**
 * 离线 UUID 的生成与自定义覆盖。
 *
 * 默认与上游一致：MD5("OfflinePlayer:" + 名称) 并置版本位。
 * 玩家可在角色设置中覆盖为自定义值，覆盖后一切按覆盖值走。
 *
 * 关于"标准 v3"：java.util.UUID.nameUUIDFromBytes 用的确实是 MD5 且版本位为 3，
 * 但它不带命名空间前缀，因此与 RFC 4122 意义的 uuid3(namespace, name) 结果不同。
 * 与上游保持一致优先于与通用工具一致 —— 不一致会让玩家失去原有存档归属，
 * 这是比"算法是否更标准"严重得多的后果。
 */
public final class KcuUuid {

    private static final String PREF = "kcu_uuid";

    private KcuUuid() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String keyOf(String username) {
        return "u_" + username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** 按名称推导默认 UUID；与上游实现完全一致 */
    public static UUID derive(String username) {
        return com.kongbai.kculauncher.core.auth.offline.OfflineAccountFactory
                .getUUIDFromUserName(username);
    }

    /**
     * 取该账号实际应使用的 UUID：玩家自定义优先，否则按名称推导。
     *
     * 自定义值只影响本启动器内的账号身份判定，不会自动改写存档；
     * 存档侧的对齐由启动前的确认流程处理。
     */
    public static UUID resolve(Context context, Account account) {
        if (account == null) {
            return null;
        }
        UUID accountUuid = account.getUUID();
        if (context != null) {
            try {
                String custom = prefs(context).getString(keyOf(account.getUsername()), null);
                if (custom != null && !custom.isEmpty()) {
                    UUID parsed = parse(custom);
                    if (parsed != null) {
                        return parsed;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return accountUuid;
    }

    /** 读取玩家自定义值；未设置返回 null */
    public static UUID getCustom(Context context, String username) {
        if (context == null || username == null) {
            return null;
        }
        try {
            return parse(prefs(context).getString(keyOf(username), null));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static void setCustom(Context context, String username, UUID uuid) {
        if (context == null || username == null || username.trim().isEmpty()) {
            return;
        }
        try {
            if (uuid == null) {
                prefs(context).edit().remove(keyOf(username)).apply();
            } else {
                prefs(context).edit().putString(keyOf(username), uuid.toString()).apply();
            }
        } catch (Throwable ignored) {
        }
    }

    /** 解析 UUID 文本；带或不带连字符均可，非法返回 null */
    public static UUID parse(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (Throwable ignored) {
        }
        if (text.length() == 32) {
            try {
                String hex = text.substring(0, 8) + "-" + text.substring(8, 12) + "-"
                        + text.substring(12, 16) + "-" + text.substring(16, 20) + "-"
                        + text.substring(20);
                return UUID.fromString(hex);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
