package com.wellee.hookplugin;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public class HookInstrumentationUtils {

    private static final String TAG = HookInstrumentationUtils.class.getSimpleName();
    private static final String EXTRA_ORIGINAL_INTENT = "EXTRA_ORIGINAL_INTENT";

    private Context mContext;
    private Class<?> mProxyClass;

    private HookInstrumentationUtils(){}

    public HookInstrumentationUtils(Context context, Class<?> proxyClass) {
        this.mContext = context;
        this.mProxyClass = proxyClass;
    }

    @SuppressLint("PrivateApi")
    public void hookInstrumentation() throws Exception {
        Class<?> contextImplClass = Class.forName("android.app.ContextImpl");
        Field mMainThreadField = contextImplClass.getDeclaredField("mMainThread");
        mMainThreadField.setAccessible(true);
        Object activityThread = mMainThreadField.get(mContext);

        Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
        Field mInstrumentationField = activityThreadClass.getDeclaredField("mInstrumentation");
        mInstrumentationField.setAccessible(true);

        Object mInstrumentation = mInstrumentationField.get(activityThread);
        mInstrumentationField.set(activityThread, new InstrumentationProxy(mInstrumentation, mContext, mProxyClass));
    }

    private static class InstrumentationProxy extends Instrumentation {

        private Object mInstrumentation;
        private Context mContext;
        private Class<?> mProxyClass;

        public InstrumentationProxy(Object instrumentation, Context context, Class<?> proxyClass) {
            this.mInstrumentation = instrumentation;
            this.mContext = context;
            this.mProxyClass = proxyClass;
        }

        public ActivityResult execStartActivity(
                Context who, IBinder contextThread, IBinder token, Activity target,
                Intent intent, int requestCode, Bundle options) {
            int flag = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flag = PackageManager.MATCH_ALL;
            }
            List<ResolveInfo> resolveInfoList = mContext.getPackageManager().queryIntentActivities(intent, flag);
            Intent stubIntent = null;
            if (resolveInfoList.size() == 0) {
                stubIntent = new Intent(mContext, mProxyClass);
                stubIntent.putExtra(EXTRA_ORIGINAL_INTENT, intent);
            }
            try {
                Method execStartActivityMethod
                        = Instrumentation.class.getDeclaredMethod("execStartActivity", Context.class,
                        IBinder.class, IBinder.class, Activity.class, Intent.class, int.class, Bundle.class);
                return (ActivityResult) execStartActivityMethod.invoke(mInstrumentation,
                        who, contextThread, token, target, stubIntent, requestCode, options);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException,
                IllegalAccessException, ClassNotFoundException{
            Intent originalIntent = intent.getParcelableExtra(EXTRA_ORIGINAL_INTENT);
            intent = originalIntent == null ? intent : originalIntent;
            className = originalIntent == null ? className : originalIntent.getComponent() == null ?
                    className : originalIntent.getComponent().getClassName();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return ((Instrumentation)mInstrumentation).newActivity(cl, className, intent);
            }
            return super.newActivity(cl, className, intent);
        }

    }
}
