package com.example.usagemanagement;

import android.os.Bundle;
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

    private void startPuzzleGame() {
        if (gameInProgress) return;

        instructionsText.setText("Remember these tiles!");
        resetTilesToDefault();

        // Generate new correct pattern
        correctTiles.clear();
        List<Integer> allTiles = new ArrayList<>();
        for (int i = 0; i < TILE_COUNT; i++) {
            allTiles.add(i);
        }
        Collections.shuffle(allTiles);
        correctTiles.addAll(allTiles.subList(0, TOTAL_TILES_TO_SELECT)); // Select 7 tiles

        // Initialize remaining tiles to select
        remainingTilesToSelect = new ArrayList<>(correctTiles);

        // Highlight correct tiles
        highlightCorrectTiles();

        new android.os.Handler().postDelayed(() -> {
            resetTilesToDefault();
            instructionsText.setText("Select the correct tiles!");
            gameInProgress = true;
        }, (long) showTimeMs);
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
}
