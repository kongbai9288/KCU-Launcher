package com.kongbai.kculauncher.ui.controller;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.appcompat.widget.LinearLayoutCompat;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.activity.MainActivity;
import com.kongbai.kculauncher.setting.Controller;
import com.kongbai.kculauncher.launcher.utils.KCUPath;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUCheckBox;
import com.kongbai.kculauncher.library.component.view.KCUEditText;
import com.kongbai.kculauncher.library.component.view.KCUImageButton;
import com.kongbai.kculauncher.library.component.view.KCUTextView;

import java.io.File;
import java.util.ArrayList;

public class ControllerUploadDialog extends KCUDialog implements View.OnClickListener, AdapterView.OnItemSelectedListener, CompoundButton.OnCheckedChangeListener {

    private final Activity activity;
    private final Callback callback;

    private final KCUEditText name;
    private final KCUEditText author;
    private final KCUEditText intro;
    private final KCUEditText description;

    private final AppCompatSpinner lang;
    private final KCUCheckBox phone;
    private final KCUCheckBox pad;
    private final KCUCheckBox other;
    private final KCUTextView iconText;
    private final KCUImageButton icon;
    private final KCUImageButton screenshot;
    private final LinearLayoutCompat screenshotLayout;

    private final KCUButton share;
    private final KCUButton negative;

    private String language = "all";
    private final ArrayList<Integer> devices = new ArrayList<>();
    private final ArrayList<String> screenshots = new ArrayList<>();

    public ControllerUploadDialog(@NonNull Context context, Activity activity, Controller controller, Callback callback) {
        super(context);
        setCancelable(false);
        setContentView(R.layout.dialog_controller_upload);
        this.activity = activity;
        this.callback = callback;

        name = findViewById(R.id.name);
        author = findViewById(R.id.author);
        intro = findViewById(R.id.intro);
        description = findViewById(R.id.description);

        name.setText(controller.getName());
        author.setText(controller.getAuthor());
        intro.setText(controller.getDescription());
        description.setText(controller.getDescription());

        lang = findViewById(R.id.lang);
        phone = findViewById(R.id.phone);
        pad = findViewById(R.id.pad);
        other = findViewById(R.id.other);
        iconText = findViewById(R.id.icon_text);
        icon = findViewById(R.id.icon);
        screenshot = findViewById(R.id.screenshot);
        screenshotLayout = findViewById(R.id.screenshot_layout);

        ArrayList<String> langs = new ArrayList<>();
        langs.add(getContext().getString(R.string.curse_category_0));
        langs.add(getContext().getString(R.string.settings_launcher_language_english));
        langs.add(getContext().getString(R.string.settings_launcher_language_simplified_chinese));
        langs.add(getContext().getString(R.string.settings_launcher_language_russian));
        langs.add(getContext().getString(R.string.settings_launcher_language_brazilian_portuguese));
        langs.add(getContext().getString(R.string.settings_launcher_language_persian));
        langs.add(getContext().getString(R.string.settings_launcher_language_ukrainian));
        langs.add(getContext().getString(R.string.settings_launcher_language_german));
        ArrayAdapter<String> langAdapter = new ArrayAdapter<>(getContext(), R.layout.item_spinner_theme_color, langs);
        langAdapter.setDropDownViewResource(R.layout.item_spinner_dropdown);
        lang.setAdapter(langAdapter);
        lang.setSelection(0);
        lang.setOnItemSelectedListener(this);

        phone.setOnCheckedChangeListener(this);
        pad.setOnCheckedChangeListener(this);
        other.setOnCheckedChangeListener(this);
        phone.setChecked(true);

        iconText.setText("");
        icon.setOnClickListener(this);

        screenshot.setOnClickListener(this);
        screenshotLayout.removeAllViews();

        share = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);
        share.setOnClickListener(this);
        negative.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view == icon) {
            ArrayList<String> suffix = new ArrayList<>();
            suffix.add(".png");
            MainActivity.getInstance().fileLauncher.launchSingleSelection(null, suffix, (files) -> {
                if (files == null) return;
                iconText.setText(files.get(0).toFile(activity, new File(KCUPath.CACHE_DIR)).getAbsolutePath());
            });
        }
        if (view == screenshot) {
            if (screenshots.size() < 16) {
                ArrayList<String> suffix = new ArrayList<>();
                suffix.add(".png");
                MainActivity.getInstance().fileLauncher.launchMultiSelection(null, suffix, (files) -> {
                    if (files != null && !files.isEmpty()) {
                        files.forEach(f -> {
                            String r = f.toFile(activity, new File(KCUPath.CACHE_DIR)).getAbsolutePath();
                            if (!screenshots.contains(r) && screenshots.size() < 16) {
                                screenshots.add(r);
                                Item item = new Item(getContext(), r, screenshots::remove);
                                screenshotLayout.addView(item.createView(), new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                            }
                        });
                    }
                });
            } else {
                Toast.makeText(getContext(), getContext().getString(R.string.control_info_screenshot_max), Toast.LENGTH_SHORT).show();
            }
        }

        if (view == share) {
            if (StringUtils.isBlank(name.getText().toString())
                    || StringUtils.isBlank(author.getText().toString())
                    || StringUtils.isBlank(intro.getText().toString())
                    || StringUtils.isBlank(description.getText().toString())
                    || devices.isEmpty()
                    || screenshots.isEmpty()
                    || StringUtils.isBlank(iconText.getText().toString())) {
                Toast.makeText(getContext(), getContext().getString(R.string.input_not_empty), Toast.LENGTH_SHORT).show();
            } else {
                callback.onPositive(name.getText().toString(),
                        author.getText().toString(),
                        intro.getText().toString(),
                        description.getText().toString(),
                        language,
                        devices,
                        screenshots,
                        iconText.getText().toString());
            }
        }
        if (view == negative) {
            dismiss();
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        switch (i) {
            case 0:
                language = "all";
                break;
            case 1:
                language = "en";
                break;
            case 2:
                language = "zh_CN";
                break;
            case 3:
                language = "ru";
                break;
            case 4:
                language = "pt";
                break;
            case 5:
                language = "fa";
                break;
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {

    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
        int id = -1;
        if (compoundButton == phone) {
            id = 0;
        }
        if (compoundButton == pad) {
            id = 1;
        }
        if (compoundButton == other) {
            id = 2;
        }
        if (id != -1) {
            if (b) {
                devices.add(id);
            } else {
                for (int i = 0; i < devices.size(); i++) {
                    if (devices.get(i) == id) {
                        devices.remove(i);
                        break;
                    }
                }
            }
        }
    }

    static class Item implements View.OnClickListener {

        private final Context context;
        private final String text;
        private final RemoveListener listener;

        private View root;
        private KCUTextView textView;
        private KCUImageButton imageButton;

        public Item(Context context, String text, RemoveListener listener) {
            this.context = context;
            this.text = text;
            this.listener = listener;
        }

        public View createView() {
            if (root == null) {
                root = LayoutInflater.from(context).inflate(R.layout.item_screenshot_path, null);
                textView = root.findViewById(R.id.text);
                imageButton = root.findViewById(R.id.delete);
                textView.setText(text);
                imageButton.setOnClickListener(this);
            }
            return root;
        }

        @Override
        public void onClick(View view) {
            if (view == imageButton) {
                listener.remove(text);
                ((LinearLayoutCompat) root.getParent()).removeView(root);
            }
        }

        public interface RemoveListener {
            void remove(String text);
        }
    }

    public interface Callback {
        void onPositive(String name, String author, String intro, String description, String lang, ArrayList<Integer> devices, ArrayList<String> screenshots, String iconPath);
    }
}
