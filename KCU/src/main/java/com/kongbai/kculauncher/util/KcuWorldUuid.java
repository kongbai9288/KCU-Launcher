package com.kongbai.kculauncher.util;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntArrayTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

/**
 * 存档内玩家 UUID 的只读检测。
 *
 * 玩家用文件管理器直接把存档拷进实例目录时，存档里记的仍是原设备的 UUID，
 * 与本启动器生成的账号 UUID 不一致 —— 表现为进游戏后背包、进度、末影箱全空，
 * 看起来像"存档丢了"，实际数据还在，只是身份对不上。
 *
 * 本类只负责发现并报告，不提供任何改写能力。
 * 改写存档属于破坏性操作，一旦自动执行，出问题会被归咎于启动器；
 * 因此只在启动前劝导玩家自行用 NBT 编辑工具处理。
 */
public final class KcuWorldUuid {

    private KcuWorldUuid() {
    }

    /**
     * 单次扫描的世界数上限。
     *
     * 扫描跑在启动路径上，每读一个世界就要解压并解析一次 level.dat。
     * 世界数量多时逐个处理会拖慢启动，因此设上限；超出部分本次不扫。
     */
    private static final int MAX_SCAN_WORLDS = 32;

    /** level.dat 的体积上限，超过则跳过：正常的远小于此，异常大的多半已损坏 */
    private static final long MAX_LEVEL_DAT_BYTES = 64L * 1024 * 1024;

    public static final class Mismatch {
        /** 世界目录名 */
        public final String worldName;
        /** 存档中记录的 UUID */
        public final UUID found;
        /** 本账号当前使用的 UUID */
        public final UUID expected;

        Mismatch(String worldName, UUID found, UUID expected) {
            this.worldName = worldName;
            this.found = found;
            this.expected = expected;
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
        int scanned = 0;
        for (File world : worlds) {
            if (!world.isDirectory()) {
                continue;
            }
            // 扫描发生在启动路径上，每个世界都要解压并解析一次 level.dat。
            // 世界很多时逐个读会明显拖慢启动，因此设上限。
            // 超出上限后不再继续，结果可能不完整 —— 这属于已知取舍，不假装能覆盖全部
            if (scanned >= MAX_SCAN_WORLDS) {
                break;
            }
            scanned++;
            UUID found = readWorldUuid(world);
            if (found == null || found.equals(target)) {
                continue;
            }
            result.add(new Mismatch(world.getName(), found, target));
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
        // 体积异常大的多半已损坏，解压会拖慢启动；跳过即可，宁可漏报
        if (levelDat.length() > MAX_LEVEL_DAT_BYTES || levelDat.length() <= 0) {
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

    private static UUID fromIntArray(int[] value) {
        return new UUID(
                ((long) value[0] << 32) | (value[1] & 0xFFFFFFFFL),
                ((long) value[2] << 32) | (value[3] & 0xFFFFFFFFL));
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

}
