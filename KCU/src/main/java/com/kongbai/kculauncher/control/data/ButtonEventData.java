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
import com.kongbai.kculauncher.core.fakefx.beans.property.BooleanProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.ObjectProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleBooleanProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleObjectProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleStringProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.StringProperty;
import com.kongbai.kculauncher.core.fakefx.collections.FXCollections;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableList;
import com.kongbai.kculauncher.core.util.fakefx.ObservableHelper;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Optional;

@JsonAdapter(ButtonEventData.Serializer.class)
public class ButtonEventData implements Cloneable, Observable {

    /**
     * Control mouse pointer
     */
    private final BooleanProperty pointerFollowProperty = new SimpleBooleanProperty(this, "pointerFollow", false);

    public BooleanProperty pointerFollowProperty() {
        return pointerFollowProperty;
    }

    public void setPointerFollow(boolean pointerFollow) {
        pointerFollowProperty.set(pointerFollow);
    }

    public boolean isPointerFollow() {
        return pointerFollowProperty.get();
    }

    /**
     * Movable
     */
    private final BooleanProperty movableProperty = new SimpleBooleanProperty(this, "movable", false);

    public BooleanProperty movableProperty() {
        return movableProperty;
    }

    public void setMovable(boolean movable) {
        movableProperty.set(movable);
    }

    public boolean isMovable() {
        return movableProperty.get();
    }

    /**
     * 滑动联动：手指滑出边界自动释放，滑入相邻联动按钮触发其按下
     */
    private final BooleanProperty swipableProperty = new SimpleBooleanProperty(this, "swipable", false);

    public BooleanProperty swipableProperty() {
        return swipableProperty;
    }

    public void setSwipable(boolean swipable) {
        swipableProperty.set(swipable);
    }

    public boolean isSwipable() {
        return swipableProperty.get();
    }

    /**
     * Press event
     */
    private final ObjectProperty<Event> pressEventProperty = new SimpleObjectProperty<>(this, "pressEvent", new Event());
    
    public ObjectProperty<Event> pressEventProperty() {
        return pressEventProperty;
    }
    
    public void setPressEvent(Event event) {
        pressEventProperty.set(event);
    }
    
    public Event getPressEvent() {
        return pressEventProperty.get();
    }

    /**
     * Long press event
     */
    private final ObjectProperty<Event> longPressEventProperty = new SimpleObjectProperty<>(this, "longPressEvent", new Event());

    public ObjectProperty<Event> longPressEventProperty() {
        return longPressEventProperty;
    }

    public void setLongPressEvent(Event event) {
        longPressEventProperty.set(event);
    }

    public Event getLongPressEvent() {
        return longPressEventProperty.get();
    }

    /**
     * Click event
     */
    private final ObjectProperty<Event> clickEventProperty = new SimpleObjectProperty<>(this, "clickEvent", new Event());

    public ObjectProperty<Event> clickEventProperty() {
        return clickEventProperty;
    }

    public void setClickEvent(Event event) {
        clickEventProperty.set(event);
    }

    public Event getClickEvent() {
        return clickEventProperty.get();
    }

    /**
     * Click event
     */
    private final ObjectProperty<Event> doubleClickEventProperty = new SimpleObjectProperty<>(this, "doubleClickEvent", new Event());

    public ObjectProperty<Event> doubleClickEventProperty() {
        return doubleClickEventProperty;
    }

    public void setDoubleClickEvent(Event event) {
        doubleClickEventProperty.set(event);
    }

    public Event getDoubleClickEvent() {
        return doubleClickEventProperty.get();
    }

    public ButtonEventData() {
        addPropertyChangedListener(onInvalidating(this::invalidate));
    }

    public void addPropertyChangedListener(InvalidationListener listener) {
        pointerFollowProperty.addListener(listener);
        movableProperty.addListener(listener);
        swipableProperty.addListener(listener);
        pressEventProperty.addListener(listener);
        longPressEventProperty.addListener(listener);
        clickEventProperty.addListener(listener);
        doubleClickEventProperty.addListener(listener);
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
        observableHelper.invalidate();
    }

    @Override
    public ButtonEventData clone() {
        ButtonEventData data = new ButtonEventData();
        data.setPointerFollow(isPointerFollow());
        data.setMovable(isMovable());
        data.setSwipable(isSwipable());
        data.setPressEvent(getPressEvent().clone());
        data.setLongPressEvent(getLongPressEvent().clone());
        data.setClickEvent(getClickEvent().clone());
        data.setDoubleClickEvent(getDoubleClickEvent().clone());
        return data;
    }

    public static class Serializer implements JsonSerializer<ButtonEventData>, JsonDeserializer<ButtonEventData> {
        @Override
        public JsonElement serialize(ButtonEventData src, Type typeOfSrc, JsonSerializationContext context) {
            if (src == null) return JsonNull.INSTANCE;
            JsonObject obj = new JsonObject();

            obj.addProperty("pointerFollow", src.isPointerFollow());
            obj.addProperty("Movable", src.isMovable());
            obj.addProperty("swipable", src.isSwipable());
            obj.add("pressEvent", new Event.Serializer().serialize(src.getPressEvent(), null, null));
            obj.add("longPressEvent", new Event.Serializer().serialize(src.getLongPressEvent(), null, null));
            obj.add("clickEvent", new Event.Serializer().serialize(src.getClickEvent(), null, null));
            obj.add("doubleClickEvent", new Event.Serializer().serialize(src.getDoubleClickEvent(), null, null));

            return obj;
        }

        @Override
        public ButtonEventData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == JsonNull.INSTANCE || !(json instanceof JsonObject))
                return null;
            JsonObject obj = (JsonObject) json;

            ButtonEventData data = new ButtonEventData();

            data.setPointerFollow(Optional.ofNullable(get(obj, "pointerFollow")).map(JsonElement::getAsBoolean).orElse(false));
            data.setMovable(Optional.ofNullable(get(obj, "Movable")).map(JsonElement::getAsBoolean).orElse(false));
            data.setSwipable(Optional.ofNullable(get(obj, "swipable")).map(JsonElement::getAsBoolean).orElse(false));
            data.setPressEvent(Optional.ofNullable(get(obj, "pressEvent")).map(JsonElement::getAsJsonObject).map(event -> new Event.Serializer().deserialize(event, null, null)).orElseGet(Event::new));
            data.setLongPressEvent(Optional.ofNullable(get(obj, "longPressEvent")).map(JsonElement::getAsJsonObject).map(event -> new Event.Serializer().deserialize(event, null, null)).orElseGet(Event::new));
            data.setClickEvent(Optional.ofNullable(get(obj, "clickEvent")).map(JsonElement::getAsJsonObject).map(event -> new Event.Serializer().deserialize(event, null, null)).orElseGet(Event::new));
            data.setDoubleClickEvent(Optional.ofNullable(get(obj, "doubleClickEvent")).map(JsonElement::getAsJsonObject).map(event -> new Event.Serializer().deserialize(event, null, null)).orElseGet(Event::new));

            return data;
        }
    }

    @JsonAdapter(Event.Serializer.class)
    public static class Event implements Cloneable, Observable {

        /**
         * Keep pressing
         */
        private final BooleanProperty autoKeepProperty = new SimpleBooleanProperty(this, "autoKeep", false);

        public BooleanProperty autoKeepProperty() {
            return autoKeepProperty;
        }

        public void setAutoKeep(boolean autoKeep) {
            autoKeepProperty.set(autoKeep);
        }

        public boolean isAutoKeep() {
            return autoKeepProperty.get();
        }

        /**
         * Keep clicking
         */
        private final BooleanProperty autoClickProperty = new SimpleBooleanProperty(this, "autoClick", false);

        public BooleanProperty autoClickProperty() {
            return autoClickProperty;
        }

        public void setAutoClick(boolean autoClick) {
            autoClickProperty.set(autoClick);
        }

        public boolean isAutoClick() {
            return autoClickProperty.get();
        }

        /**
         * Open menu
         */
        private final BooleanProperty openMenuProperty = new SimpleBooleanProperty(this, "openMenu", false);

        public BooleanProperty openMenuProperty() {
            return openMenuProperty;
        }

        public void setOpenMenu(boolean openMenu) {
            openMenuProperty.set(openMenu);
        }

        public boolean isOpenMenu() {
            return openMenuProperty.get();
        }

        /**
         * Switch touch mode
         */
        private final BooleanProperty switchTouchModeProperty = new SimpleBooleanProperty(this, "switchTouchMode", false);

        public BooleanProperty switchTouchModeProperty() {
            return switchTouchModeProperty;
        }

        public void setSwitchTouchMode(boolean switchTouchMode) {
            switchTouchModeProperty.set(switchTouchMode);
        }

        public boolean isSwitchTouchMode() {
            return switchTouchModeProperty.get();
        }

        /**
         * Switch mouse mode
         */
        private final BooleanProperty switchMouseModeProperty = new SimpleBooleanProperty(this, "switchMouseMode", false);

        public BooleanProperty switchMouseModeProperty() {
            return switchMouseModeProperty;
        }

        public void setSwitchMouseMode(boolean switchMouseMode) {
            switchMouseModeProperty.set(switchMouseMode);
        }

        public boolean isSwitchMouseMode() {
            return switchMouseModeProperty.get();
        }

        /**
         * Input words
         */
        private final BooleanProperty inputProperty = new SimpleBooleanProperty(this, "input", false);

        public BooleanProperty inputProperty() {
            return inputProperty;
        }

        public void setInput(boolean input) {
            inputProperty.set(input);
        }

        public boolean isInput() {
            return inputProperty.get();
        }

        /**
         * Open quick input dialog
         */
        private final BooleanProperty quickInputProperty = new SimpleBooleanProperty(this, "quickInput", false);

        public BooleanProperty quickInputProperty() {
            return quickInputProperty;
        }

        public void setQuickInput(boolean quickInput) {
            quickInputProperty.set(quickInput);
        }

        public boolean isQuickInput() {
            return quickInputProperty.get();
        }

        /**
         * Output text
         */
        /**
         * 触发时读取系统剪贴板内容并提交到聊天栏。
         *
         * 与 outputText 的区别：outputText 是预先写死在布局里的文本，
         * 这一项取的是按下那一刻剪贴板里的实际内容，玩家可以先在系统别处复制好，
         * 进游戏按一下就粘进去。两者同时存在时以剪贴板内容优先。
         */
        private final BooleanProperty pasteClipboardProperty = new SimpleBooleanProperty(this, "pasteClipboard", false);

        public BooleanProperty pasteClipboardProperty() {
            return pasteClipboardProperty;
        }

        public void setPasteClipboard(boolean pasteClipboard) {
            pasteClipboardProperty.set(pasteClipboard);
        }

        public boolean isPasteClipboard() {
            return pasteClipboardProperty.get();
        }

        /**
         * 触发时把按钮文字切到下一套（多字符模式）。
         *
         * 只在预置了多套文字时才有实际效果；只有一套时按下不产生任何变化，
         * 也不提示 —— 提示会让没用过这个功能的玩家困惑。
         */
        private final BooleanProperty switchTextProperty = new SimpleBooleanProperty(this, "switchText", false);

        public BooleanProperty switchTextProperty() {
            return switchTextProperty;
        }

        public void setSwitchText(boolean switchText) {
            switchTextProperty.set(switchText);
        }

        public boolean isSwitchText() {
            return switchTextProperty.get();
        }

        /**
         * 粘贴前是否先收起输入法。
         *
         * 聊天栏开着时输入法正弹着，直接粘贴内容容易被输入法顶开或吞掉，
         * 所以默认先收一次。但有些玩家此刻正是想用输入法打字，
         * 强行收掉会打断他 —— 关掉这一项就不再收输入法，按下即粘贴。
         */
        private final BooleanProperty pasteDismissImeProperty = new SimpleBooleanProperty(this, "pasteDismissIme", true);

        public BooleanProperty pasteDismissImeProperty() {
            return pasteDismissImeProperty;
        }

        public void setPasteDismissIme(boolean pasteDismissIme) {
            pasteDismissImeProperty.set(pasteDismissIme);
        }

        public boolean isPasteDismissIme() {
            return pasteDismissImeProperty.get();
        }

        private final StringProperty outputTextProperty = new SimpleStringProperty(this, "outputText", "");

        public StringProperty outputTextProperty() {
            return outputTextProperty;
        }

        public void setOutputText(String outputText) {
            outputTextProperty.set(outputText);
        }

        public String getOutputText() {
            return outputTextProperty.get();
        }

        /**
         * Output keycodes
         */
        private final ObservableList<Integer> outputKeycodesList = FXCollections.observableList(new ArrayList<>());

        public ObservableList<Integer> outputKeycodesList() {
            return outputKeycodesList;
        }

        public void setOutputKeycodes(ObservableList<Integer> outputKeycodes) {
            outputKeycodesList.setAll(outputKeycodes);
        }

        /**
         * Switch view group visibility
         */
        private final ObservableList<String> bindViewGroupList = FXCollections.observableList(new ArrayList<>());

        public ObservableList<String> bindViewGroupList() {
            return bindViewGroupList;
        }

        public void setBindViewGroup(ObservableList<String> bindViewGroup) {
            bindViewGroupList.setAll(bindViewGroup);
        }

        public Event() {
            addPropertyChangedListener(onInvalidating(this::invalidate));
        }

        public void addPropertyChangedListener(InvalidationListener listener) {
            autoKeepProperty.addListener(listener);
            autoClickProperty.addListener(listener);
            openMenuProperty.addListener(listener);
            switchTouchModeProperty.addListener(listener);
            switchMouseModeProperty.addListener(listener);
            inputProperty.addListener(listener);
            quickInputProperty.addListener(listener);
            pasteClipboardProperty.addListener(listener);
            switchTextProperty.addListener(listener);
            pasteDismissImeProperty.addListener(listener);
            outputTextProperty.addListener(listener);
            outputKeycodesList.addListener(listener);
            bindViewGroupList.addListener(listener);
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
            observableHelper.invalidate();
        }

        @Override
        public Event clone() {
            Event event = new Event();
            event.setAutoKeep(isAutoKeep());
            event.setAutoClick(isAutoClick());
            event.setOpenMenu(isOpenMenu());
            event.setSwitchTouchMode(isSwitchTouchMode());
            event.setSwitchMouseMode(isSwitchMouseMode());
            event.setInput(isInput());
            event.setQuickInput(isQuickInput());
            event.setPasteClipboard(isPasteClipboard());
            event.setSwitchText(isSwitchText());
            event.setPasteDismissIme(isPasteDismissIme());
            event.setOutputText(getOutputText());
            event.setOutputKeycodes(outputKeycodesList());
            event.setBindViewGroup(bindViewGroupList());
            return event;
        }

        public static class Serializer implements JsonSerializer<Event>, JsonDeserializer<Event> {
            @Override
            public JsonElement serialize(Event src, Type typeOfSrc, JsonSerializationContext context) {
                if (src == null) return JsonNull.INSTANCE;
                JsonObject obj = new JsonObject();

                obj.addProperty("autoKeep", src.isAutoKeep());
                obj.addProperty("autoClick", src.isAutoClick());
                obj.addProperty("openMenu", src.isOpenMenu());
                obj.addProperty("switchTouchMode", src.isSwitchTouchMode());
                obj.addProperty("switchMouseMode", src.isSwitchMouseMode());
                obj.addProperty("input", src.isInput());
                obj.addProperty("quickInput", src.isQuickInput());
                obj.addProperty("pasteClipboard", src.isPasteClipboard());
                obj.addProperty("switchText", src.isSwitchText());
                obj.addProperty("pasteDismissIme", src.isPasteDismissIme());
                obj.addProperty("outputText", src.getOutputText());
                JsonArray outputKeycodes = new JsonArray();
                for (Integer code : src.outputKeycodesList()) {
                    if (code != null) outputKeycodes.add(code);
                }
                obj.add("outputKeycodes", outputKeycodes);
                JsonArray bindViewGroup = new JsonArray();
                for (String group : src.bindViewGroupList()) {
                    if (group != null) bindViewGroup.add(group);
                }
                obj.add("bindViewGroup", bindViewGroup);

                return obj;
            }

            @Override
            public Event deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                if (json == JsonNull.INSTANCE || !(json instanceof JsonObject))
                    return null;
                JsonObject obj = (JsonObject) json;

                Event event = new Event();

                event.setAutoKeep(Optional.ofNullable(get(obj, "autoKeep")).map(JsonElement::getAsBoolean).orElse(false));
                event.setAutoClick(Optional.ofNullable(get(obj, "autoClick")).map(JsonElement::getAsBoolean).orElse(false));
                event.setOpenMenu(Optional.ofNullable(get(obj, "openMenu")).map(JsonElement::getAsBoolean).orElse(false));
                event.setSwitchTouchMode(Optional.ofNullable(get(obj, "switchTouchMode")).map(JsonElement::getAsBoolean).orElse(false));
                event.setSwitchMouseMode(Optional.ofNullable(get(obj, "switchMouseMode")).map(JsonElement::getAsBoolean).orElse(false));
                event.setInput(Optional.ofNullable(get(obj, "input")).map(JsonElement::getAsBoolean).orElse(false));
                event.setQuickInput(Optional.ofNullable(get(obj, "quickInput")).map(JsonElement::getAsBoolean).orElse(false));
                event.setPasteClipboard(Optional.ofNullable(get(obj, "pasteClipboard")).map(JsonElement::getAsBoolean).orElse(false));
                event.setSwitchText(Optional.ofNullable(get(obj, "switchText")).map(JsonElement::getAsBoolean).orElse(false));
                // 旧布局没有这一项，默认收输入法，与加这项之前的行为一致
                event.setPasteDismissIme(Optional.ofNullable(get(obj, "pasteDismissIme")).map(JsonElement::getAsBoolean).orElse(true));
                event.setOutputText(Optional.ofNullable(get(obj, "outputText")).map(JsonElement::getAsString).orElse(""));
                ArrayList<Integer> outputKeycodes = new ArrayList<>();
                for (JsonElement element : Optional.ofNullable(get(obj, "outputKeycodes")).map(JsonElement::getAsJsonArray).orElseGet(JsonArray::new)) {
                    if (element.isJsonPrimitive()) outputKeycodes.add(element.getAsInt());
                }
                event.setOutputKeycodes(FXCollections.observableList(outputKeycodes));
                ArrayList<String> bindViewGroup = new ArrayList<>();
                for (JsonElement element : Optional.ofNullable(get(obj, "bindViewGroup")).map(JsonElement::getAsJsonArray).orElseGet(JsonArray::new)) {
                    if (element.isJsonPrimitive()) bindViewGroup.add(element.getAsString());
                }
                event.setBindViewGroup(FXCollections.observableList(bindViewGroup));

                return event;
            }
        }
        
    }

}
