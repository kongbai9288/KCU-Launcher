package com.kongbai.kculauncher.ui.manage;

import static com.kongbai.kculauncher.setting.ConfigHolder.config;
import com.kongbai.kculauncher.ui.UIManager;

import android.content.Context;
import android.view.View;
import android.widget.Toast;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.activity.MainActivity;
import com.kongbai.kculauncher.setting.Accounts;
import com.kongbai.kculauncher.setting.Profile;
import com.kongbai.kculauncher.setting.VersionSetting;
import com.kongbai.kculauncher.core.auth.Account;
import com.kongbai.kculauncher.core.auth.authlibinjector.AuthlibInjectorServer;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleBooleanProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleIntegerProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleStringProperty;
import com.kongbai.kculauncher.core.mod.ModAdviser;
import com.kongbai.kculauncher.core.mod.ModpackExportInfo;
import com.kongbai.kculauncher.core.mod.mcbbs.McbbsModpackManifest;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.util.Lang;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.core.util.platform.OperatingSystem;
import com.kongbai.kculauncher.library.component.ui.KCUPage;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUEditText;
import com.kongbai.kculauncher.library.component.view.KCUImageButton;
import com.kongbai.kculauncher.library.component.view.KCULinearLayout;
import com.kongbai.kculauncher.library.component.view.KCUNumberSeekBar;
import com.kongbai.kculauncher.library.component.view.KCUSpinner;
import com.kongbai.kculauncher.library.component.view.KCUSwitch;
import com.kongbai.kculauncher.library.component.view.KCUTextView;
import com.kongbai.kculauncher.library.component.view.KCUUILayout;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ModpackInfoPage extends KCUPage implements View.OnClickListener {

    private final Profile profile;
    private final String versionName;
    private final String type;
    private final ModpackExportInfo.Options options;

    private final ModpackExportInfo exportInfo = new ModpackExportInfo();

    private final SimpleStringProperty path = new SimpleStringProperty("");
    private final SimpleStringProperty fileName = new SimpleStringProperty("");

    private final SimpleStringProperty name = new SimpleStringProperty("");
    private final SimpleStringProperty author = new SimpleStringProperty("");
    private final SimpleStringProperty version = new SimpleStringProperty("1.0");
    private final SimpleStringProperty description = new SimpleStringProperty("");
    private final SimpleStringProperty url = new SimpleStringProperty("");
    private final SimpleBooleanProperty forceUpdate = new SimpleBooleanProperty();
    private final SimpleBooleanProperty packCurseForge = new SimpleBooleanProperty(true);
    private final SimpleStringProperty fileApi = new SimpleStringProperty("");
    private final SimpleIntegerProperty minMemory = new SimpleIntegerProperty(0);
    private final SimpleStringProperty authlibInjectorServer = new SimpleStringProperty();
    private final SimpleStringProperty launchArguments = new SimpleStringProperty("");
    private final SimpleStringProperty javaArguments = new SimpleStringProperty("");
    private final SimpleStringProperty mcbbsThreadId = new SimpleStringProperty("");

    private KCUImageButton pathButton;
    private KCUButton next;

    public ModpackInfoPage(Context context, int id, Profile profile, String version, String type, ModpackExportInfo.Options options) {
        super(context, id, R.layout.page_modpack_info);
        this.profile = profile;
        this.versionName = version;
        this.type = type;
        this.options = options;

        name.set(version);
        fileName.set(version);
        author.set(Optional.ofNullable(Accounts.getSelectedAccount()).map(Account::getCharacter).orElse(""));

        VersionSetting versionSetting = profile.getRepository().getVersionSetting(versionName);
        minMemory.set(Optional.ofNullable(versionSetting.getMinMemory()).orElse(0));
        launchArguments.set(versionSetting.getMinecraftArgs());
        javaArguments.set(versionSetting.getJavaArgs());

        // 原 onStart 逻辑：页面构造即绑定控件（findViewById 在 super 构造中已完成）
        KCULinearLayout fileApiLayout = findViewById(R.id.file_api_layout);
        KCULinearLayout launchArgsLayout = findViewById(R.id.minecraft_args_layout);
        KCULinearLayout jvmArgsLayout = findViewById(R.id.jvm_args_layout);
        KCULinearLayout originUrlLayout = findViewById(R.id.origin_url_layout);
        KCULinearLayout mcbbsLayout = findViewById(R.id.mcbbs_layout);
        KCULinearLayout memoryLayout = findViewById(R.id.memory_layout);
        KCULinearLayout serverLayout = findViewById(R.id.server_layout);
        KCULinearLayout forceUpdateLayout = findViewById(R.id.force_update_layout);
        KCULinearLayout packCurseForgeLayout = findViewById(R.id.pack_curseforge_layout);
        View splitF = findViewById(R.id.split_1);
        View splitS = findViewById(R.id.split_2);
        View splitT = findViewById(R.id.split_3);

        KCUTextView versionNameText = findViewById(R.id.game_version);
        KCUEditText nameText = findViewById(R.id.name);
        KCUEditText authorText = findViewById(R.id.author);
        KCUEditText versionText = findViewById(R.id.version);
        KCUEditText fileApiText = findViewById(R.id.file_api);
        KCUEditText launchArgsText = findViewById(R.id.minecraft_args);
        KCUEditText jvmArgsText = findViewById(R.id.jvm_args);
        KCUEditText originUrlText = findViewById(R.id.origin_url);
        KCUEditText mcbbsText = findViewById(R.id.mcbbs);
        KCUNumberSeekBar memorySeekbar = findViewById(R.id.memory);
        KCUEditText descText = findViewById(R.id.desc);
        KCUSpinner<String> serverSpinner = findViewById(R.id.server);
        KCUSwitch forceUpdateSwitch = findViewById(R.id.force_update);
        KCUSwitch packCurseForgeSwitch = findViewById(R.id.pack_curseforge);
        KCUTextView pathText = findViewById(R.id.path_text);
        KCUTextView extensionText = findViewById(R.id.file_extension);
        extensionText.setText(ModpackTypeSelectionPage.getModpackFileExtension(type));
        pathButton = findViewById(R.id.path);
        KCUEditText fileNameText = findViewById(R.id.file_name);
        next = findViewById(R.id.next);

        versionNameText.setText(versionName);
        nameText.setText(name.get());
        nameText.stringProperty().bindBidirectional(name);
        authorText.setText(author.get());
        authorText.stringProperty().bindBidirectional(author);
        versionText.setText(this.version.get());
        versionText.stringProperty().bindBidirectional(this.version);
        if (options.isRequireFileApi()) {
            if (options.isValidateFileApi()) {
                fileApiText.setHint(getContext().getString(R.string.input_hint_not_empty));
            } else {
                fileApiText.setHint("");
            }
            fileApiText.stringProperty().bindBidirectional(fileApi);
        }
        fileApiLayout.setVisibility(options.isRequireFileApi() ? View.VISIBLE : View.GONE);
        if (options.isRequireLaunchArguments()) {
            launchArgsText.setText(launchArguments.get());
            launchArgsText.stringProperty().bindBidirectional(launchArguments);
        }
        launchArgsLayout.setVisibility(options.isRequireLaunchArguments() ? View.VISIBLE : View.GONE);
        if (options.isRequireJavaArguments()) {
            jvmArgsText.setText(javaArguments.get());
            jvmArgsText.stringProperty().bindBidirectional(javaArguments);
        }
        jvmArgsLayout.setVisibility(options.isRequireJavaArguments() ? View.VISIBLE : View.GONE);
        if (options.isRequireUrl()) {
            originUrlText.stringProperty().bindBidirectional(url);
        }
        originUrlLayout.setVisibility(options.isRequireUrl() ? View.VISIBLE : View.GONE);
        if (options.isRequireOrigins()) {
            mcbbsText.stringProperty().bindBidirectional(mcbbsThreadId);
        }
        mcbbsLayout.setVisibility(options.isRequireOrigins() ? View.VISIBLE : View.GONE);
        if (options.isRequireMinMemory()) {
            memorySeekbar.setProgress(minMemory.get());
            memorySeekbar.addProgressListener();
            memorySeekbar.progressProperty().bindBidirectional(minMemory);
        }
        memoryLayout.setVisibility(options.isRequireMinMemory() ? View.VISIBLE : View.GONE);
        splitF.setVisibility(options.isRequireMinMemory() ? View.VISIBLE : View.GONE);
        descText.stringProperty().bindBidirectional(description);
        if (options.isRequireAuthlibInjectorServer()) {
            ArrayList<String> list = (ArrayList<String>) config().getAuthlibInjectorServers().stream().map(AuthlibInjectorServer::getName).collect(Collectors.toList());
            Map<String, String> map = new HashMap<>();
            list.add(0, "");
            map.put("", null);
            config().getAuthlibInjectorServers().forEach(it -> map.put(it.getName(), it.getUrl()));
            serverSpinner.setItems(list);
            serverSpinner.setOnItemSelectedListener((index, item) -> authlibInjectorServer.set(map.get(item)));
        }
        serverLayout.setVisibility(options.isRequireAuthlibInjectorServer() ? View.VISIBLE : View.GONE);
        splitS.setVisibility(options.isRequireAuthlibInjectorServer() ? View.VISIBLE : View.GONE);
        if (options.isRequireForceUpdate()) {
            forceUpdateSwitch.addCheckedChangeListener();
            forceUpdateSwitch.checkProperty().bindBidirectional(forceUpdate);
        }
        forceUpdateLayout.setVisibility(options.isRequireForceUpdate() ? View.VISIBLE : View.GONE);
        splitT.setVisibility(options.isRequireForceUpdate() ? View.VISIBLE : View.GONE);
        if (options.isRequirePackCurseForge()) {
            packCurseForgeSwitch.addCheckedChangeListener();
            packCurseForgeSwitch.checkProperty().bindBidirectional(packCurseForge);
        }
        packCurseForgeLayout.setVisibility(options.isRequirePackCurseForge() ? View.VISIBLE : View.GONE);
        pathText.stringProperty().bind(path);
        pathButton.setOnClickListener(this);
        fileNameText.setText(fileName.get());
        fileNameText.stringProperty().bindBidirectional(fileName);
        next.setOnClickListener(this);
    }


    private void selectPath() {
        MainActivity.getInstance().fileLauncher.launchSingleSelection(null, null, true, files -> {
            if (files == null) return;
            path.set(files.get(0).getPath());
        });
    }

    @Override
    public void onClick(View v) {
        if (v == pathButton) {
            selectPath();
        }
        if (v == next) {
            boolean urlValid = false;
            if (StringUtils.isNotBlank(fileApi.get())) {
                try {
                    new URL(fileApi.get()).toURI();
                    urlValid = true;
                } catch (IOException | URISyntaxException ignored) {
                }
            }
            if (StringUtils.isBlank(name.get()) || StringUtils.isBlank(author.get()) || StringUtils.isBlank(version.get()) || StringUtils.isBlank(fileName.get())
                    || (options.isRequireFileApi() && options.isValidateFileApi() && StringUtils.isBlank(fileApi.get()))) {
                Toast.makeText(getContext(), getContext().getString(R.string.input_not_empty), Toast.LENGTH_SHORT).show();
            } else if (options.isRequireFileApi() && StringUtils.isNotBlank(fileApi.get()) && !urlValid) {
                Toast.makeText(getContext(), getContext().getString(R.string.input_url), Toast.LENGTH_SHORT).show();
            } else if (options.isRequireOrigins() && StringUtils.isNotBlank(mcbbsThreadId.get()) && Lang.toIntOrNull(mcbbsThreadId.get()) == null) {
                Toast.makeText(getContext(), getContext().getString(R.string.input_number), Toast.LENGTH_SHORT).show();
            } else if (!OperatingSystem.isNameValid(fileName.get())) {
                Toast.makeText(getContext(), getContext().getString(R.string.install_new_game_malformed), Toast.LENGTH_SHORT).show();
            } else if (StringUtils.isBlank(path.get())) {
                selectPath();
            } else {
                File file = new File(path.get(), fileName.get() + ModpackTypeSelectionPage.getModpackFileExtension(type));

                if (file.exists()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.message_file_exist), Toast.LENGTH_SHORT).show();
                    return;
                }

                exportInfo.setName(name.get());
                exportInfo.setFileApi(fileApi.get());
                exportInfo.setVersion(version.get());
                exportInfo.setAuthor(author.get());
                exportInfo.setDescription(description.get());
                exportInfo.setPackWithLauncher(false);
                exportInfo.setUrl(url.get());
                exportInfo.setForceUpdate(forceUpdate.get());
                exportInfo.setPackCurseForge(packCurseForge.get());
                exportInfo.setMinMemory(minMemory.get());
                exportInfo.setLaunchArguments(launchArguments.get());
                exportInfo.setJavaArguments(javaArguments.get());
                exportInfo.setAuthlibInjectorServer(authlibInjectorServer.get());

                if (StringUtils.isNotBlank(mcbbsThreadId.get())) {
                    exportInfo.setOrigins(Collections.singletonList(new McbbsModpackManifest.Origin(
                            "mcbbs", Integer.parseInt(mcbbsThreadId.get())
                    )));
                }

                ModpackFileSelectionPage page = new ModpackFileSelectionPage(getContext(), KCUPage.PAGE_ID_TEMP, profile, versionName, type, ModAdviser::suggestMod, exportInfo, file);
                UIManager.getInstance().getManageUI().showTempPage(page);
            }
        }
    }
}
