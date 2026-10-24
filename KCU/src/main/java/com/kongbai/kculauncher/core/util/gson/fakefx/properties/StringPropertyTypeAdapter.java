package com.kongbai.kculauncher.core.util.gson.fakefx.properties;

import com.google.gson.TypeAdapter;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleStringProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.StringProperty;

import org.jetbrains.annotations.NotNull;

/**
 * A basic {@link TypeAdapter} for JavaFX {@link StringProperty}. It serializes the string inside the property instead
 * of the property itself.
 */
public class StringPropertyTypeAdapter extends PropertyTypeAdapter<String, StringProperty> {

    public StringPropertyTypeAdapter(TypeAdapter<String> delegate, boolean throwOnNullProperty) {
        super(delegate, throwOnNullProperty);
    }

    @NotNull
    @Override
    protected StringProperty createProperty(String deserializedValue) {
        return new SimpleStringProperty(deserializedValue);
    }
}