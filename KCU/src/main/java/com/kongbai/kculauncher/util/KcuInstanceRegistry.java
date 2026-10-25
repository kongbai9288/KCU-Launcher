package com.kongbai.kculauncher.util;

import android.util.JsonReader;
import android.util.JsonWriter;

import com.kongbai.kculauncher.launcher.utils.KCUPath;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 实例注册表。
 *
 * 位于公共目录下（与 manifest.json 同级），记录每个实例目录是否由本启动器创建。
 *
 * 这一区分决定了后续逻辑的激进度：
 * 由本启动器创建的实例可以放心按本启动器的约定处理；
 * 来源不明的实例（例如玩家用文件管理器直接拷进来的存档）必须走保守路径，
 * 先确认再动手 —— 否则可能连玩家原有的数据一起改掉。
 *
 * 纯辅助功能：任何异常都只记录不抛出，绝不阻断启动。
 */
public final class KcuInstanceRegistry {

    private static final String FILE_NAME = "registry.json";
    private static final int SCHEMA_VERSION = 1;

    /**
     * 实例目录下的创建标记。
     *
     * 注册表是缓存，可能被清理或丢失；标记随实例目录本身走。
     * 启动时先查注册表，未登记再查标记文件 —— 一次 stat，开销极低，
     * 比每次启动都扫描存档目录便宜得多。
     */
    private static final String MARKER_FILE = ".kcu_instance";

    /** 该实例由本启动器创建 */
    public static final String OWNER_KCU = "kcu";
    /** 该实例首次出现时不在注册表中，来源不明 */
    public static final String OWNER_UNKNOWN = "unknown";
    /** 从上游迁移而来：目录结构符合上游布局，但未经本启动器创建 */
    public static final String OWNER_LEGACY = "legacy";

    private KcuInstanceRegistry() {
    }

    public static File getFile() {
        return new File(KCUPath.KCU_SHARED_ROOT, FILE_NAME);
    }

    public static final class Entry {
        public String owner = OWNER_UNKNOWN;
        public long createdAt;
        public long lastSeen;
        /** 首次登记时的版本标识，可为空 */
        public String version = "";
        /**
         * 最近一次确认过存档状态时的存档指纹。
         *
         * 只认"实例是本启动器建的"还不够：玩家完全可能把别处的存档拷进
         * 本启动器建的实例里。指纹用于区分"存档还是上次那批"，
         * 变了就重新扫，避免为了省一次扫描而永远漏掉后来拷进来的存档。
         */
        public long savesFingerprint = -1;
    }

    /** 读取全部条目；失败返回空表 */
    public static Map<String, Entry> readAll() {
        Map<String, Entry> map = new LinkedHashMap<>();
        File file = getFile();
        if (!file.isFile()) {
            return map;
        }
        JsonReader reader = null;
        try {
            reader = new JsonReader(new FileReader(file));
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("instances".equals(key)) {
                    reader.beginObject();
                    while (reader.hasNext()) {
                        String dir = reader.nextName();
                        Entry entry = new Entry();
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String field = reader.nextName();
                            if ("owner".equals(field)) {
                                entry.owner = reader.nextString();
                            } else if ("createdAt".equals(field)) {
                                entry.createdAt = reader.nextLong();
                            } else if ("lastSeen".equals(field)) {
                                entry.lastSeen = reader.nextLong();
                            } else if ("version".equals(field)) {
                                entry.version = reader.nextString();
                            } else if ("savesFingerprint".equals(field)) {
                                entry.savesFingerprint = reader.nextLong();
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                        map.put(dir, entry);
                    }
                    reader.endObject();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        } catch (Throwable ignored) {
        } finally {
            close(reader);
        }
        return map;
    }

    /**
     * 判定实例归属，未登记时按创建标记补登记并写回注册表。
     *
     * 这是启动路径上的性能设计：由本启动器创建的实例只需查一次注册表即可跳过
     * 存档扫描；只有来源不明的实例才会走耗时的目录遍历。
     * 补登记的依据是实例目录内由本启动器写入的标记文件，不是猜测。
     */
    public static String query(String dir) {
        if (dir == null || dir.isEmpty()) {
            return OWNER_UNKNOWN;
        }
        Map<String, Entry> map = readAll();
        Entry entry = map.get(dir);
        if (entry != null) {
            return entry.owner;
        }
        if (!new File(dir, MARKER_FILE).isFile()) {
            return OWNER_UNKNOWN;
        }
        Entry fresh = new Entry();
        fresh.owner = OWNER_KCU;
        fresh.createdAt = System.currentTimeMillis();
        fresh.lastSeen = System.currentTimeMillis();
        map.put(dir, fresh);
        write(map);
        return OWNER_KCU;
    }

    /**
     * 标记一个由本启动器创建的实例：写实例目录内的标记文件并登记到注册表。
     *
     * 标记文件先写：注册表只是缓存，标记才是随实例走的依据。
     * 标记写失败会连带跳过登记，避免注册表里出现没有依据的记录。
     */
    public static void markCreated(String dir, String version) {
        if (dir == null || dir.isEmpty()) {
            return;
        }
        File instance = new File(dir);
        try {
            if (!instance.isDirectory()) {
                instance.mkdirs();
            }
            File marker = new File(instance, MARKER_FILE);
            if (!marker.isFile()) {
                marker.createNewFile();
            }
        } catch (Throwable ignored) {
            // 标记写不进去（多为权限问题）时不登记，下次启动会重新尝试
            return;
        }
        Map<String, Entry> map = readAll();
        Entry entry = map.get(dir);
        if (entry == null) {
            entry = new Entry();
            entry.owner = OWNER_KCU;
            entry.createdAt = System.currentTimeMillis();
        }
        entry.lastSeen = System.currentTimeMillis();
        // 新建时实例内还没有存档，指纹记为"空"。之后玩家拷入存档会改变指纹，
        // 从而重新触发扫描 —— 这正是只认实例归属会漏掉的场景
        entry.savesFingerprint = computeSavesFingerprint(dir);
        if (version != null) {
            entry.version = version;
        }
        map.put(dir, entry);
        write(map);
    }

    /**
     * 存档指纹：由存档目录下的世界目录名集合推导。
     *
     * 不读 level.dat —— 那正是要省下的开销。只做一次目录列举。
     *
     * 【为什么只用目录名，不掺 lastModified】
     * 掺入修改时间会让指纹在"玩过一局之后"就变化，于是每次启动都要重新扫描，
     * 跳过扫描这个优化形同虚设。目录名只在"世界被新建或被拷入"时才变化，
     * 这正是需要重新扫描的时机，其余情况保持稳定。
     *
     * 【已知代价】
     * 用同名世界目录整体覆盖（目录名不变）不会改变指纹，因而不会重新扫描。
     * 这是为换取稳定跳过而接受的取舍，需在文档中写明而非假装不存在。
     *
     * 目录名排序后再计算：listFiles 的返回顺序不保证稳定，
     * 不排序会让"内容没变"被判成"变了"，同样导致频繁重扫。
     *
     * @return 无存档目录时返回 0，实例目录不可用返回 -1
     */
    public static long computeSavesFingerprint(String dir) {
        if (dir == null || dir.isEmpty()) {
            return -1;
        }
        File saves = new File(dir, "saves");
        File[] children = saves.listFiles();
        if (children == null) {
            return 0;
        }
        List<String> names = new ArrayList<>();
        for (File child : children) {
            if (child.isDirectory()) {
                names.add(child.getName());
            }
        }
        if (names.isEmpty()) {
            return 0;
        }
        java.util.Collections.sort(names);
        long fingerprint = 0;
        for (String name : names) {
            fingerprint = fingerprint * 31 + name.hashCode();
        }
        return fingerprint;
    }

    /**
     * 该实例的存档状态是否已确认过、可以跳过扫描。
     *
     * 两个条件缺一不可：实例确由本启动器创建，且存档指纹与上次确认时一致。
     */
    public static boolean isSavesKnown(String dir) {
        if (dir == null || dir.isEmpty()) {
            return false;
        }
        Map<String, Entry> map = readAll();
        Entry entry = map.get(dir);
        if (entry == null || !OWNER_KCU.equals(entry.owner)) {
            return false;
        }
        return entry.savesFingerprint == computeSavesFingerprint(dir);
    }

    /**
     * 记下当前存档指纹，表示"这批存档已经确认过"。
     *
     * 未登记的实例不记录：没有归属依据时建立信任，等于把来源不明的存档当成确认过的。
     */
    public static void rememberSaves(String dir) {
        if (dir == null || dir.isEmpty()) {
            return;
        }
        Map<String, Entry> map = readAll();
        Entry entry = map.get(dir);
        if (entry == null || !OWNER_KCU.equals(entry.owner)) {
            return;
        }
        entry.savesFingerprint = computeSavesFingerprint(dir);
        entry.lastSeen = System.currentTimeMillis();
        map.put(dir, entry);
        write(map);
    }

    /** 标记为来源不明；用于"已确认来源不明，不要再重复提示"的场合 */
    public static void markUnknown(String dir) {
        if (dir == null || dir.isEmpty()) {
            return;
        }
        Map<String, Entry> map = readAll();
        Entry entry = map.get(dir);
        if (entry != null && OWNER_KCU.equals(entry.owner)) {
            // 已确认是本启动器创建的，不能被降级
            return;
        }
        Entry fresh = new Entry();
        fresh.owner = OWNER_UNKNOWN;
        fresh.createdAt = entry != null ? entry.createdAt : System.currentTimeMillis();
        fresh.lastSeen = System.currentTimeMillis();
        map.put(dir, fresh);
        write(map);
    }

    private static void write(Map<String, Entry> map) {
        JsonWriter writer = null;
        try {
            File file = getFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            writer = new JsonWriter(new FileWriter(file, false));
            writer.setIndent("  ");
            writer.beginObject();
            writer.name("schemaVersion").value(SCHEMA_VERSION);
            writer.name("updatedAt").value(System.currentTimeMillis());
            writer.name("instances");
            writer.beginObject();
            for (Map.Entry<String, Entry> one : map.entrySet()) {
                writer.name(one.getKey());
                writer.beginObject();
                writer.name("owner").value(one.getValue().owner);
                writer.name("createdAt").value(one.getValue().createdAt);
                writer.name("lastSeen").value(one.getValue().lastSeen);
                writer.name("version").value(one.getValue().version == null
                        ? "" : one.getValue().version);
                writer.name("savesFingerprint").value(one.getValue().savesFingerprint);
                writer.endObject();
            }
            writer.endObject();
            writer.endObject();
            writer.flush();
        } catch (Throwable ignored) {
        } finally {
            close(writer);
        }
    }

    private static void close(Object closeable) {
        if (closeable == null) {
            return;
        }
        try {
            if (closeable instanceof JsonReader) {
                ((JsonReader) closeable).close();
            } else if (closeable instanceof JsonWriter) {
                ((JsonWriter) closeable).close();
            }
        } catch (Throwable ignored) {
        }
    }
}
