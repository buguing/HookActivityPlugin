package com.wellee.hookplugin.plugin;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;

import com.wellee.hookplugin.BaseApplication;

import java.lang.reflect.Method;

public class PluginManager {

    public static void install(Context context, String apkPath) throws Exception {
        // 解决类加载的问题
        FixDexManager fixDexManager = new FixDexManager(context);
        // 把apk的class加载到ApplicationClassLoader
        fixDexManager.fixDex(apkPath);

        // 解决资源加载问题
        Resources resource = getResource(context, apkPath);
        BaseApplication.getInstance().setPluginResources(resource);
    }

    private static Resources getResource(Context context, String path) throws Exception {
        Resources superRes = context.getResources();
        // 创建AssetManager
        AssetManager asset = AssetManager.class.newInstance();
        Method method = AssetManager.class.getDeclaredMethod("addAssetPath", String.class);
        method.invoke(asset, path);
        return new Resources(asset, superRes.getDisplayMetrics(), superRes.getConfiguration());
    }
}
