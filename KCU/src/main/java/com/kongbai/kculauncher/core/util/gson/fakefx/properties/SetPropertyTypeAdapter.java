package com.kongbai.kculauncher.core.util.gson.fakefx.properties;

import com.google.gson.TypeAdapter;
import com.kongbai.kculauncher.core.fakefx.beans.property.SetProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleSetProperty;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableSet;

import org.jetbrains.annotations.NotNull;

/**
 * A basic {@link TypeAdapter} for JavaFX {@link SetProperty}. It serializes the set inside the property instead of the
 * property itself.
 */
public class SetPropertyTypeAdapter<T> extends PropertyTypeAdapter<ObservableSet<T>, SetProperty<T>> {

    public SetPropertyTypeAdapter(TypeAdapter<ObservableSet<T>> delegate, boolean throwOnNullProperty) {
        super(delegate, throwOnNullProperty);
    }

    @NotNull
    @Override
    protected SetProperty<T> createProperty(ObservableSet<T> deserializedValue) {
        return new SimpleSetProperty<>(deserializedValue);
    }
}