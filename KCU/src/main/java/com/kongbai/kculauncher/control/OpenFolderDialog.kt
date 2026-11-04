package com.kongbai.kculauncher.control

import android.annotation.SuppressLint
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.databinding.DialogOpenFolderBinding
import com.kongbai.kculauncher.library.browser.FileBrowser
import com.kongbai.kculauncher.library.browser.SelectedFile
import com.kongbai.kculauncher.library.browser.adapter.FileBrowserAdapter
import com.kongbai.kculauncher.library.browser.adapter.FileBrowserListener
import com.kongbai.kculauncher.library.browser.options.LibMode
import com.kongbai.kculauncher.library.browser.options.SortMode
import com.kongbai.kculauncher.library.component.KCUActivity
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog
import com.kongbai.kculauncher.library.component.dialog.KCUDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths
import com.kongbai.kculauncher.mio.util.getScreenHeight
import com.kongbai.kculauncher.mio.util.getScreenWidth

class OpenFolderDialog(
    val activity: KCUActivity,
    val initialPath: String
) : KCUDialog(activity), View.OnClickListener {
    private var internalPath: String = initialPath
    private val binding: DialogOpenFolderBinding
    private var job: Job? = null

    init {
        val width = (getScreenWidth() * 0.7).toInt()
        val height = (getScreenHeight() * 0.9).toInt()
        window!!.setLayout(width, height)
        binding = DialogOpenFolderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.closeButton.setOnClickListener(this)
        binding.importButton.setOnClickListener(this)
        binding.backButton.setOnClickListener(this)
        binding.cancelProgress.setOnClickListener(this)
        binding.path.isSelected = true

        binding.backButton.visibility = View.GONE

        refreshFiles()
    }

    private fun refreshFiles() {
        binding.path.text = internalPath
        binding.filesLayout.adapter = FileBrowserAdapter(
            context,
            FileBrowser.Builder(context)
                .setLibMode(LibMode.FILE_BROWSER)
                .setInitDir(internalPath)
                .create(),
            File(internalPath).toPath(),
            ArrayList<String>(),
            SortMode.NAME,
            object : FileBrowserListener {
                override fun onEnterDir(path: String) {
                    internalPath = path
                    refreshFiles()
                }

                override fun onSelect(adapter: FileBrowserAdapter, path: String) {}
            }
        )

        val currentPath = Paths.get(internalPath).normalize().toAbsolutePath()
        val rootPath = Paths.get(initialPath).normalize().toAbsolutePath()

        binding.backButton.visibility = if (currentPath == rootPath) View.GONE else View.VISIBLE
    }

    override fun onClick(v: View) {
        when (v) {
            binding.closeButton -> dismiss()
            binding.importButton -> {
                val targetDir = internalPath
                activity.fileLauncher.launchMultiSelection(targetDir, null) {
                    if (it == null) return@launchMultiSelection
                    importFiles(it, targetDir)
                }
            }

            binding.backButton -> {
                val root = Paths.get(initialPath).normalize().toAbsolutePath()
                val current = Paths.get(internalPath).normalize().toAbsolutePath()

                val parent = current.parent
                if (parent != null && parent.normalize().startsWith(root)) {
                    internalPath = parent.toString()
                    refreshFiles()
                }
            }

            binding.cancelProgress -> {
                job?.cancel()
                job = null
                binding.progress.visibility = View.GONE
            }
        }
    }


    @SuppressLint("Recycle")
    private fun importFiles(
        files: List<SelectedFile>,
        targetDir: String
    ) {
        job = lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                binding.backButton.isEnabled = false
                binding.importButton.isEnabled = false
                binding.closeButton.isEnabled = false
                binding.progress.visibility = View.VISIBLE
            }

            try {
                files.forEach { file ->
                    ensureActive()
                    val name = file.fileName(context)
                    // 原来这里自己手写了一遍拷贝：打不开源时 ?.use 什么都不做，
                    // 既没有文件也没有提示，进度条一消失看起来就像导入成功了；
                    // 目标名直接用 provider 给的名字，同名会被无声覆盖（数据丢失），
                    // 名字带非法字符时还会抛异常并把整段堆栈弹给玩家看。
                    // 统一走 SelectedFile：安全命名、不覆盖、失败即删残缺文件
                    val copied = file.copyToDir(context, File(targetDir))
                    if (copied == null) {
                        withContext(Dispatchers.Main) {
                            KCUAlertDialog.Builder(activity)
                                .setMessage(
                                    activity.getString(
                                        R.string.ingame_folder_import_failed,
                                        name,
                                        activity.getString(R.string.file_browser_operation_failed)
                                    )
                                )
                                .setPositiveButton(
                                    activity.getString(com.kongbai.kculauncher.R.string.close)
                                ) {}.create().show()
                        }
                    }
                }
                // 全部导入完只刷新一次：原来每导一个就重建一次列表，而列表是
                // 在界面线程同步列目录并逐行查磁盘的，导入十个就扫十遍
                withContext(Dispatchers.Main) {
                    refreshFiles()
                }
            } catch (_: CancellationException) {
            } catch (_: Exception) {
            }

            withContext(Dispatchers.Main) {
                binding.backButton.isEnabled = true
                binding.importButton.isEnabled = true
                binding.closeButton.isEnabled = true
                binding.progress.visibility = View.GONE
            }

            job = null
        }
    }
}