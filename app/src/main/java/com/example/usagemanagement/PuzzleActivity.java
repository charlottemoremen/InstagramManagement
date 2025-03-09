package com.example.usagemanagement;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PuzzleActivity extends AppCompatActivity {

    private GridLayout puzzleGrid;
    private Button backButton;
    private TextView instructionsText;

    private final int GRID_SIZE = 5; // 5x5 grid
    private final int TILE_COUNT = GRID_SIZE * GRID_SIZE;
    private final int MIN_SHOW_TIME_MS = 1000; // Minimum show time for correct tiles
    private final int TOTAL_TILES_TO_SELECT = 7; // Number of correct tiles
    private float showTimeMs = 3000; // Initial show time for correct pattern

    private List<Button> gridTiles = new ArrayList<>();
    private List<Integer> correctTiles = new ArrayList<>();
    private List<Integer> remainingTilesToSelect = new ArrayList<>(); // Track tiles left to select
    private boolean gameInProgress = false;
    private boolean showingPattern = false;
    private boolean localPuzzleSolved = false;
    private float reentryShowTimeMs = 1500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_puzzle);

        puzzleGrid = findViewById(R.id.puzzleGrid);
        backButton = findViewById(R.id.backButton);
        instructionsText = findViewById(R.id.instructionsText);

        setupPuzzleGrid();
        startPuzzleGame();

        // Back to MainActivity
        backButton.setOnClickListener(v -> finish());
    }

    private void setupPuzzleGrid() {
        puzzleGrid.removeAllViews();
        gridTiles.clear();

        // Screen dimensions
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int screenWidth = displayMetrics.widthPixels;

        // Tile size calculation
        int tileSize = (screenWidth / GRID_SIZE) - 20; // Subtract padding

        // Set GridLayout parameters
        puzzleGrid.setColumnCount(GRID_SIZE);
        puzzleGrid.setRowCount(GRID_SIZE);

        // Create tiles dynamically
        for (int i = 0; i < TILE_COUNT; i++) {
            Button tile = new Button(this);
            tile.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_pink)); // Default tile color

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = tileSize;
            params.height = tileSize;
            params.setMargins(5, 5, 5, 5); // Uniform margin
            tile.setLayoutParams(params);

            int finalI = i;
            tile.setOnClickListener(v -> onTileClicked(finalI));

            puzzleGrid.addView(tile);
            gridTiles.add(tile);
        }
    }

    private void completePuzzle() {
        localPuzzleSolved = true;
        gameInProgress = false;
        instructionsText.setText("You got it!");

        // clear puzzle pattern so next interval can create a new puzzle
        getSharedPreferences("PuzzlePrefs", MODE_PRIVATE)
                .edit()
                .remove("currentPuzzlePattern")
                .apply();

        new android.os.Handler().postDelayed(() -> {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("puzzleSolved", true);
            setResult(RESULT_OK, resultIntent);
            finish();
        }, 1000);
    }

    private void startPuzzleGame() {
        if (gameInProgress) return;

        // check if a puzzle is already in progress
        List<Integer> loadedPattern = loadPuzzlePattern();
        boolean alreadyInProgress = loadedPattern != null && !loadedPattern.isEmpty();

        if (!alreadyInProgress) {
            // no puzzle in progress => create a new one
            generateNewPuzzlePattern();
            savePuzzlePattern(); // store these tiles
        } else {
            // puzzle pattern already stored => load it
            correctTiles.clear();
            correctTiles.addAll(loadedPattern);
        }

        // now that correctTiles is set, set up remaining tiles
        remainingTilesToSelect = new ArrayList<>(correctTiles);

        // highlight the correct tiles first
        instructionsText.setText("remember these tiles!");
        resetTilesToDefault();
        highlightCorrectTiles();

        new android.os.Handler().postDelayed(() -> {
            resetTilesToDefault();
            instructionsText.setText("select the correct tiles!");
            gameInProgress = true;
        }, (long) showTimeMs);
    }

    private void generateNewPuzzlePattern() {
        // generate a fresh 7-tile puzzle
        correctTiles.clear();
        List<Integer> allTiles = new ArrayList<>();
        for (int i = 0; i < TILE_COUNT; i++) {
            allTiles.add(i);
        }
        Collections.shuffle(allTiles);
        correctTiles.addAll(allTiles.subList(0, TOTAL_TILES_TO_SELECT));
    }

    private void onTileClicked(int tileIndex) {
        if (!gameInProgress || showingPattern) return;

        Button clickedTile = gridTiles.get(tileIndex);

        if (remainingTilesToSelect.contains(tileIndex)) {
            // Correct tile
            clickedTile.setBackgroundColor(ContextCompat.getColor(this, R.color.accent_maroon));
            remainingTilesToSelect.remove((Integer) tileIndex);

            if (remainingTilesToSelect.isEmpty()) {
                // All tiles correct
                instructionsText.setText("You got it!");
                gameInProgress = false;
                showTimeMs = 3000; // Reset show time for new game
                completePuzzle();
            }
        } else {
            // Incorrect tile
            instructionsText.setText("Oops! Try again!");
            clickedTile.setBackgroundColor(ContextCompat.getColor(this, R.color.deep_maroon));

            new android.os.Handler().postDelayed(() -> {
                resetTilesToDefault();
                reShowCorrectTiles();
            }, 2000); // Highlight incorrect tile for 2 seconds
        }
    }

    private void highlightCorrectTiles() {
        for (int index : correctTiles) {
            gridTiles.get(index).setBackgroundColor(ContextCompat.getColor(this, R.color.accent_maroon));
        }
    }

    private void resetTilesToDefault() {
        for (Button tile : gridTiles) {
            tile.setBackgroundColor(ContextCompat.getColor(this, R.color.primary_pink));
        }
    }

    private void reShowCorrectTiles() {
        instructionsText.setText("Remember these tiles!");
        showingPattern = true;

        // Reset remaining tiles to select
        remainingTilesToSelect = new ArrayList<>(correctTiles);

        // Highlight correct tiles
        highlightCorrectTiles();

        // Reduce show time for subsequent attempts
        showTimeMs = Math.max(showTimeMs - 500, MIN_SHOW_TIME_MS);

        new android.os.Handler().postDelayed(() -> {
            resetTilesToDefault();
            instructionsText.setText("Select the correct tiles!");
            gameInProgress = true;
            showingPattern = false;
        }, (long) showTimeMs);
    }

    private void savePuzzlePattern() {
        StringBuilder sb = new StringBuilder();
        for (int tileIndex : correctTiles) {
            sb.append(tileIndex).append(",");
        }
        getSharedPreferences("PuzzlePrefs", MODE_PRIVATE)
                .edit()
                .putString("currentPuzzlePattern", sb.toString())
                .apply();
    }

    private List<Integer> loadPuzzlePattern() {
        String pattern = getSharedPreferences("PuzzlePrefs", MODE_PRIVATE)
                .getString("currentPuzzlePattern", "");
        List<Integer> loadedTiles = new ArrayList<>();
        if (!pattern.isEmpty()) {
            for (String part : pattern.split(",")) {
                if (!part.trim().isEmpty()) {
                    loadedTiles.add(Integer.parseInt(part.trim()));
                }
            }
        }
        return loadedTiles;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!localPuzzleSolved) {
            getSharedPreferences("PuzzlePrefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("puzzleAbandoned", true)
                    .apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!localPuzzleSolved) {
            instructionsText.setText("remember these tiles!");
            highlightCorrectTiles();

            new Handler().postDelayed(() -> {
                resetTilesToDefault();
                instructionsText.setText("select the correct tiles!");
                gameInProgress = true;
            }, (long) reentryShowTimeMs);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!localPuzzleSolved) {
            getSharedPreferences("PuzzlePrefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("puzzleActive", false)
                    .apply();
        }
    }
}
