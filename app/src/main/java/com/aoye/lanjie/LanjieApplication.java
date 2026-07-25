package com.aoye.lanjie;

import android.app.Application;

public class LanjieApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        registerTestAccount();
    }

    private void registerTestAccount() {
        MainActivity.registerTestAccount(this);
    }
}
