package com.kongbai.kculauncher.library.component;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.kongbai.kculauncher.core.fakefx.beans.InvalidationListener;
import com.kongbai.kculauncher.core.fakefx.beans.binding.Bindings;
import com.kongbai.kculauncher.core.fakefx.beans.property.SimpleBooleanProperty;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableList;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.library.component.view.KCUCheckBox;
import com.kongbai.kculauncher.library.component.view.KCUImageButton;
import com.kongbai.kculauncher.library.component.view.KCULinearLayout;
import com.kongbai.kculauncher.library.component.view.KCUTextView;

public class KCUCheckBoxTreeAdapter<T> extends KCUAdapter {

    private final ObservableList<KCUCheckBoxTreeItem<T>> list;

    private final SimpleBooleanProperty checkHeightProperty = new SimpleBooleanProperty(false);

    public SimpleBooleanProperty checkHeightProperty() {
        return checkHeightProperty;
    }

    public void setCheckHeight(boolean checkHeight) {
        checkHeightProperty.set(checkHeight);
    }

    public boolean isCheckHeight() {
        return checkHeightProperty.get();
    }

    public KCUCheckBoxTreeAdapter(Context context, ObservableList<KCUCheckBoxTreeItem<T>> list) {
        super(context);
        this.list = list;
    }

    private static class ViewHolder<T> {
        KCULinearLayout main;
        KCUImageButton switchView;
        KCUCheckBox checkBox;
        KCUTextView textView;
        KCUTextView comment;
        ListView listView;
        // 当前绑定的数据项；条目复用时必须先对旧数据项解绑，否则监听器随滚动无限累积
        KCUCheckBoxTreeItem<T> item;
        InvalidationListener expandedListener;
        InvalidationListener childCheckListener;
    }

    @Override
    public int getCount() {
        return list.size();
    }

    @Override
    public Object getItem(int i) {
        return list.get(i);
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        final ViewHolder<T> viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder<>();
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_check_box_tree, null);
            viewHolder.main = view.findViewById(R.id.main);
            viewHolder.switchView = view.findViewById(R.id.switch_view);
            viewHolder.checkBox = view.findViewById(R.id.check);
            viewHolder.textView = view.findViewById(R.id.text);
            viewHolder.comment = view.findViewById(R.id.comment);
            viewHolder.listView = view.findViewById(R.id.list);
            // 展开/收起：切换嵌套列表可见性、重算本行高度并向上传递
            viewHolder.expandedListener = observable -> {
                KCUCheckBoxTreeItem<T> item = viewHolder.item;
                if (item == null)
                    return;
                boolean visible = item.isExpanded() && item.getSubItem().size() > 0;
                viewHolder.listView.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (visible)
                    updateListHeight(viewHolder);
                setCheckHeight(true);
            };
            // 子级高度变化：重算本行高度并继续向上传递
            viewHolder.childCheckListener = observable -> {
                if (viewHolder.listView.getAdapter() instanceof KCUCheckBoxTreeAdapter
                        && ((KCUCheckBoxTreeAdapter<?>) viewHolder.listView.getAdapter()).isCheckHeight()) {
                    ((KCUCheckBoxTreeAdapter<?>) viewHolder.listView.getAdapter()).setCheckHeight(false);
                    updateListHeight(viewHolder);
                    setCheckHeight(true);
                }
            };
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder<T>) view.getTag();
        }

        KCUCheckBoxTreeItem<T> item = list.get(i);
        // 同一数据项重复布局时无需重绑
        if (viewHolder.item == item)
            return view;

        unbindItem(viewHolder);
        viewHolder.item = item;

        viewHolder.switchView.setVisibility(item.getSubItem().size() == 0 ? View.INVISIBLE : View.VISIBLE);
        viewHolder.switchView.imageProperty().bind(Bindings.createObjectBinding(() -> getContext().getDrawable(item.isExpanded() ? R.drawable.ic_baseline_arrow_drop_down_24 : R.drawable.ic_baseline_arrow_right_24), item.expandedProperty()));
        viewHolder.switchView.setOnClickListener(v -> item.setExpanded(!item.isExpanded()));

        viewHolder.checkBox.addCheckedChangeListener();
        // 双向绑定在绑定时会把数据项的值同步到复用前的复选框上
        viewHolder.checkBox.checkProperty().bindBidirectional(item.selectedProperty());
        viewHolder.checkBox.indeterminateProperty().bindBidirectional(item.indeterminateProperty());

        viewHolder.textView.setText(item.getText());
        viewHolder.comment.setVisibility(item.getComment() == null ? View.GONE : View.VISIBLE);
        if (item.getComment() != null)
            viewHolder.comment.setText(item.getComment());

        item.expandedProperty().addListener(viewHolder.expandedListener);

        if (item.getSubItem().size() > 0) {
            viewHolder.main.post(() -> {
                // post 执行前条目可能已被复用到其他数据项
                if (viewHolder.item != item)
                    return;
                KCUCheckBoxTreeAdapter<T> adapter = new KCUCheckBoxTreeAdapter<>(getContext(), item.getSubItem());
                viewHolder.listView.setAdapter(adapter);
                adapter.checkHeightProperty().addListener(viewHolder.childCheckListener);
                boolean visible = item.isExpanded() && item.getSubItem().size() > 0;
                viewHolder.listView.setVisibility(visible ? View.VISIBLE : View.GONE);
                if (visible)
                    updateListHeight(viewHolder);
            });
        } else {
            viewHolder.listView.setAdapter(null);
        }
        return view;
    }

    /** 条目复用前解绑上一个数据项的监听与双向绑定，避免滚动时累积导致内存溢出 */
    private void unbindItem(ViewHolder<T> viewHolder) {
        if (viewHolder.item != null) {
            viewHolder.checkBox.checkProperty().unbindBidirectional(viewHolder.item.selectedProperty());
            viewHolder.checkBox.indeterminateProperty().unbindBidirectional(viewHolder.item.indeterminateProperty());
            viewHolder.item.expandedProperty().removeListener(viewHolder.expandedListener);
        }
        if (viewHolder.listView.getAdapter() instanceof KCUCheckBoxTreeAdapter)
            ((KCUCheckBoxTreeAdapter<?>) viewHolder.listView.getAdapter()).checkHeightProperty().removeListener(viewHolder.childCheckListener);
        viewHolder.listView.setAdapter(null);
    }

    /** 按展开的子孙条目数重算嵌套列表高度 */
    private void updateListHeight(ViewHolder<T> viewHolder) {
        KCUCheckBoxTreeItem<T> item = viewHolder.item;
        if (item == null || item.getSubItem().isEmpty())
            return;
        ViewGroup.LayoutParams layoutParams = viewHolder.listView.getLayoutParams();
        int dividerHeight = viewHolder.listView.getDividerHeight();
        int baseHeight = viewHolder.main.getMeasuredHeight();
        new Thread(() -> {
            int height = getListViewHeight(item, dividerHeight, baseHeight);
            Schedulers.androidUIThread().execute(() -> {
                layoutParams.height = height;
                viewHolder.listView.setLayoutParams(layoutParams);
            });
        }).start();
    }

    public static int getListViewHeight(KCUCheckBoxTreeItem<?> item, int splitSize, int baseHeight) {
        int count = getSubItemCount(item);
        return (baseHeight * count) + (splitSize * (count - 1));
    }

    public static int getSubItemCount(KCUCheckBoxTreeItem<?> item) {
        int count = item.isExpanded() ? item.getSubItem().size() : 0;
        if (item.isExpanded()) {
            for (KCUCheckBoxTreeItem<?> subItem : item.getSubItem()) {
                count += getSubItemCount(subItem);
            }
        }
        return count;
    }
}
