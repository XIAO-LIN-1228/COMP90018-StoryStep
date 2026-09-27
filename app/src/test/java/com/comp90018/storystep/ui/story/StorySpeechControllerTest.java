package com.comp90018.storystep.ui.story;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.comp90018.storystep.platform.speech.SpeechEngine;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class StorySpeechControllerTest {

    @Test
    public void speaksOneSentenceAtATimeAndReturnsToReady() {
        FakeEngine engine = new FakeEngine();
        RecordingListener listener = new RecordingListener();
        StorySpeechController controller = new StorySpeechController(engine, listener);
        controller.setStory("First sentence. Second sentence!");
        engine.ready();

        controller.togglePlayback();
        assertEquals("First sentence.", engine.lastText);
        assertEquals(StorySpeechController.State.PLAYING, controller.getState());

        engine.finish();
        assertEquals("Second sentence!", engine.lastText);
        engine.finish();
        assertEquals(StorySpeechController.State.READY, controller.getState());

        controller.togglePlayback();
        assertEquals("First sentence.", engine.lastText);
    }

    @Test
    public void pauseAndContinueRepeatTheCurrentSentenceWithoutSkippingWords() {
        FakeEngine engine = new FakeEngine();
        StorySpeechController controller = new StorySpeechController(engine, new RecordingListener());
        controller.setStory("One. Two.");
        engine.ready();
        controller.togglePlayback();
        String firstId = engine.lastId;
        int stopsBeforePause = engine.stopCount;

        controller.pause();
        assertEquals(StorySpeechController.State.PAUSED, controller.getState());
        assertEquals(stopsBeforePause + 1, engine.stopCount);
        engine.callback.onUtteranceDone(firstId);
        assertEquals("One.", engine.lastText);

        controller.togglePlayback();
        assertEquals("One.", engine.lastText);
        assertFalse(firstId.equals(engine.lastId));
    }

    @Test
    public void changingRateWhilePlayingRestartsOnlyTheCurrentSentence() {
        FakeEngine engine = new FakeEngine();
        StorySpeechController controller = new StorySpeechController(engine, new RecordingListener());
        controller.setStory("One. Two.");
        engine.ready();
        controller.togglePlayback();
        int stopsBeforeRateChange = engine.stopCount;

        controller.setRate(1.25f);

        assertEquals(1.25f, engine.lastRate, 0.0f);
        assertEquals("One.", engine.lastText);
        assertEquals(stopsBeforeRateChange + 1, engine.stopCount);
        assertEquals(StorySpeechController.State.PLAYING, controller.getState());
    }

    @Test
    public void unavailableEngineLeavesTextReadingAsTheFallback() {
        FakeEngine engine = new FakeEngine();
        StorySpeechController controller = new StorySpeechController(engine, new RecordingListener());
        controller.setStory("Readable text remains on screen.");

        engine.callback.onUnavailable();
        controller.togglePlayback();

        assertEquals(StorySpeechController.State.UNAVAILABLE, controller.getState());
        assertEquals(0, engine.speakCount);
    }

    @Test
    public void replacingStoryStopsOldCallbacksFromAdvancingNewStory() {
        FakeEngine engine = new FakeEngine();
        StorySpeechController controller = new StorySpeechController(engine, new RecordingListener());
        controller.setStory("Old first. Old second.");
        engine.ready();
        controller.togglePlayback();
        String oldId = engine.lastId;

        controller.setStory("New story.");
        engine.callback.onUtteranceDone(oldId);
        controller.togglePlayback();

        assertEquals("New story.", engine.lastText);
    }

    @Test
    public void rejectedSpeechReportsErrorAndCanBeRetried() {
        FakeEngine engine = new FakeEngine();
        RecordingListener listener = new RecordingListener();
        StorySpeechController controller = new StorySpeechController(engine, listener);
        controller.setStory("Story.");
        engine.ready();
        engine.acceptSpeech = false;

        controller.togglePlayback();

        assertEquals(StorySpeechController.State.READY, controller.getState());
        assertEquals(1, listener.errors);
    }

    @Test
    public void shutdownReleasesTheEngineOnce() {
        FakeEngine engine = new FakeEngine();
        StorySpeechController controller = new StorySpeechController(engine, new RecordingListener());

        controller.shutdown();
        controller.shutdown();

        assertEquals(1, engine.shutdownCount);
    }

    private static final class RecordingListener implements StorySpeechController.Listener {
        final List<StorySpeechController.State> states = new ArrayList<>();
        int errors;

        @Override
        public void onStateChanged(StorySpeechController.State state, float rate) {
            states.add(state);
        }

        @Override
        public void onSpeechError() {
            errors++;
        }
    }

    private static final class FakeEngine implements SpeechEngine {
        Callback callback;
        String lastText;
        String lastId;
        float lastRate;
        int speakCount;
        int stopCount;
        int shutdownCount;
        boolean acceptSpeech = true;

        @Override
        public void initialize(Callback callback) {
            this.callback = callback;
        }

        void ready() {
            callback.onReady();
        }

        void finish() {
            callback.onUtteranceDone(lastId);
        }

        @Override
        public boolean speak(String text, float rate, String utteranceId) {
            speakCount++;
            lastText = text;
            lastRate = rate;
            lastId = utteranceId;
            return acceptSpeech;
        }

        @Override
        public void stop() {
            stopCount++;
        }

        @Override
        public void shutdown() {
            shutdownCount++;
        }
    }
}
