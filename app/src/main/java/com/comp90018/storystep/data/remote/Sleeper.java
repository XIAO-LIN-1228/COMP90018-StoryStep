package com.comp90018.storystep.data.remote;

/**
 * Waiting boundary used by retry logic.
 *
 * <p>Production code uses Thread.sleep, while tests can supply an implementation
 * that records the requested delay without actually waiting.
 */
public interface Sleeper {

    Sleeper THREAD = Thread::sleep;

    void sleep(long millis) throws InterruptedException;
}