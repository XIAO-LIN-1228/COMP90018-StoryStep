package com.comp90018.storystep.ui.story;

import com.comp90018.storystep.platform.speech.SpeechEngine;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Lifecycle-neutral state machine for story narration.
 *
 * <p>Android TTS cannot report a reliable character position. The story is therefore spoken one
 * sentence at a time. Pausing stops the current utterance; continuing repeats that sentence and
 * then advances, so words are never silently skipped.
 */
public final class StorySpeechController implements SpeechEngine.Callback {

    public enum State {
        INITIALIZING,
        READY,
        PLAYING,
        PAUSED,
        UNAVAILABLE
    }

    public interface Listener {
        void onStateChanged(State state, float rate);

        void onSpeechError();
    }

    private final SpeechEngine engine;
    private final Listener listener;
    private List<String> sentences = Collections.emptyList();
    private String story = "";
    private State state = State.INITIALIZING;
    private int sentenceIndex;
    private int generation;
    private float rate = 1.0f;
    private String activeUtteranceId;
    private boolean released;

    public StorySpeechController(SpeechEngine engine, Listener listener) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.listener = Objects.requireNonNull(listener, "listener");
        engine.initialize(this);
    }

    public State getState() {
        return state;
    }

    public float getRate() {
        return rate;
    }

    /** Replaces the narration without disturbing playback when the same story is rendered again. */
    public void setStory(String text) {
        String normalized = text == null ? "" : text.trim();
        if (story.equals(normalized)) {
            return;
        }
        engine.stop();
        generation++;
        activeUtteranceId = null;
        sentenceIndex = 0;
        story = normalized;
        sentences = splitSentences(normalized);
        if (state != State.INITIALIZING && state != State.UNAVAILABLE) {
            setState(State.READY);
        }
    }

    public void togglePlayback() {
        if (released || sentences.isEmpty()) {
            return;
        }
        if (state == State.PLAYING) {
            pause();
        } else if (state == State.READY || state == State.PAUSED) {
            speakCurrentSentence();
        }
    }

    /** Stops speech but keeps the current sentence ready to repeat on continue. */
    public void pause() {
        if (state != State.PLAYING) {
            return;
        }
        engine.stop();
        generation++;
        activeUtteranceId = null;
        setState(State.PAUSED);
    }

    public void setRate(float rate) {
        if (rate < 0.5f || rate > 2.0f || Float.isNaN(rate)) {
            throw new IllegalArgumentException("rate must be between 0.5 and 2.0");
        }
        if (Float.compare(this.rate, rate) == 0) {
            return;
        }
        this.rate = rate;
        if (state == State.PLAYING) {
            engine.stop();
            generation++;
            activeUtteranceId = null;
            speakCurrentSentence();
        } else {
            notifyState();
        }
    }

    public void shutdown() {
        if (released) {
            return;
        }
        released = true;
        generation++;
        activeUtteranceId = null;
        engine.stop();
        engine.shutdown();
    }

    @Override
    public void onReady() {
        if (!released) {
            setState(State.READY);
        }
    }

    @Override
    public void onUnavailable() {
        if (!released) {
            activeUtteranceId = null;
            setState(State.UNAVAILABLE);
        }
    }

    @Override
    public void onUtteranceDone(String utteranceId) {
        if (released || state != State.PLAYING || !Objects.equals(activeUtteranceId, utteranceId)) {
            return;
        }
        activeUtteranceId = null;
        sentenceIndex++;
        if (sentenceIndex >= sentences.size()) {
            sentenceIndex = 0;
            setState(State.READY);
        } else {
            speakCurrentSentence();
        }
    }

    @Override
    public void onUtteranceError(String utteranceId) {
        if (released || !Objects.equals(activeUtteranceId, utteranceId)) {
            return;
        }
        activeUtteranceId = null;
        sentenceIndex = 0;
        setState(State.READY);
        listener.onSpeechError();
    }

    private void speakCurrentSentence() {
        if (sentences.isEmpty() || released) {
            return;
        }
        String utteranceId = "story-" + generation + '-' + sentenceIndex;
        activeUtteranceId = utteranceId;
        if (engine.speak(sentences.get(sentenceIndex), rate, utteranceId)) {
            setState(State.PLAYING);
        } else {
            activeUtteranceId = null;
            sentenceIndex = 0;
            setState(State.READY);
            listener.onSpeechError();
        }
    }

    private void setState(State state) {
        this.state = state;
        notifyState();
    }

    private void notifyState() {
        listener.onStateChanged(state, rate);
    }

    static List<String> splitSentences(String text) {
        if (text == null || text.trim().isEmpty()) {
            return Collections.emptyList();
        }
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.ENGLISH);
        iterator.setText(text);
        List<String> result = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).trim();
            if (!sentence.isEmpty()) {
                result.add(sentence);
            }
        }
        if (result.isEmpty()) {
            result.add(text.trim());
        }
        return Collections.unmodifiableList(result);
    }
}
