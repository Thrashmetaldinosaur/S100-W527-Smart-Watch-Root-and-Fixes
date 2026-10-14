package com.s100.cameraflip;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Test build specifically for S100 Android 13 stock Camera2.apk.
 * PhotoModule.capture calls CameraProxy.setJpegOrientation(int) before shutter.
 * CameraAppUI.mTextureView is the preview surface.
 */
public final class CameraFlipHook implements IXposedHookLoadPackage {
    private static final String TARGET = "com.android.camera2";
    private static final String PREF = "s100_camera_flip_settings";
    private static final String KEY_FLIP = "flip_enabled";
    private static final String TAG = "S100CameraFlip";
    private static final int BUTTON_ID = 0x53F10001;
    private static volatile boolean flip = false;
    private static WeakReference<Activity> active = new WeakReference<>(null);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!TARGET.equals(param.packageName)) return;
        try {
            Class<?> activity = XposedHelpers.findClass("com.android.camera.CameraActivity", param.classLoader);
            Class<?> photo = XposedHelpers.findClass("com.android.camera.PhotoModule", param.classLoader);
            Class<?> proxy = XposedHelpers.findClass("com.android.ex.camera2.portability.CameraAgent$CameraProxy", param.classLoader);

            XposedHelpers.findAndHookMethod(activity, "onResumeTasks", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    refreshAsync((Activity) p.thisObject);
                }
            });
            XposedHelpers.findAndHookMethod(photo, "onPreviewStarted", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        Activity a = (Activity) XposedHelpers.getObjectField(p.thisObject, "mActivity");
                        refreshAsync(a);
                    } catch (Throwable err) { log("onPreviewStarted: " + err); }
                }
            });
            XposedHelpers.findAndHookMethod(proxy, "setJpegOrientation", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!flip) return;
                    int original = (Integer) p.args[0];
                    p.args[0] = ((original + 180) % 360 + 360) % 360;
                    log("JPEG orientation " + original + " -> " + p.args[0]);
                }
            });
            log("Hooks loaded for com.android.camera2");
        } catch (Throwable err) { log("Hook setup FAILED: " + err); }
    }

    private static void refreshAsync(Activity a) {
        if (a == null) return;
        a.runOnUiThread(() -> {
            if (a.isFinishing() || a.isDestroyed()) return;
            Activity previous = active.get();
            if (previous != a) {
                active = new WeakReference<>(a);
                SharedPreferences prefs = a.getSharedPreferences(PREF, Context.MODE_PRIVATE);
                flip = prefs.getBoolean(KEY_FLIP, false);
            }
            try {
                boolean photoMode = isPhotoMode(a);
                TextView button = findButton(a);
                if (!photoMode) {
                    if (button != null) button.setVisibility(View.GONE);
                    rotatePreview(a, false);
                    return;
                }
                if (button == null) button = installButton(a);
                if (button != null) {
                    button.setVisibility(View.VISIBLE);
                    paintButton(button);
                }
                rotatePreview(a, flip);
            } catch (Throwable err) { log("UI refresh error: " + err); }
        });
    }

    private static boolean isPhotoMode(Activity a) {
        Object current = XposedHelpers.getObjectField(a, "mCurrentModule");
        if (current == null) return false;
        return "com.android.camera.PhotoModule".equals(current.getClass().getName());
    }

    private static TextView findButton(Activity a) {
        View found = a.getWindow().getDecorView().findViewById(BUTTON_ID);
        return found instanceof TextView ? (TextView) found : null;
    }

    private static TextView installButton(Activity a) {
        View decor = a.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return null;
        TextView button = new TextView(a);
        button.setId(BUTTON_ID);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setTextColor(Color.WHITE);
        button.setElevation(dp(a, 8));
        button.setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 8));
        button.setContentDescription("Toggle 180 degree selfie camera rotation");
        button.setOnClickListener(v -> {
            flip = !flip;
            a.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_FLIP, flip).apply();
            paintButton(button);
            rotatePreview(a, flip);
            log("Selfie mode " + (flip ? "ON" : "OFF"));
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lp.topMargin = dp(a, 22);
        lp.rightMargin = dp(a, 16);
        if (decor instanceof FrameLayout) {
            ((FrameLayout) decor).addView(button, lp);
        } else {
            ((ViewGroup) decor).addView(button, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return button;
    }

    private static void paintButton(TextView button) {
        button.setText(flip ? "\u21bb 180\u00b0 ON" : "\u21bb 180\u00b0 OFF");
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(flip ? 0xDD196A3C : 0xCC202020);
        shape.setCornerRadius(dp(button.getContext(), 16));
        shape.setStroke(dp(button.getContext(), 1), 0xBBFFFFFF);
        button.setBackground(shape);
    }

    private static void rotatePreview(Activity a, boolean enabled) {
        try {
            Object appUI = XposedHelpers.getObjectField(a, "mCameraAppUI");
            if (appUI == null) return;
            Object view = XposedHelpers.getObjectField(appUI, "mTextureView");
            if (view instanceof TextureView) {
                ((TextureView) view).setRotation(enabled ? 180f : 0f);
            } else { log("No TextureView found in CameraAppUI"); }
        } catch (Throwable e) { log("Preview rotation failed: " + e); }
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }
    private static void log(String line) { XposedBridge.log(TAG + ": " + line); }
}
