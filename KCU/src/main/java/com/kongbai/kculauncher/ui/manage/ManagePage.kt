package com.kongbai.kculauncher.ui.manage

import android.content.Context
import android.content.res.ColorStateList
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import com.kongbai.kculauncher.mio.util.AnimUtil
import com.kongbai.kculauncher.mio.util.AnimUtil.Companion.interpolator
import com.kongbai.kculauncher.mio.util.showErrorDialog
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.databinding.PageManageVersionBinding
import com.kongbai.kculauncher.setting.Profile
import com.kongbai.kculauncher.ui.UIManager.Companion.instance
import com.kongbai.kculauncher.ui.manage.ManageUI.VersionLoadable
import com.kongbai.kculauncher.ui.manage.adapter.ManageItemAdapter
import com.kongbai.kculauncher.ui.manage.item.ManageItem
import com.kongbai.kculauncher.ui.version.Versions
import com.kongbai.kculauncher.launcher.utils.KCUPath
import com.kongbai.kculauncher.core.fakefx.beans.property.BooleanProperty
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleBooleanProperty
import com.kongbai.kculauncher.core.task.Schedulers
import com.kongbai.kculauncher.core.task.Task
import com.kongbai.kculauncher.core.util.io.FileUtils
import com.kongbai.kculauncher.library.browser.FileBrowser
import com.kongbai.kculauncher.library.browser.options.LibMode
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog
import com.kongbai.kculauncher.library.component.theme.ThemeEngine
import com.kongbai.kculauncher.library.component.ui.KCUPage
import com.kongbai.kculauncher.library.ui.ProgressDialog
import com.kongbai.kculauncher.library.util.uploadLog
import java.io.File

class ManagePage(context: Context?, id: Int) : KCUPage(context, id, R.layout.page_manage_version), VersionLoadable {
    private val currentVersionUpgradable: BooleanProperty = SimpleBooleanProperty()
    val profile: Profile
        get() = instance.manageUI.profile
    val version: String
        get() = instance.manageUI.version

    private lateinit var binding: PageManageVersionBinding

    init {
        create()
    }


    override fun loadVersion(profile: Profile, version: String?) {
        // null 是合法分发（切到未选版本 Profile 的瞬态）：无版本可判定整合包升级，保持现状
        if (version == null) return
        currentVersionUpgradable.set(profile.repository.isModpack(version))
    }

    private fun create() {
        binding = PageManageVersionBinding.bind(contentView).apply {
            ThemeEngine.getInstance().registerEvent(left) {
                left.backgroundTintList = ColorStateList(
                    arrayOf(intArrayOf()), intArrayOf(ThemeEngine.getInstance().getTheme().ltColor)
                )
            }
            ThemeEngine.getInstance().registerEvent(right) {
                right.backgroundTintList = ColorStateList(
                    arrayOf(intArrayOf()), intArrayOf(ThemeEngine.getInstance().getTheme().ltColor)
                )
            }

            left.layoutManager = LinearLayoutManager(context)
            left.adapter = ManageItemAdapter(
                context,
                listOf(
                    ManageItem(R.drawable.ic_baseline_cloud_upload_24, R.string.upload_log) {
                        uploadLatestLog()
                    },
                    ManageItem(R.drawable.ic_baseline_script_24, R.string.folder_kcu_log) {
                        onBrowse(
                            KCUPath.LOG_DIR
                        )
                    },
                    ManageItem(R.drawable.ic_baseline_videogame_asset_24, R.string.folder_game) {
                        onBrowse("")
                    },
                    ManageItem(R.drawable.ic_outline_extension_24, R.string.folder_mod) {
                        onBrowse("mods")
                    },
                    ManageItem(R.drawable.ic_baseline_settings_24, R.string.folder_config) {
                        onBrowse("config")
                    },
                    ManageItem(R.drawable.ic_baseline_texture_24, R.string.folder_resourcepacks) {
                        onBrowse("resourcepacks")
                    },
                    ManageItem(R.drawable.ic_baseline_application_24, R.string.folder_shaderpacks) {
                        onBrowse("shaderpacks")
                    },
                    ManageItem(R.drawable.ic_baseline_screenshot_24, R.string.folder_screenshots) {
                        onBrowse("screenshots")
                    },
                    ManageItem(R.drawable.ic_baseline_earth_24, R.string.folder_saves) {
                        onBrowse("saves")
                    }

                ))
            right.layoutManager = LinearLayoutManager(context)
            right.adapter = ManageItemAdapter(
                context,
                listOf(
                    ManageItem(R.drawable.ic_baseline_update_24, R.string.version_update) {
                        if (!currentVersionUpgradable.get()) {
                            AnimUtil.playTranslationX(it, 500, 0f, 50f, -50f, 0f)
                                .interpolator(OvershootInterpolator()).start()
                        } else {
                            updateGame()
                        }
                    },
                    ManageItem(R.drawable.ic_baseline_edit_24, R.string.version_manage_rename) {
                        rename()
                    },
                    ManageItem(
                        R.drawable.ic_baseline_content_copy_24,
                        R.string.version_manage_duplicate
                    ) {
                        duplicate()
                    },
                    ManageItem(R.drawable.ic_baseline_output_24, R.string.modpack_export) {
                        export()
                    },
                    ManageItem(
                        R.drawable.ic_baseline_list_24,
                        R.string.version_manage_redownload_assets_index
                    ) {
                        redownloadAssetIndex()
                    },
                    ManageItem(
                        R.drawable.ic_baseline_delete_24,
                        R.string.version_manage_remove_libraries
                    ) {
                        clearLibraries()
                    },
                    ManageItem(
                        R.drawable.ic_baseline_delete_24,
                        R.string.version_manage_clean
                    ) {
                        clearJunkFiles()
                    }
                ))
        }
    }

    private fun onBrowse(path: String) {
        val root =
            if (path.startsWith("/")) File(path) else if (path.isEmpty()) profile.repository.getRunDirectory(
                version
            ) else File(
                profile.repository.getRunDirectory(version), path
            )
        if (!root.exists()) {
            root.mkdirs()
        }
        FileBrowser.Builder(context)
            .setInitDir(root.absolutePath)
            .setLibMode(LibMode.FILE_BROWSER)
            .create()
            .browse(activity)
    }

    private fun redownloadAssetIndex() {
        Versions.updateGameAssets(context, profile, version)
    }

    private fun clearLibraries() {
        val builder = KCUAlertDialog.Builder(context)
        builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT)
        builder.setMessage(
            String.format(
                context.getString(R.string.version_manage_remove_confirm),
                "libraries"
            )
        )
        builder.setPositiveButton {
            val progress = ProgressDialog(context)
            Task.runAsync {
                FileUtils.deleteDirectoryQuietly(
                    File(
                        profile.repository.baseDirectory, "libraries"
                    )
                )
            }.whenComplete(Schedulers.androidUIThread()) { _: Exception? ->
                progress.dismiss()
            }.start()
        }
        builder.setNegativeButton(null)
        builder.create().show()
    }

    private fun clearJunkFiles() {
        val builder = KCUAlertDialog.Builder(context)
        builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT)
        builder.setMessage(
            String.format(
                context.getString(R.string.version_manage_remove_confirm),
                "logs"
            )
        )
        builder.setPositiveButton {
            val progress = ProgressDialog(context)
            Task.runAsync {
                Versions.cleanVersion(
                    profile, version
                )
            }.whenComplete(Schedulers.androidUIThread()) { _: Exception? ->
                progress.dismiss()
            }.start()
        }
        builder.setNegativeButton(null)
        builder.create().show()
    }

    private fun updateGame() {
        Versions.updateVersion(context, profile, version)
    }

    private fun export() {
        Versions.exportVersion(context, profile, version)
    }

    private fun rename() {
        Versions.renameVersion(context, profile, version)
            .thenApply {
                instance.manageUI.preferredVersionName = it
            }
    }

    private fun duplicate() {
        Versions.duplicateVersion(context, profile, version)
    }

    private fun uploadLatestLog() {
        val logFile = KCUPath.getLatestGameLog()
        if (!logFile.exists()) {
            showErrorDialog(context,R.string.log_not_found)
            return
        }
        try {
            if (logFile.length() > 5 * 1024 * 1024) {
                showErrorDialog(context,R.string.log_too_large)
                return
            }
            val logs = FileUtils.readText(logFile)
            uploadLog(activity, logs)
        } catch (e: Exception) {
            showErrorDialog(context,"Failed to read log: ${e.message}")
        }
    }

}
