package com.kongbai.kculauncher.ui.manage;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.core.fakefx.beans.InvalidationListener;
import com.kongbai.kculauncher.core.fakefx.beans.property.BooleanProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.ListProperty;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleListProperty;
import com.kongbai.kculauncher.core.fakefx.collections.FXCollections;
import com.kongbai.kculauncher.library.component.KCUAdapter;
import com.kongbai.kculauncher.library.component.theme.ThemeEngine;
import com.kongbai.kculauncher.library.component.view.KCUCheckBox;
import com.kongbai.kculauncher.library.component.view.KCULinearLayout;
import com.kongbai.kculauncher.library.component.view.KCUTextView;

public class DatapackListAdapter extends KCUAdapter {

    private final ListProperty<DatapackListPage.DatapackInfoObject> listProperty = new SimpleListProperty<>(FXCollections.observableArrayList());
    private final ListProperty<DatapackListPage.DatapackInfoObject> selectedItemsProperty = new SimpleListProperty<>(FXCollections.observableArrayList());

    public ListProperty<DatapackListPage.DatapackInfoObject> listProperty() {
        return listProperty;
    }

    public ListProperty<DatapackListPage.DatapackInfoObject> selectedItemsProperty() {
        return selectedItemsProperty;
    }

    private boolean fromSelf = false;

    public DatapackListAdapter(Context context) {
        super(context);

        Handler handler = new Handler();

        this.listProperty.addListener((InvalidationListener) observable -> {
            fromSelf = true;
            selectedItemsProperty.clear();
            fromSelf = false;
            handler.post(this::notifyDataSetChanged);
        });
        selectedItemsProperty.addListener((InvalidationListener) observable -> {
            if (!fromSelf) {
                handler.post(this::notifyDataSetChanged);
            }
        });
    }

    private static class ViewHolder {
        KCULinearLayout parent;
        KCUCheckBox checkBox;
        KCUTextView name;
        KCUTextView description;
        BooleanProperty booleanProperty;
    }

    @Override
    public int getCount() {
        return listProperty.getSize();
    }

    @Override
    public Object getItem(int i) {
        return listProperty.get(i);
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        final ViewHolder viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder();
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_datapack, null);
            viewHolder.parent = view.findViewById(R.id.parent);
            viewHolder.checkBox = view.findViewById(R.id.check);
            viewHolder.name = view.findViewById(R.id.name);
            viewHolder.description = view.findViewById(R.id.description);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        DatapackListPage.DatapackInfoObject datapackInfoObject = listProperty.get(i);
        viewHolder.parent.setBackgroundTintList(new ColorStateList(new int[][] { { } }, new int[] { selectedItemsProperty.contains(datapackInfoObject) ? ThemeEngine.getInstance().getTheme().getColor() : ThemeEngine.getInstance().getTheme().getLtColor() }));
        ThemeEngine.getInstance().registerEvent(viewHolder.parent, () -> viewHolder.parent.setBackgroundTintList(new ColorStateList(new int[][] { { } }, new int[] { selectedItemsProperty.contains(datapackInfoObject) ? ThemeEngine.getInstance().getTheme().getColor() : ThemeEngine.getInstance().getTheme().getLtColor() })));
        viewHolder.parent.setOnClickListener(v -> {
            if (selectedItemsProperty.contains(datapackInfoObject)) {
                fromSelf = true;
                selectedItemsProperty.remove(datapackInfoObject);
                fromSelf = false;
                viewHolder.parent.setBackgroundTintList(new ColorStateList(new int[][] { { } }, new int[] { ThemeEngine.getInstance().getTheme().getLtColor() }));
            } else {
                fromSelf = true;
                selectedItemsProperty.add(datapackInfoObject);
                fromSelf = false;
                viewHolder.parent.setBackgroundTintList(new ColorStateList(new int[][] { { } }, new int[] { ThemeEngine.getInstance().getTheme().getColor() }));
            }
        });
        viewHolder.checkBox.addCheckedChangeListener();
        if (viewHolder.booleanProperty != null) {
            viewHolder.checkBox.checkProperty().unbindBidirectional(viewHolder.booleanProperty);
        }
        viewHolder.checkBox.checkProperty().bindBidirectional(viewHolder.booleanProperty = datapackInfoObject.getActive());
        viewHolder.name.setText(datapackInfoObject.getTitle());
        viewHolder.description.setText(datapackInfoObject.getSubtitle());
        return view;
    }
}
