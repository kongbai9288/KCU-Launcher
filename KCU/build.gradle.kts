import com.android.build.api.variant.FilterConfiguration.FilterType.ABI
import com.android.build.gradle.tasks.MergeSourceSetFolders
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    id("checkstyle")
}

checkstyle {
    // 规则集 config/checkstyle/checkstyle.xml 与 Android Studio 默认格式对齐，仅检查 Java 代码
    toolVersion = "10.12.5"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
}

// AGP 不提供 Java 插件的 SourceSetContainer，checkstyle 插件不会自动创建任务，
// 因此手动注册 checkstyle 任务，检查范围为主源码目录的 Java 文件
tasks.register<Checkstyle>("checkstyle") {
    description = "Run checkstyle on the FCL Java sources."
    group = "verification"
    source(layout.projectDirectory.dir("src/main/java"))
    include("**/*.java")
    classpath = files()
    maxErrors = 0
    maxWarnings = 0
}

android {
    namespace = "com.kongbai.kculauncher"
    compileSdk = libs.versions.compileSdk.get().toInt()

    var localProperty: Properties? = null
    if (file("${rootDir}/local.properties").exists()) {
        localProperty = Properties()
        file("${rootDir}/local.properties").inputStream().use { localProperty.load(it) }
    }
    // KCU：密钥一律来自环境变量或 local.properties，读不到就是空，绝不让构建失败
    val pwd = System.getenv("KCU_KEYSTORE_PASSWORD")
        ?: System.getenv("FCL_KEYSTORE_PASSWORD")
        ?: localProperty?.getProperty("pwd")
    val kcuKeyAlias = System.getenv("KCU_KEY_ALIAS") ?: localProperty?.getProperty("key.alias") ?: "FCL-Key"
    val keyPwd = System.getenv("KCU_KEY_PASSWORD") ?: pwd
    val curseApiKey = System.getenv("CURSE_API_KEY") ?: localProperty?.getProperty("curse.api.key")
    val oauthApiKey = System.getenv("OAUTH_API_KEY") ?: localProperty?.getProperty("oauth.api.key")
    val useReleaseSign = file("${rootDir}/key-store.jks").exists() && !pwd.isNullOrEmpty()
    // 命令行 -Darch 优先；local.properties 仅在命令行未指定时生效
    if (System.getProperty("arch") == null && localProperty != null && localProperty.getProperty(
            "arch",
            "all"
        ) == "arm64"
    )
        System.setProperty("arch", "arm64")

    signingConfigs {
        // KCU：正式签名库缺失时留空，交由 buildTypes 回退到 AGP 默认 debug 签名
        create("FCLKey") {
            if (useReleaseSign) {
                storeFile = file("../key-store.jks")
                storePassword = pwd
                keyAlias = kcuKeyAlias
                keyPassword = keyPwd
                // KCU：签名库为 PKCS12（扩展名沿用 .jks，实际 DER 头 0x30 82），
                // 不显式声明 storeType 时 AGP 按 JKS 解析会失败
                storeType = System.getenv("KCU_STORE_TYPE") ?: "pkcs12"
            }
        }
        create("FCLDebugKey") {
            val dbg = file("../debug-key.jks")
            if (dbg.exists()) {
                storeFile = dbg
                storePassword = "FCL-Debug"
                keyAlias = "FCL-Debug"
                keyPassword = "FCL-Debug"
            }
        }
        // minSdk 26 ≥ 24，v1 已无必要，统一启用 v2 + v3 签名方案
        configureEach {
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.kcu.launcher"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 15
        versionName = "0.15.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }

    testBuildType = "fordebug"

    buildTypes {
        // KCU：签名为空时回退 AGP 默认 debug 签名，产物为未签名/调试签名，构建不失败
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (useReleaseSign) signingConfigs.getByName("FCLKey")
                            else signingConfigs.getByName("debug")
        }
        getByName("debug") {
            isMinifyEnabled = false
            signingConfig = if (useReleaseSign) signingConfigs.getByName("FCLKey")
                            else signingConfigs.getByName("debug")
        }
        create("fordebug") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("FCLDebugKey")
            // 与 FileProvider authority（${applicationId}.provider）保持一致（原 FCLLibrary 模块的 resValue）
            resValue("string", "file_browser_provider", "com.kcu.launcher.debug.provider")
        }
        configureEach {
            resValue("string", "app_version", defaultConfig.versionName.toString())
            // KCU：空则写空串，运行时据此切换国内镜像，不依赖 "null" 字符串比较
            resValue("string", "curse_api_key", curseApiKey ?: "")
            resValue("string", "oauth_api_key", oauthApiKey ?: "")
        }
    }



    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // core library desugaring：java.time / java.util.stream / Optional 等脱糖到 minSdk 26 可用
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            pickFirsts += listOf("**/libbytehook.so")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
        prefab = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
        }
    }

    ndkVersion = "27.0.12077973"

    splits {
        val arch = System.getProperty("arch", "all")
        if (arch != "all") {
            abi {
                isEnable = true
                reset()
                when (arch) {
                    "arm" -> include("armeabi-v7a")
                    "arm64" -> include("arm64-v8a")
                    "x86" -> include("x86")
                    "x86_64" -> include("x86_64")
                }
            }
        }
    }
}

/**
 * 按 -Darch 过滤 JRE 压缩包，生成当前架构需要的资产目录。
 * 非 all 构建只保留 version、universal 和 bin-<arch>.tar.xz（运行时两者都需要，见 RuntimeUtils#installJava）。
 */
abstract class FilterJreAssets : Sync() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty
}

val filterJreAssets = tasks.register<FilterJreAssets>("filterJreAssets") {
    val arch = System.getProperty("arch", "all")
    // Copy/Sync 的 up-to-date 检查不包含 copy spec 的过滤规则，必须显式声明 arch 输入，
    // 否则切换架构时任务不会重跑，产物会残留上一架构的 JRE 包
    inputs.property("arch", arch)
    from(layout.projectDirectory.dir("src/main/jreAssets"))
    into(outputDir)
    if (arch != "all") {
        exclude { it.name.startsWith("bin-") && it.name != "bin-$arch.tar.xz" }
    }
}

androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                (output.getFilter(ABI)?.identifier ?: "all").let { abi ->
                    output.outputFileName =
                        "KCU-${variant.buildType}-${project.android.defaultConfig.versionName}-${abi}.apk"
                }
            }
        }

        // JRE 资产不放在 src/main/assets 里（AGP 的 mergeAssets 不应用 source set 的 exclude 过滤），
        // 而是按架构注册为生成源；arch 变化会让 Sync 任务重新执行，mergeAssets 随之重跑，避免产物残留旧架构文件。
        if (System.getProperty("arch", "all") != "all") {
            variant.sources.assets?.addGeneratedSourceDirectory(filterJreAssets) { it.outputDir }
        } else {
            variant.sources.assets?.addStaticSourceDirectory("src/main/jreAssets")
        }

        // LWJGL natives 打包在 lwjgl-*-natives aar 的 assets/app_runtime/lwjgl/<版本>/natives/<abi> 下，
        // JNA natives 在 assets/app_runtime/jna/<版本>/natives/<abi> 下，
        // 均不走 AGP 的 abiFilters，需在 mergeAssets 后手动按架构删除其他 ABI 的 natives 目录。
        val variantName = variant.name.replaceFirstChar { it.uppercaseChar() }
        afterEvaluate {
            val mergeAssets =
                tasks.named("merge${variantName}Assets", MergeSourceSetFolders::class.java)
            val arch = System.getProperty("arch", "all")
            // 显式声明 arch 输入，arch 变化时任务重跑，避免产物残留上一架构的 natives
            mergeAssets.configure { inputs.property("lwjglArch", arch) }
            mergeAssets.configure {
                doLast {
                    if (arch == "all") return@doLast
                    val abi = when (arch) {
                        "arm" -> "armeabi-v7a"
                        "arm64" -> "arm64-v8a"
                        "x86" -> "x86"
                        "x86_64" -> "x86_64"
                        else -> return@doLast
                    }
                    val assetsDir = outputDir.get().asFile
                    // 版本列表从 libs 下的 lwjgl-*-natives-release.aar 文件名推导，避免新增版本时忘记同步
                    val lwjglVersions = project.file("libs").listFiles { f ->
                        f.name.matches(Regex("lwjgl-\\d+\\.\\d+\\.\\d+-natives-release\\.aar"))
                    }
                        ?.map { Regex("lwjgl-(\\d+\\.\\d+\\.\\d+)-natives-release\\.aar").find(it.name)!!.groupValues[1] }
                        ?: emptyList()
                    lwjglVersions.forEach { version ->
                        val nativesDir = File(assetsDir, "app_runtime/lwjgl/$version/natives")
                        if (nativesDir.isDirectory) {
                            nativesDir.listFiles()?.forEach { dir ->
                                if (dir.isDirectory && dir.name != abi) {
                                    logger.lifecycle("删除非目标架构 natives: $dir")
                                    dir.deleteRecursively()
                                }
                            }
                        }
                    }
                    // JNA natives 在 assets/app_runtime/jna/<版本>/natives/<abi> 下，同样按架构裁剪
                    File(assetsDir, "app_runtime/jna").listFiles()?.forEach { versionDir ->
                        val nativesDir = File(versionDir, "natives")
                        if (nativesDir.isDirectory) {
                            nativesDir.listFiles()?.forEach { dir ->
                                if (dir.isDirectory && dir.name != abi) {
                                    logger.lifecycle("删除非目标架构 natives: $dir")
                                    dir.deleteRecursively()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

// Room schema 导出（收藏库用，为将来迁移留底）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))
    implementation(project(":ZipFileSystem"))
    implementation(project(":Terracotta"))
    implementation(libs.commons.io)
    implementation(libs.jelf)
    implementation(libs.nanohttpd)
    implementation(libs.commons.compress)
    implementation(libs.xz)
    implementation(libs.opennbt)
    implementation(libs.lz4)
    implementation(libs.gson)
    implementation(libs.tomlj)
    implementation(libs.constant.pool.scanner)
    implementation(libs.jsoup)
    implementation(libs.chardet)
    implementation(libs.junrar)
    implementation(libs.bytehook)
    implementation(libs.appcompat)
    implementation(libs.androidx.viewpager2)
    implementation(libs.core.splashscreen)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.recyclerview)
    implementation(libs.coroutines.android)
    implementation(libs.glide)
    implementation(libs.touchcontroller)
    implementation(libs.palette.ktx)
    implementation(libs.gamepad.remapper)
    implementation(libs.segmented.button)
    implementation(libs.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

tasks.register("updateMap") {
    doLast {
        val list = mutableListOf<String>()
        val mapFile = file("${rootDir}/version_map.json")
        mapFile.forEachLine {
            list.add(
                when {
                    it.contains("versionCode") -> it.replace(
                        Regex("[0-9]+"),
                        android.defaultConfig.versionCode.toString()
                    )

                    it.contains("versionName") -> it.replace(
                        Regex("\\d+(\\.\\d+)+"),
                        android.defaultConfig.versionName.toString()
                    )

                    it.contains("date") -> it.replace(
                        Regex("\\d{4}\\.\\d{2}\\.\\d{2}"),
                        SimpleDateFormat("yyyy.MM.dd").format(Date())
                    )

                    it.contains("url") -> it.replace(
                        Regex("\\d+(\\.\\d+)+"),
                        android.defaultConfig.versionName.toString()
                    )

                    else -> it
                }
            )
        }
        mapFile.writeText(list.joinToString("\n"), Charsets.UTF_8)
    }
}