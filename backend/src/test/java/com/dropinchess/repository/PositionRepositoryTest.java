package com.dropinchess.repository;

import com.dropinchess.controller.GameController;
import com.dropinchess.controller.PositionController;
import com.dropinchess.positiongen.PositionWriter;
import com.dropinchess.service.GameService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PositionRepositoryTest {
    private final ClassPathResource resource = new ClassPathResource("positions/positions.json");

    @Test void publishedCollectionHasBothQuotasAndValidHistories() throws Exception {
        var repository = new PositionRepository(resource);
        assertEquals(1000, repository.allPositions().size());
        assertEquals(500, repository.allPositions().stream().filter(p -> p.phase().equals("MIDDLEGAME")).count());
        assertEquals(500, repository.allPositions().stream().filter(p -> p.phase().equals("ENDGAME")).count());
        try (var input = resource.getInputStream()) {
            var collection = JsonMapper.builder().build().readValue(input, PositionWriter.Collection.class);
            for (var position : collection.positions()) PositionWriter.validate(position, collection.generation());
            assertEquals(1000, collection.positions().stream().map(p -> PositionWriter.identity(p.fen())).distinct().count());
        }
    }

    @Test void bothHttpEndpointsUseTheCollectionAndNewGamesCanMove() throws Exception {
        var repository = new PositionRepository(resource);
        var service = new GameService();
        var mvc = MockMvcBuilders.standaloneSetup(new GameController(service, repository), new PositionController(repository)).build();
        var mapper = JsonMapper.builder().build();
        Set<String> fens = repository.allPositions().stream().map(PositionRepository.Position::fen).collect(Collectors.toSet());
        String starting = mvc.perform(get("/startingFEN")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(fens.contains(mapper.readTree(starting).get("fen").asText()));
        String created = mvc.perform(post("/games")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        var response = mapper.readTree(created);
        assertTrue(fens.contains(response.get("fen").asText()));
        assertEquals("IN_PROGRESS", response.get("status").asText());
        var game = service.getAllGames().iterator().next();
        var move = game.getBoard().legalMoves().getFirst();
        var expected = new com.github.bhlangonijr.chesslib.Board(); expected.loadFromFen(game.getStartingFen()); expected.doMove(move, true);
        String promotion = move.getPromotion() == com.github.bhlangonijr.chesslib.Piece.NONE ? null : move.getPromotion().getFenSymbol().toUpperCase();
        service.makeMove(game.getId(), move.getFrom(), move.getTo(), promotion, expected.getFen());
        assertEquals(expected.getFen(), game.getBoard().getFen());
    }

    @Test void invalidCollectionsFailInsteadOfFallingBackToSamples() {
        for (String json : new String[] {
                "{\"schemaVersion\":1,\"complete\":false,\"positions\":[]}",
                "{\"schemaVersion\":1,\"complete\":true,\"positions\":[]}",
                "{\"schemaVersion\":99,\"complete\":true,\"positions\":[]}",
                "{\"schemaVersion\":1,\"complete\":true,\"positions\":[{\"id\":\"bad\",\"phase\":\"ENDGAME\",\"fen\":\"invalid\"}]}"
        }) {
            assertThrows(java.io.IOException.class, () -> new PositionRepository(new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8))));
        }
        assertThrows(java.io.IOException.class, () -> new PositionRepository(new ClassPathResource("missing-positions.json")));
    }
}
