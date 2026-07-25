package com.aoye.callaccount;

import android.graphics.Rect;

/** 计算系统 Calling accounts 页面中第一条账号 Preference 的屏幕区域。 */
final class PhoneAccountGuidePosition {
    private static final float HORIZONTAL_INSET_DP = 12f;
    private static final float ACTION_BAR_HEIGHT_DP = 80f;
    private static final float ACCOUNT_ROW_HEIGHT_DP = 56f;

    private PhoneAccountGuidePosition() {
    }

    static Rect calculate(int screenWidth, int statusBarInset, float density) {
        int horizontalInset = Math.round(HORIZONTAL_INSET_DP * density);
        int top = statusBarInset + Math.round(ACTION_BAR_HEIGHT_DP * density);
        int bottom = top + Math.round(ACCOUNT_ROW_HEIGHT_DP * density);
        return new Rect(horizontalInset, top, screenWidth - horizontalInset, bottom);
    }
}
