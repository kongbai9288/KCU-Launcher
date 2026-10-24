package com.kongbai.kculauncher.util;

import android.util.JsonReader;
import android.util.JsonWriter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 导入版本的元数据。
 *
 * 存放于版本目录 kcu_meta.json，与版本 JSON 分离：
 * 探测到的真实版本、用户选定的运行时都记在这里，避免改动上游版本解析结构。
 */
public final class KcuVersionMeta {

    private static final String FILE = "kcu_meta.json";

    /** 尚未探测出真实版本时的展示文本 */
    public static final String UNKNOWN = "未知";

    private KcuVersionMeta() {
    }

    public static final class Meta {
        /** 是否已探测出真实版本 */
        public boolean known;
        /** 展示用版本文本 */
        public String display = UNKNOWN;
        /** 用户选定的运行时名（对应 JavaManager 的 name，如 jre17）；空表示未选 */
        public String java = "";
    }

    public static File file(File versionDir) {
        return new File(versionDir, FILE);
    }

    public static Meta read(File versionDir) {
        Meta meta = new Meta();
        File file = file(versionDir);
        if (!file.isFile()) {
            return meta;
        }
        JsonReader reader = null;
        try {
            reader = new JsonReader(new FileReader(file));
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("known".equals(key)) {
                    meta.known = reader.nextBoolean();
                } else if ("display".equals(key)) {
                    meta.display = reader.nextString();
                } else if ("java".equals(key)) {
                    meta.java = reader.nextString();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        } catch (Throwable ignored) {
            // 损坏或未写全时返回默认值，不阻断启动
        } finally {
            close(reader);
        }
        if (meta.display == null || meta.display.isEmpty()) {
            meta.display = UNKNOWN;
        }
        if (meta.java == null) {
            meta.java = "";
        }
        return meta;
    }

    public static void write(File versionDir, Meta meta) {
        JsonWriter writer = null;
        try {
            if (!versionDir.exists() && !versionDir.mkdirs()) {
                return;
            }
            writer = new JsonWriter(new FileWriter(file(versionDir), false));
            writer.setIndent("  ");
            writer.beginObject();
            writer.name("known").value(meta.known);
            writer.name("display").value(meta.display);
            writer.name("java").value(meta.java == null ? "" : meta.java);
            writer.endObject();
            writer.flush();
        } catch (Throwable ignored) {
        } finally {
            close(writer);
        }
    }

    /**
     * 从 jar 内 version.json 探测真实版本。
     * 官方客户端 jar 根目录带该文件；第三方整合包可能没有，此时返回 null。
     */
    public static String probeFromJar(File versionDir, String id) {
        if (versionDir == null || id == null || id.isEmpty()) {
            return null;
        }
        File jar = new File(versionDir, id + ".jar");
        if (!jar.isFile()) {
            return null;
        }
        ZipFile zip = null;
        try {
            zip = new ZipFile(jar);
            ZipEntry entry = zip.getEntry("version.json");
            if (entry == null) {
                return null;
            }
            // 条目大小可能返回 -1（ZIP 未记录真实大小），此时不能用于分配数组
            long declared = entry.getSize();
            InputStream in = null;
            try {
                in = zip.getInputStream(entry);
                if (declared > 0) {
                    byte[] buf = new byte[(int) Math.min(declared, 65536)];
                    int off = 0;
                    while (off < buf.length) {
                        int n = in.read(buf, off, buf.length - off);
                        if (n <= 0) {
                            break;
                        }
                        off += n;
                    }
                    return parseId(new String(buf, 0, off, "UTF-8"));
                }
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while (out.size() < 65536 && (n = in.read(chunk)) > 0) {
                    out.write(chunk, 0, n);
                }
                return parseId(new String(out.toByteArray(), "UTF-8"));
            } finally {
                close(in);
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String parseId(String json) {
        JsonReader reader = null;
        try {
            reader = new JsonReader(new java.io.StringReader(json));
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("id".equals(key)) {
                    String value = reader.nextString();
                    return value == null || value.isEmpty() ? null : value;
                }
                reader.skipValue();
            }
            reader.endObject();
        } catch (Throwable ignored) {
        } finally {
            close(reader);
        }
        return null;
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
            } else if (closeable instanceof InputStream) {
                ((InputStream) closeable).close();
            }
        } catch (Throwable ignored) {
        }
    }
}
