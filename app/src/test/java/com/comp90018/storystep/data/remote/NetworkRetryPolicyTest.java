package com.comp90018.storystep.data.remote;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NetworkRetryPolicyTest {

    private final NetworkRetryPolicy policy = new NetworkRetryPolicy();

    @Test
    public void transientFailuresUseBoundedExponentialBackoff() {
        assertEquals(
                500L,
                policy.delayAfter(
                        1,
                        NetworkRetryPolicy.Failure.TIMEOUT,
                        -1,
                        -1L));

        assertEquals(
                1_000L,
                policy.delayAfter(
                        2,
                        NetworkRetryPolicy.Failure.TIMEOUT,
                        -1,
                        -1L));

        assertEquals(
                NetworkRetryPolicy.DO_NOT_RETRY,
                policy.delayAfter(
                        3,
                        NetworkRetryPolicy.Failure.TIMEOUT,
                        -1,
                        -1L));
    }

    @Test
    public void onlyServerHttpErrorsAreRetried() {
        assertEquals(
                NetworkRetryPolicy.DO_NOT_RETRY,
                policy.delayAfter(
                        1,
                        NetworkRetryPolicy.Failure.HTTP_ERROR,
                        401,
                        -1L));

        assertEquals(
                NetworkRetryPolicy.DO_NOT_RETRY,
                policy.delayAfter(
                        1,
                        NetworkRetryPolicy.Failure.HTTP_ERROR,
                        403,
                        -1L));

        assertEquals(
                500L,
                policy.delayAfter(
                        1,
                        NetworkRetryPolicy.Failure.HTTP_ERROR,
                        500,
                        -1L));
    }

    @Test
    public void rateLimitHonorsClampedRetryAfterOnce() {
        assertEquals(
                5_000L,
                policy.delayAfter(
                        1,
                        NetworkRetryPolicy.Failure.RATE_LIMITED,
                        429,
                        60_000L));

        assertEquals(
                NetworkRetryPolicy.DO_NOT_RETRY,
                policy.delayAfter(
                        2,
                        NetworkRetryPolicy.Failure.RATE_LIMITED,
                        429,
                        1_000L));
    }

    @Test
    public void malformedPayloadIsNeverRetried() {
        assertEquals(
                NetworkRetryPolicy.DO_NOT_RETRY,
                policy.delayAfter(
                        1,
                        NetworkRetryPolicy.Failure.MALFORMED,
                        200,
                        -1L));
    }
}