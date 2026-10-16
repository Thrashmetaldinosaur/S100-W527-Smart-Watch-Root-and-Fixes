package com.s100.cameraflip;

import android.app.Activity;
import android.app.Instrumentation;
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
 * S100 stock camera v1.1-test2. Injected only into com.android.camera2.
 * The stock camera's mCurrentModule is not guaranteed to be ready when an
 * activity resumes. The toggle is therefore attached unconditionally to
 * activity_root_view, rather than gated on PhotoModule detection.
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

        // Instrumentation is the reliable framework dispatch point even if the
        // actual CameraLauncher activity inherits its onResume() method.
        try {
            XposedHelpers.findAndHookMethod(Instrumentation.class,
                    "callActivityOnResume", Activity.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Activity a = (Activity) p.args[0];
                    if (!TARGET.equals(a.getPackageName())) return;
                    log("Activity resumed: " + a.getClass().getName());
                    scheduleRefresh(a, "instrumentation resume");
                }
            });
            log("Instrumentation resume hook installed");
        } catch (Throwable e) {
            log("Instrumentation hook FAILED: " + e);
        }

        // Additional retry points if the activity layout/preview initializes
        // after the standard onResume lifecycle callback.
        try {
            Class<?> activity = XposedHelpers.findClass(
                    "com.android.camera.CameraActivity", param.classLoader);
            XposedHelpers.findAndHookMethod(activity, "onResumeTasks",
                    new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    log("CameraActivity.onResumeTasks fired");
                    scheduleRefresh((Activity) p.thisObject, "onResumeTasks");
                }
            });
            log("CameraActivity.onResumeTasks hook installed");
        } catch (Throwable e) {
            log("Optional onResumeTasks hook FAILED: " + e);
        }

        try {
            Class<?> photo = XposedHelpers.findClass(
                    "com.android.camera.PhotoModule", param.classLoader);
            XposedHelpers.findAndHookMethod(photo, "onPreviewStarted",
                    new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    log("PhotoModule.onPreviewStarted fired");
                    try {
                        Activity a = (Activity) XposedHelpers.getObjectField(
                                p.thisObject, "mActivity");
                        scheduleRefresh(a, "preview started");
                    } catch (Throwable e) {
                        log("Preview retry FAILED: " + e);
                    }
                }
            });
            log("PhotoModule.onPreviewStarted hook installed");
        } catch (Throwable e) {
            log("Optional preview hook FAILED: " + e);
        }

        try {
            Class<?> proxy = XposedHelpers.findClass(
                    "com.android.ex.camera2.portability.CameraAgent$CameraProxy",
                    param.classLoader);
            XposedHelpers.findAndHookMethod(proxy, "setJpegOrientation", int.class,
                    new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!flip) return;
                    int original = (Integer) p.args[0];
                    p.args[0] = ((original + 180) % 360 + 360) % 360;
                    log("JPEG orientation " + original + " -> " + p.args[0]);
                }
            });
            log("JPEG orientation hook installed");
        } catch (Throwable e) {
            log("JPEG hook FAILED: " + e);
        }

        log("v1.1 hooks setup completed for " + param.processName);
    }

    private static void scheduleRefresh(Activity a, String reason) {
        if (a == null || a.isFinishing() || a.isDestroyed()) return;
        a.runOnUiThread(() -> {
            try {
                View decor = a.getWindow().getDecorView();
                if (decor == null) {
                    log("No decor view yet (" + reason + ")");
                    return;
                }
                decor.postDelayed(() -> refresh(a, reason), 200);
            } catch (Throwable e) {
                log("scheduleRefresh FAILED (" + reason + "): " + e);
            }
        });
    }

    private static void refresh(Activity a, String reason) {
        if (a == null || a.isFinishing() || a.isDestroyed()) return;
        try {
            Activity previous = active.get();
            if (previous != a) {
                active = new WeakReference<>(a);
                SharedPreferences prefs = a.getSharedPreferences(
                        PREF, Context.MODE_PRIVATE);
                flip = prefs.getBoolean(KEY_FLIP, false);
                log("Preferences loaded: flip=" + flip);
            }
            TextView button = findButton(a);
            if (button == null) button = installButton(a);
            if (button != null) {
                button.setVisibility(View.VISIBLE);
                paintButton(button);
                button.bringToFront();
                log("Button visible (" + reason + "), parent="
                        + button.getParent().getClass().getSimpleName());
            } else {
                log("Button not attached (" + reason + ")");
            }
            rotatePreview(a, flip);
        } catch (Throwable e) {
            log("UI refresh FAILED (" + reason + "): " + e);
        }
    }

    private static TextView findButton(Activity a) {
        View found = a.getWindow().getDecorView().findViewById(BUTTON_ID);
        return found instanceof TextView ? (TextView) found : null;
    }

    private static FrameLayout chooseRoot(Activity a) {
        int id = a.getResources().getIdentifier(
                "activity_root_view", "id", TARGET);
        View found = id != 0 ? a.findViewById(id) : null;
        if (found instanceof FrameLayout) return (FrameLayout) found;
        View content = a.findViewById(android.R.id.content);
        if (content instanceof FrameLayout) {
            log("Using android.R.id.content fallback");
            return (FrameLayout) content;
        }
        View decor = a.getWindow().getDecorView();
        if (decor instanceof FrameLayout) {
            log("Using decor FrameLayout fallback");
            return (FrameLayout) decor;
        }
        log("No FrameLayout root available");
        return null;
    }

    private static TextView installButton(Activity a) {
        FrameLayout root = chooseRoot(a);
        if (root == null) return null;
        TextView button = new TextView(a);
        button.setId(BUTTON_ID);
        button.setTextSize(13);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setTextColor(Color.WHITE);
        button.setClickable(true);
        button.setFocusable(true);
        button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        button.setElevation(dp(a, 16));
        button.setPadding(dp(a, 10), dp(a, 8), dp(a, 10), dp(a, 8));
        button.setContentDescription("Toggle 180 degree camera rotation");
        button.setOnClickListener(v -> {
            flip = !flip;
            a.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_FLIP, flip).apply();
            paintButton(button);
            rotatePreview(a, flip);
            log("Toggle clicked, flip=" + flip);
        });

        // Top left avoids stock Options at x=266..352 and Shutter at
        // x=366..480 in the confirmed 480x480 camera layout.
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        lp.topMargin = dp(a, 12);
        lp.leftMargin = dp(a, 12);
        paintButton(button);
        root.addView(button, lp);
        button.bringToFront();
        log("Button ATTACHED to " + root.getClass().getName()
                + " at root id=" + root.getId());
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
            if (appUI == null) {
                log("CameraAppUI not ready");
                return;
            }
            Object view = XposedHelpers.getObjectField(appUI, "mTextureView");
            if (view instanceof TextureView) {
                ((TextureView) view).setRotation(enabled ? 180f : 0f);
                log("Preview rotation applied: " + (enabled ? 180 : 0));
            } else {
                log("Preview TextureView not available: " + view);
            }
        } catch (Throwable e) {
            log("Preview rotation FAILED: " + e);
        }
    }

    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
    }
}
