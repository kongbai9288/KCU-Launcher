package com.kongbai.kculauncher.control;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.control.data.BaseInfoData;
import com.kongbai.kculauncher.control.data.ControlButtonStyle;
import com.kongbai.kculauncher.control.data.ControlDirectionStyle;
import com.kongbai.kculauncher.control.data.DirectionStyles;
import com.kongbai.kculauncher.control.view.ControlDirection;
import com.kongbai.kculauncher.util.FXUtils;
import com.kongbai.kculauncher.core.fakefx.beans.binding.Bindings;
import com.kongbai.kculauncher.core.fakefx.beans.property.IntegerProperty;
import com.kongbai.kculauncher.core.util.StringUtils;
import com.kongbai.kculauncher.library.component.dialog.EditDialog;
import com.kongbai.kculauncher.library.component.dialog.KCUColorPickerDialog;
import com.kongbai.kculauncher.library.component.dialog.KCUDialog;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUEditText;
import com.kongbai.kculauncher.library.component.view.KCULinearLayout;
import com.kongbai.kculauncher.library.component.view.KCUPreciseSeekBar;
import com.kongbai.kculauncher.library.component.view.KCUSpinner;
import com.kongbai.kculauncher.library.component.view.KCUTextView;

import java.util.ArrayList;

public class AddDirectionStyleDialog extends KCUDialog implements View.OnClickListener {

    private final Callback callback;

    private KCUButton positive;
    private KCUButton negative;

    private KCUEditText editName;
    private ControlDirection direction;
    private KCUSpinner<String> typeSpinner;

    private ScrollView container;
    private KCULinearLayout buttonStyleLayout;
    private KCULinearLayout rockerStyleLayout;

    private ControlDirectionStyle style;

    private ControlButtonStyle buttonStyle;
    private boolean isEdit;
    private ControlButtonStyle beforeStyle;
    private GameMenu menu;

    public interface Callback {
        void onStyleAdd(ControlDirectionStyle style);
    }

    public AddDirectionStyleDialog(@NonNull Context context, ControlDirectionStyle beforeStyle, boolean isEdit, Callback callback) {
        super(context);
        setContentView(R.layout.dialog_add_direction_style);
        setCancelable(false);
        this.callback = callback;
        this.style = beforeStyle == null ? new ControlDirectionStyle("") : beforeStyle.clone();
        this.isEdit = isEdit;

        positive = findViewById(R.id.positive);
        negative = findViewById(R.id.negative);
        positive.setOnClickListener(this);
        negative.setOnClickListener(this);

        editName = findViewById(R.id.name);
        direction = findViewById(R.id.direction);
        typeSpinner = findViewById(R.id.type);
        ArrayList<ControlDirectionStyle.Type> types = new ArrayList<>();
        types.add(ControlDirectionStyle.Type.BUTTON);
        types.add(ControlDirectionStyle.Type.ROCKER);
        ArrayList<String> typeString = new ArrayList<>();
        typeString.add(getContext().getString(R.string.style_direction_button));
        typeString.add(getContext().getString(R.string.style_direction_rocker));
        typeSpinner.setItems(typeString);

        container = findViewById(R.id.container);

        buttonStyleLayout = (KCULinearLayout) LayoutInflater.from(getContext()).inflate(R.layout.view_direction_style_button, null);
        rockerStyleLayout = (KCULinearLayout) LayoutInflater.from(getContext()).inflate(R.layout.view_direction_style_rocker, null);

        editName.setText(style.getName());
        style.nameProperty().bind(editName.stringProperty());
        changeDirectionStyle();
        style.addListener(observable -> changeDirectionStyle());
        {
            KCUPreciseSeekBar interval = buttonStyleLayout.findViewById(R.id.interval);

            KCUTextView intervalText = buttonStyleLayout.findViewById(R.id.interval_text);

            intervalText.setOnClickListener(v -> openTextEditDialog(context, interval.progressProperty(), true));

            interval.setProgress(style.getButtonStyle().getInterval());
            style.getButtonStyle().intervalProperty().bindBidirectional(interval.progressProperty());

            intervalText.stringProperty().bind(Bindings.createStringBinding(() -> interval.getProgress() / 10f + " %", interval.progressProperty()));

            KCUTextView buttonStyleText = buttonStyleLayout.findViewById(R.id.button_style_text);
            KCUButton buttonStyleSet = buttonStyleLayout.findViewById(R.id.set_button_style);

            buttonStyle = new ControlButtonStyle("");
            buttonStyleText.setText(buttonStyle.getName());
            buttonStyleText.stringProperty().bind(buttonStyle.nameProperty());
            buttonStyle.setTextSize(style.getButtonStyle().getTextSize());
            buttonStyle.setTextColor(style.getButtonStyle().getTextColor());
            buttonStyle.setStrokeWidth(style.getButtonStyle().getStrokeWidth());
            buttonStyle.setStrokeColor(style.getButtonStyle().getStrokeColor());
            buttonStyle.setCornerRadius(style.getButtonStyle().getCornerRadius());
            buttonStyle.setFillColor(style.getButtonStyle().getFillColor());
            buttonStyle.setTextSizePressed(style.getButtonStyle().getTextSizePressed());
            buttonStyle.setTextColorPressed(style.getButtonStyle().getTextColorPressed());
            buttonStyle.setStrokeWidthPressed(style.getButtonStyle().getStrokeWidthPressed());
            buttonStyle.setStrokeColorPressed(style.getButtonStyle().getStrokeColorPressed());
            buttonStyle.setCornerRadiusPressed(style.getButtonStyle().getCornerRadiusPressed());
            buttonStyle.setFillColorPressed(style.getButtonStyle().getFillColorPressed());
            buttonStyle.setFillColorPressed(style.getButtonStyle().getFillColorPressed());
            this.beforeStyle = buttonStyle.clone();
            style.getButtonStyle().textSizeProperty().bind(buttonStyle.textSizeProperty());
            style.getButtonStyle().textColorProperty().bind(buttonStyle.textColorProperty());
            style.getButtonStyle().strokeWidthProperty().bind(buttonStyle.strokeWidthProperty());
            style.getButtonStyle().strokeColorProperty().bind(buttonStyle.strokeColorProperty());
            style.getButtonStyle().cornerRadiusProperty().bind(buttonStyle.cornerRadiusProperty());
            style.getButtonStyle().fillColorProperty().bind(buttonStyle.fillColorProperty());
            style.getButtonStyle().textSizePressedProperty().bind(buttonStyle.textSizePressedProperty());
            style.getButtonStyle().textColorPressedProperty().bind(buttonStyle.textColorPressedProperty());
            style.getButtonStyle().strokeWidthPressedProperty().bind(buttonStyle.strokeWidthPressedProperty());
            style.getButtonStyle().strokeColorPressedProperty().bind(buttonStyle.strokeColorPressedProperty());
            style.getButtonStyle().cornerRadiusPressedProperty().bind(buttonStyle.cornerRadiusPressedProperty());
            style.getButtonStyle().fillColorPressedProperty().bind(buttonStyle.fillColorPressedProperty());

            buttonStyleSet.setOnClickListener(v -> {
                ButtonStyleDialog dialog = new ButtonStyleDialog(getContext(), true, buttonStyle, style -> {
                    buttonStyle.setName(style.getName());
                    buttonStyle.setTextSize(style.getTextSize());
                    buttonStyle.setTextColor(style.getTextColor());
                    buttonStyle.setStrokeWidth(style.getStrokeWidth());
                    buttonStyle.setStrokeColor(style.getStrokeColor());
                    buttonStyle.setCornerRadius(style.getCornerRadius());
                    buttonStyle.setFillColor(style.getFillColor());
                    buttonStyle.setTextSizePressed(style.getTextSizePressed());
                    buttonStyle.setTextColorPressed(style.getTextColorPressed());
                    buttonStyle.setStrokeWidthPressed(style.getStrokeWidthPressed());
                    buttonStyle.setStrokeColorPressed(style.getStrokeColorPressed());
                    buttonStyle.setCornerRadiusPressed(style.getCornerRadiusPressed());
                    buttonStyle.setFillColorPressed(style.getFillColorPressed());
                    changeDirectionStyle();
                });
                dialog.setGameMenu(menu);
                dialog.show();
            });
        }

        {
            KCUPreciseSeekBar rockerSize = rockerStyleLayout.findViewById(R.id.rocker_size);
            KCUPreciseSeekBar bgStrokeWidth = rockerStyleLayout.findViewById(R.id.bg_stroke_width);
            KCUPreciseSeekBar bgCornerRadius = rockerStyleLayout.findViewById(R.id.bg_corner_radius);
            KCUPreciseSeekBar strokeWidth = rockerStyleLayout.findViewById(R.id.stroke_width);
            KCUPreciseSeekBar cornerRadius = rockerStyleLayout.findViewById(R.id.corner_radius);

            KCUTextView rockerSizeText = rockerStyleLayout.findViewById(R.id.rocker_size_text);
            KCUTextView bgStrokeWidthText = rockerStyleLayout.findViewById(R.id.bg_stroke_width_text);
            KCUTextView bgCornerRadiusText = rockerStyleLayout.findViewById(R.id.bg_corner_radius_text);
            KCUTextView strokeWidthText = rockerStyleLayout.findViewById(R.id.stroke_width_text);
            KCUTextView cornerRadiusText = rockerStyleLayout.findViewById(R.id.corner_radius_text);

            rockerSizeText.setOnClickListener(v -> openTextEditDialog(context, rockerSize.progressProperty(), true));
            bgStrokeWidthText.setOnClickListener(v -> openTextEditDialog(context, bgStrokeWidth.progressProperty(), true));
            bgCornerRadiusText.setOnClickListener(v -> openTextEditDialog(context, bgCornerRadius.progressProperty(), true));
            strokeWidthText.setOnClickListener(v -> openTextEditDialog(context, strokeWidth.progressProperty(), true));
            cornerRadiusText.setOnClickListener(v -> openTextEditDialog(context, cornerRadius.progressProperty(), true));

            rockerSize.setProgress(style.getRockerStyle().getRockerSize());
            bgStrokeWidth.setProgress(style.getRockerStyle().getBgStrokeWidth());
            bgCornerRadius.setProgress(style.getRockerStyle().getBgCornerRadius());
            strokeWidth.setProgress(style.getRockerStyle().getRockerStrokeWidth());
            cornerRadius.setProgress(style.getRockerStyle().getRockerCornerRadius());
            style.getRockerStyle().rockerSizeProperty().bindBidirectional(rockerSize.progressProperty());
            style.getRockerStyle().bgStrokeWidthProperty().bindBidirectional(bgStrokeWidth.progressProperty());
            style.getRockerStyle().bgCornerRadiusProperty().bindBidirectional(bgCornerRadius.progressProperty());
            style.getRockerStyle().rockerStrokeWidthProperty().bindBidirectional(strokeWidth.progressProperty());
            style.getRockerStyle().rockerCornerRadiusProperty().bindBidirectional(cornerRadius.progressProperty());

            rockerSizeText.stringProperty().bind(Bindings.createStringBinding(() -> rockerSize.getProgress() / 10f + " %", rockerSize.progressProperty()));
            bgStrokeWidthText.stringProperty().bind(Bindings.createStringBinding(() -> bgStrokeWidth.getProgress() / 10f + " dp", bgStrokeWidth.progressProperty()));
            bgCornerRadiusText.stringProperty().bind(Bindings.createStringBinding(() -> bgCornerRadius.getProgress() / 10f + " %", bgCornerRadius.progressProperty()));
            strokeWidthText.stringProperty().bind(Bindings.createStringBinding(() -> strokeWidth.getProgress() / 10f + " dp", strokeWidth.progressProperty()));
            cornerRadiusText.stringProperty().bind(Bindings.createStringBinding(() -> cornerRadius.getProgress() / 10f + " %", cornerRadius.progressProperty()));

            KCUTextView bgStrokeColorText = rockerStyleLayout.findViewById(R.id.bg_stroke_color_text);
            KCUTextView bgFillColorText = rockerStyleLayout.findViewById(R.id.bg_fill_color_text);
            KCUTextView strokeColorText = rockerStyleLayout.findViewById(R.id.stroke_color_text);
            KCUTextView fillColorText = rockerStyleLayout.findViewById(R.id.fill_color_text);

            View bgStrokeColorView = rockerStyleLayout.findViewById(R.id.bg_stroke_color_view);
            View bgFillColorView = rockerStyleLayout.findViewById(R.id.bg_fill_color_view);
            View strokeColorView = rockerStyleLayout.findViewById(R.id.stroke_color_view);
            View fillColorView = rockerStyleLayout.findViewById(R.id.fill_color_view);

            KCUButton bgStrokeColorSet = rockerStyleLayout.findViewById(R.id.set_bg_stroke_color);
            KCUButton bgFillColorSet = rockerStyleLayout.findViewById(R.id.set_bg_fill_color);
            KCUButton strokeColorSet = rockerStyleLayout.findViewById(R.id.set_stroke_color);
            KCUButton fillColorSet = rockerStyleLayout.findViewById(R.id.set_fill_color);

            bgStrokeColorView.setBackgroundColor(style.getRockerStyle().getBgStrokeColor());
            bgStrokeColorText.setText(getHex(style.getRockerStyle().getBgStrokeColor()));
            bgFillColorView.setBackgroundColor(style.getRockerStyle().getBgFillColor());
            bgFillColorText.setText(getHex(style.getRockerStyle().getBgFillColor()));
            strokeColorView.setBackgroundColor(style.getRockerStyle().getRockerStrokeColor());
            strokeColorText.setText(getHex(style.getRockerStyle().getRockerStrokeColor()));
            fillColorView.setBackgroundColor(style.getRockerStyle().getRockerFillColor());
            fillColorText.setText(getHex(style.getRockerStyle().getRockerFillColor()));
            style.getRockerStyle().bgStrokeColorProperty().addListener(observable -> {
                bgStrokeColorView.setBackgroundColor(style.getRockerStyle().getBgStrokeColor());
                bgStrokeColorText.setText(getHex(style.getRockerStyle().getBgStrokeColor()));
            });
            style.getRockerStyle().bgFillColorProperty().addListener(observable -> {
                bgFillColorView.setBackgroundColor(style.getRockerStyle().getBgFillColor());
                bgFillColorText.setText(getHex(style.getRockerStyle().getBgFillColor()));
            });
            style.getRockerStyle().rockerStrokeColorProperty().addListener(observable -> {
                strokeColorView.setBackgroundColor(style.getRockerStyle().getRockerStrokeColor());
                strokeColorText.setText(getHex(style.getRockerStyle().getRockerStrokeColor()));
            });
            style.getRockerStyle().rockerFillColorProperty().addListener(observable -> {
                fillColorView.setBackgroundColor(style.getRockerStyle().getRockerFillColor());
                fillColorText.setText(getHex(style.getRockerStyle().getRockerFillColor()));
            });

            bgStrokeColorSet.setOnClickListener(v -> {
                KCUColorPickerDialog dialog = new KCUColorPickerDialog(getContext(), style.getRockerStyle().getBgStrokeColor(), new KCUColorPickerDialog.Listener() {
                    @Override
                    public void onColorChanged(int color) {

                    }

                    @Override
                    public void onPositive(int destColor) {
                        style.getRockerStyle().setBgStrokeColor(destColor);
                    }

                    @Override
                    public void onNegative(int initColor) {

                    }
                });
                dialog.show();
            });
            bgFillColorSet.setOnClickListener(v -> {
                KCUColorPickerDialog dialog = new KCUColorPickerDialog(getContext(), style.getRockerStyle().getBgFillColor(), new KCUColorPickerDialog.Listener() {
                    @Override
                    public void onColorChanged(int color) {

                    }

                    @Override
                    public void onPositive(int destColor) {
                        style.getRockerStyle().setBgFillColor(destColor);
                    }

                    @Override
                    public void onNegative(int initColor) {

                    }
                });
                dialog.show();
            });
            strokeColorSet.setOnClickListener(v -> {
                KCUColorPickerDialog dialog = new KCUColorPickerDialog(getContext(), style.getRockerStyle().getRockerStrokeColor(), new KCUColorPickerDialog.Listener() {
                    @Override
                    public void onColorChanged(int color) {

                    }

                    @Override
                    public void onPositive(int destColor) {
                        style.getRockerStyle().setRockerStrokeColor(destColor);
                    }

                    @Override
                    public void onNegative(int initColor) {

                    }
                });
                dialog.show();
            });
            fillColorSet.setOnClickListener(v -> {
                KCUColorPickerDialog dialog = new KCUColorPickerDialog(getContext(), style.getRockerStyle().getRockerFillColor(), new KCUColorPickerDialog.Listener() {
                    @Override
                    public void onColorChanged(int color) {

                    }

                    @Override
                    public void onPositive(int destColor) {
                        style.getRockerStyle().setRockerFillColor(destColor);
                    }

                    @Override
                    public void onNegative(int initColor) {

                    }
                });
                dialog.show();
            });
        }

        container.addView(style.styleTypeProperty().get() == ControlDirectionStyle.Type.BUTTON ? buttonStyleLayout : rockerStyleLayout);
        typeSpinner.setSelection(style.styleTypeProperty().get() == ControlDirectionStyle.Type.BUTTON ? 0 : 1);
        typeSpinner.setOnItemSelectedListener((index, item) -> style.styleTypeProperty().set(types.get(index)));
        style.styleTypeProperty().addListener(observable -> {
            container.removeAllViewsInLayout();
            container.addView(style.styleTypeProperty().get() == ControlDirectionStyle.Type.BUTTON ? buttonStyleLayout : rockerStyleLayout);
        });
    }

    private void changeDirectionStyle() {
        ControlDirectionStyle clone = style.clone();
        direction.getData().setStyle(clone);
        direction.getData().getBaseInfo().setSizeType(BaseInfoData.SizeType.ABSOLUTE);
        direction.getData().getBaseInfo().setAbsoluteWidth(60);
        direction.getData().getBaseInfo().setAbsoluteHeight(60);
    }

    private String getHex(int color) {
        return "#" + String.format("%08X", (color));
    }

    @Override
    public void onClick(View v) {
        if (v == positive) {
            if (!isEdit && DirectionStyles.getStyles().stream().anyMatch(it -> it.getName().equals(style.getName()))) {
                Toast.makeText(getContext(), getContext().getString(R.string.style_warning_exist), Toast.LENGTH_SHORT).show();
            } else if (StringUtils.isBlank(style.getName())) {
                Toast.makeText(getContext(), getContext().getString(R.string.style_warning_name), Toast.LENGTH_SHORT).show();
            } else {
                dismiss();
                callback.onStyleAdd(style);
            }
        }
        if (v == negative) {
            buttonStyle.setTextSize(beforeStyle.getTextSize());
            buttonStyle.setTextColor(beforeStyle.getTextColor());
            buttonStyle.setStrokeWidth(beforeStyle.getStrokeWidth());
            buttonStyle.setStrokeColor(beforeStyle.getStrokeColor());
            buttonStyle.setCornerRadius(beforeStyle.getCornerRadius());
            buttonStyle.setFillColor(beforeStyle.getFillColor());
            buttonStyle.setTextSizePressed(beforeStyle.getTextSizePressed());
            buttonStyle.setTextColorPressed(beforeStyle.getTextColorPressed());
            buttonStyle.setStrokeWidthPressed(beforeStyle.getStrokeWidthPressed());
            buttonStyle.setStrokeColorPressed(beforeStyle.getStrokeColorPressed());
            buttonStyle.setCornerRadiusPressed(beforeStyle.getCornerRadiusPressed());
            buttonStyle.setFillColorPressed(beforeStyle.getFillColorPressed());
            buttonStyle.setFillColorPressed(beforeStyle.getFillColorPressed());
            dismiss();
        }
    }

    private void openTextEditDialog(Context context, IntegerProperty property, boolean isPercentage) {
        EditDialog dialog = new EditDialog(context, s -> {
            if (s.matches("\\d+(\\.\\d+)?$")) {
                float progress = Float.parseFloat(s);
                if (isPercentage) {
                    progress = progress > 100 ? 100 : progress;
                    property.set((int) (progress * 10));
                } else {
                    property.set((int) progress);
                }
            }
        });
        dialog.getEditText().setInputType(EditorInfo.TYPE_NUMBER_FLAG_DECIMAL);
        dialog.show();
    }

    public void setGameMenu(GameMenu menu) {
        this.menu = menu;
    }
}
