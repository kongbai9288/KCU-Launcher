package com.kongbai.kculauncher.ui.manage;

import android.content.Context;
import android.view.View;
import android.widget.ListView;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.activity.MainActivity;
import com.kongbai.kculauncher.launcher.utils.KCUPath;
import com.kongbai.kculauncher.core.fakefx.beans.binding.Bindings;
import com.kongbai.kculauncher.core.fakefx.beans.property.BooleanProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.ListProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleListProperty;
import com.kongbai.kculauncher.core.fakefx.collections.FXCollections;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableList;
import com.kongbai.kculauncher.core.mod.Datapack;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.core.task.Task;
import com.kongbai.kculauncher.core.util.Logging;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.core.util.fakefx.MappedObservableList;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;
import com.kongbai.kculauncher.library.component.ui.KCUPage;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUProgressBar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class DatapackListPage extends KCUPage implements View.OnClickListener {

    private final ListProperty<DatapackInfoObject> itemsProperty = new SimpleListProperty<>(FXCollections.observableArrayList());

    private final Path worldDir;
    private final Datapack datapack;

    private KCUButton deleteButton;
    private KCUButton enableButton;
    private KCUButton disableButton;
    private KCUButton addButton;
    private KCUButton refreshButton;
    private KCUProgressBar progressBar;
    private ListView listView;
    private final DatapackListAdapter adapter;

    public DatapackListPage(Context context, int id, String worldName, Path worldDir) {
        super(context, id, R.layout.page_datapack_list);
        this.worldDir = worldDir;

        datapack = new Datapack(worldDir.resolve("datapacks"));

        // 原 onStart 逻辑：页面构造即初始化列表并刷新
        adapter = new DatapackListAdapter(getContext());
        listView.setAdapter(adapter);
        Bindings.bindContent(adapter.listProperty(), itemsProperty);

        refresh();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        deleteButton = findViewById(R.id.delete);
        enableButton = findViewById(R.id.enable);
        disableButton = findViewById(R.id.disable);
        addButton = findViewById(R.id.add);
        refreshButton = findViewById(R.id.refresh);
        progressBar = findViewById(R.id.progress);
        listView = findViewById(R.id.list);

        deleteButton.setOnClickListener(this);
        enableButton.setOnClickListener(this);
        disableButton.setOnClickListener(this);
        addButton.setOnClickListener(this);
        refreshButton.setOnClickListener(this);
    }


    @Override
    public void onClick(View v) {
        if (v == deleteButton) {
            KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
            builder.setCancelable(false);
            builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
            builder.setMessage(getContext().getString(R.string.button_remove_confirm));
            builder.setPositiveButton(getContext().getString(R.string.button_remove), () -> removeSelected(adapter.selectedItemsProperty()));
            builder.setNegativeButton(null);
            builder.create().show();
        }
        if (v == enableButton) {
            enableSelected(adapter.selectedItemsProperty());
        }
        if (v == disableButton) {
            disableSelected(adapter.selectedItemsProperty());
        }
        if (v == addButton) {
            add();
        }
        if (v == refreshButton) {
            refresh();
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        listView.setVisibility(loading ? View.GONE : View.VISIBLE);
        deleteButton.setEnabled(!loading);
        enableButton.setEnabled(!loading);
        disableButton.setEnabled(!loading);
        addButton.setEnabled(!loading);
        refreshButton.setEnabled(!loading);
    }

    private void installSingleDatapack(File datapack) throws IOException {
        Datapack zip = new Datapack(datapack.toPath());
        zip.loadFromZip();
        zip.installTo(worldDir);
    }

    boolean first = true;

    public void refresh() {
        setLoading(true);
        adapter.selectedItemsProperty().clear();
        Task.runAsync(datapack::loadFromDir)
                .withRunAsync(Schedulers.androidUIThread(), () -> {
                    if (first) {
                        itemsProperty.set(MappedObservableList.create(datapack.getInfo(), DatapackInfoObject::new));
                        first = false;
                    }
                    setLoading(false);
                })
                .start();
    }

    public void add() {
        ArrayList<String> suffix = new ArrayList<>();
        suffix.add(".zip");
        MainActivity.getInstance().fileLauncher.launchMultiSelection(null, suffix, files -> {
            if (files == null) return;
            List<File> res = files.stream()
                    .map(f -> f.toFile(getContext(), new File(KCUPath.CACHE_DIR)))
                    .collect(Collectors.toList());
            KCUAlertDialog.Builder builder1 = new KCUAlertDialog.Builder(getContext());
            builder1.setCancelable(false);
            builder1.setAlertLevel(KCUAlertDialog.AlertLevel.INFO);
            builder1.setMessage(getContext().getString(R.string.datapack_add));
            KCUAlertDialog installDialog = builder1.create();
            installDialog.show();
            new Thread(() -> {
                res.forEach(it -> {
                    try {
                        installSingleDatapack(it);
                    } catch (IOException e) {
                        Logging.LOG.log(Level.WARNING, "Unable to parse datapack file " + datapack, e);
                    }
                });
                Schedulers.androidUIThread().execute(() -> {
                    installDialog.dismiss();
                    refresh();
                });
            }).start();
        });
    }

    void removeSelected(ObservableList<DatapackInfoObject> selectedItems) {
        ObservableList<DatapackInfoObject> items = FXCollections.observableArrayList();
        items.setAll(selectedItems);
        items.stream()
                .map(DatapackInfoObject::getPackInfo)
                .forEach(pack -> {
                    try {
                        datapack.deletePack(pack);
                    } catch (IOException e) {
                        // Fail to remove mods if the game is running or the datapack is absent.
                        Logging.LOG.warning("Failed to delete datapack " + pack);
                    }
                });
    }

    void enableSelected(ObservableList<DatapackInfoObject> selectedItems) {
        selectedItems.stream()
                .map(DatapackInfoObject::getPackInfo)
                .forEach(info -> info.setActive(true));
    }

    void disableSelected(ObservableList<DatapackInfoObject> selectedItems) {
        selectedItems.stream()
                .map(DatapackInfoObject::getPackInfo)
                .forEach(info -> info.setActive(false));
    }

    public static class DatapackInfoObject {
        private final BooleanProperty active;
        private final Datapack.Pack packInfo;

        DatapackInfoObject(Datapack.Pack packInfo) {
            this.packInfo = packInfo;
            this.active = packInfo.activeProperty();
        }

        public BooleanProperty getActive() {
            return active;
        }

        String getTitle() {
            return packInfo.getId();
        }

        String getSubtitle() {
            return StringUtils.parseColorEscapes(packInfo.getDescription().toString());
        }

        Datapack.Pack getPackInfo() {
            return packInfo;
        }
    }
}
