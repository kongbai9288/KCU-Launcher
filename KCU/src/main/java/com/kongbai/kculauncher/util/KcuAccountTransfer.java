package com.kongbai.kculauncher.util;

import android.util.JsonReader;
import android.util.JsonWriter;

import com.kongbai.kculauncher.core.auth.Account;
import com.kongbai.kculauncher.core.auth.offline.OfflineAccount;
import com.kongbai.kculauncher.launcher.utils.KCUPath;
import com.kongbai.kculauncher.setting.Accounts;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 离线账号的迁移导出与导入。
 *
 * 文件位于公共目录下（与 manifest.json 同级），供外部迁移工具读写。
 *
 * 只处理离线账号：正版与第三方（微软 / authlib-injector）账号一律不导出，
 * 因为其存储内容包含登录凭据，导出等同于把凭据明文写到可被任意应用读取的公共目录。
 * 导入同样只接受离线条目，且只取名称与 UUID —— 这两项足以保留离线身份
 * （世界数据归属按 UUID 判定），不含任何凭据。
 *
 * 纯辅助功能：任何异常都只记录不抛出，绝不阻断启动。
 */
public final class KcuAccountTransfer {

    private static final String FILE_NAME = "accounts.json";
    private static final int SCHEMA_VERSION = 1;

    /** 由外部工具写入，表示"请启动器导入这批账号" */
    private static final String STATUS_PENDING = "pending";
    /** 启动器导出时写入，表示"这是当前账号快照" */
    private static final String STATUS_EXPORTED = "exported";

    /** importPending 的返回值：表示没有待导入文件，属于正常无操作 */
    public static final int NO_PENDING_FILE = -1;

    private KcuAccountTransfer() {
    }

    public static File getFile() {
        return new File(KCUPath.KCU_SHARED_ROOT, FILE_NAME);
    }

    /** 导出当前所有离线账号；非离线账号跳过 */
    public static void export() {
        List<Account> accounts;
        try {
            accounts = Accounts.getAccounts();
        } catch (Throwable ignored) {
            return;
        }
        List<String> names = new ArrayList<>();
        List<String> uuids = new ArrayList<>();
        if (accounts != null) {
            for (Account account : accounts) {
                if (!(account instanceof OfflineAccount)) {
                    continue;
                }
                String name = account.getUsername();
                UUID uuid = account.getUUID();
                if (name == null || name.isEmpty() || uuid == null) {
                    continue;
                }
                names.add(name);
                uuids.add(uuid.toString());
            }
        }
        write(names, uuids, STATUS_EXPORTED);
    }

    /**
     * 仅当外部工具把状态置为 pending 时才导入。
     * 导入完成后文件被重写为 exported，因此同一份文件不会被重复导入。
     *
     * 【返回值为什么要区分"无操作"与"导入了零个"】
     * 文件不存在或状态不是 pending 属于正常的无操作，不需要告知玩家。
     * 但文件确实是 pending 而最终一个都没导入成功，说明名称非法、
     * 已存在同名账号或写入失败 —— 玩家此时正等着账号出现，
     * 一声不响地跳过会让他以为启动器把自己的账号弄丢了。
     * 调用方据此区分：负数表示无操作，大于等于零表示确实处理过。
     *
     * @return 实际新增的账号数量；文件不存在或状态不是 pending 时返回 -1
     */
    public static int importPending() {
        File file = getFile();
        if (!file.isFile()) {
            return NO_PENDING_FILE;
        }
        String status = readStatus(file);
        if (!STATUS_PENDING.equals(status)) {
            return NO_PENDING_FILE;
        }
        List<Entry> entries = readEntries(file);
        if (entries == null) {
            // 读不出来就一个都不导入，也不改写状态：
            // 文件由外部工具生成，保留原样等它重新生成后还能完整导入
            android.util.Log.w("KCU", "Account transfer file is unreadable: " + file);
            return NO_PENDING_FILE;
        }
        int added = 0;
        for (Entry entry : entries) {
            if (entry.name == null || entry.name.isEmpty()) {
                continue;
            }
            if (!KcuAccountFlow.isValidName(entry.name)) {
                continue;
            }
            // 已存在同名账号时不覆盖：迁移是补充，不是替换
            if (KcuAccountFlow.findByName(entry.name) != null) {
                continue;
            }
            UUID uuid = entry.uuid;
            if (uuid == null) {
                uuid = com.kongbai.kculauncher.core.auth.offline.OfflineAccountFactory
                        .getUUIDFromUserName(entry.name);
            }
            Account created = KcuAccountFlow.createOffline(entry.name, uuid);
            if (created == null) {
                continue;
            }
            try {
                Accounts.addAccount(created);
                added++;
            } catch (Throwable e) {
                // 单个账号失败不能中断整批导入，但必须留下记录：
                // 否则玩家发现少了一个账号时无任何线索可查
                android.util.Log.w("KCU", "Failed to import account: " + entry.name, e);
            }
        }
        // 无论成功多少都改写状态，避免反复导入
        export();
        return added;
    }

    /**
     * 先写临时文件再改名。
     *
     * 【为什么不能直接覆盖】
     * 这个文件位于公共目录，外部迁移工具也会读它。直接以截断方式打开，
     * 写入中途出问题就会留下半截内容，外部工具读到的就是坏文件，
     * 而它无从判断这是启动器写坏了还是自己生成的格式不对。
     * 先写临时文件、写完整再改名，外部工具要么读到旧的完整内容，
     * 要么读到新的完整内容。
     */
    private static void write(List<String> names, List<String> uuids, String status) {
        File file = getFile();
        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }
        if (!parent.exists() && !parent.mkdirs()) {
            return;
        }
        File tmp = new File(parent, FILE_NAME + ".tmp");
        boolean ok = false;
        JsonWriter writer = null;
        try {
            writer = new JsonWriter(new FileWriter(tmp, false));
            writer.setIndent("  ");
            writer.beginObject();
            writer.name("schemaVersion").value(SCHEMA_VERSION);
            writer.name("status").value(status);
            writer.name("exportedAt").value(System.currentTimeMillis());
            writer.name("accounts");
            writer.beginArray();
            for (int i = 0; i < names.size(); i++) {
                writer.beginObject();
                writer.name("username").value(names.get(i));
                writer.name("uuid").value(uuids.get(i));
                writer.endObject();
            }
            writer.endArray();
            writer.endObject();
            writer.flush();
            ok = true;
        } catch (Throwable ignored) {
            // 写入失败不影响启动
        } finally {
            close(writer);
        }
        if (!ok || !tmp.renameTo(file)) {
            if (!tmp.delete()) {
                android.util.Log.w("KCU", "unable to remove " + tmp);
            }
        }
    }

    private static String readStatus(File file) {
        JsonReader reader = null;
        try {
            reader = new JsonReader(new FileReader(file));
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("status".equals(key)) {
                    return reader.nextString();
                }
                reader.skipValue();
            }
            reader.endObject();
        } catch (Throwable ignored) {
        } finally {
            close(reader);
        }
        return "";
    }

    /**
     * 解析失败时返回 null，而不是已经读到的那部分。
     *
     * 【为什么不能用部分结果】
     * 文件由外部工具生成，中途损坏时按部分结果导入会造成真实的账号丢失：
     * 导入前几个之后，末尾的导出会把文件重写成当前账号快照，
     * 源文件里没读到的那部分就再也找不回来了。
     * 返回 null 让调用方停止导入、也不改写状态，等外部工具重新生成后
     * 还能完整导入 —— 少导入可以重来，丢了就找不回来。
     */
    private static List<Entry> readEntries(File file) {
        List<Entry> list = new ArrayList<>();
        JsonReader reader = null;
        try {
            reader = new JsonReader(new FileReader(file));
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if ("accounts".equals(key)) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        Entry entry = new Entry();
                        reader.beginObject();
                        while (reader.hasNext()) {
                            String field = reader.nextName();
                            if ("username".equals(field)) {
                                entry.name = reader.nextString();
                            } else if ("uuid".equals(field)) {
                                entry.uuid = parseUuid(reader.nextString());
                            } else {
                                reader.skipValue();
                            }
                        }
                        reader.endObject();
                        list.add(entry);
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return list;
        } catch (Throwable e) {
            android.util.Log.w("KCU", "Failed to parse account transfer file", e);
            return null;
        } finally {
            close(reader);
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (Throwable ignored) {
            return null;
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

    private static final class Entry {
        String name;
        UUID uuid;
    }
}
