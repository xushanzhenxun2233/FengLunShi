package com.xiao.clock;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.widget.Toast;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

/**
 * Hourly & half-hour chime manager compatible with Android 4.0+ (API 14 - API 34).
 * Uses Android system TextToSpeech (Chinese) with an automatic built-in synthesized
 * melodic bell chime via AudioTrack so it works 100% offline even on stripped-down old ROMs.
 */
public class HourlyChimeManager {

    private static volatile HourlyChimeManager instance;

    private final Context appContext;
    private final ClockPrefs prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private volatile boolean ttsChineseAvailable = false;
    private volatile boolean ttsInitAttempted = false;

    private HourlyChimeManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = new ClockPrefs(appContext);
        initTtsIfNeeded();
    }

    public static HourlyChimeManager getInstance(Context context) {
        if (instance == null) {
            synchronized (HourlyChimeManager.class) {
                if (instance == null) {
                    instance = new HourlyChimeManager(context);
                }
            }
        }
        return instance;
    }

    public synchronized void initTtsIfNeeded() {
        if (ttsInitAttempted && tts != null) return;
        ttsInitAttempted = true;
        try {
            tts = new TextToSpeech(appContext, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    int langResult = tts.setLanguage(Locale.CHINA);
                    if (langResult == TextToSpeech.LANG_MISSING_DATA
                            || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        langResult = tts.setLanguage(Locale.CHINESE);
                    }
                    if (langResult == TextToSpeech.LANG_MISSING_DATA
                            || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        ttsChineseAvailable = false;
                        // Still try default locale just in case
                        int defRes = tts.setLanguage(Locale.getDefault());
                        ttsReady = (defRes != TextToSpeech.LANG_MISSING_DATA
                                && defRes != TextToSpeech.LANG_NOT_SUPPORTED);
                    } else {
                        ttsChineseAvailable = true;
                        ttsReady = true;
                    }
                    try {
                        tts.setSpeechRate(0.95f);
                        tts.setPitch(1.02f);
                    } catch (Exception ignored) {
                    }
                } else {
                    ttsReady = false;
                    ttsChineseAvailable = false;
                }
            });
        } catch (Exception e) {
            ttsReady = false;
            ttsChineseAvailable = false;
        }
    }

    public boolean isTtsReady() {
        return ttsReady;
    }

    public boolean isTtsChineseAvailable() {
        return ttsChineseAvailable;
    }

    /**
     * Checks current time and triggers hourly (or half-hour) chime once per slot.
     * Safe to call from both MainActivity and KeepAliveService.
     */
    public void checkAndChimeIfNeeded() {
        if (!prefs.isHourlyChimeEnabled()) return;

        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        int second = cal.get(Calendar.SECOND);

        boolean isFullHour = (minute == 0);
        boolean isHalfHour = (minute == 30 && prefs.isHalfHourChimeEnabled());
        if (!isFullHour && !isHalfHour) return;
        if (second > 20) return;

        if (isInQuietHours(hour)) return;

        String slotKey = String.format(Locale.US, "%04d%02d%02d_%02d%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                hour, minute);
        if (slotKey.equals(prefs.getLastChimedKey())) {
            return;
        }
        prefs.setLastChimedKey(slotKey);
        announceTime(hour, minute, false, null);
    }

    /**
     * Immediately tests the chime with the current time.
     */
    public void testChimeNow(final Context uiContext) {
        initTtsIfNeeded();
        Calendar cal = Calendar.getInstance();
        int hour = cal.get(Calendar.HOUR_OF_DAY);
        int minute = cal.get(Calendar.MINUTE);
        announceTime(hour, minute, true, uiContext);
    }

    private void announceTime(final int hour, final int minute, final boolean isTest, final Context uiContext) {
        final int mode = prefs.getChimeMode();
        final boolean isFullHour = (minute == 0);
        final String spokenText = buildAnnouncementText(hour, minute, isTest);

        if (mode == ClockPrefs.CHIME_MODE_BELL_ONLY) {
            playBuiltInBellAsync(isFullHour, null);
            showTestToast(uiContext, "正在播放内置清脆钟声");
            return;
        }

        boolean canUseTts = ttsReady && tts != null;
        if (mode == ClockPrefs.CHIME_MODE_TTS_WITH_BELL) {
            if (canUseTts) {
                playBuiltInBellAsync(isFullHour, () -> speakText(spokenText));
                showTestToast(uiContext, ttsChineseAvailable
                        ? ("内置钟声 + 系统TTS报时：" + spokenText)
                        : "内置钟声 + 系统TTS（未检测到中文语音包）");
            } else {
                // Automatic fallback to built-in bell when system has no TTS engine
                playBuiltInBellAsync(isFullHour, null);
                showTestToast(uiContext, "未检测到系统TTS语音引擎，已自动使用内置钟声报时");
            }
        } else {
            // CHIME_MODE_TTS_ONLY
            if (canUseTts) {
                speakText(spokenText);
                showTestToast(uiContext, "系统TTS报时：" + spokenText);
            } else {
                playBuiltInBellAsync(isFullHour, null);
                showTestToast(uiContext, "未检测到系统TTS语音引擎，已自动回退内置钟声");
            }
        }
    }

    @SuppressWarnings("deprecation")
    private void speakText(String text) {
        if (tts == null || !ttsReady) return;
        try {
            if (Build.VERSION.SDK_INT >= 21) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "xiao_clock_chime_" + System.currentTimeMillis());
            } else {
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
            }
        } catch (Exception ignored) {
        }
    }

    private String buildAnnouncementText(int hour24, int minute, boolean isTest) {
        String period;
        int displayHour;
        if (hour24 == 0) {
            period = "午夜";
            displayHour = 12;
        } else if (hour24 < 6) {
            period = "凌晨";
            displayHour = hour24;
        } else if (hour24 < 12) {
            period = "上午";
            displayHour = hour24;
        } else if (hour24 == 12) {
            period = "中午";
            displayHour = 12;
        } else if (hour24 < 18) {
            period = "下午";
            displayHour = hour24 - 12;
        } else {
            period = "晚上";
            displayHour = hour24 - 12;
        }

        if (minute == 0) {
            return "现在时间，" + period + displayHour + "点整";
        } else if (minute == 30) {
            return "现在时间，" + period + displayHour + "点半";
        } else if (isTest) {
            return "现在时间，" + period + displayHour + "点" + minute + "分";
        } else {
            return "现在时间，" + period + displayHour + "点" + minute + "分";
        }
    }

    private boolean isInQuietHours(int hour) {
        if (!prefs.isChimeQuietHoursEnabled()) return false;
        int start = prefs.getChimeQuietStartHour();
        int end = prefs.getChimeQuietEndHour();
        if (start == end) return false;
        if (start < end) {
            return hour >= start && hour < end;
        } else {
            // Spans midnight, e.g. 23:00 -> 07:00
            return hour >= start || hour < end;
        }
    }

    private void showTestToast(final Context uiContext, final String msg) {
        if (uiContext == null) return;
        mainHandler.post(() -> Toast.makeText(uiContext, msg, Toast.LENGTH_SHORT).show());
    }

    /**
     * Synthesizes a warm, pleasant harmonic bell chime completely in software using AudioTrack.
     * Requires zero external audio files and works natively on Android 4.0 (API 14) through Android 14.
     */
    private void playBuiltInBellAsync(final boolean isFullHour, final Runnable onFinished) {
        new Thread(() -> {
            AudioTrack track = null;
            try {
                final int sampleRate = 22050;
                // Pleasant pentatonic bell notes: Full hour = E5 -> G5 -> C6; Half hour = G5 -> E5
                final double[] freqs = isFullHour
                        ? new double[]{659.25, 783.99, 1046.50}
                        : new double[]{783.99, 659.25};
                final int noteSamples = (int) (sampleRate * 0.42);
                final int totalSamples = noteSamples * freqs.length;
                final short[] pcm = new short[totalSamples];

                for (int n = 0; n < freqs.length; n++) {
                    double f = freqs[n];
                    int offset = n * noteSamples;
                    for (int i = 0; i < noteSamples; i++) {
                        double t = i / (double) sampleRate;
                        // Fast soft attack + natural exponential bell decay
                        double attack = Math.min(1.0, t / 0.012);
                        double decay = Math.exp(-t * 6.5);
                        double env = attack * decay;
                        // Fundamental + soft 2nd & 3rd bell harmonics
                        double wave = 0.72 * Math.sin(2.0 * Math.PI * f * t)
                                + 0.20 * Math.sin(2.0 * Math.PI * (f * 2.0) * t)
                                + 0.08 * Math.sin(2.0 * Math.PI * (f * 3.0) * t);
                        int sample = (int) (wave * env * 22000.0);
                        if (sample > 32767) sample = 32767;
                        if (sample < -32768) sample = -32768;
                        pcm[offset + i] = (short) sample;
                    }
                }

                int bufBytes = pcm.length * 2;
                int minBuf = AudioTrack.getMinBufferSize(
                        sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                track = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        sampleRate,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(bufBytes, minBuf),
                        AudioTrack.MODE_STATIC
                );
                track.write(pcm, 0, pcm.length);
                track.play();
                Thread.sleep((long) (freqs.length * 420 + 80));
            } catch (Exception ignored) {
            } finally {
                if (track != null) {
                    try {
                        track.stop();
                        track.release();
                    } catch (Exception ignored) {
                    }
                }
                if (onFinished != null) {
                    mainHandler.post(onFinished);
                }
            }
        }).start();
    }
}
