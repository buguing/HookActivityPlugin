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
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HookActivityUtils hookUtils = new HookActivityUtils(this, StubActivity.class);
        try {
            hookUtils.hookStartActivity();
            hookUtils.hookLauncherActivity();
            hookSuccess = true;
        } catch (Exception e) {
            e.printStackTrace();
            hookSuccess = false;
            // Android10 在application中hook startActivity拿到mInstance为null 所以在Activity中判断是否需hook
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
