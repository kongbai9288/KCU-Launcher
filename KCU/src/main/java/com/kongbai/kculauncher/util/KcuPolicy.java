package com.kongbai.kculauncher.util;

import android.content.Context;
import android.util.JsonReader;

import com.kongbai.kculauncher.KCUApp;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.launcher.utils.KCUPath;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
/**
 * 远程策略：决定某个功能在当前地区是否可用。
 *
 * 【为什么要有这个】
 * 有些功能的可用性会随外部要求变化，而这种变化无法提前预知。
 * 若把规则写死在安装包里，一旦需要调整就只能重新发版，
 * 而旧版本的安装包会一直按老规则运行。把规则放在可以随时更新的远端，
 * 需要调整时改远端即可，已发出的安装包也会跟着变。
 *
 * 【拉取流程：先看有没有新内容，没有就关闭请求】
 * 1. 拉更新时间索引（很小）
 * 2. 与本地缓存里记录的时间比对
 * 3. 远端更新了才去拉规则正文，否则到此为止，不再发起请求
 * 这样绝大多数启动只会产生一个极小请求，规则没有变动时不会重复下载正文。
 *
 * 【拉不到远端时怎么办】
 * 用本地缓存；本地也没有才用内置默认。
 * 拉取失败绝不视为"禁止"，也绝不视为"全部允许"后清空已有缓存 ——
 * 拿不到新信息时保持现有判断，是最不容易出意外的做法。
 */
public final class KcuPolicy {

    /** 版本导入与下载 */
    public static final String FEATURE_DOWNLOAD = "download";
    /** 统一登录 */
    public static final String FEATURE_LOGIN = "login";
    /** 存档检测与提示 */
    public static final String FEATURE_WORLD_SCAN = "world_scan";
    /** 布局与按键自定义 */
    public static final String FEATURE_CONTROLLER = "controller";

    /**
     * 更新时间索引与规则正文分放在两个不同目录。
     * 索引很小，每次都可以拉；正文只在索引显示有更新时才拉。
     */
    private static final String BASE =
            "https://raw.githubusercontent.com/kongbai9288/KCU-Policy/main/";
    private static final String INDEX_URL = BASE + "index/index.json";
    private static final String RULES_URL = BASE + "policy/rules.json";

    private static final String CACHE_NAME = "kcu_policy.json";
    private static final int TIMEOUT_MS = 10000;

    /**
     * 内置默认规则：仅在远端和本地缓存都拿不到时使用。
     *
     * 默认只限制下载功能在中国大陆地区，其余功能默认不限制 ——
     * 限制是例外而非常态，正常情况下这些限制都不该出现。
     */
    private static final String FALLBACK =
            "{\"updatedAt\":0,\"rules\":[{\"version\":\"*\",\"client\":\"*\","
                    + "\"features\":[{\"feature\":\"download\",\"regions\":[\"CN\"],"
                    + "\"unknown\":\"allow\"}]}]}";

    private static volatile List<Rule> rules = new ArrayList<>();
    private static volatile boolean prepared = false;

    /**
     * 已经探测到的地区；未探测到时为 null。
     *
     * 【为什么要把地区单独存一份，而不是判定时再去取】
     * 地区判定是网络操作。若判定时才去取，那么调用方在界面线程上判断时，
     * 一旦此前没探测成功，就会在界面线程上发起网络请求 —— 这在多数系统上
     * 会直接失败，也可能拖住界面。判定必须只使用已经准备好的值，
     * 探测这件事只允许在后台准备阶段发生。
     */
    private static volatile String region = null;

    /**
     * 单次拉取允许的最大字节数。
     *
     * 【为什么要设上限】
     * 拉取发生在启动路径上，内容会被整段读进内存再解析。
     * 远端若返回异常大的内容（配置写错、代理返回了别的页面、链路被插了东西），
     * 不限量的话会整段读进来，轻则让启动卡住，重则把内存吃满。
     * 规则文件本身很小，这个上限留了极大余量，正常内容远达不到。
     */
    private static final int MAX_FETCH_BYTES = 262144;

    private KcuPolicy() {
    }

    /** 一条功能限制 */
    private static final class Block {
        String feature = "";
        List<String> regions = new ArrayList<>();
        /** 地区判定不出来时的取向；缺省按 allow */
        boolean blockOnUnknown = false;
        String title = "";
        String body = "";
    }

    /** 强制更新：低于指定版本就要求更新，不更新则不可用 */
    public static final class ForceUpdate {
        /** 最低可用版本；低于此版本被挡住 */
        public String minVersion = "";
        public String title = "";
        public String body = "";
        /** 更新页地址；留空时只提示不给跳转 */
        public String url = "";
    }

    /** 强制停止：整个应用不可用 */
    public static final class Shutdown {
        public String title = "";
        public String body = "";
        /** 是否提供导出数据的入口 */
        public boolean allowExport = true;
    }

    /**
     * 一组规则，只在版本与客户端都匹配时生效。
     *
     * 【为什么按组而不是每条单独写版本】
     * 同一件事往往同时涉及多个功能限制、更新要求和停止要求，
     * 分组写一次版本与客户端即可，不必每条重复，也不容易写漏。
     */
    private static final class Rule {
        String version = "";
        String client = "";
        List<Block> blocks = new ArrayList<>();
        ForceUpdate forceUpdate = null;
        Shutdown shutdown = null;
    }

    /**
     * 准备：拉取远端规则并判定地区。
     *
     * 这是网络操作，必须在后台线程调用。
     * 未调用过本方法时，isBlocked 按"地区未知 + 内置默认"判断，
     * 因此不会因为没有准备好而误伤任何功能。
     */
    public static void prepare() {
        try {
            // 先取镜像清单，后续拉取才能走镜像：
            // 清单取不到不影响后续，会自动退回直连
            KcuMirror.load();
        } catch (Throwable ignored) {
        }
        try {
            syncRules();
        } catch (Throwable ignored) {
        }
        String found = null;
        try {
            found = KcuRegion.detect();
        } catch (Throwable ignored) {
        }
        if (found != null && !found.isEmpty()) {
            region = found;
            // 只有真正拿到地区才算准备好：
            // 拿不到地区时规则无法生效，此时若标记为已准备，
            // 后续就不会再重试，限制功能会在整个进程内一直失效
            prepared = true;
        }
    }

    /** 规则是否已就绪 */
    public static boolean isPrepared() {
        return prepared;
    }

    /**
     * 该功能当前是否不可用。
     *
     * 判定顺序：先找匹配功能的规则，再看地区。
     * 规则里地区列表为空表示所有地区都限制；非空则只有列出的地区受限。
     */
    public static boolean isBlocked(String feature) {
        if (feature == null || feature.isEmpty()) {
            return false;
        }
        if (exempt()) {
            return false;
        }
        // 只使用准备阶段探测到的地区，这里绝不发起网络请求
        String here = region;
        boolean unknown = here == null || here.isEmpty();
        for (Rule rule : applicable()) {
            for (Block block : rule.blocks) {
                if (!feature.equals(block.feature)) {
                    continue;
                }
                // 地区列表为空 = 全部地区
                if (block.regions.isEmpty()) {
                    return true;
                }
                if (unknown) {
                    // 判定不出地区时按规则自身的取向处理，默认不限制：
                    // 猜错的代价（正常用户用不了）比放过的大
                    return block.blockOnUnknown;
                }
                for (String one : block.regions) {
                    if (one != null && one.trim().equalsIgnoreCase(here)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 当前适用的规则组。
     *
     * 【为什么匹配不上时当作不适用】
     * 规则写错、版本取不到、客户端识别不了，这些情况如果当成"适用"，
     * 就会把停止和更新这类强措施套到所有人头上，后果不可收拾。
     * 当成不适用最多是这条规则没生效，是安全的那一侧。
     */
    private static List<Rule> applicable() {
        List<Rule> out = new ArrayList<>();
        String version = KcuTarget.currentVersion();
        String client = KcuTarget.currentClient();
        for (Rule rule : rules) {
            if (!KcuTarget.versionMatches(rule.version, version)) {
                continue;
            }
            if (!KcuTarget.clientMatches(rule.client)) {
                continue;
            }
            out.add(rule);
        }
        return out;
    }

    /**
     * 已经验明身份的人不受任何限制。
     *
     * 【为什么要有这个豁免】
     * 远程配置的目的是在情况变化时能及时止损，
     * 但它同时也会挡住需要排查问题的人 ——
     * 一旦下发停止，连开发者自己都进不去，问题也就无从查起。
     * 因此已验明身份的人一律放行，保证任何时候都还有人能进来处理。
     *
     * 【为什么放在这里统一判断，而不是每个调用点各判一次】
     * 分散判断迟早会漏掉某一处。功能限制、强制更新、强制停止
     * 三类判断都经过这里，只要这一处不漏，就不会出现
     * "开发者也被挡住"的情况。
     */
    private static boolean exempt() {
        try {
            return KcuDevMode.isEnabled();
        } catch (Throwable e) {
            // 判断本身出错时按不豁免处理：
            // 豁免是例外，出错时保持常规判断更安全
            return false;
        }
    }

    /**
     * 当前是否要求强制更新。
     *
     * 【为什么版本取不到时不要求更新】
     * 版本取不到意味着无法判断该不该更新，此时要求更新会把所有
     * 拿不到版本信息的人一视同仁地挡住，属于误伤。
     * 版本为空时直接不生效，规则里的强措施只在能确认时才执行。
     */
    public static ForceUpdate forceUpdate() {
        if (exempt()) {
            return null;
        }
        String version = KcuTarget.currentVersion();
        if (version.isEmpty()) {
            return null;
        }
        for (Rule rule : applicable()) {
            ForceUpdate force = rule.forceUpdate;
            if (force == null || force.minVersion.isEmpty()) {
                continue;
            }
            // 当前版本低于最低可用版本才要求更新
            if (KcuTarget.compareVersions(version, force.minVersion) < 0) {
                return force;
            }
        }
        return null;
    }

    /** 当前是否要求强制停止 */
    public static Shutdown shutdown() {
        if (exempt()) {
            return null;
        }
        for (Rule rule : applicable()) {
            if (rule.shutdown != null) {
                return rule.shutdown;
            }
        }
        return null;
    }

    /** 被限制时显示的标题；规则没写则回退到内置文案 */
    public static String titleFor(android.content.Context context, String feature) {
        Block hit = find(feature);
        if (hit != null && !hit.title.isEmpty()) {
            return hit.title;
        }
        return context.getString(R.string.kcu_policy_blocked_title);
    }

    /** 被限制时显示的说明；规则没写则回退到内置文案 */
    public static String bodyFor(android.content.Context context, String feature) {
        Block hit = find(feature);
        if (hit != null && !hit.body.isEmpty()) {
            return hit.body;
        }
        return context.getString(R.string.kcu_policy_blocked_body);
    }

    private static Block find(String feature) {
        if (feature == null) {
            return null;
        }
        for (Rule rule : applicable()) {
            for (Block block : rule.blocks) {
                if (feature.equals(block.feature)) {
                    return block;
                }
            }
        }
        return null;
    }

    /** 首次使用或缓存为空时载入：优先本地缓存，其次内置默认 */
    public static void loadCached() {
        if (!rules.isEmpty()) {
            return;
        }
        File file = getCacheFile();
        if (file != null && file.isFile()) {
            List<Rule> parsed = parse(readFile(file));
            if (parsed != null) {
                rules = parsed;
                return;
            }
        }
        List<Rule> parsed = parse(FALLBACK);
        rules = parsed != null ? parsed : new ArrayList<>();
    }

    private static void syncRules() {
        loadCached();
        String index = fetch(INDEX_URL);
        if (index == null) {
            return;
        }
        long remote = readUpdatedAt(index);
        if (remote <= 0) {
            return;
        }
        long local = readUpdatedAt(readFile(getCacheFile()));
        // 远端没有比本地更新就到此为止，不再请求规则正文。
        // 这一步是"没有新内容就关闭请求"的关键：绝大多数启动只产生一个索引请求
        if (local > 0 && remote <= local) {
            return;
        }
        String body = fetch(RULES_URL);
        if (body == null || body.trim().isEmpty()) {
            return;
        }
        List<Rule> parsed = parse(body);
        if (parsed == null) {
            return;
        }
        // 先落盘成功再替换内存：否则进程内认为已生效、磁盘上却没有，
        // 下次启动又会重新拉一遍
        if (writeCache(body)) {
            rules = parsed;
            // 同步一份到公共目录供查看；这份只写不读，不参与任何判断
            writeSharedCopy(body);
        }
    }

    /**
     * 真正生效的规则文件：位于私有目录。
     *
     * 【为什么生效的内容必须在私有目录】
     * 公共目录任何应用都能读写，放在那里等于把开关交给了别人：
     * 别的程序改一下文件就能让限制失效，玩家也会误以为改那个文件有用。
     * 真正生效的内容只认私有目录这一份，公共目录不参与任何判断。
     */
    private static File getCacheFile() {
        try {
            Context context = KCUApp.getAppContext();
            if (context == null) {
                return null;
            }
            return new File(context.getFilesDir(), CACHE_NAME);
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 公共目录下的展示副本。
     *
     * 【为什么还要写一份到公共目录】
     * 只是为了让人能直接看到当前实际生效的规则内容，方便核对与排查。
     * 这份只写不读：本软件的任何判断都不会去看它，
     * 改它也不会影响任何行为，避免被当成可改配置的口子。
     */
    private static void writeSharedCopy(String content) {
        try {
            File shared = new File(KCUPath.KCU_SHARED_ROOT, CACHE_NAME);
            File parent = shared.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                return;
            }
            File tmp = new File(parent, CACHE_NAME + ".shared.tmp");
            OutputStream out = new FileOutputStream(tmp, false);
            try {
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } finally {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
            if (!tmp.renameTo(shared)) {
                tmp.delete();
            }
        } catch (Throwable ignored) {
            // 展示副本写不出去不影响功能，静默即可
        }
    }

    private static String readFile(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
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
        }
    }

    /**
     * 先写临时文件再改名。
     *
     * 规则文件决定功能是否可用，写坏一半的后果是解析失败，
     * 判断结果会整个退回默认，与该次更新的意图可能完全相反。
     */
    private static boolean writeCache(String content) {
        File target = getCacheFile();
        if (target == null) {
            return false;
        }
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        File tmp = new File(parent, CACHE_NAME + ".tmp");
        try {
            OutputStream out = new FileOutputStream(tmp, false);
            try {
                out.write(content.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } finally {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
            if (!tmp.renameTo(target)) {
                tmp.delete();
                return false;
            }
            return true;
        } catch (Throwable e) {
            // 写坏的内容已经没用了，留在公共目录里只会让人以为是正常文件
            tmp.delete();
            return false;
        }
    }

    /**
     * 取远端内容，优先走镜像。
     *
     * 【为什么要优先走镜像】
     * 直连境外代码托管在国内经常超时。规则拉取发生在启动路径上，
     * 卡在一个连不上的地址会把启动拖住。镜像通常更快也更稳。
     *
     * 【为什么原始地址仍要保留】
     * 所有镜像都不通时，直连还有机会成功。因此镜像只是"先试试"的选项，
     * 不是唯一途径 —— 展开候选地址时原始地址始终排在最后兜底。
     */
    private static String fetch(String address) {
        return KcuMirror.fetch(address);
    }

    private static long readUpdatedAt(String json) {
        if (json == null || json.trim().isEmpty()) {
            return 0;
        }
        JsonReader reader = null;
        try {
            reader = new JsonReader(new java.io.StringReader(json));
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("updatedAt".equals(key)) {
                    return reader.nextLong();
                }
                reader.skipValue();
            }
            reader.endObject();
        } catch (Throwable e) {
            return 0;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return 0;
    }

    /**
     * 解析规则文件。
     *
     * 支持两种写法：
     * 顶层 rules 数组（每条带 version 与 client），
     * 以及早期写法顶层 blocks 数组（不带限定，对所有版本与客户端生效）。
     * 保留旧写法是为了让已经存在的缓存文件仍可被读懂，不至于整个失效。
     */
    private static List<Rule> parse(String json) {
        if (json == null || json.trim().isEmpty()) {
            return null;
        }
        List<Rule> list = new ArrayList<>();
        JsonReader reader = null;
        try {
            reader = new JsonReader(new java.io.StringReader(json));
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("rules".equals(key)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        Rule rule = readRule(reader);
                        if (rule != null) {
                            list.add(rule);
                        }
                    }
                    reader.endArray();
                } else if ("blocks".equals(key)) {
                    // 早期写法：装进一条不限定的规则里
                    Rule legacy = new Rule();
                    reader.beginArray();
                    while (reader.hasNext()) {
                        Block block = readBlock(reader);
                        if (block != null) {
                            legacy.blocks.add(block);
                        }
                    }
                    reader.endArray();
                    if (!legacy.blocks.isEmpty()) {
                        list.add(legacy);
                    }
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return list;
        } catch (Throwable e) {
            // 解析失败返回 null 而不是已经读到的那部分：
            // 部分规则会让判断结果不完整，而"缺一条限制"和"多一条限制"
            // 相比，前者更安全，且下次启动会重新拉取
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static Rule readRule(JsonReader reader) throws Exception {
        Rule rule = new Rule();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if ("version".equals(key)) {
                rule.version = reader.nextString();
            } else if ("client".equals(key)) {
                rule.client = reader.nextString();
            } else if ("forceUpdate".equals(key)) {
                rule.forceUpdate = readForceUpdate(reader);
            } else if ("shutdown".equals(key)) {
                rule.shutdown = readShutdown(reader);
            } else if ("features".equals(key) || "blocks".equals(key)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    Block block = readBlock(reader);
                    if (block != null) {
                        rule.blocks.add(block);
                    }
                }
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        // 一条规则里什么都没写就没有存在意义
        if (rule.blocks.isEmpty() && rule.forceUpdate == null && rule.shutdown == null) {
            return null;
        }
        return rule;
    }

    private static ForceUpdate readForceUpdate(JsonReader reader) throws Exception {
        ForceUpdate force = new ForceUpdate();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if ("minVersion".equals(key) || "min".equals(key)) {
                force.minVersion = reader.nextString();
            } else if ("title".equals(key)) {
                force.title = reader.nextString();
            } else if ("body".equals(key)) {
                force.body = reader.nextString();
            } else if ("url".equals(key)) {
                force.url = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return force;
    }

    private static Shutdown readShutdown(JsonReader reader) throws Exception {
        Shutdown shutdown = new Shutdown();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if ("title".equals(key)) {
                shutdown.title = reader.nextString();
            } else if ("body".equals(key)) {
                shutdown.body = reader.nextString();
            } else if ("export".equals(key)) {
                shutdown.allowExport = reader.nextBoolean();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return shutdown;
    }

    private static Block readBlock(JsonReader reader) throws Exception {
        Block block = new Block();
        reader.beginObject();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if ("feature".equals(key)) {
                block.feature = reader.nextString();
            } else if ("regions".equals(key)) {
                reader.beginArray();
                while (reader.hasNext()) {
                    String one = reader.nextString();
                    if (one != null && !one.trim().isEmpty()) {
                        block.regions.add(one.trim().toUpperCase(Locale.ROOT));
                    }
                }
                reader.endArray();
            } else if ("unknown".equals(key)) {
                block.blockOnUnknown = "block".equalsIgnoreCase(reader.nextString());
            } else if ("title".equals(key)) {
                block.title = reader.nextString();
            } else if ("body".equals(key)) {
                block.body = reader.nextString();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        if (block.feature.isEmpty()) {
            return null;
        }
        return block;
    }

}
