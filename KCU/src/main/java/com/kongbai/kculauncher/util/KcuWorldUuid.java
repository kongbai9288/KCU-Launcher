package com.kongbai.kculauncher.util;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntArrayTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 存档内玩家 UUID 的读取与改写。
 *
 * 玩家用文件管理器直接把存档拷进实例目录时，存档里记的仍是原设备的 UUID，
 * 与本启动器生成的账号 UUID 不一致 —— 表现为进游戏后背包、进度、末影箱全空，
 * 看起来像"存档丢了"，实际数据还在，只是身份对不上。
 *
 * 改写属于破坏性操作：会同时改动 level.dat 与玩家数据文件名。
 * 因此只提供能力，是否执行必须由玩家在启动前明确确认。
 */
public final class KcuWorldUuid {

    private KcuWorldUuid() {
    }

    public static final class Mismatch {
        /** 世界目录名 */
        public final String worldName;
        /** 存档中记录的 UUID */
        public final UUID found;
        /** 将要写入的 UUID */
        public final UUID target;
        /** 存放玩家数据的目录名，改写时需要同步重命名文件 */
        public final String dataDir;

        Mismatch(String worldName, UUID found, UUID target, String dataDir) {
            this.worldName = worldName;
            this.found = found;
            this.target = target;
            this.dataDir = dataDir;
        }
    }

    /**
     * 扫描实例下的存档，返回 UUID 与目标不一致的世界。
     *
     * 读不出来的一律跳过：宁可漏报也不误报 —— 误报会让玩家在没问题的存档上
     * 执行一次破坏性改写。
     */
    public static List<Mismatch> scan(File instanceDir, UUID target) {
        List<Mismatch> result = new ArrayList<>();
        if (instanceDir == null || target == null || !instanceDir.isDirectory()) {
            return result;
        }
        File saves = new File(instanceDir, "saves");
        if (!saves.isDirectory()) {
            return result;
        }
        File[] worlds = saves.listFiles();
        if (worlds == null) {
            return result;
        }
        for (File world : worlds) {
            if (!world.isDirectory()) {
                continue;
            }
            UUID found = readWorldUuid(world);
            if (found == null || found.equals(target)) {
                continue;
            }
            result.add(new Mismatch(world.getName(), found, target, resolveDataDir(world)));
        }
        return result;
    }

    /**
     * 读取世界记录的玩家 UUID。
     *
     * 新版写在 Data.singleplayer_uuid（四个 int）；旧版内嵌在 Data.Player 里。
     * 两种都读，都没有时返回 null。
     */
    public static UUID readWorldUuid(File world) {
        File levelDat = new File(world, "level.dat");
        if (!levelDat.isFile()) {
            levelDat = new File(world, "special_level.dat");
        }
        if (!levelDat.isFile()) {
            return null;
        }
        CompoundTag root = read(levelDat);
        if (root == null) {
            return null;
        }
        if (!(root.get("Data") instanceof CompoundTag data)) {
            return null;
        }
        if (data.get("singleplayer_uuid") instanceof IntArrayTag tag
                && tag.getValue() != null && tag.getValue().length == 4) {
            return fromIntArray(tag.getValue());
        }
        if (data.get("Player") instanceof CompoundTag player) {
            if (player.get("UUID") instanceof IntArrayTag tag
                    && tag.getValue() != null && tag.getValue().length == 4) {
                return fromIntArray(tag.getValue());
            }
        }
        return null;
    }

    /**
     * 判定玩家数据目录名。
     *
     * 现代版本为 playerdata，上游世界管理里出现过 players/data；
     * 两个都存在时取实际有内容的那个。
     */
    private static String resolveDataDir(File world) {
        File modern = new File(world, "playerdata");
        if (modern.isDirectory()) {
            return "playerdata";
        }
        File legacy = new File(world, "players/data");
        if (legacy.isDirectory()) {
            return "players/data";
        }
        return "playerdata";
    }

    /**
     * 把世界记录的 UUID 改写为目标值，并同步重命名玩家数据文件。
     *
     * @return 改写成功返回 true；任一步失败返回 false —— 此时存档处于半改写状态，
     *         调用方必须提示玩家，不能当作无事发生
     */
    public static boolean rewrite(Mismatch mismatch, File instanceDir) {
        if (mismatch == null || instanceDir == null) {
            return false;
        }
        File world = new File(new File(instanceDir, "saves"), mismatch.worldName);
        if (!world.isDirectory()) {
            return false;
        }
        File levelDat = new File(world, "level.dat");
        if (!levelDat.isFile()) {
            levelDat = new File(world, "special_level.dat");
        }
        if (!levelDat.isFile()) {
            return false;
        }
        CompoundTag root = read(levelDat);
        if (root == null || !(root.get("Data") instanceof CompoundTag data)) {
            return false;
        }
        int[] value = toIntArray(mismatch.target);
        if (data.get("singleplayer_uuid") instanceof IntArrayTag) {
            data.put(new IntArrayTag("singleplayer_uuid", value));
        }
        if (data.get("Player") instanceof CompoundTag player
                && player.get("UUID") instanceof IntArrayTag) {
            player.put(new IntArrayTag("UUID", value));
        }
        if (!write(levelDat, root)) {
            return false;
        }
        renamePlayerFiles(world, mismatch.dataDir, mismatch.found, mismatch.target);
        return true;
    }

    /** 重命名玩家数据文件；失败不影响 level.dat 已写入的结果，但会被调用方提示 */
    private static void renamePlayerFiles(File world, String dataDir, UUID from, UUID to) {
        File dir = new File(world, dataDir);
        if (!dir.isDirectory()) {
            return;
        }
        renameIfExists(dir, from + ".dat", to + ".dat");
        renameIfExists(dir, from + ".dat_old", to + ".dat_old");
        // 进度与统计按 UUID 命名，一并改名，否则成就与统计同样对不上
        File advancements = new File(world, "advancements");
        if (advancements.isDirectory()) {
            renameIfExists(advancements, from + ".json", to + ".json");
        }
        File stats = new File(world, "stats");
        if (stats.isDirectory()) {
            renameIfExists(stats, from + ".json", to + ".json");
        }
    }

    private static void renameIfExists(File dir, String oldName, String newName) {
        File old = new File(dir, oldName);
        if (!old.isFile()) {
            return;
        }
        File target = new File(dir, newName);
        try {
            old.renameTo(target);
        } catch (Throwable ignored) {
        }
    }

    private static UUID fromIntArray(int[] value) {
        return new UUID(
                ((long) value[0] << 32) | (value[1] & 0xFFFFFFFFL),
                ((long) value[2] << 32) | (value[3] & 0xFFFFFFFFL));
    }

    private static int[] toIntArray(UUID uuid) {
        long msb = uuid.getMostSignificantBits();
        long lsb = uuid.getLeastSignificantBits();
        return new int[]{
                (int) (msb >> 32), (int) msb,
                (int) (lsb >> 32), (int) lsb
        };
    }

    /** 按文件头识别压缩方式读取；支持 GZip 与未压缩，读不出来返回 null */
    private static CompoundTag read(File file) {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            java.io.PushbackInputStream pis = new java.io.PushbackInputStream(is, 2);
            int first = pis.read();
            int second = pis.read();
            pis.unread(second);
            pis.unread(first);
            InputStream source = pis;
            if (first == 0x1f && second == 0x8b) {
                source = new GZIPInputStream(pis);
            }
            Tag tag = NBTIO.readTag(source);
            if (tag instanceof CompoundTag compound) {
                return compound;
            }
            return null;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean write(File file, CompoundTag tag) {
        OutputStream os = null;
        try {
            os = new FileOutputStream(file, false);
            OutputStream gos = new GZIPOutputStream(os);
            NBTIO.writeTag(gos, tag);
            gos.flush();
            if (gos instanceof java.io.Flushable) {
                ((GZIPOutputStream) gos).finish();
            }
            os.flush();
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
