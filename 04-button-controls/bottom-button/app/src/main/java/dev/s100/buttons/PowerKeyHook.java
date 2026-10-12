package dev.s100.buttons;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * S100 W527 / crDroid 9.3 Android 13 SDK 33 experimental power key override.
 * Exact method signatures confirmed in the user's services.jar on 2026-10-12.
 */
public final class PowerKeyHook implements IXposedHookLoadPackage {
    private static final String TAG = "S100-PowerKey v0.2: ";
    private static final int SHORT_HOME = 4;
    private static final int DOUBLE_BRIGHTNESS_PLACEHOLDER = 2;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // Legacy LSPosed may call the system_server package "android" or "system".
        if (!"android".equals(lpparam.packageName)
                && !"system".equals(lpparam.packageName)) {
            return;
        }

        try {
            Class<?> pwm = XposedHelpers.findClass(
                    "com.android.server.policy.PhoneWindowManager", lpparam.classLoader);
            Method init = getMethod(pwm, "init", 2);
            Method powerPress = pwm.getDeclaredMethod(
                    "powerPress", long.class, int.class, boolean.class);
            final Method sleep = pwm.getDeclaredMethod(
                    "sleepDefaultDisplayFromPowerButton", long.class, int.class);
            sleep.setAccessible(true);

            if (init == null || sleep.getReturnType() != boolean.class
                    || powerPress.getReturnType() != void.class) {
                XposedBridge.log(TAG + "ABORT: unexpected framework signature");
                return;
            }

            // Intercept double action *before* overriding any framework policy.
            XposedBridge.hookMethod(powerPress, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (((Integer) p.args[1]) != 2) return;

                    if ((Boolean) p.args[2]) {
                        // A press beginning asleep must not turn the screen off again.
                        XposedBridge.log(TAG + "double originated asleep: ignore");
                        p.setResult(null);
                        return;
                    }
                    try {
                        Object result = sleep.invoke(p.thisObject, (Long) p.args[0], 0);
                        XposedBridge.log(TAG + "double sleep returned " + result);
                    } catch (Throwable failure) {
                        XposedBridge.log(TAG + "double sleep error: " + failure);
                    }
                    // Suppress the framework placeholder brightness boost.
                    p.setResult(null);
                }
            });

            // init loads resource-based policy; set our fields AFTER it finishes.
            XposedBridge.hookMethod(init, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        XposedHelpers.setIntField(
                                p.thisObject, "mShortPressOnPowerBehavior", SHORT_HOME);
                        // Nonzero enables native double-press recognition.
                        XposedHelpers.setIntField(
                                p.thisObject, "mDoublePressOnPowerBehavior",
                                DOUBLE_BRIGHTNESS_PLACEHOLDER);
                        XposedBridge.log(TAG + "READY: single Home, double sleep, "
                                + "native long power menu and wake");
                    } catch (Throwable failure) {
                        XposedBridge.log(TAG + "ABORT: policy fields " + failure);
                    }
                }
            });
            XposedBridge.log(TAG + "method hooks registered");
        } catch (Throwable failure) {
            // Never throw from this module into Android's system process.
            XposedBridge.log(TAG + "ABORT: " + failure);
        }
    }

    private static Method getMethod(Class<?> clazz, String name, int count) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (name.equals(m.getName()) && m.getParameterTypes().length == count) {
                return m;
            }
        }
        return null;
    }
}
