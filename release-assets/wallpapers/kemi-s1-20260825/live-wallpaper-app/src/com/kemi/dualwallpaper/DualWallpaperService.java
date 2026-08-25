package com.kemi.dualwallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.content.SharedPreferences;
import android.service.wallpaper.WallpaperService;
import android.view.Display;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.io.InputStream;

/**
 * Draws a different pixel-addressed 1920x1280 asset on each physical display.
 * Display 2 is the upper panel; Display 0 is the lower panel.
 */
public final class DualWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new DualEngine();
    }

    private final class DualEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private SharedPreferences preferences;
        private String assetName;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            // The artwork is static and centered. Disabling offset callbacks avoids
            // redraws while the launcher scrolls between pages.
            setOffsetNotificationsEnabled(false);
            preferences = getSharedPreferences("wallpaper", MODE_PRIVATE);
            preferences.registerOnSharedPreferenceChangeListener(this);
            updateAssetForDisplay();
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            drawFrame(holder);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            drawFrame(holder);
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            drawFrame(holder);
        }

        @Override
        public void onDestroy() {
            if (preferences != null) {
                preferences.unregisterOnSharedPreferenceChangeListener(this);
            }
            super.onDestroy();
        }

        private void updateAssetForDisplay() {
            Display display = getDisplayContext().getDisplay();
            int displayId = display == null ? Display.DEFAULT_DISPLAY : display.getDisplayId();
            int selectedSet = preferences == null ? 11 : preferences.getInt("selected_set", 11);
            if (selectedSet < 1 || selectedSet > 11) {
                selectedSet = 11;
            }
            assetName = "set" + selectedSet + (displayId == 2 ? "_d2.png" : "_d0.png");
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if ("selected_set".equals(key)) {
                updateAssetForDisplay();
                drawFrame(getSurfaceHolder());
            }
        }

        private void drawFrame(SurfaceHolder holder) {
            if (assetName == null) {
                return;
            }
            Bitmap bitmap = null;
            Canvas canvas = null;
            try (InputStream input = getAssets().open(assetName)) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                // Source PNG preserves fine gradients. RGB_565 halves the temporary
                // decode allocation, and the bitmap is released immediately after
                // its pixels are posted to the static wallpaper surface.
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inDither = true;
                bitmap = BitmapFactory.decodeStream(input, null, options);
                if (bitmap == null) {
                    return;
                }
                canvas = holder.lockCanvas();
                if (canvas == null) {
                    return;
                }
                canvas.drawColor(Color.BLACK);

                int canvasWidth = canvas.getWidth();
                int canvasHeight = canvas.getHeight();
                int targetWidth = Math.min(canvasWidth, bitmap.getWidth());
                int targetHeight = Math.min(canvasHeight, bitmap.getHeight());
                int left = (canvasWidth - targetWidth) / 2;
                int top = (canvasHeight - targetHeight) / 2;

                Rect source = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
                Rect destination = new Rect(left, top, left + targetWidth, top + targetHeight);
                canvas.drawBitmap(bitmap, source, destination, paint);
            } catch (IOException error) {
                throw new IllegalStateException("Unable to load " + assetName, error);
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
        }
    }
}
