package com.tungsten.fcl.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.tungsten.fcl.util.KcuAgeGate;
import com.tungsten.fcl.util.KcuDevMode;

/**
 * 首次使用时的年龄声明，以及受限状态下的说明。
 */
public class KcuAgeActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (KcuAgeGate.isBlocked()) {
            setContentView(KcuDevMode.buildBlockedView(this));
            return;
        }

        final int[] selected = new int[]{KcuAgeGate.THRESHOLD};
        View view = KcuAgeGate.buildView(this, selected, () -> {
            KcuAgeGate.declare(selected[0]);
            if (KcuAgeGate.isBlocked()) {
                setContentView(KcuDevMode.buildBlockedView(KcuAgeActivity.this));
                return;
            }
            Intent intent = new Intent(KcuAgeActivity.this, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
        setContentView(view);
    }
}
