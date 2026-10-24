package com.kongbai.kculauncher.fragment;

import static android.content.Context.MODE_PRIVATE;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.kongbai.kculauncher.R;
import com.kongbai.kculauncher.activity.SplashActivity;
import com.kongbai.kculauncher.core.util.io.IOUtils;
import com.kongbai.kculauncher.library.component.KCUFragment;
import com.kongbai.kculauncher.library.component.view.KCUButton;
import com.kongbai.kculauncher.library.component.view.KCUProgressBar;
import com.kongbai.kculauncher.library.component.view.KCUTextView;

import java.io.IOException;

public class EulaFragment extends KCUFragment implements View.OnClickListener {

    private KCUProgressBar progressBar;
    private KCUTextView eula;
    private KCUButton next;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_eula, container, false);

        progressBar = findViewById(view, R.id.progress);
        eula = findViewById(view, R.id.eula);

        next = findViewById(view, R.id.next);
        next.setOnClickListener(this);

        loadEula();

        return view;
    }

    private void loadEula() {
        new Thread(() -> {
            String str;
            try {
                str = IOUtils.readFullyAsString(requireActivity().getAssets().open("eula.txt"));
            } catch (IOException e) {
                e.printStackTrace();
                str = getString(R.string.splash_eula_error);
            }
            final String s = str;
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    eula.setText(s);
                });
            }
        }).start();
    }

    @Override
    public void onClick(View view) {
        if (view == next) {
            if (getActivity() != null) {
                SharedPreferences sharedPreferences = getActivity().getSharedPreferences("launcher", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean("isFirstLaunch", false);
                editor.apply();
                ((SplashActivity) getActivity()).start();
            }
        }
    }
}
