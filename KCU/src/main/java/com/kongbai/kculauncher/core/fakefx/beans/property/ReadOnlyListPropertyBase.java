package com.kongbai.kculauncher.core.fakefx.beans.property;

import com.kongbai.kculauncher.core.fakefx.beans.InvalidationListener;
import com.kongbai.kculauncher.core.fakefx.beans.value.ChangeListener;
import com.kongbai.kculauncher.core.fakefx.binding.ListExpressionHelper;
import com.kongbai.kculauncher.core.fakefx.collections.ListChangeListener;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableList;

public abstract class ReadOnlyListPropertyBase<E> extends ReadOnlyListProperty<E> {

    private ListExpressionHelper<E> helper;

    /**
     * Creates a default {@code ReadOnlyListPropertyBase}.
     */
    public ReadOnlyListPropertyBase() {
    }

    @Override
    public void addListener(InvalidationListener listener) {
        helper = ListExpressionHelper.addListener(helper, this, listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        helper = ListExpressionHelper.removeListener(helper, listener);
    }

    @Override
    public void addListener(ChangeListener<? super ObservableList<E>> listener) {
        helper = ListExpressionHelper.addListener(helper, this, listener);
    }

    @Override
    public void removeListener(ChangeListener<? super ObservableList<E>> listener) {
        helper = ListExpressionHelper.removeListener(helper, listener);
    }

    @Override
    public void addListener(ListChangeListener<? super E> listener) {
        helper = ListExpressionHelper.addListener(helper, this, listener);
    }

    @Override
    public void removeListener(ListChangeListener<? super E> listener) {
        helper = ListExpressionHelper.removeListener(helper, listener);
    }

    protected void fireValueChangedEvent() {
        ListExpressionHelper.fireValueChangedEvent(helper);
    }

    protected void fireValueChangedEvent(ListChangeListener.Change<? extends E> change) {
        ListExpressionHelper.fireValueChangedEvent(helper, change);
    }



}
