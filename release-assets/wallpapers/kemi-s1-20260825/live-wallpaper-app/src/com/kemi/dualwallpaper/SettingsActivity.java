package com.kemi.dualwallpaper;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;

public final class SettingsActivity extends Activity {
    private static final int THEME_COUNT = 11;
    private Bitmap previewBitmap;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(72, 56, 72, 56);
        root.setBackgroundColor(Color.rgb(8, 18, 38));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("双屏壁纸");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("11 套风格 · D2 上屏 + D0 下屏异显 · 1920×1280 点对点");
        subtitle.setTextColor(Color.rgb(170, 205, 255));
        subtitle.setTextSize(17);
        subtitle.setPadding(0, 12, 0, 20);
        root.addView(subtitle);

        ImageView preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setBackgroundColor(Color.rgb(3, 9, 20));
        root.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(300)));

        Spinner choices = new Spinner(this);
        String[] labels = {
                "01  芯片地平线 · 硬件与智能",
                "02  神经流动 · 人与 AI",
                "03  两种智慧 · 蓝色理性与金色创造",
                "04  NPU 地球 · 本地智能与全球视野",
                "05  云山金阙 · 当代古风",
                "06  城市晨光 · 现代建筑",
                "07  量子脉冲 · 科技未来",
                "08  黑金星河 · 克制奢华",
                "09  机械核心 · 精密工业",
                "10  留白秩序 · 极简设计",
                "11  深海生境 · 自然未来（默认）"
        };
        int selected = getSharedPreferences("wallpaper", MODE_PRIVATE).getInt("selected_set", 11);
        if (selected < 1 || selected > THEME_COUNT) selected = 11;
        updatePreview(preview, selected);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        choices.setAdapter(adapter);
        choices.setSelection(selected - 1);
        choices.setBackgroundColor(Color.WHITE);
        choices.setPadding(12, 8, 12, 8);
        choices.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updatePreview(preview, position + 1);
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        root.addView(choices);

        Button apply = new Button(this);
        apply.setText("应用到双屏");
        apply.setTextSize(18);
        apply.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                int value = choices.getSelectedItemPosition() + 1;
                if (value < 1 || value > THEME_COUNT) value = 11;
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
        setContentView(scroll);
    }

    @Override
    protected void onDestroy() {
        if (previewBitmap != null) {
            previewBitmap.recycle();
            previewBitmap = null;
        }
        super.onDestroy();
    }

    private void updatePreview(ImageView view, int set) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 4;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inDither = true;

        try (InputStream upperInput = getAssets().open("set" + set + "_d2.png");
             InputStream lowerInput = getAssets().open("set" + set + "_d0.png")) {
            Bitmap upper = BitmapFactory.decodeStream(upperInput, null, options);
            Bitmap lower = BitmapFactory.decodeStream(lowerInput, null, options);
            if (upper == null || lower == null) return;

            Bitmap combined = Bitmap.createBitmap(480, 640, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(combined);
            Rect sourceUpper = new Rect(0, 0, upper.getWidth(), upper.getHeight());
            Rect sourceLower = new Rect(0, 0, lower.getWidth(), lower.getHeight());
            canvas.drawBitmap(upper, sourceUpper, new Rect(0, 0, 480, 320), null);
            canvas.drawBitmap(lower, sourceLower, new Rect(0, 320, 480, 640), null);
            upper.recycle();
            lower.recycle();

            if (previewBitmap != null) previewBitmap.recycle();
            previewBitmap = combined;
            view.setImageBitmap(previewBitmap);
        } catch (IOException ignored) {
            view.setImageDrawable(null);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
