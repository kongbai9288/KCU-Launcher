package com.kongbai.kculauncher.control;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.control.data.ControlDirectionStyle;
import com.kongbai.kculauncher.control.data.ControlViewGroup;
import com.kongbai.kculauncher.control.data.DirectionStyles;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;

public class DirectionStyleDialog extends KCUDialog implements View.OnClickListener {

    private final boolean select;
    private final ControlDirectionStyle initStyle;
    private final Callback callback;

    private KCUButton addStyle;
    private KCUButton editStyle;
    private KCUButton positive;

    private ListView listView;

    private GameMenu menu;

    public interface Callback {
        void onStyleSelect(ControlDirectionStyle style);
    }

    public DirectionStyleDialog(@NonNull Context context, boolean select, @Nullable ControlDirectionStyle initStyle, Callback callback) {
        super(context);
        this.select = select;
        this.initStyle = initStyle;
        this.callback = callback;
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        setContentView(R.layout.dialog_manage_direction_style);
        setCancelable(false);

        addStyle = findViewById(R.id.add_style);
        editStyle = findViewById(R.id.edit_style);
        positive = findViewById(R.id.positive);
        addStyle.setOnClickListener(this);
        editStyle.setOnClickListener(this);
        positive.setOnClickListener(this);

        listView = findViewById(R.id.list);
        refreshList();

        if (!select) {
            editStyle.setVisibility(View.GONE);
        }
    }

    private DirectionStyleAdapter adapter;

    public void refreshList() {
        adapter = new DirectionStyleAdapter(getContext(), DirectionStyles.getStyles(), select, initStyle);
        listView.setAdapter(adapter);
        if (initStyle != null)
            listView.setSelection(DirectionStyles.findStyleIndexByName(initStyle.getName()));
    }

    @Override
    public void onClick(View v) {
        if (v == addStyle) {
            AddDirectionStyleDialog dialog = new AddDirectionStyleDialog(getContext(), null, false, style -> {
                DirectionStyles.addStyle(style);
                refreshList();
            });
            dialog.show();
        }
        if (v == editStyle) {
            AddDirectionStyleDialog dialog = new AddDirectionStyleDialog(getContext(), adapter.getSelectedStyle(), true, style -> {
                ControlDirectionStyle before = adapter.getSelectedStyle();
                int i = DirectionStyles.getStyles().indexOf(before);
                String beforeName = before.getName();
                DirectionStyles.removeStyles(before);
                DirectionStyles.addStyle(style, i);
                refreshList();
                adapter.setSelectedStyle(style);
                if (menu != null) {
                    ControlViewGroup viewGroup = menu.getViewGroup();
                    if (viewGroup != null) {
                        viewGroup.getViewData().directionList().forEach(it -> {
                            String name = it.getStyle().getName();
                            if (name.equals(style.getName()) || name.equals(beforeName)) {
                                it.setStyle(style);
                            }
                        });
                    }
                }
            });
            dialog.setGameMenu(menu);
            dialog.show();
        }
        if (v == positive) {
            dismiss();
            if (callback != null && select) {
                callback.onStyleSelect(adapter.getSelectedStyle());
            }
        }
    }

    public void setGameMenu(GameMenu menu) {
        this.menu = menu;
    }
}
