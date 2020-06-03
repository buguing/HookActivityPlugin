package com.wellee.hookplugin;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wellee.hookplugin.plugin.PluginManager;

import java.io.File;
import java.lang.reflect.Field;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private File pluginParentFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pluginParentFile = getExternalFilesDir(null);
        if (!pluginParentFile.exists()) {
            pluginParentFile.mkdirs();
        }
        if (!BaseApplication.getInstance().isHookSuccess()) {
            HookActivityUtils hookUtils = new HookActivityUtils(this, StubActivity.class);
            try {
                hookUtils.hookStartActivity();
                hookUtils.hookLauncherActivity();
                BaseApplication.getInstance().setHookSuccess(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void jump(View view) {
        startActivity(new Intent(this, NotRegisteredActivity.class));
    }

    public void installPluginFromFile(View view) {
        File plugin = new File(pluginParentFile, "plugin-debug.apk");
        if (!plugin.exists()) {
            Toast.makeText(this, "未发现插件apk", Toast.LENGTH_SHORT).show();
            return;
        }
        installPlugin(plugin);
    }

    public void installPluginFromAssets(View view) {
        FileUtil.extractAssets(this, "plugin-debug.apk");
        File plugin = new File(getFilesDir(), "plugin-debug.apk");
        if (!plugin.exists()) {
            Toast.makeText(this, "未发现插件apk", Toast.LENGTH_SHORT).show();
            return;
        }
        installPlugin(plugin);
    }

    boolean installSuccess;

    private void installPlugin(File plugin) {
        try {
            PluginManager.install(this, plugin.getAbsolutePath());
            installSuccess = true;
        } catch (Exception e) {
            e.printStackTrace();
            installSuccess = false;
            Toast.makeText(this, "插件安装失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "插件安装成功", Toast.LENGTH_SHORT).show();
    }

    public void jumpPlugin(View view) {
        if (installSuccess) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(this, "com.wellee.plugin.MainActivity"));
            intent.putExtra("data", "haha");
            startActivity(intent);
        } else {
            Toast.makeText(this, "请先成功安装插件", Toast.LENGTH_SHORT).show();
        }

    }

    public void invokeBlackListApi(View view) {
        boolean success = testBlackListApi();
        Toast.makeText(this, success ? "success" : "fail", Toast.LENGTH_SHORT).show();
    }

    private boolean testBlackListApi() {
        boolean success = true;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true;
        try {
            Class<?> activityManagerClazz = Class.forName("android.app.ActivityManager");
            Field field = activityManagerClazz.getField("INSTR_FLAG_DISABLE_HIDDEN_API_CHECKS");
            Object object = field.get(null);
            if (object == null) {
                return false;
            }
            int check_flag = (int) object;
            Log.d(TAG, "get blacklist api :" + check_flag);
        } catch (NoSuchFieldException | ClassNotFoundException | IllegalAccessException e) {
            e.printStackTrace();
            success = false;
        }
        return success;
    }

}
