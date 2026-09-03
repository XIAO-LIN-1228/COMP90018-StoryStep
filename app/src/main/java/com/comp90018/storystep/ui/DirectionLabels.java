package com.comp90018.storystep.ui;

import androidx.annotation.StringRes;

import com.comp90018.storystep.R;
import com.comp90018.storystep.domain.guidance.CardinalDirection;
import com.comp90018.storystep.domain.model.MovementState;

/**
 * Maps sensing results to localized labels, for the same reason as {@link CategoryLabels}: the
 * domain layer decides what is true, and the UI layer decides how to say it.
 */
public final class DirectionLabels {

    private DirectionLabels() {
        // Utility class.
    }

    @StringRes
    public static int cardinalOf(CardinalDirection direction) {
        switch (direction) {
            case NORTH_EAST:
                return R.string.direction_north_east;
            case EAST:
                return R.string.direction_east;
            case SOUTH_EAST:
                return R.string.direction_south_east;
            case SOUTH:
                return R.string.direction_south;
            case SOUTH_WEST:
                return R.string.direction_south_west;
            case WEST:
                return R.string.direction_west;
            case NORTH_WEST:
                return R.string.direction_north_west;
            case NORTH:
            default:
                return R.string.direction_north;
        }
    }

    /**
     * A label for the movement estimate.
     *
     * <p>The three non-moving cases stay distinct in the wording as well as in the model: "standing
     * still" is a statement about the user, while "sensing" and "no motion sensor" are statements
     * about the app. Showing the first when one of the others is true would be a small lie the user
     * has no way to catch.
     */
    @StringRes
    public static int movementOf(MovementState state) {
        switch (state) {
            case MOVING:
                return R.string.movement_moving;
            case STATIONARY:
                return R.string.movement_stationary;
            case UNAVAILABLE:
                return R.string.movement_unavailable;
            case UNKNOWN:
            default:
                return R.string.movement_unknown;
        }
    }
}
