package com.dropinchess.controller;

import com.dropinchess.repository.PositionRepository;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class PositionController {
    private final PositionRepository positions;

    public PositionController(PositionRepository positions) {
        this.positions = positions;
    }

    @GetMapping("/startingFEN")
    public Map<String, String> startingFEN() {
        return Map.of("fen", positions.randomPosition().fen());
    }
}
