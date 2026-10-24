package com.kongbai.kculauncher.util;

import android.util.JsonReader;
import android.util.JsonWriter;

import com.kongbai.kculauncher.launcher.utils.KCUPath;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.LinkedHashMap;
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
     * 判定实例归属。
     *
     * 已登记的直接返回记录值；未登记的不写入任何东西 —— 归属判定发生在启动流程中，
     * 此时擅自"顺手登记"，等于把来源不明的实例当成自己创建的，与保守原则冲突。
     */
    public static String query(String dir) {
        if (dir == null || dir.isEmpty()) {
            return OWNER_UNKNOWN;
        }
        Entry entry = readAll().get(dir);
        return entry == null ? OWNER_UNKNOWN : entry.owner;
    }

    /** 登记一个由本启动器创建的实例 */
    public static void register(String dir, String version) {
        if (dir == null || dir.isEmpty()) {
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
        if (version != null) {
            entry.version = version;
        }
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
