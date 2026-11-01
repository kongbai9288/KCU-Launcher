package com.kongbai.kculauncher.library.browser

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kongbai.kculauncher.core.util.Logging
import com.kongbai.kculauncher.core.util.io.IOUtils
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.logging.Level

/**
 * 统一的文件选择结果：既可能是本地文件/目录路径，也可能是 content provider 提供的 content:// uri。
 * 调用方无需区分来源，按需调用对应方法即可。
 */
class SelectedFile(val uri: Uri) {

    /** 是否为 content provider 提供的内容（授权是临时的，需要立即读取或拷贝） */
    val isContent: Boolean
        get() = uri.scheme == ContentResolver.SCHEME_CONTENT

    /** 本地文件/目录，content 时为 null */
    val file: File?
        get() = if (isContent) null else File(uri.path ?: uri.toString())

    /** 原始字符串（本地路径或 content uri） */
    val path: String
        get() = uri.toString()

    /** 文件名（不含目录部分） */
    fun fileName(context: Context): String {
        if (!isContent) return file?.name ?: ""
        return try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
                ?: return uri.lastPathSegment ?: ""
            cursor.use {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index == -1 || !it.moveToFirst()) {
                    return uri.lastPathSegment ?: ""
                }
                it.getString(index) ?: uri.lastPathSegment ?: ""
            }
        } catch (_: Exception) {
            // 部分文件管理器多选返回的 uri 未授予读权限，query 抛 SecurityException。
            // 不兜底的话整个导入流程会崩，且玩家看不到任何原因
            uri.lastPathSegment ?: "unknown"
        }
    }

    /** 打开输入流读取内容 */
    fun openInputStream(context: Context): InputStream? {
        return if (isContent) {
            context.contentResolver.openInputStream(uri)
        } else {
            file?.inputStream()
        }
    }

    /**
     * 获取真实文件：本地路径直接返回；content 拷贝到 cacheDir 后返回拷贝结果。
     * 拷贝失败时目标文件会被删掉，调用方用 exists() 判断就能正确识别，
     * 不会拿着一个残缺文件继续往下装
     */
    fun toFile(context: Context, cacheDir: File): File {
        file?.let { return it }
        val dest = File(cacheDir, uniqueName(cacheDir, fileName(context)))
        copyTo(context, dest)
        return dest
    }

    /** 拷贝内容到指定文件（需要固定文件名时使用），返回是否成功 */
    fun copyTo(context: Context, dest: File): Boolean {
        try {
            val input = openInputStream(context)
            if (input == null) {
                Logging.LOG.log(Level.WARNING, "Cannot open input stream for selected file: $uri")
                return false
            }
            input.use { i ->
                FileOutputStream(dest).use { o ->
                    IOUtils.copyTo(i, o)
                }
            }
            return true
        } catch (e: Exception) {
            Logging.LOG.log(Level.WARNING, "Failed to copy selected file $uri to $dest", e)
            // 写了一半失败会留下残缺文件，而调用方大多只判断 exists()，
            // 那样会把残缺文件当成完整文件用下去，所以失败即删
            try {
                dest.delete()
            } catch (_: Exception) {
            }
            return false
        }
    }

    /**
     * 生成安全且不重名的目标文件名：content 源给的名字可能带非法字符、为空，
     * 多选时还可能同名，直接拿来用会互相覆盖
     */
    private fun uniqueName(dir: File, raw: String): String {
        val cleaned = buildString {
            for (ch in raw) {
                append(if (ch == '/' || ch == '\\' || ch == ':' || ch == '*' || ch == '?' || ch == '"' || ch == '<' || ch == '>' || ch == '|') '_' else ch)
            }
        }
        val safe = cleaned.trim()
        val base = if (safe.isEmpty() || safe == "." || safe == "..") "file" else safe
        if (!File(dir, base).exists()) return base
        val dot = base.lastIndexOf('.')
        val stem = if (dot > 0) base.substring(0, dot) else base
        val ext = if (dot > 0) base.substring(dot) else ""
        var index = 1
        while (index < 1000) {
            val candidate = "$stem ($index)$ext"
            if (!File(dir, candidate).exists()) return candidate
            index++
        }
        return "$stem (${System.currentTimeMillis()})$ext"
    }
}
