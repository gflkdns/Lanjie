package com.aoye.callaccount;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

/**
 * 来电测试账号工具类，负责账号注册、启用状态查询、系统设置跳转和模拟来电。
 *
 * <p>宿主必须显式传入账号名称。名称会同时用于系统 Calling accounts 列表和模拟
 * 来电的主叫显示名称。</p>
 */
public final class PhoneAccountUtils {
    /** 宿主启动页接收被拦截号码时使用的 Intent extra。 */
    public static final String EXTRA_NUMBER = "com.aoye.callaccount.extra.NUMBER";
    /** 宿主启动页接收来电时间时使用的 Intent extra。 */
    public static final String EXTRA_TIME = "com.aoye.callaccount.extra.TIME";
    /** 模块内部传递模拟来电模式时使用的 extra。 */
    public static final String EXTRA_TEST_MODE = "com.aoye.callaccount.extra.TEST_MODE";
    /** 账号可用性检查完成后发送给当前宿主包的广播 action。 */
    public static final String ACTION_ACCOUNT_CHECK_RESULT =
            "com.aoye.callaccount.ACCOUNT_CHECK_RESULT";
    /** {@link #ACTION_ACCOUNT_CHECK_RESULT} 广播中的检查结果。 */
    public static final String EXTRA_ACCOUNT_CHECK_SUCCESS =
            "com.aoye.callaccount.extra.ACCOUNT_CHECK_SUCCESS";
    /** 创建后立即断开的账号可用性检查模式。 */
    public static final String MODE_CHECK = "check";
    /** 拒接来电并打开宿主拦截页面的模式。 */
    public static final String MODE_BLOCK = "block";
    /** 保持响铃并交给系统来电界面处理的模式。 */
    public static final String MODE_RINGING = "ringing";

    private static final String TAG = "CallAccount";
    private static final String ACCOUNT_ID = "lanjie-test-account";
    private static final String LEGACY_SERVICE_CLASS =
            "com.aoye.lanjie.TestCallConnectionService";
    private static final String PREFERENCES_NAME = "call_account";
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String TELECOM_SETTINGS_PACKAGE = "com.android.server.telecom";
    private static final String ENABLE_ACCOUNT_SETTINGS_ACTIVITY =
            "com.android.server.telecom.settings.EnableAccountPreferenceActivity";

    private PhoneAccountUtils() {
    }

    /**
     * 注册或更新来电账号。
     *
     * <p>相同 {@link PhoneAccountHandle} 可重复注册，因此动态名称发生变化时再次调用即可
     * 更新系统中的显示名称。注册成功后名称会保存给 {@link TestCallConnectionService} 使用。</p>
     *
     * @param context 应用上下文或组件上下文
     * @param accountName 显示在系统账号列表中的名称，不可为空
     * @return 注册请求是否成功提交给 Telecom
     * @throws IllegalArgumentException 当 {@code accountName} 为空或仅包含空格时
     */
    public static boolean register(Context context, String accountName) {
        requireAccountName(accountName);
        TelecomManager manager = telecomManager(context);
        if (manager == null) {
            return false;
        }
        PhoneAccountHandle handle = accountHandle(context);
        unregisterLegacyAccount(context, manager);
        try {
            PhoneAccount account = new PhoneAccount.Builder(handle, accountName)
                    .setCapabilities(PhoneAccount.CAPABILITY_CALL_PROVIDER)
                    .addSupportedUriScheme(PhoneAccount.SCHEME_TEL)
                    .build();
            manager.registerPhoneAccount(account);
            preferences(context).edit().putString(KEY_ACCOUNT_NAME, accountName).apply();
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to register phone account", e);
            return false;
        }
    }

    /**
     * 查询当前账号是否已被用户启用。
     *
     * <p>部分厂商系统会限制 {@link TelecomManager#getPhoneAccount(PhoneAccountHandle)}；
     * 遇到权限或系统异常时本方法返回 {@code false}，不会申请额外电话权限。</p>
     *
     * @param context 应用上下文或组件上下文
     * @return 账号存在且已启用时返回 {@code true}
     */
    public static boolean isEnabled(Context context) {
        TelecomManager manager = telecomManager(context);
        if (manager == null) {
            return false;
        }
        try {
            PhoneAccount account = manager.getPhoneAccount(accountHandle(context));
            return account != null && account.isEnabled();
        } catch (RuntimeException e) {
            Log.w(TAG, "Unable to read phone account", e);
            return false;
        }
    }

    /**
     * 注册账号并打开系统账号启用页面。
     *
     * <p>优先打开 Telecom 的账号启用页；设备不支持时依次降级到电话账号设置、
     * 通话设置和系统设置。</p>
     *
     * @param activity 用于启动系统设置页面的 Activity
     * @param accountName 显示在系统账号列表中的名称，不可为空
     * @throws IllegalArgumentException 当 {@code accountName} 为空或仅包含空格时
     */
    public static void openSettings(Activity activity, String accountName) {
        requireAccountName(accountName);
        if (isEnabled(activity)) {
            return;
        }
        register(activity, accountName);
        if (start(activity, new Intent().setClassName(
                TELECOM_SETTINGS_PACKAGE, ENABLE_ACCOUNT_SETTINGS_ACTIVITY))) {
            return;
        }
        if (start(activity, new Intent(TelecomManager.ACTION_CHANGE_PHONE_ACCOUNTS))) {
            return;
        }
        if (start(activity, new Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS))) {
            return;
        }
        activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
    }

    /**
     * 通过当前账号向 Telecom 提交一通模拟来电。
     *
     * @param context 应用上下文或组件上下文
     * @param accountName 账号显示名称，不可为空
     * @param number 模拟来电号码
     * @param mode {@link #MODE_CHECK}、{@link #MODE_BLOCK} 或 {@link #MODE_RINGING}
     * @return Telecom 是否接受了模拟来电请求
     * @throws IllegalArgumentException 当 {@code accountName} 为空或仅包含空格时
     */
    public static boolean requestIncomingCall(
            Context context, String accountName, String number, String mode) {
        requireAccountName(accountName);
        TelecomManager manager = telecomManager(context);
        if (manager == null) {
            return false;
        }
        register(context, accountName);
        Bundle extras = new Bundle();
        extras.putParcelable(
                TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null));
        extras.putString(EXTRA_TEST_MODE, mode);
        try {
            manager.addNewIncomingCall(accountHandle(context), extras);
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to request incoming call", e);
            return false;
        }
    }

    static PhoneAccountHandle accountHandle(Context context) {
        return new PhoneAccountHandle(
                new ComponentName(context, TestCallConnectionService.class), ACCOUNT_ID);
    }

    static String accountName(Context context) {
        String accountName = preferences(context).getString(KEY_ACCOUNT_NAME, null);
        if (accountName == null || accountName.trim().isEmpty()) {
            throw new IllegalStateException("Phone account has not been registered with a name");
        }
        return accountName;
    }

    private static void requireAccountName(String accountName) {
        if (accountName == null || accountName.trim().isEmpty()) {
            throw new IllegalArgumentException("accountName must not be blank");
        }
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private static void unregisterLegacyAccount(Context context, TelecomManager manager) {
        // 服务迁入 library 后组件名发生变化，尽力清理旧版本遗留的无效账号。
        PhoneAccountHandle legacyHandle = new PhoneAccountHandle(
                new ComponentName(context.getPackageName(), LEGACY_SERVICE_CLASS), ACCOUNT_ID);
        try {
            manager.unregisterPhoneAccount(legacyHandle);
        } catch (RuntimeException e) {
            Log.d(TAG, "Unable to remove legacy phone account", e);
        }
    }

    private static TelecomManager telecomManager(Context context) {
        TelecomManager manager = context.getSystemService(TelecomManager.class);
        if (manager == null) {
            Log.w(TAG, "TelecomManager unavailable");
        }
        return manager;
    }

    private static boolean start(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.d(TAG, "Phone account settings activity unavailable", e);
            return false;
        }
    }
}
