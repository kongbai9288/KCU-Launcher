package com.tungsten.fclauncher.utils;

import android.util.JsonReader;
import android.util.JsonWriter;

import com.tungsten.fclauncher.bridge.FCLBridge;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 玩家自带原生库的加载顺序控制。
 *
 * 配置按实例独立存放于 &lt;实例目录&gt;/natives/load_order.json，仅用于控制顺序，
 * 本身不参与加载：既不会被复制进运行时目录，也不会被 dlopen。
 *
 * 首次使用时自动生成一份模板，内容为当前运行时目录里已有的 .so（含原有库与玩家自带库），
 * 便于直接调整次序。模板的 enabled 为 false，因此生成后本次与后续启动仍走原行为，
 * 直到用户显式启用——避免"什么都不设置反而被改变"。
 */
public final class KcuNativeOrder {

    /** 实例目录下的配置子目录与文件名 */
    private static final String SUBDIR = "natives";
    private static final String FILE = "load_order.json";

    private KcuNativeOrder() {
    }

    private static final class Config {
        List<String> order = new ArrayList<>();
        String abi = "";
        boolean enabled = false;
        boolean abortOnFailure = false;
    }

    /**
     * 按配置顺序加载已同步的原生库。
     * 返回是否全部成功；仅在配置声明 abort 且确有失败时才为 false。
     */
    public static boolean loadInOrder(FCLBridge bridge, String instanceDir) {
        if (instanceDir == null || instanceDir.isEmpty()) {
            return true;
        }
        File dir = new File(instanceDir, SUBDIR);
        File file = new File(dir, FILE);

        // 首次进入该实例：生成一份仅含既有 .so 的模板，本次不生效
        if (!file.isFile()) {
            writeTemplate(file);
            return true;
        }

        Config cfg = read(file);
        if (cfg == null || !cfg.enabled || cfg.order.isEmpty()) {
            return true;
        }
        String runtime = FCLPath.MOD_RUNTIME_DIR;
        if (runtime == null || bridge == null) {
            return true;
        }
        boolean allOk = true;
        for (String name : cfg.order) {
            if (!isSafeName(name)) {
                continue;
            }
            File so = new File(runtime, name);
            // 只加载同步进来的文件：未同步说明源目录已无该文件，或校验未通过
            if (!so.isFile()) {
                continue;
            }
            try {
                bridge.dlopen(so.getAbsolutePath());
            } catch (Throwable ignored) {
                allOk = false;
                if (cfg.abortOnFailure) {
                    return false;
                }
            }
        }
        return allOk;
    }

    /**
     * 文件名必须是不含路径分隔的 .so。
     * 阻止路径穿越与误把配置文件等非库文件塞进加载流程。
     */
    private static boolean isSafeName(String name) {
        if (name == null) {
            return false;
        }
        if (!name.endsWith(".so")) {
            return false;
        }
        if (name.length() <= 3 || name.startsWith(".")) {
            return false;
        }
        return name.indexOf('/') < 0 && name.indexOf('\\') < 0;
    }

    /** 生成默认模板：列出运行时目录当前已有的 .so，enabled 为 false */
    private static void writeTemplate(File file) {
        JsonWriter writer = null;
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                return;
            }
            List<String> names = listRuntimeLibs();
            writer = new JsonWriter(new FileWriter(file, false));
            writer.setIndent("  ");
            writer.beginObject();
            writer.name("enabled").value(false);
            writer.name("abi")
                    .value(Architecture.archAsStringAndroid(Architecture.getDeviceArchitecture()));
            writer.name("onFailure").value("skip");
            writer.name("order");
            writer.beginArray();
            for (String n : names) {
                writer.value(n);
            }
            writer.endArray();
            writer.endObject();
            writer.flush();
        } catch (Throwable ignored) {
            // 模板写不出来不影响启动，等同于未配置
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 运行时目录当前已有的 .so（原有库与玩家自带库都在其中），按名排序 */
    private static List<String> listRuntimeLibs() {
        String runtime = FCLPath.MOD_RUNTIME_DIR;
        if (runtime == null) {
            return Collections.emptyList();
        }
        File[] files = new File(runtime).listFiles();
        if (files == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        for (File f : files) {
            if (f.isFile() && isSafeName(f.getName())) {
                names.add(f.getName());
            }
        }
        Collections.sort(names);
        return names;
    }

    private static Config read(File file) {
        JsonReader reader = null;
        try {
            Config cfg = new Config();
            reader = new JsonReader(new FileReader(file));
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("order".equals(key)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        cfg.order.add(reader.nextString());
                    }
                    reader.endArray();
                } else if ("abi".equals(key)) {
                    cfg.abi = reader.nextString();
                } else if ("enabled".equals(key)) {
                    cfg.enabled = reader.nextBoolean();
                } else if ("onFailure".equals(key)) {
                    cfg.abortOnFailure = "abort".equals(reader.nextString());
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            String current = Architecture.archAsStringAndroid(Architecture.getDeviceArchitecture());
            // abi 声明了但与当前设备不符：整份配置不生效，避免跨架构加载直接崩溃
            if (!cfg.abi.isEmpty() && !cfg.abi.equals(current)) {
                return null;
            }
            return cfg;
        } catch (Throwable ignored) {
            // 配置损坏等同于未配置，不阻断启动
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
}
