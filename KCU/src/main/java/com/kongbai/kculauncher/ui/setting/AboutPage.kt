package com.kongbai.kculauncher.ui.setting

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.kongbai.kculauncher.mio.ui.adapter.SpacingItemDecoration
import com.kongbai.kculauncher.mio.util.openLink
import com.kongbai.kculauncher.activity.KcuLicenseActivity
import com.kongbai.kculauncher.util.KcuDevMode
import com.kongbai.kculauncher.util.KcuUi
import com.kongbai.kculauncher.R
import com.kongbai.kculauncher.databinding.ItemAboutBinding
import com.kongbai.kculauncher.databinding.ItemAboutDescBinding
import com.kongbai.kculauncher.databinding.PageSettingListBinding
import com.kongbai.kculauncher.core.task.Task
import com.kongbai.kculauncher.library.component.theme.ThemeEngine
import com.kongbai.kculauncher.library.component.ui.KCUPage

/**
 * 关于页。
 *
 * 顶部是本应用卡片：图标、名称、版本、系统信息、更新入口。
 * 其下是开发者模块，每人一行（图标 + 名称 + 主页）。
 * 图标一律用本地字符徽章，整页不产生任何网络图片请求。
 */
class AboutPage(context: Context?, id: Int) : KCUPage(context, id, R.layout.page_setting_list) {

    private lateinit var binding: PageSettingListBinding

    private var devTaps: Int = 0

    init {
        create()
    }

    private fun create() {
        binding = PageSettingListBinding.bind(contentView)
        binding.settingList.layoutManager = LinearLayoutManager(context)
        val rowSpacing = (8 * context.resources.displayMetrics.density).toInt()
        val groupDivider = (1 * context.resources.displayMetrics.density).toInt()
        binding.settingList.addItemDecoration(
            SpacingItemDecoration(
                rowSpacing,
                { parent, position ->
                    val adapter = parent.adapter as? AboutAdapter
                    if (adapter?.isNextInSameGroup(position) == true) groupDivider else rowSpacing
                },
                true,
                { ThemeEngine.getInstance().getTheme().getColor() }
            )
        )
        ThemeEngine.getInstance().registerEvent(binding.settingList) {
            binding.settingList.invalidate()
        }
        binding.settingList.adapter = AboutAdapter(
            onLinkClick = { openLinkAt(it) },
            onDevClick = { onDevTap() },
            onUpdateClick = { openReleases() }
        )
    }

    private fun openReleases() {
        openLink(context, RELEASES_URL)
    }

    /**
     * 连点达到阈值才写入请求文件。
     *
     * 请求文件本身不足以开启开发者模式 —— 它只负责让下次进入应用时弹出令牌框，
     * 真正开启要凭令牌校验归属。因此这里不必担心误触。
     */
    private fun onDevTap() {
        if (KcuDevMode.isEnabled()) {
            return
        }
        devTaps++
        val left = KcuDevMode.UNLOCK_TAPS - devTaps
        if (left > 0) {
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.kcu_dev_tap_hint, left),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        devTaps = 0
        KcuDevMode.markRequested()
        android.widget.Toast.makeText(
            context, R.string.kcu_dev_requested, android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    private fun openLinkAt(linkIndex: Int) {
        when (val link = LINKS[linkIndex].link) {
            null -> joinQQGroup(QQ_GROUP_KEY)
            LICENSE_MARK -> context.startActivity(
                Intent(context, KcuLicenseActivity::class.java)
            )
            else -> openLink(context, link)
        }
    }

    private fun joinQQGroup(key: String) {
        val intent = Intent().apply {
            data = Uri.parse(
                "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26jump_from%3Dwebapi%26k%3D" + key
            )
        }
        try {
            context.startActivity(intent)
        } catch (ignored: Exception) {
        }
    }

    /** 本应用卡片用到的信息，单独取值便于在构造失败时给出占位而不崩 */
    private fun appVersionName(): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    context.packageName, PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "-"
        } catch (e: Exception) {
            "-"
        }
    }

    /** 架构与 Android 版本：玩家排查渲染器和模组兼容性时真正需要的两项 */
    private fun systemInfo(): String {
        val abi = if (Build.SUPPORTED_ABIS.isNullOrEmpty()) "-" else Build.SUPPORTED_ABIS[0]
        return "$abi  ·  Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }

    private data class LinkItem(val glyph: String, val title: String, val link: String?)

    private inner class AboutAdapter(
        private val onLinkClick: (Int) -> Unit,
        private val onDevClick: () -> Unit,
        private val onUpdateClick: () -> Unit
    ) : RecyclerView.Adapter<AboutAdapter.Holder>() {

        override fun getItemCount(): Int = 1 + DEVS.size + 1 + LINKS.size

        override fun getItemViewType(position: Int): Int = when {
            position == 0 -> TYPE_APP
            position <= DEVS.size -> TYPE_DEV
            position == DEVS.size + 1 -> TYPE_DESC
            else -> TYPE_LINK
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val inflater = LayoutInflater.from(parent.context)
            val item: View = when (viewType) {
                TYPE_APP -> buildAppCard(parent.context)
                TYPE_DESC -> ItemAboutDescBinding.inflate(inflater, parent, false).root
                else -> ItemAboutBinding.inflate(inflater, parent, false).root
            }
            return Holder(item)
        }

        /** 本应用卡片：图标徽章 + 名称 + 版本 + 系统信息 + 更新按钮 */
        private fun buildAppCard(ctx: Context): View {
            val card = KcuUi.card(ctx)
            card.layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            val head = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            head.addView(KcuUi.iconBadge(ctx, "K"))
            val texts = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                lp.marginStart = KcuUi.dp(ctx, 14f)
                layoutParams = lp
            }
            texts.addView(KcuUi.title(ctx, ctx.getString(R.string.kcu_about_app_name)))
            texts.addView(KcuUi.hint(ctx, ctx.getString(R.string.kcu_about_version, appVersionName())))
            texts.addView(KcuUi.hint(ctx, systemInfo()))
            head.addView(texts)
            card.addView(head)
            card.addView(KcuUi.gap(ctx, 14))
            val update = KcuUi.primaryButton(ctx, ctx.getString(R.string.kcu_about_update))
            update.setOnClickListener { onUpdateClick() }
            card.addView(update)
            return card
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val last = itemCount - 1
            holder.itemView.setBackgroundResource(
                when (position) {
                    0 -> R.drawable.bg_item_rounded
                    1 -> R.drawable.bg_item_rounded_top
                    last -> R.drawable.bg_item_rounded_bottom
                    else -> R.drawable.bg_item_rounded_middle
                }
            )
            ThemeEngine.getInstance().unregisterEvent(holder.itemView)
            ThemeEngine.getInstance().registerEvent(holder.itemView) {
                holder.itemView.backgroundTintList =
                    ColorStateList.valueOf(ThemeEngine.getInstance().getTheme().ltColor)
            }

            when (getItemViewType(position)) {
                TYPE_APP -> holder.itemView.setOnClickListener(null)
                TYPE_DESC -> {
                    holder.itemView.setOnClickListener(null)
                    ItemAboutDescBinding.bind(holder.itemView).title.setText(R.string.about_desc)
                }
                TYPE_DEV -> {
                    val dev = DEVS[position - 1]
                    holder.itemView.setOnClickListener {
                        val target = dev.link
                        if (target != null) {
                            openLink(context, target)
                        }
                    }
                    val row = ItemAboutBinding.bind(holder.itemView)
                    row.title.text = dev.title
                    row.icon.text = dev.glyph
                    row.icon.background = KcuUi.badgeBackground()
                }
                else -> {
                    val linkIndex = position - DEVS.size - 2
                    holder.itemView.setOnClickListener {
                        if (LINKS[linkIndex].link == DEV_MARK) {
                            onDevClick()
                        } else {
                            onLinkClick(linkIndex)
                        }
                    }
                    val item = LINKS[linkIndex]
                    val row = ItemAboutBinding.bind(holder.itemView)
                    row.title.text = item.title
                    row.icon.text = item.glyph
                    row.icon.background = KcuUi.badgeBackground()
                }
            }
        }

        /** 供间距装饰器判断：组内相邻时行间留 1dp 缝（绘制分割线） */
        fun isNextInSameGroup(position: Int): Boolean =
            position > 0 && position + 1 < itemCount
    }

    /**
     * 必须定义在适配器之外：Kotlin 不允许在 inner class 内部再声明嵌套类。
     * 适配器之所以是 inner，是因为构造应用卡片要读本页的版本与系统信息。
     */
    private class Holder(itemView: View) : RecyclerView.ViewHolder(itemView)

    companion object {
        private const val TYPE_APP = 0
        private const val TYPE_DEV = 1
        private const val TYPE_DESC = 2
        private const val TYPE_LINK = 3

        private const val QQ_GROUP_KEY = "9_Mnxe5x1l6L7giLuRYQyBh0iWBgCUbw"
        private const val LICENSE_MARK = "kcu://licenses"
        private const val DEV_MARK = "kcu://dev"
        private const val RELEASES_URL = "https://github.com/kongbai9288/KCU-Launcher/releases"

        /** 开发者模块：图标一律本地字符徽章，不引用任何网络图片 */
        private val DEVS = listOf(
            LinkItem("K", "kongbai9288（架构师）", "https://github.com/kongbai9288"),
            LinkItem("F", "FCL-Team", "https://github.com/FCL-Team"),
            LinkItem("P", "PojavLauncher", "https://github.com/PojavLauncherTeam/PojavLauncher"),
            LinkItem("H", "HMCL", "https://github.com/HMCL-dev/HMCL")
        )

        private val LINKS = listOf(
            LinkItem("\u2261", "源代码仓库", "https://github.com/kongbai9288/KCU-Launcher"),
            LinkItem("\u25CF", "QQ 交流群", null),

            LinkItem("F", "Fold Craft Launcher（上游）", "https://github.com/FCL-Team/FoldCraftLauncher"),
            LinkItem("J", "Android-OpenJDK-Build", "https://github.com/HMCL-dev/Android-OpenJDK-Build"),
            LinkItem("S", "SDL3", "https://github.com/libsdl-org/SDL"),
            LinkItem("L", "LWJGL", "https://github.com/LWJGL/lwjgl3"),
            LinkItem("M", "MobileGlues", "https://github.com/MobileGL-Dev/MobileGlues"),

            LinkItem("\u00A7", "开源许可", LICENSE_MARK),
            LinkItem("\u2699", "开发者", DEV_MARK)
        )
    }
}
