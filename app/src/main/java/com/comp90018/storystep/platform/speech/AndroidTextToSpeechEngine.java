package com.comp90018.storystep.platform.speech;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.Locale;
import java.util.Objects;

/** Android {@link TextToSpeech} adapter with all callbacks returned on the main thread. */
public final class AndroidTextToSpeechEngine implements SpeechEngine,
        TextToSpeech.OnInitListener {

    private final Context applicationContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Callback callback;
    private TextToSpeech textToSpeech;
    private boolean ready;
    private boolean released;

    public AndroidTextToSpeechEngine(Context context) {
        applicationContext = Objects.requireNonNull(context, "context").getApplicationContext();
    }

    @Override
    public void initialize(Callback callback) {
        if (released) {
            callback.onUnavailable();
            return;
        }
        this.callback = Objects.requireNonNull(callback, "callback");
        if (textToSpeech == null) {
            textToSpeech = new TextToSpeech(applicationContext, this);
        } else if (ready) {
            post(callback::onReady);
        }
    }

    @Override
    public void onInit(int status) {
        if (released || textToSpeech == null) {
            return;
        }
        if (status != TextToSpeech.SUCCESS
                || textToSpeech.setLanguage(Locale.ENGLISH) < TextToSpeech.LANG_AVAILABLE) {
            ready = false;
            notifyUnavailable();
            return;
        }
        textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // The controller already enters PLAYING when speak() is accepted.
            }

            @Override
            public void onDone(String utteranceId) {
                Callback current = callback;
                if (current != null && !released) {
                    post(() -> current.onUtteranceDone(utteranceId));
                }
            }

            @Override
            public void onError(String utteranceId) {
                notifyError(utteranceId);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                notifyError(utteranceId);
            }
        });
        ready = true;
        Callback current = callback;
        if (current != null) {
            post(current::onReady);
        }
    }

    @Override
    public boolean speak(String text, float rate, String utteranceId) {
        if (!ready || released || textToSpeech == null) {
            return false;
        }
        if (textToSpeech.setSpeechRate(rate) == TextToSpeech.ERROR) {
            return false;
        }
        return textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                == TextToSpeech.SUCCESS;
    }

    @Override
    public void stop() {
        if (textToSpeech != null && !released) {
            textToSpeech.stop();
        }
    }

    @Override
    public void shutdown() {
        released = true;
        ready = false;
        callback = null;
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeech = null;
        }
    }

    private void notifyUnavailable() {
        Callback current = callback;
        if (current != null && !released) {
            post(current::onUnavailable);
        }
    }

    private void notifyError(String utteranceId) {
        Callback current = callback;
        if (current != null && !released) {
            post(() -> current.onUtteranceError(utteranceId));
        }
    }

    private void post(Runnable action) {
        mainHandler.post(action);
    }
}
