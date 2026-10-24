package com.kongbai.kculauncher.control.data;

import static com.kongbai.kculauncher.util.FXUtils.onInvalidating;
import static com.kongbai.kculauncher.core.fakefx.collections.FXCollections.observableArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.kongbai.kculauncher.launcher.utils.KCUPath;
import com.kongbai.kculauncher.core.fakefx.beans.property.ReadOnlyListProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.ReadOnlyListWrapper;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableList;
import com.kongbai.kculauncher.core.util.Logging;
import com.kongbai.kculauncher.core.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;

public class QuickInputTexts {

    private QuickInputTexts() {
    }

    private static final ObservableList<String> inputTexts = observableArrayList(new ArrayList<>());
    private static final ReadOnlyListWrapper<String> inputTextsWrapper = new ReadOnlyListWrapper<>(inputTexts);
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * True if {@link #init()} hasn't been called.
     */
    private static boolean initialized = false;

    public static boolean isInitialized() {
        return initialized;
    }

    private static void updateInputTextsStorages() {
        // don't update the underlying storage before data loading is completed
        // otherwise it might cause data loss
        if (!initialized)
            return;
        // update storage
        saveInputTexts();
    }

    static {
        inputTexts.addListener(onInvalidating(QuickInputTexts::updateInputTextsStorages));
    }

    public static void init() {
        if (initialized)
            throw new IllegalStateException("Already initialized");

        inputTexts.addAll(getInputTextsFromDisk());

        initialized = true;
    }

    private static ArrayList<String> getInputTextsFromDisk() {
        try {
            File file = new File(KCUPath.CONTROLLER_DIR + "/input/input_text.json");
            if (file.exists()) {
                String json = FileUtils.readText(file);
                JsonElement element = JsonParser.parseString(json);
                if (element.isJsonArray()) {
                    ArrayList<String> list = new ArrayList<>();
                    for (JsonElement item : element.getAsJsonArray()) {
                        if (item.isJsonPrimitive()) list.add(item.getAsString());
                    }
                    return list;
                }
            }
        } catch (IOException e) {
            Logging.LOG.log(Level.SEVERE, "Failed to get quick input text", e);
        } catch (JsonSyntaxException e) {
            new File(KCUPath.CONTROLLER_DIR + "/input/input_text.json").delete();
        }
        return new ArrayList<>();
    }

    public static ObservableList<String> getInputTexts() {
        return inputTexts;
    }

    public static ReadOnlyListProperty<String> inputTextsProperty() {
        return inputTextsWrapper.getReadOnlyProperty();
    }

    public static void saveInputTexts() {
        JsonArray array = new JsonArray();
        for (String text : inputTexts) {
            array.add(text);
        }
        String json = PRETTY_GSON.toJson(array);
        try {
            FileUtils.writeText(new File(KCUPath.CONTROLLER_DIR + "/input/input_text.json"), json);
        } catch (IOException e) {
            Logging.LOG.log(Level.SEVERE, "Failed to save quick input text", e);
        }
    }

    public static void addInputText(String inputText) {
        if (!initialized) return;
        inputTexts.add(inputText);
    }

    public static void removeInputText(String inputText) {
        if (!initialized) return;
        inputTexts.remove(inputText);
    }

}
