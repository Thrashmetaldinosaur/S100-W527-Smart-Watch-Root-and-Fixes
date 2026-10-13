package com.s100.youtubefullscreen;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.app.Instrumentation;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.text.Layout;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.lang.reflect.Method;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.lang.ref.WeakReference;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * S100 W527 YouTube fullscreen compatibility module, v1.0.
 * Confirmed on Android 13 / crDroid 9.3 with YouTube 21.40.161.
 *
 * Keeps the portrait display, centers 16:9 video in a 480x480 canvas,
 * provides transient controls, and mirrors YouTube captions to the lower band.
 * Hooks are scoped to the YouTube process; original geometry and z-order
 * are restored when playback is minimized.
 */
public final class S100YoutubeHook implements IXposedHookLoadPackage {
    private static final String TAG = "S100YT-V100";
    private static final String PACKAGE = "com.google.android.youtube";
    // The actual class seen in ActivityClient on S100 YouTube 21.40.161.
    // Shell$HomeActivity appears in dumpsys, but it is not always the
    // concrete class of the running Activity object.
    private static final String ACTIVITY = "com.google.android.apps.youtube.app.watchwhile.MainActivity";
    private static final String SECONDARY_ACTIVITY =
        "com.google.android.youtube.app.honeycomb.Shell$HomeActivity";
    // LSPosed loads our class in the Zygote specialization path, before
    // ActivityThread prepares the main Looper. NEVER construct a Handler
    // during class initialization or the module fails before hooks register.
    private static Handler mainHandler() {
        Looper main = Looper.getMainLooper();
        if (main == null) {
            log("main Looper not yet ready; UI update skipped");
            return null;
        }
        return new Handler(main);
    }

    private static WeakReference<Activity> currentActivity = new WeakReference<>(null);
    private static boolean inFullscreen = false;
    private static boolean applying = false;
    private static boolean miniRestoreQueued = false;
    private static WeakReference<SurfaceView> lastPlayerSurface = new WeakReference<>(null);
    // SF dump for v0.2.5: 640x360 source, but an incorrectly expanded
    // 480x480 SurfaceView produced non-uniform native matrices and a
    // final 360px-wide compositor frame. Keep the native SurfaceView
    // itself 480x270; only its parent viewport should be 480x480.
    private static boolean surfaceGeometryReported = false;
    // v0.2.7 compositor-order probe. SurfaceFlinger shows an opaque
    // YouTube window above the video layer. As a diagnostic, put ONLY
    // the existing video SurfaceView above its app window while in fullscreen.
    // Android 13 permits runtime Z changes for targetSdk >= 30.
    private static WeakReference<SurfaceView> topLayerSurface = new WeakReference<>(null);
    private static boolean topLayerProbeEnabled = false;
    private static boolean topLayerOriginallyAbove = false;
    // v0.3.2: caption-view detection and provisional caption mirroring.
    // Never modify the underlying video SurfaceView or its Z order.
    private static final HashSet<String> captionDrawSamples = new HashSet<>();
    private static final IdentityHashMap<View, Boolean> captionViewSamples = new IdentityHashMap<>();
    private static WeakReference<TextView> captionSource = new WeakReference<>(null);
    private static String lastCaption = "";
    private static long lastCaptionUpdateMs = 0;
    private static String lastCaptionStatus = "";
    // Rebuild a caption frame from all drawn subtitle Layouts over a short interval.
    // A single cue can contain two independently rendered text layouts.
    private static final LinkedHashSet<String> pendingCaptionParts = new LinkedHashSet<>();
    private static boolean captionFlushScheduled = false;
    private static final long CAPTION_GROUP_MS = 55;
    private static final long CAPTION_EXPIRE_MS = 5000;
    // v0.2.9: auto-hiding application-panel controls above the video surface.
    // The lower letterbox band is transparent when controls are hidden.
    private static final long CONTROL_AUTO_HIDE_MS = 4000;
    // v0.2.8: attached application-panel window above the video surface.
    // It is not a system overlay and needs no overlay permission. Never
    // reparent YouTube's SurfaceView or disturb its successful geometry.
    private static ControlPanel panelWindow = null;
    private static final ArrayList<WeakReference<MediaSession>> discoveredSessions = new ArrayList<>();
    private static final class ControlPanel {
        final Activity activity;
        final WindowManager manager;
        final View root;
        final View buttons;
        final SeekBar timeline;
        final TextView caption;
        final Handler handler;
        final Runnable refresher;
        final Runnable autoHide;
        boolean seeking = false;
        boolean controlsVisible = true;
        ControlPanel(Activity a, WindowManager wm, View r, View b, SeekBar bar,
                     TextView captionView, Handler h, Runnable ticker, Runnable autoHideTask) {
            activity = a; manager = wm; root = r; buttons = b; timeline = bar; caption = captionView;
            handler = h; refresher = ticker; autoHide = autoHideTask;
        }
    }

    // v0.2.4: video in v0.2.3 was clipped at Y~277 after shifting apdu.
    // Shift the whole outer player viewport and its clipping boundary instead.
    // Reset synchronously when YouTube exposes the mini-player.
    private static WeakReference<View> letterboxParent = new WeakReference<>(null);
    private static float letterboxOriginalTranslationY = 0f;
    private static float letterboxOffsetY = 0f;
    // v0.2.5: Test Android SurfaceView's own compositor crop rather than
    // another geometry/translation adjustment. Save prior state for rollback.
    private static WeakReference<SurfaceView> clipProbeSurface = new WeakReference<>(null);
    private static Rect clipProbeOriginalBounds = null;
    private static boolean clipProbeOriginalEnabled = false;
    private static boolean clipProbeEnabledReadable = false;
    private static boolean clipProbeApplied = false;
    private static int miniControlsId = 0;
    private static int miniCloseId = 0;
    private static final IdentityHashMap<View, SavedView> saved = new IdentityHashMap<>();
    private static int oldWindowFlags = -1;
    private static int oldSystemUiFlags = -1;
    private static Drawable oldBackground;
    private static final IdentityHashMap<ViewGroup, boolean[]> oldClip = new IdentityHashMap<>();

    // v0.2.0: Keep the existing SurfaceView attached to YouTube. The v0.1.8
    // experiment reparented the player and caused a black video surface.
    // Instead, force only the five observed player ancestor containers through their
    // original View.measure/View.layout calls while fullscreen is active.
    private static final IdentityHashMap<View, int[]> viewportTargets = new IdentityHashMap<>();
    private static final IdentityHashMap<View, Boolean> reportedMeasure = new IdentityHashMap<>();
    private static final IdentityHashMap<View, Boolean> reportedLayout = new IdentityHashMap<>();

    private static class SavedView {
        final int visibility;
        final float translationX, translationY, scaleX, scaleY;
        final ViewGroup.LayoutParams layout;
        SavedView(View v) {
            visibility = v.getVisibility();
            translationX = v.getTranslationX();
            translationY = v.getTranslationY();
            scaleX = v.getScaleX();
            scaleY = v.getScaleY();
            layout = copyParams(v.getLayoutParams());
        }
    }

    private static ViewGroup.LayoutParams copyParams(ViewGroup.LayoutParams p) {
        if (p == null) return null;
        if (p instanceof FrameLayout.LayoutParams) return new FrameLayout.LayoutParams((FrameLayout.LayoutParams)p);
        if (p instanceof ViewGroup.MarginLayoutParams) return new ViewGroup.MarginLayoutParams((ViewGroup.MarginLayoutParams)p);
        return new ViewGroup.LayoutParams(p);
    }

    // Keep release logs concise and never write video captions to persistent LSPosed logs.
    // Set true locally when diagnosing a regression; no release behavior depends on it.
    private static final boolean VERBOSE_DIAGNOSTICS = false;
    private static void log(String s) {
        if (!VERBOSE_DIAGNOSTICS) {
            String lower = s.toLowerCase(java.util.Locale.ROOT);
            if (!(s.startsWith("loaded package=") || s.startsWith("FULLSCREEN requested") ||
                  s.startsWith("MINIPLAYER detected") || s.startsWith("RESTORED watch page") ||
                  s.startsWith("CONTROL_PANEL created") || s.startsWith("TOP_LAYER RESTORED") ||
                  lower.contains("error") || lower.contains("failure") || lower.contains("unavailable")))
                return;
        }
        XposedBridge.log(TAG + ": " + s);
    }

    private static boolean isHome(Activity a) {
        if (a == null || !PACKAGE.equals(a.getPackageName())) return false;
        for (Class<?> c = a.getClass(); c != null && c != Activity.class; c = c.getSuperclass()) {
            String name = c.getName();
            if (ACTIVITY.equals(name) || SECONDARY_ACTIVITY.equals(name)) return true;
        }
        return false;
    }

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PACKAGE.equals(lpparam.packageName)) return;
        log("loaded package=" + lpparam.packageName + " version-target=21.40.161");
        try {
            // MediaSession is the public Android playback transport. Capture
            // sessions created inside the already-scoped YouTube process so
            // the separate control panel can pause/play and seek. If this
            // YouTube build does not expose one, use media key events instead.
            try {
                XposedBridge.hookAllConstructors(MediaSession.class, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (p.thisObject instanceof MediaSession)
                            trackSession((MediaSession)p.thisObject, "created");
                    }
                });
                XposedHelpers.findAndHookMethod(MediaSession.class, "setActive", boolean.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam p) {
                            if (Boolean.TRUE.equals(p.args[0]))
                                trackSession((MediaSession)p.thisObject, "active");
                        }
                    });
                log("media session discovery registered");
            } catch (Throwable t) {
                log("media session discovery unavailable: " + t.getClass().getSimpleName());
            }

            // YouTube captions can be obscured by our necessary video-on-top
            // compositor workaround. Only recognize caption-tagged TextViews:
            // never mirror arbitrary YouTube titles, menus, or screen text.
            // The additional Layout.draw diagnostics help identify renderers
            // that are NOT TextViews (common with custom subtitle painters).
            try {
                XposedBridge.hookAllMethods(TextView.class, "setText", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!inFullscreen || p.args == null || p.args.length == 0 ||
                                !(p.thisObject instanceof TextView) ||
                                !(p.args[0] instanceof CharSequence)) return;
                        TextView view = (TextView)p.thisObject;
                        ControlPanel controls = panelWindow;
                        if (controls != null && view == controls.caption) return;
                        String line = p.args[0].toString().trim();
                        if (line.length() > 250) return;
                        String origin = captionOrigin(view);
                        if (origin == null) return;
                        log("CAPTION_TEXT source=" + origin + " value=" + shortCaption(line));
                        updateCaption(view, line);
                    }
                });
                log("CAPTION_PROBE TextView.setText registered");
            } catch (Throwable e) {
                log("CAPTION_PROBE setText unavailable " + e.getClass().getSimpleName());
            }
            // v0.3.2: confirmed via v0.3.1 logs that real subtitle lines are
            // rendered by Layout.draw beneath YouTubePlayerOverlaysLayout.drawChild.
            // Previously this callback only logged them, never sent them to
            // the mirror. Do not use generic text heuristics here: check the
            // verified player-overlay call stack to exclude titles and menus.
            try {
                XposedBridge.hookAllMethods(Layout.class, "draw", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!inFullscreen || !(p.thisObject instanceof Layout) || panelWindow == null)
                            return;
                        CharSequence value = ((Layout)p.thisObject).getText();
                        if (value == null) return;
                        String line = value.toString().trim();
                        if (line.isEmpty() || line.length() > 400) return;
                        if (!isYoutubePlayerCaptionDraw()) return;
                        receiveDrawnCaption(line);
                    }
                });
                log("CAPTION_PROBE Layout.draw live-mirror registered");
            } catch (Throwable e) {
                log("CAPTION_PROBE Layout.draw live-mirror unavailable " + e.getClass().getSimpleName());
            }

            // Android 13 Activity.setRequestedOrientation normally delegates to
            // ActivityClient. YouTube may override the Activity method and bypass
            // the inherited-method hook, so watch both paths independently.
            XposedHelpers.findAndHookMethod(Activity.class, "setRequestedOrientation", int.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Activity a = (Activity) p.thisObject;
                    if (!isHome(a)) return;
                    interceptOrientation(p, a, ((Number)p.args[0]).intValue(), "Activity");
                }
            });
            log("hook Activity.setRequestedOrientation registered");

            // v0.2.0: all three outer 237px ancestors were fixed in v0.1.9,
            // but apdu and apdv still measured 270px high. The SurfaceView
            // starts near screen Y=105, leaving the native layer clipped at
            // those ancestors' lower edge near Y=259. Expand these TWO
            // intermediate wrappers as well, preserving their origins.
            // Only these five verified instances are targeted, never SurfaceView.
            XposedHelpers.findAndHookMethod(View.class, "measure", int.class, int.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!inFullscreen) return;
                        View v = (View)p.thisObject;
                        if (v == lastPlayerSurface.get()) {
                            // Do not let YouTube's expanded square parent
                            // stretch its video SurfaceView to 480x480.
                            // This modifies the measured View geometry but
                            // never changes decoder/buffer fixed-size APIs.
                            try {
                                if (v.getMeasuredWidth() != 480 || v.getMeasuredHeight() != 270) {
                                    int priorW = v.getMeasuredWidth();
                                    int priorH = v.getMeasuredHeight();
                                    XposedHelpers.callMethod(v, "setMeasuredDimension", 480, 270);
                                    if (!surfaceGeometryReported) {
                                        surfaceGeometryReported = true;
                                        log("VIDEO_MEASURE constrained " + priorW + "x" + priorH +
                                            " -> " + v.getMeasuredWidth() + "x" + v.getMeasuredHeight());
                                    }
                                }
                            } catch (Throwable e) {
                                log("VIDEO_MEASURE failure " + e.getClass().getSimpleName() + ": " + e.getMessage());
                            }
                            return;
                        }
                        int[] target = viewportTargets.get(v);
                        if (target == null) return;
                        try {
                            if (v.getMeasuredHeight() != target[1] ||
                                    v.getMeasuredWidth() != target[0]) {
                                int before = v.getMeasuredHeight();
                                XposedHelpers.callMethod(v, "setMeasuredDimension", target[0], target[1]);
                                if (!reportedMeasure.containsKey(v)) {
                                    reportedMeasure.put(v, true);
                                    log("FORCED measure " + v.getClass().getSimpleName() +
                                        " " + before + "->" + v.getMeasuredHeight());
                                }
                            }
                        } catch (Throwable e) {
                            log("measure hook failure " + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    }
                });
            log("hook View.measure registered");

            // NextGenWatchLayout also positions the player at y=28. Give the
            // same THREE view instances actual 480x480 bounds starting at 0,0.
            // Don't move/reparent the player or alter its SurfaceView lifecycle.
            XposedHelpers.findAndHookMethod(View.class, "layout",
                int.class, int.class, int.class, int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (!inFullscreen) return;
                        View v = (View)p.thisObject;
                        if (v == lastPlayerSurface.get()) {
                            // The five parent containers are anchored at 0,0,
                            // and the 16:9 480x270 movie is centered inside.
                            // No translation, scale, reparenting or crop is
                            // applied to the video SurfaceView.
                            p.args[0] = 0;
                            p.args[1] = 105;
                            p.args[2] = 480;
                            p.args[3] = 375;
                            return;
                        }
                        int[] target = viewportTargets.get(v);
                        if (target == null) return;
                        if (target[2] == 1) {
                            // Outer player containers occupy the full square.
                            p.args[0] = 0;
                            p.args[1] = 0;
                            p.args[2] = target[0];
                            p.args[3] = target[1];
                        } else {
                            // Do NOT move apdu/apdv (the video decoder's
                            // existing intermediate parents). Keep their
                            // caller-assigned origins; extend just bounds.
                            int left = ((Number)p.args[0]).intValue();
                            int top = ((Number)p.args[1]).intValue();
                            p.args[2] = left + target[0];
                            p.args[3] = top + target[1];
                        }
                    }
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!inFullscreen) return;
                        View v = (View)p.thisObject;
                        // A minimized player does not request a portrait orientation:
                        // detect its actual UI when YouTube lays out the mini controls.
                        if (maybeRestoreForMini(v, "layout")) return;
                        if (v == lastPlayerSurface.get()) {
                            if (!reportedLayout.containsKey(v)) {
                                reportedLayout.put(v, true);
                                int[] position = new int[2];
                                v.getLocationOnScreen(position);
                                log("VIDEO_LAYOUT view=" + v.getWidth() + "x" + v.getHeight() +
                                    " measured=" + v.getMeasuredWidth() + "x" + v.getMeasuredHeight() +
                                    " screen=" + position[0] + "," + position[1] +
                                    " local=" + v.getLeft() + "," + v.getTop());
                            }
                            return;
                        }
                        if (!viewportTargets.containsKey(v) || reportedLayout.containsKey(v)) return;
                        reportedLayout.put(v, true);
                        log("FORCED layout " + v.getClass().getSimpleName() +
                            " actual=" + v.getWidth() + "x" + v.getHeight() +
                            " at=" + v.getLeft() + "," + v.getTop());
                    }
                });
            log("hook View.layout registered");

            // YouTube may show the minimized-player controls without relaying out
            // the container (e.g., when an animation switches a visibility flag).
            // Only act when one of the two known mini-player control IDs changes.
            XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        if (!inFullscreen || miniRestoreQueued) return;
                        View v = (View)p.thisObject;
                        if (isMiniSignalView(v)) maybeRestoreForMini(v, "visibility");
                    }
                });
            log("hook View.setVisibility registered (mini-player only)");

            // Capture the HomeActivity with the public Application lifecycle API.
            // Instrumentation.callActivityOnResume was registered on v0.1.4,
            // but never ran on this YouTube build, leaving its token unknown.
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Application app = (Application)p.thisObject;
                        if (!PACKAGE.equals(app.getPackageName())) return;
                        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                            @Override public void onActivityCreated(Activity a, Bundle state) {
                                rememberActivity(a, "created");
                            }
                            @Override public void onActivityStarted(Activity a) {}
                            @Override public void onActivityResumed(Activity a) {
                                rememberActivity(a, "resumed");
                                if (isHome(a) && inFullscreen) queueApply(a);
                            }
                            @Override public void onActivityPaused(Activity a) {}
                            @Override public void onActivityStopped(Activity a) {}
                            @Override public void onActivitySaveInstanceState(Activity a, Bundle state) {}
                            @Override public void onActivityDestroyed(Activity a) {
                                if (isHome(a) && a == currentActivity.get()) {
                                    currentActivity = new WeakReference<>(null);
                                }
                            }
                        });
                        log("Application lifecycle callbacks registered");
                    }
                });
            log("hook Application.attach registered");

            // Stable lifecycle entry point: captures the actual HomeActivity and
            // its activity token even when its own onResume implementation varies.
            XposedHelpers.findAndHookMethod(Instrumentation.class, "callActivityOnResume", Activity.class,
                new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Activity a = (Activity) p.args[0];
                        if (isHome(a)) {
                            currentActivity = new WeakReference<>(a);
                            log("Player Activity resumed="+a.getClass().getName()+", token available=" + (activityToken(a) != null));
                        }
                    }
                });
            log("hook Instrumentation.callActivityOnResume registered");

            Class<?> activityClient = XposedHelpers.findClassIfExists("android.app.ActivityClient", null);
            if (activityClient != null) {
                XposedBridge.hookAllMethods(activityClient, "setRequestedOrientation", new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        if (p.args.length != 2 || !(p.args[0] instanceof IBinder) ||
                                !(p.args[1] instanceof Number)) return;
                        int value = ((Number)p.args[1]).intValue();
                        IBinder requestedToken = (IBinder)p.args[0];
                        Activity resolved = findActivityForToken(requestedToken);
                        Activity captured = currentActivity.get();
                        boolean resolvedHome = isHome(resolved);
                        boolean capturedMatch = isHome(captured) &&
                                requestedToken.equals(activityToken(captured));
                        Activity target = resolvedHome ? resolved : capturedMatch ? captured : null;
                        log("ActivityClient orientation=" + value +
                            " resolved=" + (resolved != null ? resolved.getClass().getName() : "null") +
                            " captured=" + (captured != null ? captured.getClass().getName() : "null") +
                            " tokenMatch=" + capturedMatch);
                        if (target != null) {
                            interceptOrientation(p, target, value, "ActivityClient");
                        } else if (isLandscape(value)) {
                            // LSPosed scope is ONLY com.google.android.youtube. This
                            // explicit, narrow fallback is needed because the S100
                            // YouTube activity token was unavailable in v0.1.4.
                            // Do not affect any global rotation/display setting.
                            p.setResult(null);
                            inFullscreen = true;
                            log("landscape request suppressed in YouTube process; activity unresolved, UI deferred");
                            if (isHome(captured)) queueApply(captured);
                        } else if (inFullscreen && isNormalOrientation(value)) {
                            inFullscreen = false;
                            log("normal orientation restored, request=" + value);
                            if (isHome(captured)) restore(captured);
                        }
                    }
                });
                log("hook ActivityClient.setRequestedOrientation registered");
            } else {
                log("ActivityClient not found on this ROM");
            }

            // Some YouTube builds override the Activity method. Hook a declared
            // override if one exists rather than silently missing its dispatch.
            Class<?> homeClass = XposedHelpers.findClassIfExists(ACTIVITY, lpparam.classLoader);
            if (homeClass != null) {
                Class<?> cls = homeClass;
                while (cls != null && cls != Activity.class) {
                    try {
                        Method override = cls.getDeclaredMethod("setRequestedOrientation", int.class);
                        XposedBridge.hookMethod(override, new XC_MethodHook() {
                            @Override protected void beforeHookedMethod(MethodHookParam p) {
                                Activity a = (Activity) p.thisObject;
                                if (isHome(a)) interceptOrientation(p, a,
                                        ((Number)p.args[0]).intValue(), "override");
                            }
                        });
                        log("hook override " + cls.getName() + ".setRequestedOrientation registered");
                    } catch (NoSuchMethodException ignored) {
                        // Most YouTube base classes do not declare an override.
                    }
                    cls = cls.getSuperclass();
                }
            } else {
                log("HomeActivity class lookup unavailable; platform hooks still active");
            }

            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Activity a = (Activity)p.thisObject;
                    if (isHome(a)) {
                        currentActivity = new WeakReference<>(a);
                        log("Activity.onResume HomeActivity");
                        if (inFullscreen) queueApply(a);
                    }
                }
            });
            XposedHelpers.findAndHookMethod(Activity.class, "onBackPressed", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Activity a = (Activity)p.thisObject;
                    if (isHome(a) && inFullscreen) {
                        log("Back pressed - restoring normal view");
                        inFullscreen = false;
                        restore(a);
                    }
                }
            });
            XposedHelpers.findAndHookMethod(Activity.class, "onDestroy", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Activity a = (Activity)p.thisObject;
                    if (isHome(a)) {
                        inFullscreen = false;
                        restore(a);
                    }
                }
            });
            log("hooks registered");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " hook registration failed");
            XposedBridge.log(e);
        }
    }

    private static void rememberActivity(Activity a, String stage) {
        if (!isHome(a)) return;
        currentActivity = new WeakReference<>(a);
        // IDs verified in the uploaded S100 mini-player UI hierarchy.
        miniControlsId = id(a, "floaty_bar_controls_view");
        miniCloseId = id(a, "modern_miniplayer_close");
        log("Player Activity " + stage + "="+a.getClass().getName()+", token=" +
            (activityToken(a) != null) + " miniIds=" + miniControlsId + "," + miniCloseId);
    }

    private static boolean isMiniSignalView(View v) {
        if (v == null) return false;
        int viewId = v.getId();
        return viewId != View.NO_ID &&
            ((miniControlsId != 0 && viewId == miniControlsId) ||
             (miniCloseId != 0 && viewId == miniCloseId));
    }

    private static boolean maybeRestoreForMini(View changedView, String source) {
        if (!inFullscreen || miniRestoreQueued || !isMiniSignalView(changedView)) return false;
        Activity activity = currentActivity.get();
        if (!isHome(activity)) return false;
        View controls = named(activity, "floaty_bar_controls_view");
        View close = named(activity, "modern_miniplayer_close");
        // The captured S100 mini UI exposes a 232x130 floaty control region
        // and a visible close button. Both must be shown; neither condition
        // holds in the watch player's expanded fullscreen presentation.
        if (controls == null || close == null || !controls.isShown() || !close.isShown()) return false;
        int w = controls.getWidth(), h = controls.getHeight();
        if (w < 140 || w > 300 || h < 50 || h > 220 || close.getWidth() < 16) return false;
        // Disable View.measure/layout enforcement SYNCHRONOUSLY before the
        // restore posts its original layout data to the main thread.
        miniRestoreQueued = true;
        inFullscreen = false;
        resetLetterboxAlignment();
        restoreSurfaceClip();
        log("MINIPLAYER detected via " + source + " controls=" + w + "x" + h +
            " close=" + close.getWidth() + "x" + close.getHeight() +
            "; returning to original player geometry");
        restore(activity);
        return true;
    }

    // ActivityThread.mActivities is the authoritative token -> activity
    // registry on Android 13. Avoid relying on a hook on YouTube's obfuscated
    // Activity class (which is unavailable during early package injection).
    private static Activity findActivityForToken(IBinder token) {
        try {
            Class<?> cls = XposedHelpers.findClass("android.app.ActivityThread", null);
            Object thread = XposedHelpers.callStaticMethod(cls, "currentActivityThread");
            if (thread == null) return null;
            Object entries = XposedHelpers.getObjectField(thread, "mActivities");
            if (!(entries instanceof Map)) return null;
            Object record = ((Map<?, ?>)entries).get(token);
            if (record == null) return null;
            Object candidate = XposedHelpers.getObjectField(record, "activity");
            return candidate instanceof Activity ? (Activity)candidate : null;
        } catch (Throwable e) {
            log("activity token lookup unavailable: " + e.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean isLandscape(int value) {
        return value == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
               value == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ||
               value == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE ||
               value == ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE;
    }

    private static boolean isNormalOrientation(int value) {
        return value == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED ||
               value == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
               value == ActivityInfo.SCREEN_ORIENTATION_USER ||
               value == ActivityInfo.SCREEN_ORIENTATION_FULL_USER ||
               value == ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
    }

    private static IBinder activityToken(Activity a) {
        if (a == null) return null;
        try { return (IBinder)XposedHelpers.getObjectField(a, "mToken"); }
        catch (Throwable ignored) { return null; }
    }

    private static void interceptOrientation(XC_MethodHook.MethodHookParam p,
                                             Activity a, int value, String source) {
        log(source + " orientation request=" + value + " active=" + inFullscreen);
        if (isLandscape(value)) {
            // Only YouTube activity instance affected. No global rotation setting.
            p.setResult(null);
            if (!inFullscreen) {
                inFullscreen = true;
                currentActivity = new WeakReference<>(a);
                log("FULLSCREEN requested via " + source + "; orientation suppressed");
            }
            queueApply(a);
        } else if (inFullscreen && isNormalOrientation(value)) {
            log("FULLSCREEN exit via " + source + " orientation=" + value);
            inFullscreen = false;
            restore(a);
        }
    }

    private static void queueApply(final Activity a) {
        final Handler handler = mainHandler();
        if (handler == null) return;
        // YouTube uses async UI transitions: apply more than once, but only for
        // the first few seconds after request. No polling service runs later.
        for (long delay : new long[]{0, 180, 500, 1200, 2500}) {
            handler.postDelayed(() -> {
                if (inFullscreen && isHome(a) && !a.isFinishing()) apply(a);
            }, delay);
        }
    }

    private static int id(Activity a, String name) {
        return a.getResources().getIdentifier(name, "id", PACKAGE);
    }
    private static View named(Activity a, String name) {
        int i = id(a, name);
        return i == 0 ? null : a.findViewById(i);
    }
    private static void remember(View v) {
        if (v != null && !saved.containsKey(v)) saved.put(v, new SavedView(v));
    }

    private static void apply(Activity a) {
        if (applying) return;
        applying = true;
        try {
            final Window w = a.getWindow();
            final View decor = w.getDecorView();
            if (oldWindowFlags == -1) {
                oldWindowFlags = w.getAttributes().flags;
                oldSystemUiFlags = decor.getSystemUiVisibility();
                oldBackground = decor.getBackground();
            }
            // This is fullscreen within the square 480x480 UI, not landscape.
            w.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            decor.setSystemUiVisibility(oldSystemUiFlags |
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            decor.setBackgroundColor(Color.BLACK);

            // These resource IDs were extracted from the ACTUAL S100 YouTube
            // 21.40.161 UI capture; they are not borrowed from an S13 hook.
            View panel = named(a, "watch_panel");
            View list = named(a, "watch_list");
            if (panel != null) { remember(panel); panel.setVisibility(View.GONE); }
            if (list != null) { remember(list); list.setVisibility(View.GONE); }
            View engagement = named(a, "engagement_panel_wrapper");
            if (engagement != null) { remember(engagement); engagement.setVisibility(View.GONE); }

            // The timebar normally occupies the bottom of the embedded player;
            // hiding it also stops YouTube's normal-page controller from
            // overlaying the fullscreen video while testing this first build.
            View timebar = named(a, "watch_while_time_bar_view");
            if (timebar != null) { remember(timebar); timebar.setVisibility(View.GONE); }
            View timeOverlay = named(a, "watch_while_time_bar_view_overlay");
            if (timeOverlay != null) { remember(timeOverlay); timeOverlay.setVisibility(View.GONE); }

            SurfaceView surface = findSurface(decor);
            if (surface == null) {
                log("NO_SURFACE (may be video in separate window)");
                return;
            }
            // Crucial v0.2.2 difference: the SurfaceView is owned by YouTube's
            // decoder and SurfaceControl, so DO NOT capture and replay its
            // layout/scale/translation as if it were an ordinary View.
            if (lastPlayerSurface.get() != surface) surfaceGeometryReported = false;
            lastPlayerSurface = new WeakReference<>(surface);
            int[] loc = new int[2];
            surface.getLocationOnScreen(loc);
            int screenH = a.getResources().getDisplayMetrics().heightPixels;
            int screenW = a.getResources().getDisplayMetrics().widthPixels;
            int surfaceW = surface.getWidth();
            int surfaceH = surface.getHeight();
            if (screenH <= 0 || screenW <= 0 || surfaceW <= 0 || surfaceH <= 0) return;

            // S100 v0.1.6: This prototype sets a centered square-screen
            // letterboxed *surface area*, without detaching SurfaceView from
            // YouTube's existing playback pipeline. Unlike reparenting the
            // player, transforms can be reversed without recreating a codec.
            // Disable ancestor clipping only while the test is active.
            for (ViewGroup parent = surface.getParent() instanceof ViewGroup
                    ? (ViewGroup)surface.getParent() : null;
                 parent != null;
                 parent = parent.getParent() instanceof ViewGroup
                    ? (ViewGroup)parent.getParent() : null) {
                if (!oldClip.containsKey(parent)) {
                    oldClip.put(parent, new boolean[]{parent.getClipChildren(), parent.getClipToPadding()});
                }
                parent.setClipChildren(false);
                parent.setClipToPadding(false);
            }

            // The observed S100 player tree is:
            //   SurfaceView[480x270] > apdu[480x270] > apdv[480x270] >
            //   YouTubePlayerViewNotForReflection[480x237] >
            //   FrameLayout[480x237] > FrameLayout[480x237] >
            //   NextGenWatchLayout[480x480].
            // Transforming the SurfaceView alone pushed video outside those
            // 237px containers, where the native video layer remained cropped
            // even with clipChildren=false. Give the player viewport and both
            // enclosing FrameLayouts a full-height square region FIRST.
            // v0.2.0 also expands apdu and apdv, previously the last two
            // 270px-high SurfaceControl crop boundaries.
            int expanded = expandPlayerViewport(surface, screenW, screenH);

            // v0.2.7: Test compositor-order hypothesis AFTER resizing/positioning
            // the existing video surface. This is deliberately a separate test
            // from the geometry modifications: if the black band disappears,
            // the opaque app-window layer was obstructing the SurfaceView.
            enableTopLayerProbe(surface);
            showControlPanel(a);

            // v0.2.4: v0.2.3 shifted the video inside its clip, so it still
            // ended at screen Y~277 and lost its lower 100 pixels. Instead
            // translate the verified outer player viewport, carrying the
            // video and its crop region together. Preserve native SurfaceView.
            // v0.2.6: disabled both previous experiments. The compositor
            // should receive a centered, naturally sized 480x270 SurfaceView.
            // alignLetterboxParent(surface, screenW, screenH);
            // probeAndExpandSurfaceClip(surface, screenW, screenH);

            // S100 v0.2.2: No setScaleX/Y, setTranslationX/Y or
            // setLayoutParams on SurfaceView. v0.2.1 shrank a 480x480 native
            // surface by sy=.5625, producing ~152 px of visible movie. It also
            // tried to replay old SurfaceView geometry when YouTube minimized.
            // Let the YouTube renderer determine the video content rectangle.
            // Its 480x480 SurfaceView already places the picture near Y=105,
            // exactly where a 480x270 16:9 picture should begin on the square.
            // Only the verified ancestor containers are expanded while active.
            surface.getLocationOnScreen(loc);
            log("applied: activity=" + a.getClass().getName() +
                " panel=" + (panel!=null) + " engagement=" + (engagement!=null) +
                " nativeSurface=" + surfaceW + "x" + surfaceH +
                " screen=" + screenW + "x" + screenH +
                " nativeTop=" + loc[1] +
                " surfaceScale=" + surface.getScaleX() + "," + surface.getScaleY() +
                " viewportParentsExpanded=" + expanded +
                " enforcedViewCount=" + viewportTargets.size() +
                " targetVideo=480x270@0,105 no-crop no-translation");
            logSurfaceParents(surface);
        } catch (Throwable e) {
            log("apply failure " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            applying = false;
        }
    }

    /**
     * v0.2.0 - The log from v0.1.9 showed the three outer ancestors are
     * genuinely 480x480, but the SurfaceView was still clipped at Y~259:
     *   SurfaceView 480x270 @Y=105
     *   apdu        480x270 @Y=-11  (bottom Y=259)
     *   apdv        480x270 @Y=-11  (bottom Y=259)
     *   YouTubePlayerViewNotForReflection 480x480
     *   FrameLayout 480x480
     *   FrameLayout 480x480.
     *
     * Those intermediate wrappers need 480px height too. Do NOT reparent
     * SurfaceView or modify its width/height or playback pipeline.
     * Strictly validate the known five-ancestor chain before changing any.
     */
    private static int expandPlayerViewport(SurfaceView surface, int width, int height) {
        ArrayList<View> chain = new ArrayList<>();
        View node = surface.getParent() instanceof View
                ? (View) surface.getParent() : null;
        for (int i = 0; i < 5 && node != null; i++) {
            chain.add(node);
            node = node.getParent() instanceof View ? (View) node.getParent() : null;
        }
        String[] expected = {
            "apdu", "apdv", "YouTubePlayerViewNotForReflection",
            "FrameLayout", "FrameLayout"
        };
        if (chain.size() != expected.length) {
            log("SKIP geometry: unexpected parent chain length=" + chain.size());
            return 0;
        }
        for (int i = 0; i < expected.length; i++) {
            String actual = chain.get(i).getClass().getSimpleName();
            if (!expected[i].equals(actual)) {
                log("SKIP geometry: chain[" + i + "]=" + actual + " expected=" + expected[i]);
                return 0;
            }
        }

        int expanded = 0;
        for (int i = 0; i < chain.size(); i++) {
            View v = chain.get(i);
            String cls = v.getClass().getSimpleName();
            ViewGroup.LayoutParams original = v.getLayoutParams();
            if (original == null || v.getMeasuredWidth() > width + 32 ||
                    v.getMeasuredWidth() < width / 2) {
                log("SKIP geometry: invalid size/params for " + cls +
                    " width=" + v.getMeasuredWidth());
                continue;
            }
            remember(v);
            // Inner wrapper positions must stay unchanged; two outer Frames
            // and the YouTubePlayerView should fill [0,0..480,480].
            viewportTargets.put(v, new int[]{width, height, 1});
            if (original.height != height) {
                ViewGroup.LayoutParams changed = copyParams(original);
                changed.height = height;
                v.setLayoutParams(changed);
                log("viewport expanded " + cls +
                    " initial=" + v.getWidth() + "x" + v.getHeight() +
                    " layoutHeight=" + original.height + "->" + height +
                    " originMode=zero" );
            }
            if (v instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) v;
                if (!oldClip.containsKey(group)) {
                    oldClip.put(group, new boolean[]{group.getClipChildren(), group.getClipToPadding()});
                }
                group.setClipChildren(false);
                group.setClipToPadding(false);
            }
            expanded++;
        }
        return expanded;
    }

    /**
     * v0.2.4: Move the entire verified player viewport, not the inner video
     * wrapper. v0.2.3 translated apdu and shifted the actual picture, while
     * the visible crop continued ending around screen Y=277. The crop is not
     * carried along when we translate a descendant inside the player.
     *
     * The fifth ancestor is the outer FrameLayout immediately inside
     * NextGenWatchLayout. Translating it carries the player and its descendants
     * (including any clipping region tied to that viewport) together. It does
     * not reparent or directly transform SurfaceView. This is an experimental
     * hypothesis that requires screenshot verification on the S100.
     */
    private static void alignLetterboxParent(SurfaceView surface, int screenW, int screenH) {
        if (screenW != 480 || screenH != 480) return;
        View node = surface;
        String[] expected = {
            "apdu", "apdv", "YouTubePlayerViewNotForReflection", "FrameLayout", "FrameLayout"
        };
        for (String name : expected) {
            if (!(node.getParent() instanceof View)) {
                log("LETTERBOX skipped: parent chain terminated at " + name);
                return;
            }
            node = (View)node.getParent();
            if (!name.equals(node.getClass().getSimpleName())) {
                log("LETTERBOX skipped: expected " + name + " found " +
                    node.getClass().getSimpleName());
                return;
            }
        }
        View viewport = node;
        View previouslyShifted = letterboxParent.get();
        if (previouslyShifted != viewport) {
            resetLetterboxAlignment();
            letterboxParent = new WeakReference<>(viewport);
            letterboxOriginalTranslationY = viewport.getTranslationY();
        }
        // Both screen and forced outer player viewport are square. Movie is
        // 480x270, so the desired top edge of the viewport's movie is 105px.
        // Recompute based on actual *untranslated* screen coordinates because
        // the first fullscreen pass sees the old embedded y=28, while layout
        // subsequently moves the container to y=0. Never accumulate deltas.
        int[] screenXY = new int[2];
        viewport.getLocationOnScreen(screenXY);
        float currentExtra = viewport.getTranslationY() - letterboxOriginalTranslationY;
        float baseScreenTop = screenXY[1] - currentExtra;
        float movieTop = (screenH - Math.round(screenW * 9f / 16f)) / 2f;
        float targetTranslation = letterboxOriginalTranslationY + movieTop - baseScreenTop;
        letterboxOffsetY = targetTranslation - letterboxOriginalTranslationY;
        if (Math.abs(viewport.getTranslationY() - targetTranslation) > 0.5f) {
            float previous = viewport.getTranslationY();
            viewport.setTranslationY(targetTranslation);
            int[] afterXY = new int[2];
            viewport.getLocationOnScreen(afterXY);
            int[] surfaceXY = new int[2];
            surface.getLocationOnScreen(surfaceXY);
            log("LETTERBOX viewport=outer-FrameLayout" +
                " measured=" + viewport.getWidth() + "x" + viewport.getHeight() +
                " baseTop=" + baseScreenTop + " desiredTop=" + movieTop +
                " translation=" + previous + "->" + targetTranslation +
                " viewportScreenY=" + screenXY[1] + "->" + afterXY[1] +
                " surfaceScreenY=" + surfaceXY[1] +
                " surface=" + surface.getWidth() + "x" + surface.getHeight());
        }
    }

    private static void resetLetterboxAlignment() {
        View viewport = letterboxParent.get();
        if (viewport != null) {
            viewport.setTranslationY(letterboxOriginalTranslationY);
            log("LETTERBOX reset viewport=outer-FrameLayout translationY=" +
                letterboxOriginalTranslationY);
        }
        letterboxParent = new WeakReference<>(null);
        letterboxOriginalTranslationY = 0f;
        letterboxOffsetY = 0f;
    }

    /**
     * v0.2.5: The screenshot's picture abruptly terminates near Y=265 even
     * when every ordinary ancestor is 480 high. On Android SurfaceView the
     * native SurfaceControl crop may be separate from ViewGroup.clipChildren.
     * Test only the existing Android View/SurfaceView clipping interfaces;
     * DO NOT mutate getSurfaceControl() or reparent the video layer.
     * Report view-visible rects to distinguish RenderThread clipping from
     * ordinary view geometry. Applied just once per playing SurfaceView.
     */
    private static void probeAndExpandSurfaceClip(SurfaceView surface, int w, int h) {
        if (w != 480 || h != 480) return;
        if (clipProbeApplied && clipProbeSurface.get() == surface) return;
        if (clipProbeApplied) restoreSurfaceClip();
        clipProbeSurface = new WeakReference<>(surface);
        Rect oldClip = surface.getClipBounds();
        clipProbeOriginalBounds = oldClip == null ? null : new Rect(oldClip);
        clipProbeEnabledReadable = false;
        try {
            clipProbeOriginalEnabled = XposedHelpers.getBooleanField(surface, "mClipSurfaceToBounds");
            clipProbeEnabledReadable = true;
        } catch (Throwable e) {
            log("CROP original enabled flag unavailable=" + e.getClass().getSimpleName());
        }
        Rect before = new Rect();
        boolean beforeVisible = surface.getGlobalVisibleRect(before);
        log("CROP BEFORE visible=" + beforeVisible + ":" + before +
            " clipBounds=" + clipProbeOriginalBounds +
            " surfaceClipEnabled=" + (clipProbeEnabledReadable ? clipProbeOriginalEnabled : "unknown") +
            " surfaceView=" + surface.getWidth() + "x" + surface.getHeight());
        try {
            // SurfaceView's hidden API is available through Xposed reflection
            // on Android 13. With clipping enabled, setClipBounds can refresh
            // its window-crop rather than merely moving the Java view.
            XposedHelpers.callMethod(surface, "setEnableSurfaceClipping", true);
            surface.setClipBounds(new Rect(0, 0, w, h));
            surface.invalidate();
            clipProbeApplied = true;
            Rect after = new Rect();
            boolean afterVisible = surface.getGlobalVisibleRect(after);
            log("CROP TEST enabled=true bounds=0,0," + w + "," + h +
                " visible=" + afterVisible + ":" + after);
        } catch (Throwable e) {
            log("CROP TEST unsupported " + e.getClass().getSimpleName() + ": " + e.getMessage());
            // Avoid leaving a half-applied change if the hidden API differs.
            restoreSurfaceClip();
        }
    }

    private static void restoreSurfaceClip() {
        SurfaceView surface = clipProbeSurface.get();
        if (surface != null) {
            try {
                surface.setClipBounds(clipProbeOriginalBounds);
                if (clipProbeEnabledReadable) {
                    XposedHelpers.callMethod(surface, "setEnableSurfaceClipping", clipProbeOriginalEnabled);
                }
                surface.invalidate();
                log("CROP RESTORED bounds=" + clipProbeOriginalBounds +
                    " enabled=" + (clipProbeEnabledReadable ? clipProbeOriginalEnabled : "unknown"));
            } catch (Throwable e) {
                log("CROP restore error=" + e.getClass().getSimpleName());
            }
        }
        clipProbeApplied = false;
        clipProbeSurface = new WeakReference<>(null);
        clipProbeOriginalBounds = null;
        clipProbeEnabledReadable = false;
    }

    /** Compositor-layer test; DOES NOT reparent, resize or scale the surface. */
    private static void enableTopLayerProbe(SurfaceView surface) {
        if (topLayerProbeEnabled && topLayerSurface.get() == surface) return;
        if (topLayerProbeEnabled) restoreTopLayerProbe();
        try {
            boolean wasAbove = false;
            try {
                Object result = XposedHelpers.callMethod(surface, "isZOrderedOnTop");
                if (result instanceof Boolean) wasAbove = (Boolean)result;
            } catch (Throwable ignored) {
                // The S100 API may omit the hidden method; normal YouTube
                // SurfaceView is below its window in our SurfaceFlinger capture.
            }
            topLayerOriginallyAbove = wasAbove;
            topLayerSurface = new WeakReference<>(surface);
            if (!wasAbove) surface.setZOrderOnTop(true);
            topLayerProbeEnabled = true;
            int target = surface.getContext().getApplicationInfo().targetSdkVersion;
            log("TOP_LAYER TEST aboveWindow=true previous=" + wasAbove +
                " targetSdk=" + target +
                " video=" + surface.getWidth() + "x" + surface.getHeight() +
                " valid=" + surface.getHolder().getSurface().isValid());
        } catch (Throwable e) {
            log("TOP_LAYER test failed " + e.getClass().getSimpleName() + ": " + e.getMessage());
            restoreTopLayerProbe();
        }
    }

    // Verified on S100 / YouTube 21.40.161: actual caption Layout.draw calls
    // contain this specific player-overlay drawChild frame. Other UI text
    // (e.g. video titles, durations, menus) does NOT share this draw path.
    private static boolean isYoutubePlayerCaptionDraw() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if ("com.google.android.apps.youtube.app.common.player.overlay.YouTubePlayerOverlaysLayout"
                    .equals(frame.getClassName()) && "drawChild".equals(frame.getMethodName()))
                return true;
        }
        return false;
    }

    // The caption painter can draw multiple Layouts in the same frame (e.g.
    // two speakers). Group them before updating the separate caption panel.
    private static void receiveDrawnCaption(String line) {
        if (!inFullscreen || panelWindow == null) return;
        // This draw hook runs on the UI thread; use the panel's handler so
        // caption updates never manipulate the window from another thread.
        ControlPanel panel = panelWindow;
        synchronized (pendingCaptionParts) {
            pendingCaptionParts.add(line);
            if (captionFlushScheduled) return;
            captionFlushScheduled = true;
        }
        panel.handler.postDelayed(S100YoutubeHook::flushDrawnCaption, CAPTION_GROUP_MS);
    }

    private static void flushDrawnCaption() {
        String caption;
        synchronized (pendingCaptionParts) {
            caption = android.text.TextUtils.join("\n", pendingCaptionParts);
            pendingCaptionParts.clear();
            captionFlushScheduled = false;
        }
        if (!inFullscreen || panelWindow == null || caption.isEmpty()) return;
        // A currently-drawn cue is more reliable than class-name heuristics.
        captionSource = new WeakReference<>(null);
        boolean changed = !caption.equals(lastCaption);
        lastCaption = caption;
        lastCaptionUpdateMs = android.os.SystemClock.uptimeMillis();
        if (changed)
            log("CAPTION_MIRRORED live-draw text=" + shortCaption(caption));
        refreshCaption();
    }

    private static String shortCaption(String text) {
        return "[" + text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
            .replace('[', '(').replace(']', ')').substring(0, Math.min(115, text.length())) + "]";
    }

    // Recognize only caption-specific Android view classes/resource names.
    // No substring guesses from ordinary dialogue, channel titles or menus.
    private static String captionOrigin(View view) {
        Activity a = currentActivity.get();
        if (!isHome(a)) return null;
        for (View v = view; v != null;) {
            String className = v.getClass().getName().toLowerCase(java.util.Locale.ROOT);
            String resName = "";
            if (v.getId() != View.NO_ID) {
                try { resName = v.getResources().getResourceEntryName(v.getId())
                        .toLowerCase(java.util.Locale.ROOT); }
                catch (Throwable ignored) {}
            }
            // Skip settings menu switches and closed-captions settings labels.
            if (className.contains("caption") || className.contains("subtitle") ||
                    resName.contains("subtitle") || resName.contains("caption") ||
                    resName.contains("timed_text") || resName.contains("cue_view")) {
                return v.getClass().getSimpleName() + "/" + resName;
            }
            Object parent = v.getParent();
            v = parent instanceof View ? (View)parent : null;
        }
        return null;
    }

    private static void updateCaption(TextView source, String value) {
        if (!inFullscreen) return;
        captionSource = new WeakReference<>(source);
        lastCaption = value;
        lastCaptionUpdateMs = android.os.SystemClock.uptimeMillis();
        Handler handler = mainHandler();
        if (handler != null) handler.post(S100YoutubeHook::refreshCaption);
    }

    private static void refreshCaption() {
        ControlPanel c = panelWindow;
        if (c == null || !inFullscreen) return;
        String text = lastCaption;
        TextView source = captionSource.get();
        if (source != null) {
            try {
                if (source.isShown()) {
                    String latest = source.getText().toString().trim();
                    if (!text.equals(latest)) lastCaption = latest;
                    text = latest;
                    // The caption can remain legitimately visible longer than
                    // the 8-second fallback. Mirror its actual View visibility.
                    if (!latest.isEmpty())
                        lastCaptionUpdateMs = android.os.SystemClock.uptimeMillis();
                } else {
                    text = "";
                }
            } catch (Throwable ignored) {}
        } else if (android.os.SystemClock.uptimeMillis() - lastCaptionUpdateMs > CAPTION_EXPIRE_MS) {
            text = "";
        }
        boolean visible = !c.controlsVisible && !text.isEmpty();
        String status = source == null ?
            (text.isEmpty() ? "no-caption-source" : "live-draw-caption") :
            (text.isEmpty() ? "caption-source-empty-or-hidden" : "caption-source-with-text");
        if (!status.equals(lastCaptionStatus)) {
            lastCaptionStatus = status;
            log("CAPTION_STATUS " + status + " controlsVisible=" + c.controlsVisible +
                " mirrored=" + visible);
        }
        if (visible) {
            if (!text.contentEquals(c.caption.getText())) c.caption.setText(text);
            if (c.caption.getVisibility() != View.VISIBLE) c.caption.setVisibility(View.VISIBLE);
        } else if (c.caption.getVisibility() != View.GONE) {
            c.caption.setVisibility(View.GONE);
        }
    }

    // Controls use their own attached app subwindow. setZOrderOnTop(true)
    // necessarily covers views in the original YouTube activity window; a
    // normal in-app FrameLayout overlay would therefore still be invisible.
    private static void showControlPanel(Activity activity) {
        if (!inFullscreen || !isHome(activity)) return;
        if (panelWindow != null) {
            if (panelWindow.activity == activity) return;
            dismissControlPanel();
        }
        try {
            IBinder hostToken = activity.getWindow().getDecorView().getWindowToken();
            if (hostToken == null) {
                log("CONTROL_PANEL delayed: activity window token not ready");
                return;
            }
            WindowManager manager = (WindowManager)activity.getSystemService(Context.WINDOW_SERVICE);
            if (manager == null) return;
            // v0.3.2 FIX: The attached window is 96px high. v0.3.0 had
            // a VERTICAL root with a 36px seekbar + 60px buttons followed
            // by a 96px subtitle view. That pushed ALL subtitles outside
            // the visible window. Overlay them in the SAME 96px band.
            FrameLayout content = new FrameLayout(activity);
            content.setBackgroundColor(Color.argb(242, 8, 8, 8));
            // This window covers only the bottom 96px letterbox band and
            // never intercepts touches inside the 480x270 video itself.
            TextView subtitle = new TextView(activity);
            subtitle.setTextColor(Color.WHITE);
            subtitle.setTextSize(15f);
            subtitle.setGravity(Gravity.CENTER);
            subtitle.setMaxLines(4);
            subtitle.setShadowLayer(3f, 1f, 1f, Color.BLACK);
            subtitle.setPadding(10, 1, 10, 1);
            subtitle.setVisibility(View.GONE);
            subtitle.setClickable(false);
            subtitle.setContentDescription("Mirrored YouTube captions");
            FrameLayout.LayoutParams captionLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
            content.addView(subtitle, captionLp);

            // Playback controls occupy the same band while visible.
            // They hide after four seconds, revealing the caption view.
            LinearLayout controlsLayout = new LinearLayout(activity);
            controlsLayout.setOrientation(LinearLayout.VERTICAL);
            content.addView(controlsLayout, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));
            SeekBar timeline = new SeekBar(activity);
            timeline.setMax(1000);
            timeline.setContentDescription("Video progress and seek");
            timeline.setVisibility(View.GONE);
            controlsLayout.addView(timeline, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 36));
            LinearLayout buttons = new LinearLayout(activity);
            buttons.setOrientation(LinearLayout.HORIZONTAL);
            Button back10 = makeControlButton(activity, "-10s", "Seek backward ten seconds");
            Button toggle = makeControlButton(activity, "Play/Pause", "Toggle video playback");
            Button forward10 = makeControlButton(activity, "+10s", "Seek forward ten seconds");
            Button exit = makeControlButton(activity, "Exit", "Exit fullscreen");
            buttons.addView(back10, new LinearLayout.LayoutParams(0, 60, 1f));
            buttons.addView(toggle, new LinearLayout.LayoutParams(0, 60, 1f));
            buttons.addView(forward10, new LinearLayout.LayoutParams(0, 60, 1f));
            buttons.addView(exit, new LinearLayout.LayoutParams(0, 60, 1f));
            controlsLayout.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 60));
            back10.setOnClickListener(v -> {
                shiftPlayback(activity, -10000);
                scheduleControlAutoHide(panelWindow);
            });
            forward10.setOnClickListener(v -> {
                shiftPlayback(activity, 10000);
                scheduleControlAutoHide(panelWindow);
            });
            toggle.setOnClickListener(v -> {
                togglePlayback(activity);
                scheduleControlAutoHide(panelWindow);
            });
            exit.setOnClickListener(v -> {
                log("CONTROL_PANEL exit requested");
                activity.onBackPressed();
            });
            timeline.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {}
                @Override public void onStartTrackingTouch(SeekBar bar) {
                    ControlPanel active = panelWindow;
                    if (active != null) {
                        active.seeking = true;
                        active.handler.removeCallbacks(active.autoHide);
                    }
                }
                @Override public void onStopTrackingTouch(SeekBar bar) {
                    ControlPanel active = panelWindow;
                    if (active != null) active.seeking = false;
                    MediaController controller = getYouTubeController(activity);
                    long duration = getMediaDuration(controller);
                    if (controller != null && duration > 0) {
                        long position = (duration * bar.getProgress()) / 1000;
                        controller.getTransportControls().seekTo(position);
                        log("CONTROL_PANEL seekTo=" + position + "ms");
                    }
                    scheduleControlAutoHide(panelWindow);
                }
            });
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, 96,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            lp.token = hostToken;
            lp.packageName = activity.getPackageName();
            lp.setTitle("S100 YouTube playback controls");
            manager.addView(content, lp);
            Handler handler = mainHandler();
            if (handler == null) throw new IllegalStateException("main handler missing");
            Runnable[] tick = new Runnable[1];
            tick[0] = () -> {
                ControlPanel active = panelWindow;
                if (active == null || active.activity != activity || !inFullscreen) return;
                MediaController controller = getYouTubeController(activity);
                long duration = getMediaDuration(controller);
                if (active.controlsVisible && duration > 0) {
                    if (active.timeline.getVisibility() != View.VISIBLE)
                        active.timeline.setVisibility(View.VISIBLE);
                    if (!active.seeking) {
                        PlaybackState state = controller != null ? controller.getPlaybackState() : null;
                        long current = state != null ? state.getPosition() : 0;
                        active.timeline.setProgress((int)Math.max(0, Math.min(1000,
                            current * 1000 / duration)));
                    }
                } else if (active.timeline.getVisibility() != View.GONE) {
                    active.timeline.setVisibility(View.GONE);
                }
                refreshCaption();
                handler.postDelayed(tick[0], 850);
            };
            Runnable hide = () -> {
                ControlPanel active = panelWindow;
                if (active != null && active.activity == activity && !active.seeking)
                    setControlVisibility(active, false, "idle");
            };
            panelWindow = new ControlPanel(activity, manager, content, buttons,
                timeline, subtitle, handler, tick[0], hide);
            // With the child controls GONE, this transparent window remains a
            // tappable region in the bottom band. Never require a permanent handle.
            content.setClickable(true);
            content.setContentDescription("Tap bottom letterbox band to show YouTube controls");
            content.setOnClickListener(v -> {
                ControlPanel active = panelWindow;
                if (active != null && active.activity == activity)
                    setControlVisibility(active, !active.controlsVisible, "bottom-band-tap");
            });
            handler.post(tick[0]);
            scheduleControlAutoHide(panelWindow);
            log("CAPTION_LAYOUT v032 overlay-within-window=96px caption=96px " +
                "controls=36+60px videoUnchanged=true");
            log("CONTROL_PANEL created TYPE_APPLICATION_PANEL autoHideMs=" +
                CONTROL_AUTO_HIDE_MS + " transparentWhenHidden=true videoUnchanged=true");
        } catch (Throwable e) {
            log("CONTROL_PANEL failure=" + e.getClass().getSimpleName() + ": " + e.getMessage());
            dismissControlPanel();
        }
    }

    // Keep the full-screen video SurfaceView untouched. Hide only our own
    // attached subwindow's content, freeing the bottom 96px for subtitles.
    // The transparent root remains clickable to reveal controls on demand.
    private static void setControlVisibility(ControlPanel active, boolean visible, String why) {
        if (active == null || panelWindow != active || !inFullscreen) return;
        active.controlsVisible = visible;
        active.buttons.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (!visible) active.timeline.setVisibility(View.GONE);
        refreshCaption();
        active.root.setBackgroundColor(visible ? Color.argb(242, 8, 8, 8) : Color.TRANSPARENT);
        active.handler.removeCallbacks(active.autoHide);
        if (visible && !active.seeking)
            active.handler.postDelayed(active.autoHide, CONTROL_AUTO_HIDE_MS);
        log("CONTROL_PANEL " + (visible ? "SHOWN" : "HIDDEN") + " reason=" + why +
            " transparent=" + (!visible));
    }

    private static void scheduleControlAutoHide(ControlPanel active) {
        if (active == null || panelWindow != active || !active.controlsVisible) return;
        active.handler.removeCallbacks(active.autoHide);
        if (!active.seeking)
            active.handler.postDelayed(active.autoHide, CONTROL_AUTO_HIDE_MS);
    }

    private static Button makeControlButton(Activity a, String label, String description) {
        Button b = new Button(a);
        b.setText(label);
        b.setTextSize(15f);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setAllCaps(false);
        b.setContentDescription(description);
        return b;
    }

    private static void dismissControlPanel() {
        captionSource = new WeakReference<>(null);
        lastCaption = "";
        lastCaptionUpdateMs = 0;
        lastCaptionStatus = "";
        captionDrawSamples.clear();
        captionViewSamples.clear();
        synchronized (pendingCaptionParts) {
            pendingCaptionParts.clear();
            captionFlushScheduled = false;
        }
        ControlPanel active = panelWindow;
        panelWindow = null;
        if (active == null) return;
        try {
            active.handler.removeCallbacks(active.refresher);
            active.handler.removeCallbacks(active.autoHide);
            active.manager.removeViewImmediate(active.root);
            log("CONTROL_PANEL removed");
        } catch (Throwable e) {
            log("CONTROL_PANEL removal error=" + e.getClass().getSimpleName());
        }
    }

    private static void trackSession(MediaSession session, String reason) {
        synchronized (discoveredSessions) {
            for (WeakReference<MediaSession> ref : discoveredSessions)
                if (ref.get() == session) return;
            discoveredSessions.add(new WeakReference<>(session));
        }
        log("CONTROL_PANEL media session " + reason);
    }

    private static MediaController getYouTubeController(Activity a) {
        synchronized (discoveredSessions) {
            for (int i = discoveredSessions.size() - 1; i >= 0; i--) {
                MediaSession session = discoveredSessions.get(i).get();
                if (session == null) continue;
                try {
                    if (session.isActive())
                        return new MediaController(a, session.getSessionToken());
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private static long getMediaDuration(MediaController c) {
        try {
            if (c == null || c.getMetadata() == null) return -1;
            long duration = c.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
            return duration > 0 ? duration : -1;
        } catch (Throwable ignored) { return -1; }
    }

    private static void sendMediaKey(Activity a, int key) {
        try {
            AudioManager audio = (AudioManager)a.getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) return;
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
            log("CONTROL_PANEL media key=" + key);
        } catch (Throwable e) {
            log("CONTROL_PANEL media key error=" + e.getClass().getSimpleName());
        }
    }

    private static void togglePlayback(Activity a) {
        MediaController c = getYouTubeController(a);
        try {
            if (c != null) {
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING)
                    c.getTransportControls().pause();
                else c.getTransportControls().play();
                log("CONTROL_PANEL play/pause via MediaController");
                return;
            }
        } catch (Throwable e) {
            log("CONTROL_PANEL transport error=" + e.getClass().getSimpleName());
        }
        sendMediaKey(a, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    private static void shiftPlayback(Activity a, long delta) {
        MediaController c = getYouTubeController(a);
        try {
            if (c != null) {
                PlaybackState st = c.getPlaybackState();
                long duration = getMediaDuration(c);
                if (st != null && (st.getActions() & PlaybackState.ACTION_SEEK_TO) != 0) {
                    long target = Math.max(0, st.getPosition() + delta);
                    if (duration > 0) target = Math.min(target, duration);
                    c.getTransportControls().seekTo(target);
                    log("CONTROL_PANEL 10s seekTo=" + target + "ms");
                    return;
                }
            }
        } catch (Throwable e) {
            log("CONTROL_PANEL seek error=" + e.getClass().getSimpleName());
        }
        sendMediaKey(a, delta < 0 ? KeyEvent.KEYCODE_MEDIA_REWIND :
                                      KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
    }

    private static void restoreTopLayerProbe() {
        SurfaceView surface = topLayerSurface.get();
        if (surface != null && topLayerProbeEnabled) {
            try {
                surface.setZOrderOnTop(topLayerOriginallyAbove);
                log("TOP_LAYER RESTORED previous=" + topLayerOriginallyAbove +
                    " view=" + surface.getWidth() + "x" + surface.getHeight());
            } catch (Throwable e) {
                log("TOP_LAYER restore failed " + e.getClass().getSimpleName());
            }
        }
        topLayerProbeEnabled = false;
        topLayerOriginallyAbove = false;
        topLayerSurface = new WeakReference<>(null);
    }

    private static void logSurfaceParents(View surface) {
        // One compact diagnostic per successful apply, helpful if transforms
        // are still clipped by an app-level texture or video viewport.
        StringBuilder sb = new StringBuilder("surfaceParents");
        View cur = surface;
        for (int i = 0; i < 8 && cur != null; i++) {
            sb.append(" > ").append(cur.getClass().getSimpleName());
            int[] xy = new int[2];
            cur.getLocationOnScreen(xy);
            ViewGroup.LayoutParams lp = cur.getLayoutParams();
            sb.append("[").append(cur.getWidth()).append("x").append(cur.getHeight())
              .append(" @").append(xy[0]).append(",").append(xy[1])
              .append(" lpH=").append(lp != null ? lp.height : -999).append("]");
            cur = cur.getParent() instanceof View ? (View)cur.getParent() : null;
        }
        log(sb.toString());
    }

    private static SurfaceView findSurface(View root) {
        if (root instanceof SurfaceView && root.getWidth() > 80 && root.getHeight() > 80) return (SurfaceView)root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)root;
            for (int i = 0; i < group.getChildCount(); i++) {
                SurfaceView s = findSurface(group.getChildAt(i));
                if (s != null) return s;
            }
        }
        return null;
    }

    private static void restore(Activity a) {
        final Handler handler = mainHandler();
        if (handler == null) return;
        handler.post(() -> {
            try {
                // Remove the separate control window before restoring the mini-player.
                dismissControlPanel();
                // Reverse Z-order BEFORE mini-player geometry is restored.
                restoreTopLayerProbe();
                resetLetterboxAlignment();
                restoreSurfaceClip();
                IdentityHashMap<View, int[]> previouslyExpanded =
                    new IdentityHashMap<>(viewportTargets);
                // Turn off hooks FIRST, before asking YouTube to remeasure.
                viewportTargets.clear();
                reportedMeasure.clear();
                reportedLayout.clear();
                for (Map.Entry<View, SavedView> pair : new ArrayList<>(saved.entrySet())) {
                    View v = pair.getKey();
                    SavedView s = pair.getValue();
                    if (v instanceof SurfaceView) {
                        // Never reapply a saved SurfaceView layout. YouTube may
                        // already have switched the decoder to a 232x130 mini
                        // viewport by the time restoration runs.
                        continue;
                    }
                    if (previouslyExpanded.containsKey(v)) {
                        // Only revert the ancestor height WE changed. Preserve
                        // YouTube's current mini-player width/margins/animations.
                        ViewGroup.LayoutParams live = v.getLayoutParams();
                        if (live != null && s.layout != null && live.height == 480) {
                            ViewGroup.LayoutParams patched = copyParams(live);
                            patched.height = s.layout.height;
                            v.setLayoutParams(patched);
                        }
                        // We never changed an ancestor's transform/visibility;
                        // do not overwrite YouTube's new mini-player transforms.
                        continue;
                    }
                    v.setTranslationX(s.translationX);
                    v.setTranslationY(s.translationY);
                    v.setVisibility(s.visibility);
                    v.setScaleX(s.scaleX);
                    v.setScaleY(s.scaleY);
                    if (s.layout != null) v.setLayoutParams(s.layout);
                }
                saved.clear();
                for (Map.Entry<ViewGroup, boolean[]> pair : new ArrayList<>(oldClip.entrySet())) {
                    ViewGroup vg = pair.getKey();
                    boolean[] flags = pair.getValue();
                    vg.setClipChildren(flags[0]);
                    vg.setClipToPadding(flags[1]);
                }
                oldClip.clear();
                Window w = a.getWindow();
                View decor = w.getDecorView();
                if (oldWindowFlags != -1) {
                    w.setFlags(oldWindowFlags, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    decor.setSystemUiVisibility(oldSystemUiFlags);
                    decor.setBackground(oldBackground);
                }
                oldWindowFlags = -1;
                oldSystemUiFlags = -1;
                oldBackground = null;
                miniRestoreQueued = false;
                log("RESTORED watch page without modifying native SurfaceView");
                handler.postDelayed(() -> reportMiniSurface(a, "after-350ms"), 350);
                handler.postDelayed(() -> reportMiniSurface(a, "after-1200ms"), 1200);
            } catch (Throwable e) {
                log("restore failure " + e.getMessage());
            } finally {
                miniRestoreQueued = false;
            }
        });
    }
    /** Observation only: tells us whether YouTube's video Surface stays valid
     * after the minimized-player transition. Does not restart the decoder. */
    private static void reportMiniSurface(Activity a, String stage) {
        try {
            SurfaceView previous = lastPlayerSurface.get();
            SurfaceView current = findSurface(a.getWindow().getDecorView());
            SurfaceView candidate = previous != null ? previous : current;
            if (candidate == null) {
                log("MINI_SURFACE " + stage + " missing");
                return;
            }
            int[] loc = new int[2];
            candidate.getLocationOnScreen(loc);
            Rect frame = candidate.getHolder().getSurfaceFrame();
            boolean valid = candidate.getHolder().getSurface().isValid();
            log("MINI_SURFACE " + stage +
                " view=" + candidate.getWidth() + "x" + candidate.getHeight() +
                " screenXY=" + loc[0] + "," + loc[1] +
                " scale=" + candidate.getScaleX() + "," + candidate.getScaleY() +
                " translation=" + candidate.getTranslationX() + "," + candidate.getTranslationY() +
                " nativeFrame=" + frame + " valid=" + valid +
                " sameSurface=" + (previous == current));
        } catch (Throwable e) {
            log("MINI_SURFACE " + stage + " diagnostic error=" + e.getClass().getSimpleName());
        }
    }

}
