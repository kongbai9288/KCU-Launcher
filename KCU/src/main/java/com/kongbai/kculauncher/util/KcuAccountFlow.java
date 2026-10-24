package com.kongbai.kculauncher.util;

import com.kongbai.kculauncher.setting.Accounts;
import com.kongbai.kculauncher.core.auth.Account;
import com.kongbai.kculauncher.core.auth.offline.OfflineAccount;
import com.kongbai.kculauncher.core.auth.offline.OfflineAccountFactory;
import com.kongbai.kculauncher.core.auth.offline.Skin;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 按名称解析账号。
 *
 * 先复用已存在的同名账号，其次复用已存在的在线账号，
 * 都没有时按名称生成本地账号。皮肤可选，为空时不设置。
 */
public final class KcuAccountFlow {

    private KcuAccountFlow() {
    }

    /**
     * @param username 名称，必填
     * @param skin     皮肤，可为空
     */
    public static Account resolve(String username, Skin skin) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        String name = username.trim();

        try {
            List<Account> accounts = Accounts.getAccounts();
            if (accounts != null) {
                for (Account account : accounts) {
                    if (account != null && name.equalsIgnoreCase(account.getUsername())) {
                        return account;
                    }
                }
                for (Account account : accounts) {
                    if (account != null && !(account instanceof OfflineAccount)) {
                        return account;
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return createLocal(name, skin);
    }

    /** 按名称生成本地账号；皮肤为空时走无皮肤分支 */
    private static Account createLocal(String username, Skin skin) {
        try {
            // OfflineAccount 构造器对 uuid 有非空校验，必须先算出稳定的离线 uuid
            UUID uuid = OfflineAccountFactory.getUUIDFromUserName(username);
            if (skin == null) {
                return Accounts.FACTORY_OFFLINE.create(username, uuid);
            }
            Object additional = new OfflineAccountFactory.AdditionalData(null, skin);
            return Accounts.FACTORY_OFFLINE.create(null, username, "", null, additional);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 名称合法性：仅允许字母数字与下划线，长度 3-16 */
    public static boolean isValidName(String username) {
        if (username == null) {
            return false;
        }
        String name = username.trim();
        if (name.length() < 3 || name.length() > 16) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    public static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
