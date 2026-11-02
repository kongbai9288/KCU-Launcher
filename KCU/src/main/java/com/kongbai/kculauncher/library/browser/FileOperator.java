package com.kongbai.kculauncher.library.browser;

import com.kongbai.kculauncher.core.util.Logging;
import com.kongbai.kculauncher.library.browser.options.LibMode;
import com.kongbai.kculauncher.library.browser.options.SortMode;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.comparator.DirectoryFileComparator;
import org.apache.commons.io.comparator.NameFileComparator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.logging.Level;

public class FileOperator {

    /**
     * 每一行的显示信息。列表滚动时若逐行去查磁盘（时间、大小、是否目录），
     * 每滚一屏都要重查一遍，而且发生在界面线程——大目录滚动会一顿一顿的。
     * 这里在 IO 线程把整屏要用的信息一次算好，界面只查表
     */
    public static final class Row {
        public final String description;
        public final boolean directory;

        Row(String description, boolean directory) {
            this.description = description;
            this.directory = directory;
        }
    }

    /**
     * 为列表预生成每一行的显示信息。只在 IO 线程调用，故可以共用一个格式化器
     */
    public static Map<File, Row> buildRows(List<File> files) {
        Map<File, Row> map = new HashMap<>();
        if (files == null) {
            return map;
        }
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        for (File file : files) {
            StringBuilder text = new StringBuilder();
            try {
                text.append(formatter.format(new Date(FileUtils.lastModified(file))));
            } catch (IOException e) {
                // 文件可能在列目录之后被删掉：这里吃掉异常，显示为 Unknown，
                // 不至于让整行乃至整个界面崩掉
                text.append("Unknown");
            }
            boolean directory = file.isDirectory();
            if (!directory) {
                text.append("    ").append(FileUtils.byteCountToDisplaySize(file.length()));
            }
            map.put(file, new Row(text.toString(), directory));
        }
        return map;
    }

    /**
     * 列出目录内容：目录恒排前，组内按指定方式排序；可选择过滤后缀
     */
    public static List<File> getFileList(Path path, FileBrowser fileBrowser, SortMode sortMode) {
        List<File> list = new ArrayList<>();
        File[] files = path.toFile().listFiles();
        List<File> rawList = files != null ? new ArrayList<>(Arrays.asList(files)) : new ArrayList<>();
        sortRaw(rawList, sortMode);
        rawList.sort(DirectoryFileComparator.DIRECTORY_COMPARATOR);
        List<File> filterList = new ArrayList<>();
        if (!fileBrowser.getSuffix().isEmpty()) {
            // 后缀先统一成小写再比：从电脑拷过来的文件常是大写扩展名（比如 mod.JAR），
            // 按原样比的话这类文件在列表里根本不出现，玩家只会以为自己没这个文件
            List<String> lowerSuffix = new ArrayList<>();
            for (String suffix : fileBrowser.getSuffix()) {
                String wanted = normalizeSuffix(suffix);
                if (wanted != null && !wanted.isEmpty()) {
                    lowerSuffix.add(wanted);
                }
            }
            for (File file : rawList) {
                if (file.isFile()) {
                    String name = file.getName().toLowerCase(Locale.ROOT);
                    for (String suffix : lowerSuffix) {
                        if (name.endsWith(suffix)) {
                            filterList.add(file);
                            break;
                        }
                    }
                } else {
                    filterList.add(file);
                }
            }
        } else {
            filterList.addAll(rawList);
        }
        if (fileBrowser.getLibMode() == LibMode.FOLDER_CHOOSER) {
            for (File file : rawList) {
                if (file.isDirectory()) {
                    list.add(file);
                }
            }
        } else {
            list.addAll(filterList);
        }
        return list;
    }

    /**
     * 按大小或时间排序时，比较器每比一次都要 stat 一次文件：一千个文件约两万次
     * stat，大目录下列一个目录要等很久。先把取值算一遍存起来，排序时只查表
     */
    private static void sortRaw(List<File> list, SortMode sortMode) {
        if (sortMode == SortMode.SIZE) {
            Map<File, Long> cache = new HashMap<>();
            for (File file : list) {
                cache.put(file, file.length());
            }
            list.sort(Comparator.comparingLong((File file) -> cache.getOrDefault(file, 0L)));
        } else if (sortMode == SortMode.DATE) {
            Map<File, Long> cache = new HashMap<>();
            for (File file : list) {
                cache.put(file, file.lastModified());
            }
            list.sort(Comparator.comparingLong((File file) -> cache.getOrDefault(file, 0L)).reversed());
        } else {
            list.sort(NameFileComparator.NAME_INSENSITIVE_COMPARATOR);
        }
    }

    /**
     * 把配置里的后缀统一成带前导点的写法。
     * 配置漏写点时（比如写 "jar"），按原样比会把 "notjar" 这类没有扩展名的文件
     * 也算进来，筛选形同虚设；统一补上点之后两种写法行为一致
     */
    public static String normalizeSuffix(String suffix) {
        if (suffix == null) {
            return null;
        }
        String text = suffix.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return text;
        }
        return text.startsWith(".") ? text : "." + text;
    }

    /**
     * 校验文件名是否可用于新建或重命名：非空且不含路径分隔符
     */
    public static boolean isValidFileName(String name) {
        return name != null && !name.trim().isEmpty()
                && !name.contains(File.separator) && !name.contains("/");
    }

    /**
     * 为目录中不重名的目标名生成候选名：重名时保留扩展名并追加 " (n)" 序号
     */
    public static String resolveConflict(File dir, String name) {
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        String candidate = name;
        int index = 0;
        while (new File(dir, candidate).exists()) {
            index++;
            candidate = base + " (" + index + ")" + ext;
        }
        return candidate;
    }

    public static void createDirectory(File parent, String name) throws IOException {
        File dir = new File(parent, name);
        if (dir.exists()) {
            throw new IOException("Already exists: " + dir);
        }
        if (!dir.mkdir() && !dir.isDirectory()) {
            throw new IOException("Failed to create directory: " + dir);
        }
    }

    public static void createFile(File parent, String name) throws IOException {
        File file = new File(parent, name);
        if (file.exists()) {
            throw new IOException("Already exists: " + file);
        }
        if (!file.createNewFile() && !file.isFile()) {
            throw new IOException("Failed to create file: " + file);
        }
    }

    public static void rename(File file, String newName) throws IOException {
        File target = new File(file.getParentFile(), newName);
        if (target.exists()) {
            throw new IOException("Already exists: " + target);
        }
        Files.move(file.toPath(), target.toPath());
    }

    public static void delete(File file) throws IOException {
        FileUtils.forceDelete(file);
    }

    /**
     * 复制文件/目录到目标目录，重名自动追加序号，返回成功数量
     */
    public static int copyTo(List<File> files, File targetDir) {
        int count = 0;
        for (File file : files) {
            try {
                File target = new File(targetDir, resolveConflict(targetDir, file.getName()));
                if (file.isDirectory()) {
                    FileUtils.copyDirectory(file, target);
                } else {
                    FileUtils.copyFile(file, target);
                }
                count++;
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Failed to copy file: " + file, e);
            }
        }
        return count;
    }

    /**
     * 移动文件/目录到目标目录，重名自动追加序号，返回成功数量
     */
    public static int moveTo(List<File> files, File targetDir) {
        int count = 0;
        for (File file : files) {
            try {
                File target = new File(targetDir, resolveConflict(targetDir, file.getName()));
                if (file.isDirectory()) {
                    FileUtils.moveDirectory(file, target);
                } else {
                    FileUtils.moveFile(file, target);
                }
                count++;
            } catch (IOException e) {
                Logging.LOG.log(Level.WARNING, "Failed to move file: " + file, e);
            }
        }
        return count;
    }

}
