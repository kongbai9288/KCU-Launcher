package com.kongbai.kculauncher.util;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 许可密钥校验（本地对比）。
 *
 * 【为什么用签名而不是把密钥内置进来比对】
 * 这是整个设计里最关键的一条。如果为了省事，把一串"正确的密钥"或者
 * 一个用于比对的对称密钥写进应用，那么任何拿到安装包的人都能把它读出来
 * —— 编译进客户端的东西等同于公开，这是被反复验证过的事实。
 * 一旦那串值被读出来，别人就能自己签发任意多的有效密钥，整个机制当场失效。
 *
 * 所以这里采用的是签名：应用里只有"验证用的公钥"，签发能力（私钥）
 * 只存在于生成密钥的那一侧，从不进入这里。公钥被看到也无妨，
 * 它只能用来判断一个密钥是不是真的，不能用来造一个新的。
 *
 * 【验证顺序：先验证，再相信】
 * 在签名验证通过之前，密钥里的每一个字符都是不可信输入。
 * 因此解析与比对阶段一律不把内容写进日志 —— 日志里如果写入了别人
 * 控制的内容，对方就能伪造日志条目。只有验证通过之后，
 * 里面的标识才是可信的，那时才允许记录。
 *
 * 【算法选择】
 * 使用椭圆曲线 P-256 配 SHA-256。它在所有系统上都有原生实现，
 * 不需要额外引入库，也不必自己实现密码学运算 —— 自己实现容易出错，
 * 而密码学上的小错往往直接导致机制失效。
 * 不使用 MD5、SHA-1 这类已被认定不安全的摘要算法。
 */
public final class KcuGrant {

    /**
     * 内置公钥（X.509 编码后的文本形式）。
     *
     * 【为什么这个值可以公开】
     * 它只能验证，不能签发。看到它的人无法造出能通过校验的密钥。
     * 真正需要保护的是另一半，它不在这里，也不在任何公开的地方。
     *
     * 【为什么要保留多个】
     * 签发用的那一半将来总会需要更换（比如怀疑外泄）。
     * 如果应用只认一个，换的那一刻此前发出去的所有密钥会立刻全部失效。
     * 保留多个可以做到：换新的同时旧的继续有效一段时间，平滑过渡。
     */
    private static final String[] TRUSTED_KEYS = {
            // k1：初始公钥
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE0+4YsI6WXu3W1fZJliVXYiRQfWrW"
                    + "CR01BE9RshekTHprZtWrT3PuEsGxPIzx9BZo3aeWWzHlWoOEs9c8lGwjnQ==",
    };

    /** 载荷里的算法标记，决定用哪一个公钥；新增公钥时递增 */
    private static final String ALGO_PREFIX = "K1";
    private static final int KEY_INDEX_ZERO = 0;

    /** 吊销列表地址；文件名刻意不起眼 */
    private static final String REVOKED_URL =
            "https://raw.githubusercontent.com/kongbai9288/KCU-Launcher/main/"
                    + "KCU/src/main/assets/kcu_asset_manifest.dat";

    /**
     * 单个密钥允许的最大字符数。
     *
     * 密钥由人复制粘贴，正常长度远不到这个数。设上限是为了避免
     * 有人故意提交一个极长的串，让解析和比对过程白白消耗资源。
     */
    private static final int MAX_KEY_CHARS = 512;

    /** 吊销列表拉取上限，理由同上；清单本身极小，余量充足 */
    private static final int MAX_LIST_BYTES = 65536;

    private static final int TIMEOUT_MS = 8000;

    private static volatile List<String> revoked = new ArrayList<>();
    private static volatile boolean listLoaded = false;

    private KcuGrant() {
    }

    /**
     * 后台拉取已被收回的标识清单。
     *
     * 【为什么拿不到时不视为"全部收回"】
     * 拿不到意味着没有新信息。此时若按"全部收回"处理，
     * 一次网络波动就会把所有人的密钥全部作废，属于误伤。
     * 保持上一次的结果是最稳妥的：有新的收回信息才更新，否则维持原状。
     */
    public static void refresh() {
        String body = fetch(REVOKED_URL);
        if (body == null) {
            return;
        }
        List<String> parsed = parseList(body);
        if (parsed == null) {
            return;
        }
        revoked = parsed;
        listLoaded = true;
    }

    /** 清单是否取到过；取不到时按"没有收回任何一条"处理 */
    public static boolean isListLoaded() {
        return listLoaded;
    }

    /**
     * 校验一个密钥。
     *
     * 本方法只做本地运算，不发起网络请求，因此可以放在任何线程。
     *
     * @return 通过返回 true；其余一律 false，不区分具体原因
     */
    public static boolean verify(String input) {
        // 先做形状检查再谈校验：
        // 输入来自剪贴板或手动输入，先挡掉明显不合规的内容，
        // 后面的解析就不用面对各种畸形数据
        String text = normalize(input);
        if (text == null) {
            return false;
        }

        String[] parts = text.split("\\.");
        // 载荷四段 + 签名一段
        if (parts.length != 5) {
            return false;
        }
        String algo = parts[0];
        String id = parts[1];
        String nonce = parts[2];
        String expiry = parts[3];
        String signature = parts[4];

        if (!ALGO_PREFIX.equals(algo)) {
            return false;
        }
        if (id.isEmpty() || nonce.isEmpty() || signature.isEmpty()) {
            return false;
        }
        // 标识只允许常规字符：它会出现在日志和清单里，
        // 放行换行一类的字符会让别人有机会伪造记录内容
        if (!isPlain(id) || !isPlain(nonce)) {
            return false;
        }

        // 载荷必须与签名的对象完全一致，一个字符都不能差。
        // 这里用原样切分出来的载荷，而不是重新拼一个串 ——
        // 重新拼接哪怕只是顺序或空格不同，签名就对不上
        String payload = algo + "." + id + "." + nonce + "." + expiry;
        if (!signatureValid(payload, signature)) {
            // 验证未通过，此刻内容不可信，不写日志
            return false;
        }

        // 至此内容已可信，后面的判断才可以使用其中字段
        if (expired(expiry)) {
            return false;
        }
        if (isRevoked(id)) {
            return false;
        }
        return true;
    }

    /** 校验通过后取标识，供记录与展示；未通过返回 null */
    public static String idOf(String input) {
        String text = normalize(input);
        if (text == null) {
            return null;
        }
        String[] parts = text.split("\\.");
        if (parts.length != 5 || !ALGO_PREFIX.equals(parts[0])) {
            return null;
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2] + "." + parts[3];
        if (!signatureValid(payload, parts[4])) {
            return null;
        }
        return parts[1];
    }

    /**
     * 验证签名。
     *
     * 只使用内置公钥，全部运算在本地完成。
     */
    private static boolean signatureValid(String payload, String signature) {
        try {
            PublicKey key = trustedKey();
            if (key == null) {
                return false;
            }
            byte[] sig = Base64.decode(signature, Base64.URL_SAFE | Base64.NO_WRAP);
            if (sig.length == 0) {
                return false;
            }
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(key);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(sig);
        } catch (Throwable e) {
            // 拿不到算法实现、编码不合规、签名格式不对，一律按不通过处理
            return false;
        }
    }

    /** 取当前使用的公钥；取不到时返回 null，而不是退化成"放行" */
    private static PublicKey trustedKey() {
        try {
            if (KEY_INDEX_ZERO >= TRUSTED_KEYS.length) {
                return null;
            }
            byte[] der = Base64.decode(TRUSTED_KEYS[KEY_INDEX_ZERO], Base64.DEFAULT);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return KeyFactory.getInstance("EC").generatePublic(spec);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 判断标识是否已被收回。
     *
     * 【为什么用常量时间比较】
     * 逐字符提前退出会让比较耗时随匹配长度变化，
     * 理论上可据此逐位试探。这里改用等时比较，消除这个差异。
     */
    private static boolean isRevoked(String id) {
        for (String one : revoked) {
            if (one == null || one.isEmpty()) {
                continue;
            }
            if (constantTimeEquals(one, id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean constantTimeEquals(String a, String b) {
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(x, y);
    }

    /** 是否已过期；按秒比较，解析不出就不按过期处理 */
    private static boolean expired(String expiry) {
        try {
            long until = Long.parseLong(expiry);
            if (until <= 0) {
                return false;
            }
            return System.currentTimeMillis() / 1000L > until;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 规整输入：只去掉空白字符。
     *
     * 【为什么先规整再校验】
     * 密钥比较长，复制时常带上换行或被自动插入空格。
     * 直接校验会让明明正确的密钥因为格式而被判无效。
     *
     * 【为什么只去掉空白，而不是顺手把连字符之类的也去掉】
     * 密钥自身采用的编码里含有连字符与下划线，它们是内容的一部分。
     * 若把它们当成"分隔符"一并剔除，等于修改了要校验的内容本身，
     * 结果就是本该通过的密钥被判无效 —— 而且只在恰好含有这些字符时
     * 才会发生，排查起来非常费劲。凡是可能属于内容的字符一律不动，
     * 只清理明显不可能是内容的空白。
     */
    private static String normalize(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < input.length() && i < MAX_KEY_CHARS; i++) {
            char c = input.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                continue;
            }
            out.append(c);
        }
        String text = out.toString();
        if (text.isEmpty() || out.length() >= MAX_KEY_CHARS) {
            return null;
        }
        return text;
    }

    /** 只允许字母数字与少量符号，挡掉换行等可用于伪造记录的字符 */
    private static boolean isPlain(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static List<String> parseList(String body) {
        List<String> out = new ArrayList<>();
        try {
            String[] lines = body.split("\n");
            for (String line : lines) {
                String one = line.trim();
                if (one.isEmpty() || one.startsWith("#")) {
                    continue;
                }
                if (one.length() > 64) {
                    continue;
                }
                if (!isPlain(one)) {
                    continue;
                }
                out.add(one.toLowerCase(Locale.ROOT));
            }
            return out;
        } catch (Throwable e) {
            // 解析不出就返回 null，保持上一次的结果
            return null;
        }
    }

    private static String fetch(String address) {
        HttpURLConnection conn = null;
        InputStream in = null;
        try {
            conn = (HttpURLConnection) new URL(address).openConnection();
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("GET");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int total = 0;
            int n;
            // 用 != -1 判结束：个别流会返回 0，按 > 0 判会提前结束导致内容被截断
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_LIST_BYTES) {
                    return null;
                }
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Throwable ignored) {
                }
            }
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
