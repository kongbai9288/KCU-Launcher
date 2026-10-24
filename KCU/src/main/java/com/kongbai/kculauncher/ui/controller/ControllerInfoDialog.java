package com.kongbai.kculauncher.ui.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.setting.Controller;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.core.util.platform.OperatingSystem;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUCheckBox;
import com.kongbai.kculauncher.library.component.view.KCUEditText;
import com.kongbai.kculauncher.library.component.view.KCULinearLayout;
import com.kongbai.kculauncher.library.component.view.KCUTextView;
import com.kongbai.kculauncher.library.util.ConvertUtils;

public class ControllerInfoDialog extends KCUDialog implements View.OnClickListener {

    private final Controller controller;
    private final Callback callback;

    private KCUEditText editName;

    private KCUEditText editVersion;
    private KCUEditText editVersionCode;
    private KCUEditText editAuthor;
    private KCUEditText editDescription;

    private KCUButton positive;
    private KCUButton negative;

    @SuppressLint("SetTextI18n")
    public ControllerInfoDialog(@NonNull Context context, boolean create, Controller controller, Callback callback) {
        this(context, controller, callback, create ? R.string.control_create : R.string.control_info_edit);
    }

    public ControllerInfoDialog(@NonNull Context context, Controller controller, Callback callback, int titleRes) {
        super(context);
        this.controller = controller;
        this.callback = callback;
        Window window = getWindow();
        if (window != null) {
            window.setLayout(ConvertUtils.dip2px(getContext(), 400), LinearLayout.LayoutParams.WRAP_CONTENT);
        }
        setContentView(R.layout.dialog_controller_info);
        setCancelable(false);

        KCUTextView titleView = findViewById(R.id.title);
        titleView.setText(getContext().getString(titleRes));

        editName = findViewById(R.id.name);

        KCULinearLayout moreInfoLayout = findViewById(R.id.more_info_layout);
        editVersion = findViewById(R.id.version);
        editVersionCode = findViewById(R.id.version_code);
        editAuthor = findViewById(R.id.author);
        editDescription = findViewById(R.id.description);

        editVersionCode.setIntegerFilter(1);

        editName.setText(controller.getName());
        editVersion.setText(controller.getVersion());
        editVersionCode.setText(controller.getVersionCode() + "");
        editAuthor.setText(controller.getAuthor());
        editDescription.setText(controller.getDescription());

        KCUCheckBox moreInfo = findViewById(R.id.more_info);
        assert moreInfo != null;
        assert moreInfoLayout != null;
        moreInfoLayout.setVisibility(View.GONE);
        moreInfo.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                getWindow().setLayout(ConvertUtils.dip2px(getContext(), 400), LinearLayout.LayoutParams.MATCH_PARENT);
                moreInfoLayout.setVisibility(View.VISIBLE);
            } else {
                getWindow().setLayout(ConvertUtils.dip2px(getContext(), 400), ConvertUtils.dip2px(getContext(), 200));
                moreInfoLayout.setVisibility(View.GONE);
            }
        });

        positive = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);
        positive.setOnClickListener(this);
        negative.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view == positive) {
            if (!OperatingSystem.isNameValid(editName.getText().toString()) || editName.getText().toString().equals("Error")) {
                Toast.makeText(getContext(), getContext().getString(R.string.control_info_name_invalid), Toast.LENGTH_SHORT).show();
            } else {
                String id = this.controller.getId();
                if (!editAuthor.getText().toString().equals(this.controller.getAuthor())) {
                    id = Controller.generateRandomId();
                }
                Controller controller = new Controller(id,
                        editName.getText().toString(),
                        editVersion.getText().toString(),
                        Integer.parseInt(StringUtils.isBlank(editVersionCode.getText().toString()) ? "1" : editVersionCode.getText().toString()),
                        editAuthor.getText().toString(),
                        editDescription.getText().toString(),
                        this.controller.getControllerVersion());
                callback.onInfoGenerate(controller);
                dismiss();
            }
        }
        if (view == negative) {
            dismiss();
        }
    }

    public interface Callback {
        void onInfoGenerate(Controller controller);
    }
}
