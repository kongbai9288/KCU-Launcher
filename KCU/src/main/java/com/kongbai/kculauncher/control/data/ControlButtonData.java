package com.kongbai.kculauncher.control.data;

import static com.kongbai.kculauncher.control.data.JsonElements.get;
import static com.kongbai.kculauncher.util.FXUtils.onInvalidating;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import com.kongbai.kculauncher.core.fakefx.beans.InvalidationListener;
import com.kongbai.kculauncher.core.fakefx.beans.Observable;
import com.kongbai.kculauncher.core.fakefx.beans.property.IntegerProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.ObjectProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleIntegerProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleObjectProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleStringProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.StringProperty;
import com.kongbai.kculauncher.core.util.fakefx.ObservableHelper;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@JsonAdapter(ControlButtonData.Serializer.class)
public class ControlButtonData implements Cloneable, Observable, CustomControl {

    /**
     * Unique id
     */
    private final String id;

    public String getId() {
        return id;
    }

    public boolean equals(ControlButtonData data) {
        return data.getId().equals(id);
    }

    /**
     * Button display text
     */
    private final StringProperty textProperty = new SimpleStringProperty(this, "text", "");

    public StringProperty textProperty() {
        return textProperty;
    }

    public void setText(String text) {
        textProperty.set(text);
    }

    public String getText() {
        return textProperty.get();
    }

    /**
     * 多字符模式：同一个按钮预置多套文字，运行时按顺序切换。
     *
     * 存盘时两套信息都要写：候选文字本身，以及当前选中第几个。
     * 只写文字不写序号的话，玩家在游戏里切到第二个，下次进来又回到第一个，
     * 看上去像设置没保存。
     */
    private final List<String> texts = new ArrayList<>();

    private final IntegerProperty textIndexProperty = new SimpleIntegerProperty(this, "textIndex", 0);

    public List<String> getTexts() {
        return texts;
    }

    public void setTexts(List<String> list) {
        texts.clear();
        if (list != null) {
            for (String item : list) {
                if (item != null) texts.add(item);
            }
        }
        // 候选变化后旧序号可能越界，回落到第一个，避免切出空白文字
        if (getTextIndex() >= texts.size()) {
            setTextIndex(0);
            if (!texts.isEmpty()) setText(texts.get(0));
        }
    }

    public IntegerProperty textIndexProperty() {
        return textIndexProperty;
    }

    public int getTextIndex() {
        return textIndexProperty.get();
    }

    public void setTextIndex(int index) {
        textIndexProperty.set(index);
    }

    /** 切到下一套文字并立即更新按钮显示；只有一套时不产生任何变化 */
    public void switchToNextText() {
        if (texts.size() <= 1) {
            return;
        }
        int next = (getTextIndex() + 1) % texts.size();
        setTextIndex(next);
        setText(texts.get(next));
    }

    /**
     * Button style
     */
    private final ObjectProperty<ControlButtonStyle> styleProperty = new SimpleObjectProperty<>(this, "style", ControlButtonStyle.DEFAULT_BUTTON_STYLE);

    public ObjectProperty<ControlButtonStyle> styleProperty() {
        return styleProperty;
    }

    public void setStyle(ControlButtonStyle style) {
        styleProperty.set(style);
    }

    public ControlButtonStyle getStyle() {
        return styleProperty.get();
    }

    /**
     * Base info data
     * Contains position and size
     */
    public final ObjectProperty<BaseInfoData> baseInfoProperty = new SimpleObjectProperty<>(this, "baseInfo", new BaseInfoData());

    public ObjectProperty<BaseInfoData> baseInfoProperty() {
        return baseInfoProperty;
    }

    public void setBaseInfo(BaseInfoData baseInfo) {
        baseInfoProperty.set(baseInfo);
    }

    public BaseInfoData getBaseInfo() {
        return baseInfoProperty.get();
    }

    /**
     * Button event data
     */
    public final ObjectProperty<ButtonEventData> eventProperty = new SimpleObjectProperty<>(this, "event", new ButtonEventData());

    public ObjectProperty<ButtonEventData> eventProperty() {
        return eventProperty;
    }

    public void setEvent(ButtonEventData event) {
        eventProperty.set(event);
    }

    public ButtonEventData getEvent() {
        return eventProperty.get();
    }

    public ControlButtonData(String id) {
        this.id = id;

        addPropertyChangedListener(onInvalidating(this::invalidate));
    }

    public void addPropertyChangedListener(InvalidationListener listener) {
        textProperty.addListener(listener);
        textIndexProperty.addListener(listener);
        styleProperty.addListener(listener);
        baseInfoProperty.addListener(listener);
        eventProperty.addListener(listener);
    }

    private ObservableHelper observableHelper = new ObservableHelper(this);

    @Override
    public void addListener(InvalidationListener listener) {
        observableHelper.addListener(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        observableHelper.removeListener(listener);
    }

    private void invalidate() {
        try {
            observableHelper.invalidate();
        } catch (NullPointerException ignore) {
        }
    }

    @Override
    public ControlButtonData clone() {
        ControlButtonData data = new ControlButtonData(UUID.randomUUID().toString());
        data.setText(getText());
        data.setTexts(new ArrayList<>(texts));
        data.setTextIndex(getTextIndex());
        data.setStyle(getStyle().clone());
        data.setBaseInfo(getBaseInfo().clone());
        data.setEvent(getEvent().clone());
        return data;
    }

    @Override
    public ViewType getType() {
        return ViewType.CONTROL_BUTTON;
    }

    @Override
    public String getViewId() {
        return getId();
    }

    @Override
    public CustomControl cloneView() {
        ControlButtonData clone = clone();
        // 副本贴近原控件（偏移 2%），不与原控件完全重叠，也不落回左上角
        clone.getBaseInfo().setXPosition(Math.min(1000, clone.getBaseInfo().getXPosition() + 20));
        clone.getBaseInfo().setYPosition(Math.min(1000, clone.getBaseInfo().getYPosition() + 20));
        return clone;
    }

    public static class Serializer implements JsonSerializer<ControlButtonData>, JsonDeserializer<ControlButtonData> {
        @Override
        public JsonElement serialize(ControlButtonData src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) return JsonNull.INSTANCE;
            JsonObject obj = new JsonObject();

            obj.addProperty("id", src.getId());
            obj.addProperty("text", src.getText());
            JsonArray textsArray = new JsonArray();
            for (String item : src.getTexts()) {
                textsArray.add(item);
            }
            obj.add("texts", textsArray);
            obj.addProperty("textIndex", src.getTextIndex());
            obj.addProperty("style", src.getStyle().getName());
            obj.add("baseInfo", new BaseInfoData.Serializer().serialize(src.getBaseInfo(), null, null));
            obj.add("event", new ButtonEventData.Serializer().serialize(src.getEvent(), null, null));

            return obj;
        }

        @Override
        public ControlButtonData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == JsonNull.INSTANCE || !(json instanceof JsonObject))
                return null;
            JsonObject obj = (JsonObject) json;

            ControlButtonData data = new ControlButtonData(Optional.ofNullable(get(obj, "id")).map(JsonElement::getAsString).orElse(UUID.randomUUID().toString()));

            data.setText(Optional.ofNullable(get(obj, "text")).map(JsonElement::getAsString).orElse(""));
            // 旧布局没有这两个字段，缺失时保持空候选即可，切换事件自然不产生变化
            List<String> textsList = new ArrayList<>();
            JsonElement textsElement = get(obj, "texts");
            if (textsElement != null && textsElement.isJsonArray()) {
                for (JsonElement item : textsElement.getAsJsonArray()) {
                    if (item.isJsonPrimitive()) textsList.add(item.getAsString());
                }
            }
            data.setTexts(textsList);
            data.setTextIndex(Optional.ofNullable(get(obj, "textIndex")).map(JsonElement::getAsInt).orElse(0));
            if (!ButtonStyles.isInitialized()) {
                ButtonStyles.init();
            }
            JsonElement style = get(obj, "style");
            if (style != null && style.toString().contains("\"name\"")) {
                data.setStyle(new ControlButtonStyle.Serializer().deserialize(style, null, null));
                ButtonStyles.addStyle(data.getStyle());
            } else {
                data.setStyle(ButtonStyles.findStyleByName(style != null && style.isJsonPrimitive() ? style.getAsString() : ""));
            }
            data.setBaseInfo(Optional.ofNullable(get(obj, "baseInfo")).map(JsonElement::getAsJsonObject).map(baseInfo -> new BaseInfoData.Serializer().deserialize(baseInfo, null, null)).orElseGet(BaseInfoData::new));
            data.setEvent(Optional.ofNullable(get(obj, "event")).map(JsonElement::getAsJsonObject).map(event -> new ButtonEventData.Serializer().deserialize(event, null, null)).orElseGet(ButtonEventData::new));

            return data;
        }
    }

}
