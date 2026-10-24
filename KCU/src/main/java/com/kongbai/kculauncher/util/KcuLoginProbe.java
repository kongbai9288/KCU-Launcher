package com.kongbai.kculauncher.util;

import android.content.Context;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.auth.Account;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.setting.Accounts;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

/**
 * 在线验证可用性探测。
 *
 * 只做一次轻量查询：用玩家填写的名称去问该名称是否对应正版账号。
 * 查询的是公开接口，不发送密码，也不产生任何登录态 —— 因此它不能"替玩家登录"，
 * 只用于决定接下来把玩家引向哪条路。
 *
 * 网络不通、超时、非 200 一律判为不可用：宁可让玩家走离线，也不能因为探测失败就卡住。
 */
public final class KcuLoginProbe {

    /** 该名称对应正版账号 */
    public static final int RESULT_PREMIUM = 1;
    /** 该名称不是正版账号（接口正常返回 404 / 空） */
    public static final int RESULT_NOT_PREMIUM = 0;
    /** 探测未成功：网络不通、超时或返回异常 */
    public static final int RESULT_UNAVAILABLE = -1;

    private static final String ENDPOINT = "https://api.mojang.com/users/profiles/minecraft/";
    private static final int TIMEOUT_MS = 8000;

    private KcuLoginProbe() {
    }

    /**
     * 判定该名称是否对应正版账号。
     *
     * 名称非法时直接返回不可用，不发起请求 —— 名称里可能带路径字符，
     * 拼进 URL 会造成请求打到非预期地址。
     */
    public static int probe(String username) {
        if (!KcuAccountFlow.isValidName(username)) {
            return RESULT_UNAVAILABLE;
        }
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            String url = ENDPOINT + URLEncoder.encode(username.trim(), "UTF-8");
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            int code = conn.getResponseCode();
            if (code == 200) {
                return RESULT_PREMIUM;
            }
            if (code == 404 || code == 204) {
                return RESULT_NOT_PREMIUM;
            }
            return RESULT_UNAVAILABLE;
        } catch (Throwable e) {
            return RESULT_UNAVAILABLE;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
