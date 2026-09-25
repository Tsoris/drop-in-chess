package com.dropinchess.positiongen;

import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.regex.*;

/** One reusable UCI process; all calls are sequential and have a deadline. */
public final class StockfishClient implements AutoCloseable {
    public record Evaluation(Integer whiteCp, boolean mate, int depth) {
        public boolean balanced(int limit) {
            return !mate && whiteCp != null && Math.abs((long) whiteCp) <= limit;
        }
    }
    private static final Pattern SCORE = Pattern.compile("\\bscore (cp|mate) (-?\\d+)");
    private static final Pattern DEPTH = Pattern.compile("\\bdepth (\\d+)");
    private static final String EOF = "__ENGINE_EOF__";
    private final Process process;
    private final BufferedWriter input;
    private final BlockingQueue<String> output = new LinkedBlockingQueue<>();
    private final Duration timeout;
    private String version = "unknown";

    public StockfishClient(Path executable, Duration timeout) throws IOException {
        this.timeout = timeout;
        process = new ProcessBuilder(executable.toAbsolutePath().toString()).redirectErrorStream(true).start();
        input = process.outputWriter();
        Thread reader = new Thread(() -> {
            try (BufferedReader stream = process.inputReader()) {
                String line;
                while ((line = stream.readLine()) != null) output.add(line);
            } catch (IOException ignored) {
                // EOF marker communicates reader/process failure to the waiting caller.
            } finally { output.add(EOF); }
        }, "stockfish-output");
        reader.setDaemon(true);
        reader.start();
        try {
            send("uci");
            long deadline = deadline();
            String line;
            do {
                line = read(deadline);
                if (line.startsWith("id name ")) version = line.substring(8);
            } while (!line.equals("uciok"));
            send("setoption name Threads value 1");
            send("setoption name Hash value 64");
            send("setoption name MultiPV value 1");
            ready();
        } catch (IOException failure) { close(); throw failure; }
    }

    public String version() { return version; }

    public Evaluation evaluate(String fen, int depth) throws IOException {
        if (depth < 1 || depth > 128) throw new IllegalArgumentException("Depth must be 1..128");
        if (fen.contains("\n") || fen.contains("\r")) throw new IllegalArgumentException("Invalid FEN line");
        String[] fields = fen.trim().split("\\s+");
        if (fields.length != 6 || !(fields[1].equals("w") || fields[1].equals("b"))) throw new IllegalArgumentException("Invalid FEN");
        try {
            send("ucinewgame"); // Clear cross-position search history for comparable independent evaluations.
            ready();
            send("position fen " + fen);
            send("go depth " + depth);
            long deadline = deadline();
            Evaluation latest = null;
            while (true) {
                String line = read(deadline);
                if (line.startsWith("bestmove ")) {
                    if (latest == null || latest.depth() < depth) throw new IOException("Engine returned no completed score at requested depth");
                    return latest;
                }
                Evaluation parsed = parseScore(line, fields[1].equals("b"));
                if (parsed != null) latest = parsed;
            }
        } catch (IOException failure) { close(); throw failure; }
    }

    static Evaluation parseScore(String line, boolean blackToMove) {
        if (!line.startsWith("info ") || line.contains("lowerbound") || line.contains("upperbound")) return null;
        Matcher score = SCORE.matcher(line), depth = DEPTH.matcher(line);
        if (!score.find() || !depth.find()) return null;
        boolean mate = score.group(1).equals("mate");
        int value = Integer.parseInt(score.group(2));
        return new Evaluation(mate ? null : (blackToMove ? -value : value), mate, Integer.parseInt(depth.group(1)));
    }

    private void ready() throws IOException {
        send("isready");
        long deadline = deadline();
        while (!read(deadline).equals("readyok")) { }
    }
    private long deadline() { return System.nanoTime() + timeout.toNanos(); }
    private String read(long deadline) throws IOException {
        try {
            String line = output.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            if (line == null) throw new IOException("Stockfish timed out after " + timeout.toSeconds() + " seconds");
            if (line.equals(EOF)) throw new IOException("Stockfish closed its output unexpectedly");
            return line;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Stockfish interrupted", interrupted);
        }
    }
    private void send(String command) throws IOException { input.write(command); input.newLine(); input.flush(); }
    @Override public void close() {
        try { if (process.isAlive()) send("quit"); } catch (IOException ignored) { }
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly();
        } catch (InterruptedException interrupted) {
            process.destroyForcibly(); Thread.currentThread().interrupt();
        }
        try { input.close(); } catch (IOException ignored) { }
    }
}
