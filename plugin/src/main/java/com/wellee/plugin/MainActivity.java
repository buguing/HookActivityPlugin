package com.wellee.plugin;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

public class MainActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        String data = getIntent().getStringExtra("data");
        if (!TextUtils.isEmpty(data)) {
            Toast.makeText(this, data + " ---- appName = " + getTitle(), Toast.LENGTH_SHORT).show();
        }
    }
}
