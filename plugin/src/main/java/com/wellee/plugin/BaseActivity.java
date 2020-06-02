package com.wellee.plugin;

import android.content.res.AssetManager;
import android.content.res.Resources;

import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    public Resources getResources() {
        if (getApplication() == null) {
            return super.getResources();
        }
        Resources appResources = getApplication().getResources();
        return appResources == null ? super.getResources() : appResources;
    }

    @Override
    public AssetManager getAssets() {
        if (getApplication() == null) {
            return super.getAssets();
        }
        AssetManager appAssets = getApplication().getAssets();
        return appAssets == null ? super.getAssets() : appAssets;
    }

//    @Override
//    public Resources.Theme getTheme() {
//        if (getApplication() == null) {
//            return super.getTheme();
//        }
//        Resources.Theme appTheme = getApplication().getTheme();
//        return appTheme == null ? super.getTheme() : appTheme;
//    }
}
