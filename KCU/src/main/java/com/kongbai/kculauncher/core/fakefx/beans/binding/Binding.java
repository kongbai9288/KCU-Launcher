package com.kongbai.kculauncher.core.fakefx.beans.binding;

import com.kongbai.kculauncher.core.fakefx.beans.value.ObservableValue;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableList;

public interface Binding<T> extends ObservableValue<T> {

    boolean isValid();

    void invalidate();

    ObservableList<?> getDependencies();

    void dispose();

}
