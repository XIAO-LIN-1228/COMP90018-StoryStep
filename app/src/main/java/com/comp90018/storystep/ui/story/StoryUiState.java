package com.comp90018.storystep.ui.story;

import com.comp90018.storystep.domain.model.AttractionProvenance;

/**
 * What the story screen renders.
 *
 * <p>Missing content is a state of its own rather than an empty string, because the screen must
 * say the story is unavailable instead of showing a blank page — and must never fill the gap with
 * text the app made up about a real place.
 */
public final class StoryUiState {

    private final boolean loading;
    private final String attractionId;
    private final String placeName;
    private final String story;
    private final AttractionProvenance provenance;
    private final boolean archived;
    public AttractionProvenance getProvenance() { return provenance; }
    public boolean isArchived() { return archived; }

    private StoryUiState(boolean loading, String attractionId, String placeName, String story) {
        this(loading, attractionId, placeName, story, null, false);
    }

    private StoryUiState(boolean loading, String attractionId, String placeName, String story,
            AttractionProvenance provenance, boolean archived) {
        this.provenance = provenance;
        this.archived = archived;
        this.loading = loading;
        this.attractionId = attractionId;
        this.placeName = placeName;
        this.story = story;
    }

    public static StoryUiState loading() {
        return new StoryUiState(true, null, null, null);
    }

    public static StoryUiState content(String attractionId, String placeName, String story) {
        return new StoryUiState(false, attractionId, placeName, story);
    }

    public static StoryUiState content(String id, String name, String story,
            AttractionProvenance provenance, boolean archived) {
        return new StoryUiState(false, id, name, story, provenance, archived);
    }

    /** Nothing could be loaded at all — not the place, not its text. */
    public static StoryUiState unavailable() {
        return new StoryUiState(false, null, null, null);
    }

    public boolean isLoading() {
        return loading;
    }

    /** Null when nothing could be loaded. */
    public String getAttractionId() {
        return attractionId;
    }

    public boolean hasPlaceName() {
        return placeName != null && !placeName.isEmpty();
    }

    public String getPlaceName() {
        return placeName;
    }

    /** True when there is real text to show. False means the screen says so, plainly. */
    public boolean hasStory() {
        return story != null && !story.trim().isEmpty();
    }

    public String getStory() {
        return story;
    }
}
