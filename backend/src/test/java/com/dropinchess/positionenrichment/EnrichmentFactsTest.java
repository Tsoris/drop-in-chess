package com.dropinchess.positionenrichment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnrichmentFactsTest {
    @Test void calculatesCheckStatusAndBishopColors() {
        var checked = EnrichmentFacts.fromFen("6k1/7P/8/8/5K2/8/8/8 b - - 0 77");
        assertEquals(true, checked.storedFacts().get("sideToMoveInCheck"));
        assertTrue(checked.evidence().get("CHECK_STATUS").contains("Black is currently in check"));

        var bishops = EnrichmentFacts.fromFen("8/1b6/2k5/1p2K3/p5p1/P1B3P1/1P4P1/8 w - - 10 50");
        assertEquals("White bishops: c3 (dark); Black bishops: b7 (light)",
                bishops.storedFacts().get("bishopColors"));
    }
}
