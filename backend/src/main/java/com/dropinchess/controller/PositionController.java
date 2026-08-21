package com.dropinchess.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class PositionController {


    private static final List<String> SAMPLE_FENS = List.of(
            "r1b1k2r/pp1n1ppp/4p3/3p4/1b1NnP2/2N1B3/PqP1K1PP/R2Q1B1R b kq - 1 11",
            "r4rk1/1pp2ppp/2np4/p7/2B1P1b1/1P6/PBPq1PPP/R3R1K1 w - - 0 14",
            "r4rk1/pp3ppp/2n1b3/1R6/4q3/2P2N2/P1P1BPPP/3QK2R w K - 0 15",
            "r4rk1/pb2bppp/1pn1pn2/2pp4/3P4/2PBPN2/PP1N1PPP/R1BQR1K1 w - - 4 12"
    );

    @GetMapping("/startingFEN")
    public Map<String, String> startingFEN() {

        int randomIndex = ThreadLocalRandom.current()
                .nextInt(SAMPLE_FENS.size());

        return Map.of("fen", SAMPLE_FENS.get(randomIndex));
    }
}
