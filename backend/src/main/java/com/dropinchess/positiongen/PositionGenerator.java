package com.dropinchess.positiongen;

import com.github.bhlangonijr.chesslib.game.Game;
import com.github.bhlangonijr.chesslib.pgn.PgnIterator;

public class PositionGenerator {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "Provide the PGN file path as the program argument."
            );
        }

        int gamesRead = 0;

        try (PgnIterator games = new PgnIterator(args[0])) {
            for (Game game : games) {
                System.out.printf(
                        "%s vs %s%n",
                        game.getWhitePlayer().getName(),
                        game.getBlackPlayer().getName()
                );

                gamesRead++;

                if (gamesRead >= 3) {
                    break;
                }
            }
        }

        System.out.println("Games read: " + gamesRead);
    }
}
