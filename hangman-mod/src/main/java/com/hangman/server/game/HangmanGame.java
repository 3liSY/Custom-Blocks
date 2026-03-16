package com.hangman.server.game;

import com.hangman.common.config.HangmanConfig;

import java.util.*;

/**
 * Full server-side state for one Hangman game between two players.
 */
public class HangmanGame {

    public enum Phase { WAITING_FOR_WORD, IN_PROGRESS, FINISHED }

    public enum GuessResult { ALREADY_GUESSED, CORRECT, WRONG, WIN, LOSE }

    // ── players ───────────────────────────────────────────────────────────────
    private final UUID hangedId;
    private final UUID hangerId;

    // ── game state ────────────────────────────────────────────────────────────
    private Phase phase = Phase.WAITING_FOR_WORD;
    private String secretWord = "";
    private String category   = "";

    private final LinkedHashSet<Character> guessedLetters = new LinkedHashSet<>();
    private final List<Character> wrongLetters = new ArrayList<>();
    private int wrongGuesses = 0;

    /** Limb names removed so far (in order). */
    private final List<String> removedLimbs = new ArrayList<>();
    private int nextLimbIndex = 0;

    // ── hints ─────────────────────────────────────────────────────────────────
    private int hintsUsed = 0;

    // ── positions saved for post-game teleport ────────────────────────────────
    /** Stored as [x, y, z, yaw, pitch] per player UUID */
    private final Map<UUID, double[]> savedPositions = new HashMap<>();

    // ── game-specific settings snapshot ──────────────────────────────────────
    private final HangmanConfig configSnapshot;

    // ── rounds ────────────────────────────────────────────────────────────────
    private int currentRound = 1;
    private int hangedWins  = 0;
    private int hangerWins  = 0;

    // ── outcome ───────────────────────────────────────────────────────────────
    private boolean hangerWonLastRound = false;

    public HangmanGame(UUID hangedId, UUID hangerId, HangmanConfig cfg) {
        this.hangedId = hangedId;
        this.hangerId = hangerId;
        this.configSnapshot = cfg;
    }

    // ── word submission ───────────────────────────────────────────────────────

    public void setWord(String word, String category) {
        this.secretWord = word.toLowerCase(Locale.ROOT).trim();
        this.category   = category == null ? "" : category;
        this.phase      = Phase.IN_PROGRESS;
        // Reset per-round state
        guessedLetters.clear();
        wrongLetters.clear();
        wrongGuesses  = 0;
        removedLimbs.clear();
        nextLimbIndex = 0;
        hintsUsed     = 0;
    }

    // ── guessing ──────────────────────────────────────────────────────────────

    public GuessResult guess(char letter) {
        letter = Character.toLowerCase(letter);
        if (guessedLetters.contains(letter)) return GuessResult.ALREADY_GUESSED;
        guessedLetters.add(letter);

        // Space key: reveals word boundaries (blanks between words) - always "correct", never wrong
        if (letter == ' ') {
            if (isWordComplete()) {
                hangerWonLastRound = true;
                hangerWins++;
                phase = Phase.FINISHED;
                return GuessResult.WIN;
            }
            return GuessResult.CORRECT;
        }

        if (secretWord.indexOf(letter) >= 0) {
            if (isWordComplete()) {
                hangerWonLastRound = true;
                hangerWins++;
                phase = Phase.FINISHED;
                return GuessResult.WIN;
            }
            return GuessResult.CORRECT;
        } else {
            wrongLetters.add(letter);
            wrongGuesses++;
            advanceLimb();
            if (wrongGuesses >= configSnapshot.maxWrongGuesses) {
                hangerWonLastRound = false;
                hangedWins++;
                phase = Phase.FINISHED;
                return GuessResult.LOSE;
            }
            return GuessResult.WRONG;
        }
    }

    private void advanceLimb() {
        List<String> seq = configSnapshot.limbOrder;
        if (nextLimbIndex < seq.size()) {
            removedLimbs.add(seq.get(nextLimbIndex));
            nextLimbIndex++;
        }
    }

    // ── hints ─────────────────────────────────────────────────────────────────

    /**
     * Attempts to use a hint. Returns the revealed character, or '\0' if unavailable.
     */
    public char useHint() {
        if (!configSnapshot.hintsEnabled) return '\0';
        if (hintsUsed >= configSnapshot.maxHints) return '\0';

        List<Character> unrevealed = new ArrayList<>();
        for (char c : secretWord.toCharArray()) {
            if (c != ' ' && !guessedLetters.contains(c) && !unrevealed.contains(c)) {
                unrevealed.add(c);
            }
        }
        if (unrevealed.isEmpty()) return '\0';

        hintsUsed++;
        char chosen = unrevealed.get(new Random().nextInt(unrevealed.size()));
        guessedLetters.add(chosen);
        return chosen;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    public boolean isWordComplete() {
        for (char c : secretWord.toCharArray()) {
            if (c != ' ' && !guessedLetters.contains(c)) return false;
        }
        return true;
    }

    public String getMaskedWord() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < secretWord.length(); i++) {
            if (i > 0) sb.append(' ');
            char c = secretWord.charAt(i);
            if (c == ' ') sb.append(' ');
            else if (guessedLetters.contains(c)) sb.append(c);
            else sb.append('_');
        }
        return sb.toString();
    }

    public boolean isPlayerInGame(UUID id) {
        return hangedId.equals(id) || hangerId.equals(id);
    }

    // ── saved positions ───────────────────────────────────────────────────────

    public void savePosition(UUID playerId, double x, double y, double z, float yaw, float pitch) {
        savedPositions.put(playerId, new double[]{x, y, z, yaw, pitch});
    }

    public double[] getSavedPosition(UUID playerId) {
        return savedPositions.get(playerId);
    }

    // ── getters ───────────────────────────────────────────────────────────────

    public UUID getHangedId()                   { return hangedId; }
    public UUID getHangerId()                   { return hangerId; }
    public Phase getPhase()                     { return phase; }
    public String getSecretWord()               { return secretWord; }
    public String getCategory()                 { return category; }
    public Set<Character> getGuessedLetters()   { return Collections.unmodifiableSet(guessedLetters); }
    public List<Character> getWrongLetters()    { return Collections.unmodifiableList(wrongLetters); }
    public int getWrongGuesses()                { return wrongGuesses; }
    public int getMaxWrongGuesses()             { return configSnapshot.maxWrongGuesses; }
    public List<String> getRemovedLimbs()       { return Collections.unmodifiableList(removedLimbs); }
    public boolean isHangerWonLastRound()       { return hangerWonLastRound; }
    public int getHintsUsed()                   { return hintsUsed; }
    public int getCurrentRound()               { return currentRound; }
    public int getHangedWins()                  { return hangedWins; }
    public int getHangerWins()                  { return hangerWins; }
    public int getTotalRounds()                 { return configSnapshot.rounds; }
    public HangmanConfig getConfig()            { return configSnapshot; }

    public void nextRound() {
        currentRound++;
        phase = Phase.WAITING_FOR_WORD;
        secretWord = "";
    }
}
