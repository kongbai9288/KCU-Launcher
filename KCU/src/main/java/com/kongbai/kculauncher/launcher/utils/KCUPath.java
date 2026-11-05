package com.kongbai.kculauncher.launcher.utils;

import android.content.Context;
import android.os.Environment;

import java.io.File;

public class KCUPath {

    public static String NATIVE_LIB_DIR;

    public static final String LATEST_GAME_LOG = "latest_game.log";

    public static String LOG_DIR;
    public static String CACHE_DIR;

    public static String RUNTIME_DIR;
    public static String MOD_RUNTIME_DIR;
    public static String JAVA_8_PATH;
    public static String JAVA_17_PATH;
    public static String JAVA_21_PATH;
    public static String JAVA_25_PATH;
    public static String JAVA_PATH;
    public static String JNA_PATH;
    public static String LWJGL_DIR;
    public static String CACIOCAVALLO_8_DIR;
    public static String CACIOCAVALLO_17_DIR;

    public static String FILES_DIR;
    public static String PLUGIN_DIR;
    public static String BACKGROUND_DIR;
    public static String SKIN_DIR;
    public static String CONTROLLER_DIR;
    public static String SHARE_DIR;

    public static String PRIVATE_COMMON_DIR;
    /** 根选择标记文件名：存在即表示该目录被选定为公共根，用于固化首次决策 */
    public static final String KCU_ROOT_MARKER = ".kcu_root";

    /**
     * KCU 公共目录根。
     *
     * 决策只做一次并写入标记文件固化。不能仅以"目录是否存在"判定：
     * 目录存在性可被外部改变（本项目自带的迁移工具就会创建 KCU 目录），
     * 一旦变化根路径即漂移，存量数据随之"消失"。
     */
    public static String KCU_SHARED_ROOT = resolveSharedRoot();
    public static String SHARED_COMMON_DIR = KCU_SHARED_ROOT + "/.minecraft";

    /** 旧公共根是否仍有数据待迁移；静态初始化不搬文件，避免大目录拖住启动 */
    public static volatile boolean LEGACY_ROOT_PENDING = false;

    private static String resolveSharedRoot() {
        String external = Environment.getExternalStorageDirectory().getAbsolutePath();
        File kcu = new File(external, "KCU");
        File legacy = new File(external, "FCL");
        // 根只在 KCU 下决策，不再把 legacy 当作候选根。
        // 旧写法在 /sdcard/FCL/.minecraft 存在时直接沿用 FCL 为公共根并写标记固化，
        // 于是「去完 FCL 的名字，数据却还留在 FCL 里」；真正的私有目录本来也读不到。
        if (new File(kcu, KCU_ROOT_MARKER).exists()) {
            return kcu.getAbsolutePath();
        }
        if (new File(legacy, ".minecraft").isDirectory()
                && !new File(kcu, ".minecraft").isDirectory()) {
            LEGACY_ROOT_PENDING = true;
        }
        try {
            File marker = new File(kcu, KCU_ROOT_MARKER);
            marker.getParentFile().mkdirs();
            marker.createNewFile();
        } catch (Exception ignored) {
            // 标记写入失败（多为权限未授予）不阻断，下次启动会重新判定
        }
        return kcu.getAbsolutePath();
    }

    /**
     * 把旧公共根下的 .minecraft 复制（不移动、不删除源）到 KCU 根。
     * 保守口径：只在复制全过程无异常后才写标记；任何失败都保留源数据。
     * 由「功能中心 → 迁移旧目录数据」显式触发，不在静态初始化里跑。
     */
    public static boolean migrateLegacySharedRoot() {
        File external = Environment.getExternalStorageDirectory();
        File legacy = new File(new File(external, "FCL"), ".minecraft");
        File kcuRoot = new File(external, "KCU");
        File target = new File(kcuRoot, ".minecraft");
        if (!legacy.isDirectory()) {
            return false;
        }
        if (new File(kcuRoot, KCU_ROOT_MARKER).exists() && target.isDirectory()) {
            return true;
        }
        try {
            copyRecursively(legacy, target);
            new File(kcuRoot, KCU_ROOT_MARKER).createNewFile();
            LEGACY_ROOT_PENDING = false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void copyRecursively(File src, File dst) throws java.io.IOException {
        if (src.isDirectory()) {
            if (!dst.isDirectory() && !dst.mkdirs()) {
                throw new java.io.IOException("mkdir " + dst);
            }
            File[] children = src.listFiles();
            if (children == null) {
                throw new java.io.IOException("list " + src);
            }
            for (File child : children) {
                copyRecursively(child, new File(dst, child.getName()));
            }
        } else {
            java.nio.file.Files.copy(src.toPath(), dst.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String AUTHLIB_INJECTOR_PATH;
    public static String LIB_PATCHER_PATH;
    public static String MIO_LAUNCH_WRAPPER;
    public static String LT_BACKGROUND_PATH;
    public static String DK_BACKGROUND_PATH;
    public static String LIVE_BACKGROUND_PATH;

    public static void loadPaths(Context context) {
        NATIVE_LIB_DIR = context.getApplicationInfo().nativeLibraryDir;

        LOG_DIR = KCU_SHARED_ROOT + "/log";
        CACHE_DIR = context.getCacheDir() + "/kcu";

        RUNTIME_DIR = context.getDir("runtime", 0).getAbsolutePath();
        JAVA_PATH = RUNTIME_DIR + "/java";
        JAVA_8_PATH = RUNTIME_DIR + "/java/jre8";
        JAVA_17_PATH = RUNTIME_DIR + "/java/jre17";
        JAVA_21_PATH = RUNTIME_DIR + "/java/jre21";
        JAVA_25_PATH = RUNTIME_DIR + "/java/jre25";
        JNA_PATH = RUNTIME_DIR + "/jna";
        LWJGL_DIR = RUNTIME_DIR + "/lwjgl";
        CACIOCAVALLO_8_DIR = RUNTIME_DIR + "/caciocavallo";
        CACIOCAVALLO_17_DIR = RUNTIME_DIR + "/caciocavallo17";

        MOD_RUNTIME_DIR = context.getDir("runtime_mod", 0).getAbsolutePath();

        FILES_DIR = context.getFilesDir().getAbsolutePath();
        PLUGIN_DIR = FILES_DIR + "/plugins";
        BACKGROUND_DIR = FILES_DIR + "/background";
        SKIN_DIR = FILES_DIR + "/skin";
        CONTROLLER_DIR = KCU_SHARED_ROOT + "/control";
        SHARE_DIR = KCU_SHARED_ROOT + "/share";
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir == null) {
            externalFilesDir = new File(Environment.getExternalStorageDirectory(), "Android/data/" + context.getPackageName() + "/files");
        }
        PRIVATE_COMMON_DIR = new File(externalFilesDir, ".minecraft").getAbsolutePath();

        AUTHLIB_INJECTOR_PATH = PLUGIN_DIR + "/authlib-injector.jar";
        LIB_PATCHER_PATH = PLUGIN_DIR + "/MioLibPatcher.jar";
        MIO_LAUNCH_WRAPPER = PLUGIN_DIR + "/MioLaunchWrapper.jar";
        LT_BACKGROUND_PATH = BACKGROUND_DIR + "/lt.png";
        DK_BACKGROUND_PATH = BACKGROUND_DIR + "/dk.png";
        LIVE_BACKGROUND_PATH = BACKGROUND_DIR + "/live.mp4";

        init(LOG_DIR);
        init(CACHE_DIR);
        init(RUNTIME_DIR);
        init(MOD_RUNTIME_DIR);
        init(JAVA_8_PATH);
        init(JAVA_25_PATH);
        init(JAVA_17_PATH);
        init(JAVA_21_PATH);
        init(LWJGL_DIR);
        init(CACIOCAVALLO_8_DIR);
        init(CACIOCAVALLO_17_DIR);
        init(FILES_DIR);
        init(PLUGIN_DIR);
        init(BACKGROUND_DIR);
        init(SKIN_DIR);
        init(CONTROLLER_DIR);
        init(SHARE_DIR);
        init(PRIVATE_COMMON_DIR);
        init(SHARED_COMMON_DIR);
    }

    private static void init(String path) {
        if (!new File(path).exists()) {
            new File(path).mkdirs();
        }
    }

    public static File getLatestGameLog() {
        return new File(LOG_DIR, LATEST_GAME_LOG);
    }

}
