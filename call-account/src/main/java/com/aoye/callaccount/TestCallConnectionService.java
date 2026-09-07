package com.aoye.callaccount;

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
    private static final String TAG = "CallAccount";
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
        Uri address = readAddress(request);
        Bundle extras = request == null ? null : request.getExtras();
        String mode = extras == null ? PhoneAccountUtils.MODE_BLOCK
                : extras.getString(PhoneAccountUtils.EXTRA_TEST_MODE, PhoneAccountUtils.MODE_BLOCK);

        TestConnection connection = new TestConnection();
        connection.setAddress(address, TelecomManager.PRESENTATION_ALLOWED);
        connection.setCallerDisplayName(
                PhoneAccountUtils.accountName(this), TelecomManager.PRESENTATION_ALLOWED);
        if (PhoneAccountUtils.MODE_CHECK.equals(mode)) {
            disconnect(connection, DisconnectCause.LOCAL);
            sendAccountCheckResult(true);
        } else if (PhoneAccountUtils.MODE_RINGING.equals(mode)) {
            connection.setRinging();
        } else {
            openBlockedPage(readNumber(address));
            disconnect(connection, DisconnectCause.REJECTED);
        }
        return connection;
    }

    @Override
    public void onCreateIncomingConnectionFailed(
            PhoneAccountHandle connectionManagerPhoneAccount,
            ConnectionRequest request) {
        Bundle extras = request == null ? null : request.getExtras();
        String mode = extras == null ? PhoneAccountUtils.MODE_BLOCK
                : extras.getString(PhoneAccountUtils.EXTRA_TEST_MODE, PhoneAccountUtils.MODE_BLOCK);
        if (PhoneAccountUtils.MODE_CHECK.equals(mode)) {
            sendAccountCheckResult(false);
        }
        Log.w(TAG, "Incoming connection failed");
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
            disconnect(this, DisconnectCause.REJECTED);
        }

        @Override
        public void onDisconnect() {
            stopAnnouncement();
            disconnect(this, DisconnectCause.LOCAL);
        }
    }

    private static Uri readAddress(ConnectionRequest request) {
        Uri address = request == null ? null : request.getAddress();
        Bundle extras = request == null ? null : request.getExtras();
        if (address == null && extras != null) {
            address = extras.getParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS);
        }
        return address == null
                ? Uri.fromParts(PhoneAccount.SCHEME_TEL, DEFAULT_TEST_NUMBER, null)
                : address;
    }

    private void openBlockedPage(String number) {
        Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (intent == null) {
            Log.w(TAG, "Host application has no launcher activity");
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(PhoneAccountUtils.EXTRA_NUMBER, number)
                .putExtra(PhoneAccountUtils.EXTRA_TIME, System.currentTimeMillis());
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            Log.e(TAG, "Unable to open blocked-call page", e);
        }
    }

    private static String readNumber(Uri address) {
        String number = address.getSchemeSpecificPart();
        return number == null ? address.toString() : number;
    }

    private void sendAccountCheckResult(boolean success) {
        Intent intent = new Intent(PhoneAccountUtils.ACTION_ACCOUNT_CHECK_RESULT)
                .setPackage(getPackageName())
                .putExtra(PhoneAccountUtils.EXTRA_ACCOUNT_CHECK_SUCCESS, success);
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
                    descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            descriptor.close();
            announcementPlayer.setLooping(true);
            announcementPlayer.prepare();
            announcementPlayer.start();
        } catch (Exception e) {
            Log.e(TAG, "Unable to play announcement", e);
            stopAnnouncement();
        }
    }

    private static int selectAnnouncementResource() {
        String language = Locale.getDefault().getLanguage();
        if (Locale.JAPANESE.getLanguage().equals(language)) {
            return R.raw.announcement_ja_jp;
        }
        if ("pt".equals(language)) {
            return R.raw.announcement_pt_br;
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
            // Player may already be stopped.
        }
        announcementPlayer.release();
        announcementPlayer = null;
    }

    private static void disconnect(Connection connection, int cause) {
        connection.setDisconnected(new DisconnectCause(cause));
        connection.destroy();
    }
}
