package com.dropinchess.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** Player-facing guidance attached to a published starting position. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PositionContext(
        String availability,
        String quality,
        Summary openingContext,
        PositionGuide positionGuide,
        PossiblePlans possiblePlans,
        String message
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Summary(String summary) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PositionGuide(String summary, List<String> themes) {
        public PositionGuide {
            themes = themes == null ? null : List.copyOf(themes);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PossiblePlans(Plan white, Plan black) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Plan(String summary) {}
}
