package com.comp90018.storystep.ui.exploration;

import java.util.Objects;

/** Allows one transient cue for each persisted visit identity. */
final class ArrivalFeedbackGate {

    private String deliveredKey;

    boolean claim(String sessionId, String attractionId) {
        if (sessionId == null || sessionId.isEmpty()
                || attractionId == null || attractionId.isEmpty()) {
            return false;
        }
        String key = sessionId + '\u0000' + attractionId;
        if (Objects.equals(deliveredKey, key)) {
            return false;
        }
        deliveredKey = key;
        return true;
    }
}
