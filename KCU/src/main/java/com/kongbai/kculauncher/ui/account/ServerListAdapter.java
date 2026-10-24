package com.kongbai.kculauncher.ui.account;

import static com.kongbai.kculauncher.setting.ConfigHolder.config;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.setting.Accounts;
import com.kongbai.kculauncher.ui.UIManager;
import com.kongbai.kculauncher.core.auth.authlibinjector.AuthlibInjectorServer;
import com.kongbai.kculauncher.core.fakefx.beans.InvalidationListener;
import com.kongbai.kculauncher.core.fakefx.collections.ObservableList;
import com.kongbai.kculauncher.core.task.Schedulers;
import com.kongbai.kculauncher.library.component.KCUAdapter;
import com.kongbai.kculauncher.library.component.dialog.KCUAlertDialog;
import com.kongbai.kculauncher.library.component.view.KCUImageButton;
import com.kongbai.kculauncher.library.component.view.KCUTextView;

public class ServerListAdapter extends KCUAdapter {

    private final ObservableList<AuthlibInjectorServer> list;

    public ServerListAdapter(Context context) {
        super(context);
        list = config().getAuthlibInjectorServers();

        list.addListener((InvalidationListener) i -> Schedulers.androidUIThread().execute(this::notifyDataSetChanged));
    }

    static class ViewHolder {
        ConstraintLayout parent;
        KCUTextView name;
        KCUTextView url;
        KCUImageButton delete;
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
        final ViewHolder viewHolder;
        if (view == null) {
            viewHolder = new ViewHolder();
            view = LayoutInflater.from(getContext()).inflate(R.layout.item_authlib_injector_server, null);
            viewHolder.parent = view.findViewById(R.id.parent);
            viewHolder.name = view.findViewById(R.id.name);
            viewHolder.url = view.findViewById(R.id.url);
            viewHolder.delete = view.findViewById(R.id.delete);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        AuthlibInjectorServer server = list.get(i);
        viewHolder.name.setText(server.getName());
        viewHolder.url.setText(server.getUrl());
        viewHolder.url.setSelected(true);
        viewHolder.parent.setOnClickListener(v -> {
            CreateAccountDialog dialog = new CreateAccountDialog(getContext(), server);
            dialog.show();
        });
        viewHolder.delete.setOnClickListener(v -> {
            KCUAlertDialog.Builder builder = new KCUAlertDialog.Builder(getContext());
            builder.setAlertLevel(KCUAlertDialog.AlertLevel.ALERT);
            builder.setMessage(String.format(getContext().getString(R.string.version_manage_remove_confirm), server.getName()));
            builder.setPositiveButton(() -> config().getAuthlibInjectorServers().remove(server));
            builder.setNegativeButton(null);
            builder.create().show();
        });
        return view;
    }
}
