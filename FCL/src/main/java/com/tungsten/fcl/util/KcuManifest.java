package com.tungsten.fcl.util;

import android.content.Context;

import com.google.gson.GsonBuilder;
import com.tungsten.fcl.setting.Profile;
import com.tungsten.fcl.setting.Profiles;
import com.tungsten.fclauncher.utils.FCLPath;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公共目录清单。
 *
 * 供 KCU Migrator 等外部工具读取本启动器的实际目录布局。
 * 外部工具若自行猜测路径，一旦根选择不同（KCU / FCL）就会对不上，
 * 因此由启动器自己把布局写清楚。
 *
 * 纯辅助功能：任何异常都只记录不抛出，绝不阻断启动。
 */
public final class KcuManifest {

    private static final int SCHEMA_VERSION = 1;
    private static final String FILE_NAME = "manifest.json";

    private KcuManifest() {
    }

    public static File getManifestFile() {
        return new File(FCLPath.KCU_SHARED_ROOT, FILE_NAME);
    }

    public static void write(Context context) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("generatedAt", System.currentTimeMillis());
            root.put("packageName", context == null ? "" : context.getPackageName());
            root.put("root", FCLPath.KCU_SHARED_ROOT);
            root.put("paths", buildPaths());
            root.put("instances", buildInstances());

            File file = getManifestFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            // 不用 java.nio.file.Files.write：该 API 要求 API 26，而 minSdk 正好是 26，
            // 属边界值，改用传统流避免任何兼容性风险
            java.io.FileOutputStream out = new java.io.FileOutputStream(file, false);
            try {
                out.write(json.getBytes("UTF-8"));
                out.flush();
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {
            // 清单写入失败不影响启动，静默忽略
        }
    }

    private static Map<String, Object> buildPaths() {
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("minecraft", FCLPath.SHARED_COMMON_DIR);
        paths.put("privateCommon", FCLPath.PRIVATE_COMMON_DIR);
        paths.put("runtime", FCLPath.RUNTIME_DIR);
        paths.put("modRuntime", FCLPath.MOD_RUNTIME_DIR);
        paths.put("java", FCLPath.JAVA_PATH);
        paths.put("lwjgl", FCLPath.LWJGL_DIR);
        paths.put("log", FCLPath.LOG_DIR);
        paths.put("control", FCLPath.CONTROLLER_DIR);
        paths.put("share", FCLPath.SHARE_DIR);
        // 玩家自带 .so 的素材目录（可读写，供外部工具访问）；
        // 启动时校验后复制到私有 runtime/natives 才能 dlopen，公共目录不可直接加载
        paths.put("nativesExternal", FCLPath.KCU_SHARED_ROOT + "/natives");
        paths.put("nativeLibDir", FCLPath.NATIVE_LIB_DIR);
        return paths;
    }

    private static List<Map<String, Object>> buildInstances() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            for (Profile profile : Profiles.getProfiles()) {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("name", profile.getName());
                one.put("gameDir", profile.getGameDir().getAbsolutePath());
                one.put("selectedVersion", profile.getSelectedVersion());
                list.add(one);
            }
        } catch (Throwable ignored) {
            // 实例列表不可用时清单仍写入路径部分
        }
        return list;
    }
}
