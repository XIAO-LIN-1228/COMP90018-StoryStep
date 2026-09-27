package com.comp90018.storystep.platform.speech;

/** Narrow platform boundary around speech synthesis. */
public interface SpeechEngine {

    interface Callback {
        void onReady();

        void onUnavailable();

        void onUtteranceDone(String utteranceId);

        void onUtteranceError(String utteranceId);
    }

    void initialize(Callback callback);

    boolean speak(String text, float rate, String utteranceId);

    void stop();

    void shutdown();
}
