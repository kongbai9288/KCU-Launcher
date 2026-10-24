package com.kongbai.kculauncher.core.util.gson.fakefx.factories;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.kongbai.kculauncher.core.fakefx.beans.property.BooleanProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.DoubleProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.FloatProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.IntegerProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.ListProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.LongProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.MapProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.Property;
import com.kongbai.kculauncher.core.fakefx.beans.property.SetProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.StringProperty;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableList;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableMap;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableSet;
import com.kongbai.kculauncher.core.util.gson.fakefx.properties.ListPropertyTypeAdapter;
import com.kongbai.kculauncher.core.util.gson.fakefx.properties.MapPropertyTypeAdapter;
import com.kongbai.kculauncher.core.util.gson.fakefx.properties.ObjectPropertyTypeAdapter;
import com.kongbai.kculauncher.core.util.gson.fakefx.properties.SetPropertyTypeAdapter;
import com.kongbai.kculauncher.core.util.gson.fakefx.properties.StringPropertyTypeAdapter;
import com.kongbai.kculauncher.core.util.gson.fakefx.properties.primitives.BooleanPropertyTypeAdapter;
import com.kongbai.kculauncher.core.util.gson.fakefx.properties.primitives.DoublePropertyTypeAdapter;
import com.kongbai.kculauncher.core.util.gson.fakefx.properties.primitives.FloatPropertyTypeAdapter;
import com.kongbai.kculauncher.core.util.gson.fakefx.properties.primitives.IntegerPropertyTypeAdapter;
import com.kongbai.kculauncher.core.util.gson.fakefx.properties.primitives.LongPropertyTypeAdapter;

public class JavaFxPropertyTypeAdapterFactory implements TypeAdapterFactory {

    private final boolean strictProperties;

    private final boolean strictPrimitives;

    /**
     * Creates a new JavaFxPropertyTypeAdapterFactory. This default factory forbids null properties and null values for
     * primitive properties.
     *
     * @see #JavaFxPropertyTypeAdapterFactory(boolean, boolean)
     */
    public JavaFxPropertyTypeAdapterFactory() {
        this(true, true);
    }

    public JavaFxPropertyTypeAdapterFactory(boolean throwOnNullProperties, boolean throwOnNullPrimitives) {
        this.strictProperties = throwOnNullProperties;
        this.strictPrimitives = throwOnNullPrimitives;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
        Class<? super T> clazz = type.getRawType();

        // this factory only handles JavaFX property types
        if (!Property.class.isAssignableFrom(clazz)) {
            return null;
        }

        // simple property types

        if (BooleanProperty.class.isAssignableFrom(clazz)) {
            return (TypeAdapter<T>) new BooleanPropertyTypeAdapter(gson.getAdapter(boolean.class), strictProperties,
                    strictPrimitives);
        }
        if (IntegerProperty.class.isAssignableFrom(clazz)) {
            return (TypeAdapter<T>) new IntegerPropertyTypeAdapter(gson.getAdapter(int.class), strictProperties,
                    strictPrimitives);
        }
        if (LongProperty.class.isAssignableFrom(clazz)) {
            return (TypeAdapter<T>) new LongPropertyTypeAdapter(gson.getAdapter(long.class), strictProperties,
                    strictPrimitives);
        }
        if (FloatProperty.class.isAssignableFrom(clazz)) {
            return (TypeAdapter<T>) new FloatPropertyTypeAdapter(gson.getAdapter(float.class), strictProperties,
                    strictPrimitives);
        }
        if (DoubleProperty.class.isAssignableFrom(clazz)) {
            return (TypeAdapter<T>) new DoublePropertyTypeAdapter(gson.getAdapter(double.class), strictProperties,
                    strictPrimitives);
        }
        if (StringProperty.class.isAssignableFrom(clazz)) {
            return (TypeAdapter<T>) new StringPropertyTypeAdapter(gson.getAdapter(String.class), strictProperties);
        }

        // collection property types

        if (ListProperty.class.isAssignableFrom(clazz)) {
            TypeAdapter<?> delegate = gson.getAdapter(TypeHelper.withRawType(type, ObservableList.class));
            return new ListPropertyTypeAdapter(delegate, strictProperties);
        }
        if (SetProperty.class.isAssignableFrom(clazz)) {
            TypeAdapter<?> delegate = gson.getAdapter(TypeHelper.withRawType(type, ObservableSet.class));
            return new SetPropertyTypeAdapter(delegate, strictProperties);
        }
        if (MapProperty.class.isAssignableFrom(clazz)) {
            TypeAdapter<?> delegate = gson.getAdapter(TypeHelper.withRawType(type, ObservableMap.class));
            return new MapPropertyTypeAdapter(delegate, strictProperties);
        }

        // generic Property<?> type

        Type[] typeParams = ((ParameterizedType) type.getType()).getActualTypeArguments();
        Type param = typeParams[0];
        // null factory skipPast because the nested type argument might also be a Property
        TypeAdapter<?> delegate = gson.getAdapter(TypeToken.get(param));
        return (TypeAdapter<T>) new ObjectPropertyTypeAdapter<>(delegate, strictProperties);
    }
}