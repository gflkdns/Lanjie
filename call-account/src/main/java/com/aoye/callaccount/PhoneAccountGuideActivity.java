package com.aoye.callaccount;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** 在系统来电账号设置页上方显示可点击关闭的透明引导层。 */
public final class PhoneAccountGuideActivity extends Activity {
    private static final String TAG = "CallAccountGuide";
    private static final String EXTRA_ACCOUNT_NAME =
            "com.aoye.callaccount.extra.GUIDE_ACCOUNT_NAME";
    private static final String EXTRA_OPEN_SETTINGS =
            "com.aoye.callaccount.extra.GUIDE_OPEN_SETTINGS";
    private static final long OPEN_SETTINGS_DELAY_MS = 100L;
    private static final long OVERLAY_RELAUNCH_DELAY_MS = 80L;
    private static final long BACKGROUND_TIMEOUT_MS = 4000L;

    private static final GuideDeviceConfig SAMSUNG_GUIDE_CONFIG =
            new GuideDeviceConfig(
                    "samsung", -110f, 0.90f, 0.10f, -7f,
                    12f, 80f, 56f, 0f, 36f, true);
    private static final GuideDeviceConfig XIAOMI_GUIDE_CONFIG =
            // HyperOS 3 row bounds match these dp values at 420/480/520/560 dpi.
            new GuideDeviceConfig(
                    "xiaomi", -45f, 0.72f, 0.08f, 7f,
                    0f, 56f, 53.5f, 0f, 36f, false);
    private static final GuideDeviceConfig OPPO_GUIDE_CONFIG =
            new GuideDeviceConfig(
                    "oppo", -45f, 0.72f, 0.08f, 7f,
                    0f, 56f, 53.5f, 0f, 36f, false);
    private static final GuideDeviceConfig VIVO_GUIDE_CONFIG =
            new GuideDeviceConfig(
                    "vivo", -45f, 0.72f, 0.08f, 7f,
                    16f, 60f, 23.5f, 15f, 50f, false);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable backgroundTimeout = () -> {
        if (!isFinishing()) {
            closeGuide();
        }
    };
    private final Runnable keepOverlayOnTop = new Runnable() {
        @Override
        public void run() {
            if (isFinishing()) {
                return;
            }
            startActivity(createOverlayIntent(PhoneAccountGuideActivity.this));
            handler.postDelayed(this, OVERLAY_RELAUNCH_DELAY_MS);
        }
    };

    private FrameLayout guideRoot;
    private View targetHighlight;
    private ImageView handView;
    private AnimatorSet highlightAnimator;
    private AnimatorSet handAnimator;
    private int statusBarInset;
    private GuideDeviceConfig guideDeviceConfig;
    private final Object guideVoiceOwner = new Object();
    private boolean guideClosed;

    static Intent createIntent(Context context, String accountName) {
        return new Intent(context, PhoneAccountGuideActivity.class)
                .putExtra(EXTRA_ACCOUNT_NAME, accountName)
                .putExtra(EXTRA_OPEN_SETTINGS, true);
    }

    private static Intent createOverlayIntent(Context context) {
        return new Intent(context, PhoneAccountGuideActivity.class)
                .putExtra(EXTRA_OPEN_SETTINGS, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();
        createGuideView();
        GuideVoicePlayer.playLoop(this, guideVoiceOwner);
        if (getIntent().getBooleanExtra(EXTRA_OPEN_SETTINGS, false)) {
            String accountName = getIntent().getStringExtra(EXTRA_ACCOUNT_NAME);
            PhoneAccountUtils.register(this, accountName);
            handler.postDelayed(() -> PhoneAccountUtils.openSystemSettings(this),
                    OPEN_SETTINGS_DELAY_MS);
        }
        handler.postDelayed(keepOverlayOnTop, OVERLAY_RELAUNCH_DELAY_MS);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            handler.removeCallbacks(backgroundTimeout);
            positionIndicator();
            guideRoot.setAlpha(1f);
            startAnimations();
        } else {
            scheduleBackgroundTimeout();
        }
    }

    @Override
    protected void onStop() {
        scheduleBackgroundTimeout();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (!guideClosed) {
            GuideVoicePlayer.stop(guideVoiceOwner);
        }
        if (highlightAnimator != null) {
            highlightAnimator.cancel();
        }
        if (handAnimator != null) {
            handAnimator.cancel();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        // 引导期间禁用返回键，点击高亮区域后关闭浮层。
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void createGuideView() {
        guideDeviceConfig = selectGuideDeviceConfig();
        guideRoot = new FrameLayout(this);
        guideRoot.setAlpha(0f);
        guideRoot.setOnClickListener(view -> closeGuide());
        guideRoot.setOnApplyWindowInsetsListener((view, insets) -> {
            statusBarInset = readStatusBarInset(insets);
            view.post(this::positionIndicator);
            return insets;
        });
        setContentView(guideRoot);

        targetHighlight = new View(this);
        targetHighlight.setContentDescription("call_account_guide_target");
        targetHighlight.setBackground(createHighlightDrawable());
        targetHighlight.setOnClickListener(view -> closeGuide());
        guideRoot.addView(targetHighlight, new FrameLayout.LayoutParams(1, 1));

        handView = new ImageView(this);
        handView.setContentDescription("call_account_guide_hand");
        handView.setImageResource(R.drawable.call_account_guide_hand);
        handView.setRotation(guideDeviceConfig.handRotationDegrees);
        int handSize = dp(68f);
        guideRoot.addView(handView, new FrameLayout.LayoutParams(handSize, handSize));

        highlightAnimator = createHighlightAnimator(
                targetHighlight, guideDeviceConfig.animateHighlightScale);
        handAnimator = createHandAnimator(handView);
    }

    private void closeGuide() {
        if (guideClosed) {
            return;
        }
        guideClosed = true;
        handler.removeCallbacks(keepOverlayOnTop);
        handler.removeCallbacks(backgroundTimeout);
        finish();
        GuideVoicePlayer.playOnce(this, guideVoiceOwner);
    }

    private void positionIndicator() {
        int screenWidth = guideRoot.getWidth();
        if (screenWidth <= 0) {
            screenWidth = getResources().getDisplayMetrics().widthPixels;
        }
        if (statusBarInset <= 0) {
            statusBarInset = statusBarHeightFromResources();
        }
        float density = getResources().getDisplayMetrics().density;
        Rect target = PhoneAccountGuidePosition.calculateWithRowHeightPx(
                screenWidth,
                statusBarInset,
                density,
                guideDeviceConfig.horizontalInsetDp,
                guideDeviceConfig.actionBarHeightDp,
                guideDeviceConfig.accountRowHeightPx(density));

        FrameLayout.LayoutParams targetParams = (FrameLayout.LayoutParams) targetHighlight.getLayoutParams();
        targetParams.width = target.width();
        targetParams.height = target.height();
        targetParams.leftMargin = target.left;
        targetParams.topMargin = target.top;
        targetHighlight.setLayoutParams(targetParams);

        FrameLayout.LayoutParams handParams = (FrameLayout.LayoutParams) handView.getLayoutParams();
        float targetX = target.right - dp(guideDeviceConfig.targetRightOffsetDp);
        float targetY = target.exactCenterY();
        handParams.leftMargin = Math.round(
                targetX - handParams.width * guideDeviceConfig.fingertipXRatio);
        handParams.topMargin = Math.round(
                targetY - handParams.height * guideDeviceConfig.fingertipYRatio);
        handView.setLayoutParams(handParams);
        Log.i(TAG, "profile=" + guideDeviceConfig.name + " manufacturer="
                + Build.MANUFACTURER + " brand=" + Build.BRAND + " density=" + density
                + " statusBar=" + statusBarInset
                + " target=" + target.toShortString());
    }

    private void startAnimations() {
        if (!highlightAnimator.isStarted()) {
            highlightAnimator.start();
        }
        if (!handAnimator.isStarted()) {
            handAnimator.start();
        }
    }

    private void scheduleBackgroundTimeout() {
        handler.removeCallbacks(backgroundTimeout);
        handler.postDelayed(backgroundTimeout, BACKGROUND_TIMEOUT_MS);
    }

    private static AnimatorSet createHighlightAnimator(View target, boolean animateScale) {
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator alpha = repeating(target, View.ALPHA, 0.95f, 0.50f);
        if (!animateScale) {
            set.play(alpha);
            return set;
        }
        ObjectAnimator scaleX = repeating(target, View.SCALE_X, 1f, 1.02f);
        ObjectAnimator scaleY = repeating(target, View.SCALE_Y, 1f, 1.08f);
        set.playTogether(alpha, scaleX, scaleY);
        return set;
    }

    private AnimatorSet createHandAnimator(View hand) {
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator x = repeating(
                hand, View.TRANSLATION_X, 0f, guideDeviceConfig.translationXEnd);
        ObjectAnimator y = repeating(hand, View.TRANSLATION_Y, 0f, -7f);
        ObjectAnimator scaleX = repeating(hand, View.SCALE_X, 1f, 0.82f);
        ObjectAnimator scaleY = repeating(hand, View.SCALE_Y, 1f, 0.82f);
        set.playTogether(x, y, scaleX, scaleY);
        return set;
    }

    private static GuideDeviceConfig selectGuideDeviceConfig() {
        if (isXiaomiDevice()) {
            return XIAOMI_GUIDE_CONFIG;
        }
        if (isOppoDevice()) {
            return OPPO_GUIDE_CONFIG;
        }
        if (isVivoDevice()) {
            return VIVO_GUIDE_CONFIG;
        }
        // Samsung is the verified baseline and remains the fallback for unknown manufacturers.
        return SAMSUNG_GUIDE_CONFIG;
    }

    private static boolean isXiaomiDevice() {
        return isXiaomiName(Build.MANUFACTURER) || isXiaomiName(Build.BRAND);
    }

    private static boolean isOppoDevice() {
        return "oppo".equalsIgnoreCase(Build.MANUFACTURER)
                || "oppo".equalsIgnoreCase(Build.BRAND);
    }

    private static boolean isVivoDevice() {
        return "vivo".equalsIgnoreCase(Build.MANUFACTURER)
                || "vivo".equalsIgnoreCase(Build.BRAND);
    }

    private static boolean isXiaomiName(String value) {
        return "xiaomi".equalsIgnoreCase(value)
                || "redmi".equalsIgnoreCase(value)
                || "poco".equalsIgnoreCase(value);
    }

    private static final class GuideDeviceConfig {
        final String name;
        final float handRotationDegrees;
        final float fingertipXRatio;
        final float fingertipYRatio;
        final float translationXEnd;
        final float horizontalInsetDp;
        final float actionBarHeightDp;
        final float accountRowHeightDp;
        final float accountRowVerticalPaddingDp;
        final float targetRightOffsetDp;
        final boolean animateHighlightScale;

        GuideDeviceConfig(String name, float handRotationDegrees, float fingertipXRatio,
                float fingertipYRatio, float translationXEnd, float horizontalInsetDp,
                float actionBarHeightDp, float accountRowHeightDp,
                float accountRowVerticalPaddingDp, float targetRightOffsetDp,
                boolean animateHighlightScale) {
            this.name = name;
            this.handRotationDegrees = handRotationDegrees;
            this.fingertipXRatio = fingertipXRatio;
            this.fingertipYRatio = fingertipYRatio;
            this.translationXEnd = translationXEnd;
            this.horizontalInsetDp = horizontalInsetDp;
            this.actionBarHeightDp = actionBarHeightDp;
            this.accountRowHeightDp = accountRowHeightDp;
            this.accountRowVerticalPaddingDp = accountRowVerticalPaddingDp;
            this.targetRightOffsetDp = targetRightOffsetDp;
            this.animateHighlightScale = animateHighlightScale;
        }

        int accountRowHeightPx(float density) {
            int contentHeight = Math.round(accountRowHeightDp * density);
            if (accountRowVerticalPaddingDp == 0f) {
                return contentHeight;
            }
            return contentHeight + 2 * Math.round(accountRowVerticalPaddingDp * density);
        }
    }

    private static ObjectAnimator repeating(View view, android.util.Property<View, Float> property,
            float start, float end) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(view, property, start, end);
        animator.setDuration(380L);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        return animator;
    }

    private static GradientDrawable createHighlightDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x3327AE60);
        drawable.setStroke(3, 0xFF27AE60);
        return drawable;
    }

    private int readStatusBarInset(WindowInsets insets) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return insets.getInsets(WindowInsets.Type.statusBars()).top;
        }
        return insets.getSystemWindowInsetTop();
    }

    private int statusBarHeightFromResources() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id == 0 ? 0 : getResources().getDimensionPixelSize(id);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
