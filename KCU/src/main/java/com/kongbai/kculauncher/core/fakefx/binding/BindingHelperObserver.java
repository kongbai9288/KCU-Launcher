package com.kongbai.kculauncher.core.fakefx.binding;

import com.kongbai.kculauncher.core.fakefx.beans.InvalidationListener;
import com.kongbai.kculauncher.core.fakefx.beans.Observable;
import com.kongbai.kculauncher.core.fakefx.beans.WeakListener;
import com.kongbai.kculauncher.core.fakefx.beans.binding.Binding;

import java.lang.ref.WeakReference;

public class BindingHelperObserver implements InvalidationListener, WeakListener {

    private final WeakReference<Binding<?>> ref;

    public BindingHelperObserver(Binding<?> binding) {
        if (binding == null) {
            throw new NullPointerException("Binding has to be specified.");
        }
        ref = new WeakReference<Binding<?>>(binding);
    }

    @Override
    public void invalidated(Observable observable) {
        final Binding<?> binding = ref.get();
        if (binding == null) {
            observable.removeListener(this);
        } else {
            binding.invalidate();
        }
    }

    @Override
    public boolean wasGarbageCollected() {
        return ref.get() == null;
    }
}
