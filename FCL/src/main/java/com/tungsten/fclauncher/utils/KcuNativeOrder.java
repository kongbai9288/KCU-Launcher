package com.tungsten.fclauncher.utils;

import android.util.JsonReader;

import com.tungsten.fclauncher.bridge.FCLBridge;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 玩家自带原生库的加载顺序控制。
 *
 * 配置放在公共目录 natives/&lt;abi&gt;/ 下与 .so 同级，仅用于控制顺序，
 * 本身不参与加载：既不会被复制进运行时目录，也不会被 dlopen。
 *
 * 默认状态（无配置、配置为空、abi 不匹配）不产生任何 dlopen 调用，
 * 行为与上游逐字一致。
 */
public final class KcuNativeOrder {

    /** 与 .so 同级的顺序配置文件名 */
    private static final String FILE = "load_order.json";

    private KcuNativeOrder() {
    }

    private static final class Config {
        List<String> order = new ArrayList<>();
        String abi = "";
        boolean abortOnFailure = false;
    }

    /**
     * 按配置顺序加载已同步的原生库。
     * 返回是否全部成功；仅在配置声明 abort 且确有失败时才为 false。
     */
    public static boolean loadInOrder(FCLBridge bridge) {
        Config cfg = read();
        if (cfg == null || cfg.order.isEmpty()) {
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

    private static Config read() {
        File file = new File(KcuNativeSync.getExternalDir(), FILE);
        if (!file.isFile()) {
            return null;
        }
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
