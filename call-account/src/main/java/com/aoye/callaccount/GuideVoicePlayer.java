package com.aoye.callaccount;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import java.util.Locale;

/** Plays the phone-account guide prompt across the guide Activity's close transition. */
final class GuideVoicePlayer {
    private static final String TAG = "CallAccountGuide";

    private static MediaPlayer player;
    private static Object currentOwner;

    private GuideVoicePlayer() {
    }

    static synchronized void playLoop(Context context, Object owner) {
        play(context, owner, true);
    }

    static synchronized void playOnce(Context context, Object owner) {
        play(context, owner, false);
    }

    static synchronized void stop(Object owner) {
        if (currentOwner == owner) {
            releasePlayer();
        }
    }

    private static void play(Context context, Object owner, boolean looping) {
        releasePlayer();
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        MediaPlayer newPlayer = MediaPlayer.create(
                context.getApplicationContext(),
                selectGuideVoiceResource(),
                attributes,
                0);
        if (newPlayer == null) {
            Log.e(TAG, "Unable to create guide voice player");
            return;
        }
        player = newPlayer;
        currentOwner = owner;
        newPlayer.setLooping(looping);
        newPlayer.setOnCompletionListener(completedPlayer -> {
            synchronized (GuideVoicePlayer.class) {
                if (player == completedPlayer) {
                    releasePlayer();
                }
            }
        });
        newPlayer.start();
    }

    private static int selectGuideVoiceResource() {
        String language = Locale.getDefault().getLanguage();
        if (Locale.JAPANESE.getLanguage().equals(language)) {
            return R.raw.phone_account_guide_ja_jp;
        }
        if ("pt".equals(language)) {
            return R.raw.phone_account_guide_pt_br;
        }
        return R.raw.phone_account_guide_en_us;
    }

    private static void releasePlayer() {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
            } catch (IllegalStateException ignored) {
                // Player may already be stopped after an asynchronous completion.
            }
            player.release();
            player = null;
        }
        currentOwner = null;
    }
}
