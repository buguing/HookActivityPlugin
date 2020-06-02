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

    //设备系统版本是不是大于等于29(Android 10)
    private static boolean ifSdkOverIncluding29() {
        int SDK_INT = Build.VERSION.SDK_INT;
        return SDK_INT >= 29;
    }

    //设备系统版本是不是大于等于26(Android 8.0 Oreo)
    private static boolean ifSdkOverIncluding26() {
        int SDK_INT = Build.VERSION.SDK_INT;
        return SDK_INT >= 26;
    }

    //设备系统版本是不是大于等于28(Android 9.0 Pie)
    private static boolean ifSdkOverIncluding28() {
        int SDK_INT = Build.VERSION.SDK_INT;
        return SDK_INT >= 28;
    }

    public void hookStartActivity() throws Exception {
        Object defaultSingleton;
        if (ifSdkOverIncluding29()) {
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
            //android.app.ActivityThread$H.EXECUTE_TRANSACTION = 159
            //android 9.0反射,Accessing hidden field Landroid/app/ActivityThread$H;->EXECUTE_TRANSACTION:I (dark greylist, reflection)
            //android9.0 深灰名单（dark greylist）则debug版本在会弹出dialog提示框，在release版本会有Toast提示，均提示为"Detected problems with API compatibility"
            if (msg.what == 159) {//直接写死,不反射了,否则在android9.0的设备上运行会弹出使用了反射的dialog提示框
                handleActivity(msg);
            }
            return false;
        }

        private void handleActivity(Message msg) {

            try {
                //ClientTransaction-->ClientTransaction中的List<ClientTransactionItem> mActivityCallbacks-->集合中的第一个值LaunchActivityItem-->LaunchActivityItem的mIntent
                // 这里简单起见,直接取出TargetActivity;
                //final ClientTransaction transaction = (ClientTransaction) msg.obj;
                //1.获取ClientTransaction对象
                Object clientTransactionObj = msg.obj;
                if (clientTransactionObj == null) return;
                //2.获取ClientTransaction类中属性mActivityCallbacks的Field
                //private List<ClientTransactionItem> mActivityCallbacks;
                Field mActivityCallbacksField = clientTransactionObj.getClass().getDeclaredField("mActivityCallbacks");
                //3.禁止Java访问检查
                mActivityCallbacksField.setAccessible(true);
                //4.获取ClientTransaction类中mActivityCallbacks属性的值,既List<ClientTransactionItem>
                List<?> mActivityCallbacks = (List<?>) mActivityCallbacksField.get(clientTransactionObj);
                if (mActivityCallbacks == null || mActivityCallbacks.size() <= 0) return;
                if (mActivityCallbacks.get(0) == null) return;
                //5.ClientTransactionItem的Class对象
                //package android.app.servertransaction;
                //public class LaunchActivityItem extends ClientTransactionItem
                Class<?> launchActivityItemClazz = Class.forName("android.app.servertransaction.LaunchActivityItem");
                //6.判断集合中第一个元素的值是LaunchActivityItem类型的
                if (!launchActivityItemClazz.isInstance(mActivityCallbacks.get(0))) return;
                //7.获取LaunchActivityItem的实例
                // public class LaunchActivityItem extends ClientTransactionItem
                Object launchActivityItem = mActivityCallbacks.get(0);
                //8.ClientTransactionItem的mIntent属性的mIntent的Field
                //private Intent mIntent;
                Field mIntentField = launchActivityItemClazz.getDeclaredField("mIntent");
                mIntentField.setAccessible(true);
                //10.获取mIntent属性的值,既桩Intent(安全的Intent)
                //从LaunchActivityItem中获取属性mIntent的值
                Intent safeIntent = (Intent) mIntentField.get(launchActivityItem);
                if (safeIntent == null) return;
                //11.获取原始的Intent
                Intent originIntent = safeIntent.getParcelableExtra(EXTRA_ORIGINAL_INTENT);
                //12.需要判断originIntent != null
                if (originIntent == null) return;
                //13.将原始的Intent,赋值给clientTransactionItem的mIntent属性
                mIntentField.set(launchActivityItem, originIntent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

}
