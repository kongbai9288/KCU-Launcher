package com.kongbai.kculauncher.setting

import com.kongbai.kculauncher.KCUApp
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.util.WeakListenerHolder
import com.kongbai.kculauncher.launcher.utils.KCUPath
import com.kongbai.kculauncher.core.event.EventBus
import com.kongbai.kculauncher.core.event.RefreshedVersionsEvent
import com.kongbai.kculauncher.core.fakefx.collections.FXCollections
import java.io.File
import java.util.TreeMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object Profiles {
    /**
     * True if [.init] hasn't been called.
     */
    private var initialized = false
    private var isFirstRefresh = true
    /**
     * Called when it's ready to load profiles from [ConfigHolder.config].
     */
    private val holder = WeakListenerHolder()
    /** Profile 列表（Repository 单例，修改统一走 addProfile/removeProfile 以触发保存与选中项校验） */
    @JvmStatic
    val profiles = mutableListOf<Profile>()
    private val _selectedProfile = MutableStateFlow<Profile?>(null)
    /** 当前选中的 Profile（Repository 单例状态，Java 侧访问 getSelectedProfileFlow()） */
    @get:JvmName("getSelectedProfileFlow")
    @JvmStatic
    val selectedProfile: StateFlow<Profile?> = _selectedProfile.asStateFlow()
    /** 选中 Profile 变化的监听者（setter 同步通知，调用线程即回调线程） */
    private val selectedProfileListeners = mutableListOf<Runnable>()
    private val _selectedVersion = MutableStateFlow<String?>(null)
    /** 当前选中 Profile 的选中版本（Repository 单例状态，Java 侧访问 getSelectedVersionFlow()） */
    @get:JvmName("getSelectedVersionFlow")
    @JvmStatic
    val selectedVersion: StateFlow<String?> = _selectedVersion.asStateFlow()
    private var selectedVersionProfile: Profile? = null
    private var selectedVersionListener: Runnable? = null
    private val _versionsRefreshed = MutableStateFlow(0)
    /**
     * 选中 Profile 的版本列表刷新信号（tick）：RefreshedVersionsEvent 的响应式出口，
     * 页面/ViewModel collect 此流即可在版本数据变化时自动重载，无需注册监听器
     */
    @get:JvmName("getVersionsRefreshedFlow")
    val versionsRefreshed: StateFlow<Int> = _versionsRefreshed.asStateFlow()

    /** 添加 Profile（触发配置保存、默认补全与选中项校验） */
    @JvmStatic
    fun addProfile(profile: Profile) {
        registerProfileSave(profile)
        profiles.add(profile)
        // 标记实例由本启动器创建：启动路径据此跳过存档扫描。
        // 只在新建时标（init 加载已有实例不走这里），迁移来的实例不会被误标
        runCatching {
            com.kongbai.kculauncher.util.KcuInstanceRegistry.markCreated(
                profile.gameDir.absolutePath, profile.selectedVersion
            )
        }
        onProfilesChanged()
    }

    /** 移除 Profile（触发配置保存、默认补全与选中项校验） */
    @JvmStatic
    fun removeProfile(profile: Profile) {
        profiles.remove(profile)
        onProfilesChanged()
    }

    private fun onProfilesChanged() {
        updateProfileStorages()
        checkProfiles()
        // 列表变化时校验选中项仍在列表中（原 fakefx 列表监听逻辑）
        val current = _selectedProfile.value
        if (current != null && !profiles.contains(current)) {
            setSelectedProfileInternal(profiles[0])
        }
    }

    private fun checkProfiles() {
        if (profiles.isEmpty()) {
            val current = Profile(
                KCUApp.getAppContext().getString(R.string.profile_shared),
                File(KCUPath.SHARED_COMMON_DIR),
                VersionSetting(),
                null
            )
            val home = Profile(
                KCUApp.getAppContext().getString(R.string.profile_private),
                File(KCUPath.PRIVATE_COMMON_DIR)
            )
            registerProfileSave(current)
            registerProfileSave(home)
            profiles.addAll(listOf(current, home))
            // 仅首次启动创建默认实例时标记：此刻实例目录确实由本启动器建立。
            // 不能对配置里加载出来的实例做标记 —— 那些可能来自上游迁移，
            // 误标会让其永久跳过存档检测
            runCatching {
                com.kongbai.kculauncher.util.KcuInstanceRegistry.markCreated(
                    current.gameDir.absolutePath, current.selectedVersion
                )
                com.kongbai.kculauncher.util.KcuInstanceRegistry.markCreated(
                    home.gameDir.absolutePath, home.selectedVersion
                )
            }
        }
    }

    /** 字段变化（全局设置/选中版本/目录/名称）时触发配置保存 */
    private fun registerProfileSave(profile: Profile) {
        profile.onChanged = { updateProfileStorages() }
    }

    private fun updateProfileStorages() {
        // don't update the underlying storage before data loading is completed
        // otherwise it might cause data loss
        if (!initialized) return
        // update storage
        val newConfigurations = TreeMap<String, Profile>()
        for (profile in profiles) {
            newConfigurations[profile.name] = profile
        }
        ConfigHolder.config().configurations.value =
            FXCollections.observableMap(newConfigurations)
    }

    @JvmStatic
    fun init() {
        if (initialized) return

        val names = HashSet<String>()
        ConfigHolder.config().configurations.forEach { (name, profile) ->
            if (!names.add(name)) return@forEach
            profile.name = name
            registerProfileSave(profile)
            profiles.add(profile)
        }
        checkProfiles()

        initialized = true
        val profile =
            profiles.find { it.name == ConfigHolder.config().selectedProfile } ?: profiles[0]
        profile.repository.refreshVersions()
        setSelectedProfileInternal(profile)
        holder.add(
            EventBus.EVENT_BUS.channel(RefreshedVersionsEvent::class.java)
                .registerWeak { event ->
                    val profile = _selectedProfile.value ?: return@registerWeak
                    if (profile.repository === event!!.getSource()) {
                        bindSelectedVersion(profile)
                        _versionsRefreshed.value += 1
                    }
                }
        )
        isFirstRefresh = false
    }

    /** 设置选中 Profile（统一走校验、保存与版本绑定逻辑，同步通知监听者） */
    private fun setSelectedProfileInternal(profile: Profile) {
        if (_selectedProfile.value === profile) return
        _selectedProfile.value = profile
        if (!initialized) return
        if (!profiles.contains(profile)) {
            _selectedProfile.value = profiles[0]
        } else {
            ConfigHolder.config().selectedProfile = profile.name
            profile.gameDir.resolve(".nomedia").let {
                if (!it.exists()) {
                    runCatching {
                        it.parentFile?.mkdirs()
                        it.createNewFile()
                    }
                }
            }
            if (profile.repository.isLoaded) {
                bindSelectedVersion(profile)
            } else {
                unbindSelectedVersion()
                // bind when repository was reloaded.
//                    profile.repository.refreshVersionsAsync().start()
            }
        }
        // 复制后遍历：回调内可能增删监听，避免并发修改
        selectedProfileListeners.toList().forEach { listener -> listener.run() }
        // 仅未加载版本的 Profile 切换时才刷新（refreshVersions 会清空解析/jar 缓存，
        // 已加载的 Profile 保留缓存以加快切换；版本变化由刷新事件与手动刷新驱动）
        if (!isFirstRefresh && !profile.repository.isLoaded) {
            profile.repository.refreshVersionsAsync().start()
        }
    }

    @JvmStatic
    fun getSelectedProfile(): Profile {
        checkProfiles()
        return _selectedProfile.value ?: profiles[0]
    }

    @JvmStatic
    fun setSelectedProfile(profile: Profile) {
        setSelectedProfileInternal(profile)
    }

    /** 注册选中 Profile 变化监听（Java 友好，内部基于 StateFlow collect） */
    @JvmStatic
    fun addSelectedProfileListener(listener: Runnable) {
        selectedProfileListeners.add(listener)
    }

    @JvmStatic
    fun removeSelectedProfileListener(listener: Runnable) {
        selectedProfileListeners.remove(listener)
    }

    /** 跟随指定 Profile 的版本属性（原 fakefx bind 语义） */
    private fun bindSelectedVersion(profile: Profile) {
        selectedVersionProfile?.let { old ->
            selectedVersionListener?.let { old.removeSelectedVersionListener(it) }
        }
        val listener = Runnable {
            _selectedVersion.value = profile.selectedVersion
        }
        selectedVersionListener = listener
        selectedVersionProfile = profile
        profile.addSelectedVersionListener(listener)
        _selectedVersion.value = profile.selectedVersion
    }

    private fun unbindSelectedVersion() {
        selectedVersionProfile?.let { old ->
            selectedVersionListener?.let { old.removeSelectedVersionListener(it) }
        }
        selectedVersionProfile = null
        selectedVersionListener = null
        _selectedVersion.value = null
    }

    // Guaranteed that the repository is loaded.
    @JvmStatic
    fun getSelectedVersion(): String? {
        return _selectedVersion.value
    }

}
