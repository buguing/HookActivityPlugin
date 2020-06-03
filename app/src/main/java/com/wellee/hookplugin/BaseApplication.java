package com.wellee.hookplugin;

import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

public class BaseApplication extends Application {

    private static BaseApplication mInstance;
    private Resources mPluginResources;
    private AssetManager mPluginAssets;
    private Resources.Theme mTheme;
    private boolean hookSuccess;
    /**
     * 开关配置
     * hookAMS方式还是hookInstrumentation方式
     */
    public static boolean hookAMS = false;

    public static BaseApplication getInstance() {
        return mInstance;
    }

    public boolean isHookSuccess() {
        return hookSuccess;
    }

    public void setHookSuccess(boolean hookSuccess) {
        this.hookSuccess = hookSuccess;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        mInstance = this;
        int reflection = Reflection.unseal();
        Log.d("BaseApplication", reflection == 0 ? "hide api exempt success" : "hide api exempt failure");

        if (hookAMS) {
            HookActivityUtils hookUtils = new HookActivityUtils(base, StubActivity.class);
            try {
                hookUtils.hookStartActivity();
                hookUtils.hookLauncherActivity();
                hookSuccess = true;
            } catch (Exception e) {
                e.printStackTrace();
                hookSuccess = false;
                // Android10 在application中hook startActivity拿到mInstance为null 所以在Activity中判断是否需hook
            }
        } else {
            // hook instrumentation
            HookInstrumentationUtils utils = new HookInstrumentationUtils(base, StubActivity.class);
            try {
                utils.hookInstrumentation();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setPluginResources(Resources resources) {
        if (resources == null) return;
        this.mPluginResources = resources;
        this.mPluginAssets = resources.getAssets();
//        this.mTheme = resources.newTheme();
//        this.mTheme.setTo(super.getTheme());
    }

    @Override
    public Resources getResources() {
        return mPluginResources == null ? super.getResources() : mPluginResources;
    }

    @Override
    public AssetManager getAssets() {
        return mPluginAssets == null ? super.getAssets() : mPluginAssets;
    }

//    @Override
//    public Resources.Theme getTheme() {
//        return mTheme == null ? super.getTheme() : mTheme;
//    }

}
