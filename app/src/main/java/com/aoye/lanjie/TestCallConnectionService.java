package com.aoye.lanjie;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import java.util.Locale;

public class TestCallConnectionService extends ConnectionService {
    private static final String TAG = "LanjieTestCall";
    private static final String DEFAULT_TEST_NUMBER = "13581918888";

    private MediaPlayer announcementPlayer;

    @Override
    public void onDestroy() {
        stopAnnouncement();
        super.onDestroy();
    }

    @Override
    public Connection onCreateIncomingConnection(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Uri address = request == null ? null : request.getAddress();
        Bundle extras = request == null ? null : request.getExtras();
        if (address == null && request != null) {
            if (extras != null) {
                address = extras.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS);
            }
        }
        if (address == null) {
            address = Uri.fromParts(PhoneAccount.SCHEME_TEL, DEFAULT_TEST_NUMBER, null);
        }

        TestConnection connection = new TestConnection();
        connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
        connection.setCallerDisplayName("拦截骚扰电话", TelecomManager.PRESENTATION_ALLOWED);
        String number = readNumber(address);
        String mode = extras == null ? MainActivity.TEST_MODE_BLOCK
                : extras.getString(MainActivity.EXTRA_TEST_MODE, MainActivity.TEST_MODE_BLOCK);
        if (MainActivity.TEST_MODE_CHECK.equals(mode)) {
            connection.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            connection.destroy();
            sendAccountCheckResult(true);
            Log.i(TAG, "Checked test phone account successfully");
            return connection;
        }
        if (MainActivity.TEST_MODE_RINGING.equals(mode)) {
            connection.setRinging();
            Log.i(TAG, "Created ringing test incoming connection: " + address);
            return connection;
        }

        openBlockedPage(number);
        connection.setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
        connection.destroy();
        Log.i(TAG, "Blocked test incoming connection directly: " + address);
        return connection;
    }

    @Override
    public void onCreateIncomingConnectionFailed(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Bundle extras = request == null ? null : request.getExtras();
        String mode = extras == null ? MainActivity.TEST_MODE_BLOCK
                : extras.getString(MainActivity.EXTRA_TEST_MODE, MainActivity.TEST_MODE_BLOCK);
        if (MainActivity.TEST_MODE_CHECK.equals(mode)) {
            sendAccountCheckResult(false);
        }
        Log.w(TAG, "Test incoming connection failed");
    }

    private final class TestConnection extends Connection {
        @Override
        public void onAnswer() {
            setActive();
            playAnnouncement();
        }

        @Override
        public void onReject() {
            stopAnnouncement();
            setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
            destroy();
        }

        @Override
        public void onDisconnect() {
            stopAnnouncement();
            setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            destroy();
        }
    }

    private void openBlockedPage(String number) {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_NUMBER, number)
                .putExtra(MainActivity.EXTRA_TIME, System.currentTimeMillis());
        startActivity(intent);
    }

    private static String readNumber(Uri address) {
        if (address == null) {
            return "";
        }
        String number = address.getSchemeSpecificPart();
        return number == null ? address.toString() : number;
    }

    private void sendAccountCheckResult(boolean success) {
        Intent intent = new Intent(MainActivity.ACTION_TEST_ACCOUNT_CHECK_RESULT)
                .setPackage(getPackageName())
                .putExtra(MainActivity.EXTRA_TEST_ACCOUNT_CHECK_SUCCESS, success);
        sendBroadcast(intent);
    }

    private void playAnnouncement() {
        try {
            stopAnnouncement();
            AssetFileDescriptor descriptor = getResources().openRawResourceFd(
                    selectAnnouncementResource());
            announcementPlayer = new MediaPlayer();
            announcementPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            announcementPlayer.setDataSource(
                    descriptor.getFileDescriptor(),
                    descriptor.getStartOffset(),
                    descriptor.getLength());
            descriptor.close();
            announcementPlayer.setLooping(true);
            announcementPlayer.setOnCompletionListener(player -> stopAnnouncement());
            announcementPlayer.prepare();
            announcementPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Unable to play announcement", e);
            stopAnnouncement();
        }
    }

    private static int selectAnnouncementResource() {
        String language = Locale.getDefault().getLanguage();
        if (Locale.CHINESE.getLanguage().equals(language)) {
            return R.raw.announcement_zh_cn;
        }
        if (Locale.JAPANESE.getLanguage().equals(language)) {
            return R.raw.announcement_ja_jp;
        }
        if (Locale.KOREAN.getLanguage().equals(language)) {
            return R.raw.announcement_ko_kr;
        }
        return R.raw.announcement_en_us;
    }

    private void stopAnnouncement() {
        if (announcementPlayer == null) {
            return;
        }
        try {
            if (announcementPlayer.isPlaying()) {
                announcementPlayer.stop();
            }
        } catch (IllegalStateException ignored) {
            // Player may already be stopped or released.
        }
        announcementPlayer.release();
        announcementPlayer = null;
    }
}
