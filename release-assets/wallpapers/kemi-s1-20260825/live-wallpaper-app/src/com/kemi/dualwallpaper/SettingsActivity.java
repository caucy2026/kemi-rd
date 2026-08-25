package com.kemi.dualwallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(72, 56, 72, 56);
        root.setBackgroundColor(Color.rgb(8, 18, 38));

        TextView title = new TextView(this);
        title.setText("KEMI S1 · 双屏壁纸");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("每套均为 D2 上屏 + D0 下屏两张不同画面，静态点对点显示");
        subtitle.setTextColor(Color.rgb(170, 205, 255));
        subtitle.setTextSize(17);
        subtitle.setPadding(0, 12, 0, 28);
        root.addView(subtitle);

        RadioGroup choices = new RadioGroup(this);
        String[] labels = {
                "01  芯片地平线 · 硬件与智能",
                "02  神经流动 · 人与 AI",
                "03  两种智慧 · 蓝色理性与金色创造（默认）"
        };
        int selected = getSharedPreferences("wallpaper", MODE_PRIVATE).getInt("selected_set", 3);
        for (int i = 0; i < labels.length; i++) {
            RadioButton option = new RadioButton(this);
            option.setId(i + 1);
            option.setText(labels[i]);
            option.setTextColor(Color.WHITE);
            option.setTextSize(20);
            option.setPadding(8, 18, 8, 18);
            choices.addView(option);
        }
        choices.check(selected);
        root.addView(choices);

        Button apply = new Button(this);
        apply.setText("应用到双屏");
        apply.setTextSize(18);
        apply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                int value = choices.getCheckedRadioButtonId();
                if (value < 1 || value > 3) value = 3;
                getSharedPreferences("wallpaper", MODE_PRIVATE).edit().putInt("selected_set", value).apply();
                Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
                intent.putExtra(
                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                        new ComponentName(SettingsActivity.this, DualWallpaperService.class));
                startActivity(intent);
                finish();
            }
        });
        root.addView(apply);
        setContentView(root);
    }
}
