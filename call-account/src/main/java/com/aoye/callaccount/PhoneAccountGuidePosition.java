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
        return calculate(
                screenWidth,
                statusBarInset,
                density,
                HORIZONTAL_INSET_DP,
                ACTION_BAR_HEIGHT_DP,
                ACCOUNT_ROW_HEIGHT_DP);
    }

    static Rect calculate(int screenWidth, int statusBarInset, float density,
            float horizontalInsetDp, float actionBarHeightDp, float accountRowHeightDp) {
        return calculateWithRowHeightPx(
                screenWidth,
                statusBarInset,
                density,
                horizontalInsetDp,
                actionBarHeightDp,
                Math.round(accountRowHeightDp * density));
    }

    static Rect calculateWithRowHeightPx(int screenWidth, int statusBarInset, float density,
            float horizontalInsetDp, float actionBarHeightDp, int accountRowHeightPx) {
        int horizontalInset = Math.round(horizontalInsetDp * density);
        int top = statusBarInset + Math.round(actionBarHeightDp * density);
        int bottom = top + accountRowHeightPx;
        return new Rect(horizontalInset, top, screenWidth - horizontalInset, bottom);
    }
}
