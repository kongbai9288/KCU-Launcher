package com.kongbai.kculauncher.util;

import android.content.Context;

import com.google.gson.GsonBuilder;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.setting.Profiles;
import com.kongbai.kculauncher.launcher.utils.KCUPath;

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
        return new File(KCUPath.KCU_SHARED_ROOT, FILE_NAME);
    }

    public static void write(Context context) {
        try {
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("schemaVersion", SCHEMA_VERSION);
            root.put("generatedAt", System.currentTimeMillis());
            root.put("packageName", context == null ? "" : context.getPackageName());
            root.put("root", KCUPath.KCU_SHARED_ROOT);
            root.put("paths", buildPaths());
            root.put("instances", buildInstances());

            File file = getManifestFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            String json = new GsonBuilder().setPrettyPrinting().create().toJson(root);
            // 先写临时文件再整体替换：
            // 这个文件是给外部工具读的。直接截断写的话，中途出问题会留下半截内容，
            // 外部工具解析失败时无从判断是启动器写坏了，还是自己认错了格式，
            // 只能报一个说不清的错误。整体替换则要么是新内容要么是旧内容，不会半截。
            // 不用 java.nio.file.Files.move：该 API 要求 API 26，与 minSdk 正好相同，
            // 属边界值，改用传统方式避免任何兼容性风险
            File tmp = new File(file.getParentFile(), FILE_NAME + ".kcupart");
            java.io.FileOutputStream out = new java.io.FileOutputStream(tmp, false);
            boolean ok = false;
            try {
                out.write(json.getBytes("UTF-8"));
                out.flush();
                ok = true;
            } finally {
                try {
                    out.close();
                } catch (Throwable ignored) {
                }
            }
            if (!ok || !tmp.renameTo(file)) {
                // 替换不成就删掉临时文件，免得在公共目录留下看着像正常文件的残留
                tmp.delete();
            }
        } catch (Throwable ignored) {
            // 清单写入失败不影响启动，静默忽略
        }
    }

    private static Map<String, Object> buildPaths() {
        Map<String, Object> paths = new LinkedHashMap<>();
        paths.put("minecraft", KCUPath.SHARED_COMMON_DIR);
        paths.put("privateCommon", KCUPath.PRIVATE_COMMON_DIR);
        paths.put("runtime", KCUPath.RUNTIME_DIR);
        paths.put("modRuntime", KCUPath.MOD_RUNTIME_DIR);
        paths.put("java", KCUPath.JAVA_PATH);
        paths.put("lwjgl", KCUPath.LWJGL_DIR);
        paths.put("log", KCUPath.LOG_DIR);
        paths.put("control", KCUPath.CONTROLLER_DIR);
        paths.put("share", KCUPath.SHARE_DIR);
        // 玩家自带 .so 的素材目录（可读写，供外部工具访问）；
        // 启动时校验后复制到私有 runtime/natives 才能 dlopen，公共目录不可直接加载
        paths.put("nativesExternal", KCUPath.KCU_SHARED_ROOT + "/natives");
        paths.put("nativeLibDir", KCUPath.NATIVE_LIB_DIR);
        return paths;
    }

    private static List<Map<String, Object>> buildInstances() {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            for (Profile profile : Profiles.getProfiles()) {
                // 逐个单独兜住：
                // 整段共用一个异常处理时，只要有一个实例取不到信息（比如目录已被删），
                // 后面的就全都不写了，清单里会变成"没有任何实例"。
                // 外部工具看到空列表会以为玩家一个实例都没有，而实际只是某一个坏了
                try {
                    Map<String, Object> one = new LinkedHashMap<>();
                    one.put("name", profile.getName());
                    one.put("gameDir", profile.getGameDir().getAbsolutePath());
                    one.put("selectedVersion", profile.getSelectedVersion());
                    list.add(one);
                } catch (Throwable ignored) {
                    // 跳过这一个，其余照常写入
                }
            }
        } catch (Throwable ignored) {
            // 实例列表不可用时清单仍写入路径部分
        }
        return list;
    }
}
