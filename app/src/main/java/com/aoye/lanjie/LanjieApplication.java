package com.aoye.lanjie;

import android.app.Application;

import com.aoye.callaccount.PhoneAccountUtils;

public class LanjieApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PhoneAccountUtils.register(this, PhoneAccountUtils.localizedAccountName());
    }
}
