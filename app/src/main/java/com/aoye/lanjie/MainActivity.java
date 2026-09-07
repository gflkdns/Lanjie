package com.aoye.lanjie;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.DateFormat;
import java.util.Date;

import com.aoye.callaccount.PhoneAccountUtils;

public class MainActivity extends Activity {
    private static final String CHECK_NUMBER = "00000000000";
    private static final String DEFAULT_TEST_NUMBER = "13581918888";
    private static final long ACCOUNT_CHECK_TIMEOUT_MS = 3000L;
    private static final long BACKGROUND_BLOCK_REQUEST_DELAY_MS = 2000L;
    private boolean waitingForAccountCheck;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable accountCheckTimeoutRunnable = () -> {
        if (waitingForAccountCheck) {
            finishAccountRequestCheck(false, "来电账号状态: 未收到系统回调");
        }
    };
    private final BroadcastReceiver accountCheckReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!PhoneAccountUtils.ACTION_ACCOUNT_CHECK_RESULT.equals(intent.getAction())) {
                return;
            }
            boolean success = intent.getBooleanExtra(
                    PhoneAccountUtils.EXTRA_ACCOUNT_CHECK_SUCCESS, false);
            finishAccountRequestCheck(success,
                    success ? "来电账号状态: 已启用" : "来电账号状态: 未启用");
        }
    };
    private TextView accountStatusView;
    private EditText numberInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(PhoneAccountUtils.ACTION_ACCOUNT_CHECK_RESULT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(accountCheckReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(accountCheckReceiver, filter);
        }
        updateAccountStatus();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(accountCheckTimeoutRunnable);
        try {
            unregisterReceiver(accountCheckReceiver);
        } catch (IllegalArgumentException ignored) {
            // Receiver may not be registered if this Activity is showing the blocked-call page.
        }
        super.onPause();
    }

    private void buildUi() {
        if (getIntent().hasExtra(PhoneAccountUtils.EXTRA_NUMBER)) {
            showCallUi();
            return;
        }
        showSettingsUi();
    }

    private void showSettingsUi() {
        int padding = dp(24);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("来电账号测试");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        accountStatusView = new TextView(this);
        accountStatusView.setTextSize(16);
        accountStatusView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(0, dp(18), 0, dp(18));
        root.addView(accountStatusView, statusParams);

        Button testAccountButton = new Button(this);
        testAccountButton.setText("启用测试来电账号");
        testAccountButton.setOnClickListener(v -> openPhoneAccountSettings());
        root.addView(testAccountButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        Button checkAccountButton = new Button(this);
        checkAccountButton.setText("检查来电账号状态");
        checkAccountButton.setOnClickListener(v -> performAccountRequestCheck());
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        checkParams.setMargins(0, dp(12), 0, 0);
        root.addView(checkAccountButton, checkParams);

        numberInput = new EditText(this);
        numberInput.setHint("输入测试电话号码");
        numberInput.setSingleLine(true);
        numberInput.setInputType(InputType.TYPE_CLASS_PHONE);
        numberInput.setText(DEFAULT_TEST_NUMBER);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.setMargins(0, dp(28), 0, 0);
        root.addView(numberInput, inputParams);

        Button blockedCallButton = new Button(this);
        blockedCallButton.setText("模拟拦截来电并弹出页面");
        blockedCallButton.setOnClickListener(v -> requestBlockedCallFromBackground());
        LinearLayout.LayoutParams blockedParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        blockedParams.setMargins(0, dp(12), 0, 0);
        root.addView(blockedCallButton, blockedParams);

        Button ringingCallButton = new Button(this);
        ringingCallButton.setText("模拟系统来电页面");
        ringingCallButton.setOnClickListener(
                v -> requestTestIncomingCall(PhoneAccountUtils.MODE_RINGING));
        LinearLayout.LayoutParams ringingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ringingParams.setMargins(0, dp(12), 0, 0);
        root.addView(ringingCallButton, ringingParams);

        updateAccountStatus();
    }

    private void showCallUi() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        String number = getIntent().getStringExtra(PhoneAccountUtils.EXTRA_NUMBER);
        if (number == null || number.isEmpty()) {
            number = "未知号码";
        }
        long timeMillis = getIntent().getLongExtra(
                PhoneAccountUtils.EXTRA_TIME, System.currentTimeMillis());

        int padding = dp(28);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(padding, padding, padding, padding);
        setContentView(root);

        TextView title = new TextView(this);
        title.setText("已拦截来电");
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView numberView = new TextView(this);
        numberView.setText(number);
        numberView.setTextSize(24);
        numberView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        numberParams.setMargins(0, dp(24), 0, 0);
        root.addView(numberView, numberParams);

        TextView timeView = new TextView(this);
        timeView.setText(DateFormat.getDateTimeInstance().format(new Date(timeMillis)));
        timeView.setTextSize(16);
        timeView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.setMargins(0, dp(12), 0, dp(24));
        root.addView(timeView, timeParams);

        Button closeButton = new Button(this);
        closeButton.setText("知道了");
        closeButton.setOnClickListener(v -> finish());
        root.addView(closeButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void openPhoneAccountSettings() {
        PhoneAccountUtils.openSettings(this, accountName());
    }

    private void requestTestIncomingCall(String mode) {
        String number = numberInput == null ? "" : numberInput.getText().toString().trim();
        if (number.isEmpty()) {
            number = DEFAULT_TEST_NUMBER;
        }
        requestTestIncomingCall(this, number, mode);
    }

    private void requestBlockedCallFromBackground() {
        String number = numberInput == null ? "" : numberInput.getText().toString().trim();
        if (number.isEmpty()) {
            number = DEFAULT_TEST_NUMBER;
        }
        final String requestedNumber = number;
        if (accountStatusView != null) {
            accountStatusView.setText("来电账号状态: 2 秒后模拟拦截来电");
        }
        moveTaskToBack(true);
        handler.postDelayed(
                () -> PhoneAccountUtils.requestIncomingCall(
                        this, accountName(), requestedNumber, PhoneAccountUtils.MODE_BLOCK),
                BACKGROUND_BLOCK_REQUEST_DELAY_MS);
    }

    private static boolean requestTestIncomingCall(
            Context context, String number, String mode) {
        return PhoneAccountUtils.requestIncomingCall(
                context, PhoneAccountUtils.localizedAccountName(), number, mode);
    }

    private void updateAccountStatus() {
        if (accountStatusView == null) {
            return;
        }
        PhoneAccountUtils.register(this, accountName());
        accountStatusView.setText("来电账号状态: 点击检查");
    }

    private void performAccountRequestCheck() {
        if (accountStatusView != null) {
            accountStatusView.setText("来电账号状态: 正在发送检查请求...");
        }
        waitingForAccountCheck = true;
        PhoneAccountUtils.register(this, accountName());
        boolean requestStarted = requestTestIncomingCall(
                this, CHECK_NUMBER, PhoneAccountUtils.MODE_CHECK);
        if (!requestStarted) {
            finishAccountRequestCheck(false, "来电账号状态: 未启用");
            return;
        }
        handler.removeCallbacks(accountCheckTimeoutRunnable);
        handler.postDelayed(accountCheckTimeoutRunnable, ACCOUNT_CHECK_TIMEOUT_MS);
    }

    private void finishAccountRequestCheck(boolean success, String statusText) {
        waitingForAccountCheck = false;
        handler.removeCallbacks(accountCheckTimeoutRunnable);
        if (accountStatusView != null) {
            accountStatusView.setText(statusText);
        }
    }

    private String accountName() {
        return PhoneAccountUtils.localizedAccountName();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
