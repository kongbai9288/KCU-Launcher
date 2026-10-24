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
     * @return 实际新增的账号数量
     */
    public static int importPending() {
        File file = getFile();
        if (!file.isFile()) {
            return 0;
        }
        String status = readStatus(file);
        if (!STATUS_PENDING.equals(status)) {
            return 0;
        }
        List<Entry> entries = readEntries(file);
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
            } catch (Throwable ignored) {
            }
        }
        // 无论成功多少都改写状态，避免反复导入
        export();
        return added;
    }

    private static void write(List<String> names, List<String> uuids, String status) {
        JsonWriter writer = null;
        try {
            File file = getFile();
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            writer = new JsonWriter(new FileWriter(file, false));
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
        } catch (Throwable ignored) {
            // 写入失败不影响启动
        } finally {
            close(writer);
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
        } catch (Throwable ignored) {
        } finally {
            close(reader);
        }
        return list;
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
