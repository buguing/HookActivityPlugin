package com.wellee.hookplugin;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtil {

    /**
     * 把Assets里面的文件复制到 /data/data/package/files 目录下
     */
    public static void extractAssets(Context context, String sourceName) {
        AssetManager am = context.getAssets();
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            is = am.open(sourceName);
            File extractFile = context.getFileStreamPath(sourceName);
            fos = new FileOutputStream(extractFile);
            byte[] buffer = new byte[1024];
            int count;
            while ((count = is.read(buffer)) > 0) {
                fos.write(buffer, 0, count);
            }
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeStream(is);
            closeStream(fos);
        }
    }

    private static void closeStream(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
