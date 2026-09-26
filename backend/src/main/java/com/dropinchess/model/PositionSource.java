package com.dropinchess.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Player-facing provenance retained from the source game. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PositionSource(String gameUrl, String eco, String opening, String variation) {}
