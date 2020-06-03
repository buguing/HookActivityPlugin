package com.wellee.hookplugin;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * hook startActivity 和 launcherActivity
 */
public class HookActivityUtils {

    private static final String TAG = HookActivityUtils.class.getSimpleName();
    private static final String EXTRA_ORIGINAL_INTENT = "EXTRA_ORIGINAL_INTENT";

    private Context mContext;
    private Class<?> mProxyClass;

    private HookActivityUtils() {
    }

    public HookActivityUtils(Context context, Class<?> proxyClass) {
        this.mContext = context.getApplicationContext();
        this.mProxyClass = proxyClass;
    }

    private static boolean ifSdkOverIncluding29() {
        int SDK_INT = Build.VERSION.SDK_INT;
        return SDK_INT >= 29;
    }

    private static boolean ifSdkOverIncluding26() {
        int SDK_INT = Build.VERSION.SDK_INT;
        return SDK_INT >= 26;
    }

    private static boolean ifSdkOverIncluding28() {
        int SDK_INT = Build.VERSION.SDK_INT;
        return SDK_INT >= 28;
    }

    public void hookStartActivity() throws Exception {
        Object defaultSingleton;
        if (ifSdkOverIncluding29()) {
            // 29获取的是ActivityTaskManager
            defaultSingleton = getIActivityTaskManagerSingleton();
        } else if (ifSdkOverIncluding26()) {
            // 26，27，28的ams获取方式是通过ActivityManager.getService()
            // Android 8.0 获取ActivityManager里面的IActivityManagerSingleton
            defaultSingleton = getIActivityManagerSingleton();
        } else {
            // 25及以下 获取ActivityManagerNative里面的gDefault
            defaultSingleton = getDefault();
        }
        // 获取gDefault中的mInstance属性
        Class<?> singletonClass = Class.forName("android.util.Singleton");
        Field mInstanceField = singletonClass.getDeclaredField("mInstance");
        mInstanceField.setAccessible(true);

        Object mInstance = mInstanceField.get(defaultSingleton);
        if (mInstance == null) throw new NullPointerException("mInstance is null");

        Class<?> amClass;
        if (ifSdkOverIncluding29()) {
            amClass = Class.forName("android.app.IActivityTaskManager");
        } else {
            amClass = Class.forName("android.app.IActivityManager");
        }
        Object proxy = Proxy.newProxyInstance(HookActivityUtils.class.getClassLoader(),
                new Class[]{amClass},
                new StartActivityInvocationHandler(mInstance));
        mInstanceField.set(defaultSingleton, proxy);
    }

    private Object getIActivityTaskManagerSingleton() throws Exception {
        Class<?> amClass = Class.forName("android.app.ActivityTaskManager");
        Field iamsField = amClass.getDeclaredField("IActivityTaskManagerSingleton");
        iamsField.setAccessible(true);
        return iamsField.get(null);
    }

    private Object getIActivityManagerSingleton() throws Exception {
        Class<?> amClass = Class.forName("android.app.ActivityManager");
        Field iamsField = amClass.getDeclaredField("IActivityManagerSingleton");
        iamsField.setAccessible(true);
        return iamsField.get(null);
    }

    private Object getDefault() throws Exception {
        Class<?> amnClass = Class.forName("android.app.ActivityManagerNative");
        Field gDefaultField = amnClass.getDeclaredField("gDefault");
        gDefaultField.setAccessible(true);
        return gDefaultField.get(null);
    }

    private class StartActivityInvocationHandler implements InvocationHandler {

        private Object mObject;

        public StartActivityInvocationHandler(Object object) {
            this.mObject = object;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Log.d(TAG, "method name == " + method.getName());
            // 偷梁换柱
            if ("startActivity".equals(method.getName())) {
                Intent originalIntent = (Intent) args[2];
                Intent proxyIntent = new Intent(mContext, mProxyClass);
                args[2] = proxyIntent;
                proxyIntent.putExtra(EXTRA_ORIGINAL_INTENT, originalIntent);
            }
            return method.invoke(mObject, args);
        }
    }

    public void hookLauncherActivity() throws Exception {
        // 获取ActivityThread实例
        Class<?> atClass = Class.forName("android.app.ActivityThread");
        Field sCurrentActivityThreadField = atClass.getDeclaredField("sCurrentActivityThread");
        sCurrentActivityThreadField.setAccessible(true);
        Object sCurrentActivityThread = sCurrentActivityThreadField.get(null);
        // 获取mH
        Field mHField = atClass.getDeclaredField("mH");
        mHField.setAccessible(true);
        Object mH = mHField.get(sCurrentActivityThread);
        // 给Handler设置callback回调
        Class<?> handlerClass = Class.forName("android.os.Handler");
        Field mCallbackField = handlerClass.getDeclaredField("mCallback");
        mCallbackField.setAccessible(true);

        if (ifSdkOverIncluding28()) {
            mCallbackField.set(mH, new HandlerCallbackP());
        } else {
            mCallbackField.set(mH, new HCallback());
        }
    }

    private static class HCallback implements Handler.Callback {
        @Override
        public boolean handleMessage(Message msg) {
            // 100是Handler.START_ACTIVITY的值
            if (msg.what == 100) {
                handleLaunchActivity(msg);
            }
            return false;
        }

        /**
         * 借尸还魂
         *
         * @param msg msg
         */
        private void handleLaunchActivity(Message msg) {
            Object record = msg.obj;
            try {
                Field intentField = record.getClass().getDeclaredField("intent");
                intentField.setAccessible(true);
                Intent intent = (Intent) intentField.get(record);
                if (intent == null) return;
                Intent originalIntent = intent.getParcelableExtra(EXTRA_ORIGINAL_INTENT);
                if (originalIntent != null) {
                    intentField.set(record, originalIntent);
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private static class HandlerCallbackP implements Handler.Callback {

        @Override
        public boolean handleMessage(Message msg) {
            // android.app.ActivityThread$H.EXECUTE_TRANSACTION = 159
            if (msg.what == 159) {
                handleActivity(msg);
            }
            return false;
        }

        private void handleActivity(Message msg) {

            try {
                Object clientTransactionObj = msg.obj;
                Field mActivityCallbacksField = clientTransactionObj.getClass().getDeclaredField("mActivityCallbacks");
                mActivityCallbacksField.setAccessible(true);
                List<?> mActivityCallbacks = (List<?>) mActivityCallbacksField.get(clientTransactionObj);
                if (mActivityCallbacks == null || mActivityCallbacks.size() <= 0) {
                    return;
                }
                Object launchActivityItem = mActivityCallbacks.get(0);
                if (launchActivityItem == null) {
                    return;
                }
                Class<?> launchActivityItemClass = Class.forName("android.app.servertransaction.LaunchActivityItem");
                if (!launchActivityItemClass.isInstance(launchActivityItem)) {
                    return;
                }
                Field mIntentField = launchActivityItemClass.getDeclaredField("mIntent");
                mIntentField.setAccessible(true);
                Intent safeIntent = (Intent) mIntentField.get(launchActivityItem);
                if (safeIntent == null) {
                    return;
                }
                Intent originIntent = safeIntent.getParcelableExtra(EXTRA_ORIGINAL_INTENT);
                if (originIntent == null) {
                    return;
                }
                mIntentField.set(launchActivityItem, originIntent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

}
