package com.example.usagemanagement;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PuzzleActivity extends AppCompatActivity {

    private static final int GRID_SIZE = 5;
    private static final int BLUE_TILE_COUNT = 7;
    private static final int SHOW_TIME_MS = 5000;

    private List<Button> tileButtons = new ArrayList<>();
    private List<Integer> blueTileIndices = new ArrayList<>();
    private Handler handler = new Handler();
    private TextView instructionTextView;
    private boolean isSelectionPhase = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_puzzle);

        instructionTextView = findViewById(R.id.instructionTextView);
       // GridLayout gridLayout = findViewById(R.id.gridLayout); //********************************

        // Create 5x5 grid of tiles (buttons)
        for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) {
            Button tileButton = new Button(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
           // params.setMargins(4, 4, 4, 4); // Add spacing between buttons
            tileButton.setLayoutParams(params);
            tileButton.setId(View.generateViewId());
            tileButton.setOnClickListener(this::onTileClick);
            tileButtons.add(tileButton);
          //  gridLayout.addView(tileButton); //********************************
        }


        // Randomly pick blue tiles and start game
        initializeGame();
    }

    private void initializeGame() {
        isSelectionPhase = false;
        instructionTextView.setText("Memorize the blue tiles!");

        // Randomly select 7 unique tiles
        List<Integer> allIndices = new ArrayList<>();
        for (int i = 0; i < GRID_SIZE * GRID_SIZE; i++) {
            allIndices.add(i);
        }
        Collections.shuffle(allIndices);
        blueTileIndices = allIndices.subList(0, BLUE_TILE_COUNT);

        // Set selected tiles to blue
        for (int index : blueTileIndices) {
            tileButtons.get(index).setBackgroundColor(Color.BLUE);
        }

        // After SHOW_TIME_MS, reset tiles to white
        handler.postDelayed(this::hideBlueTiles, SHOW_TIME_MS);
    }

    private void hideBlueTiles() {
        // Turn all tiles back to white
        for (Button tile : tileButtons) {
            tile.setBackgroundColor(Color.WHITE);
        }
        isSelectionPhase = true;
        instructionTextView.setText("Tap the tiles that were blue!");
    }

    private void onTileClick(View view) {
        if (!isSelectionPhase) return; // Ignore clicks if not in selection phase

        Button clickedButton = (Button) view;
        int clickedIndex = tileButtons.indexOf(clickedButton);

        // Mark the tile as selected (toggle color for visual feedback)
        if (blueTileIndices.contains(clickedIndex)) {
            clickedButton.setBackgroundColor(Color.GREEN); // Correct tile
            blueTileIndices.remove(Integer.valueOf(clickedIndex)); // Remove from blue list
        } else {
            clickedButton.setBackgroundColor(Color.RED); // Incorrect tile
        }

        // Check if game is complete
        if (blueTileIndices.isEmpty()) {
            instructionTextView.setText("You found all the blue tiles!");
            isSelectionPhase = false; // Stop further interactions
        }
    }


}
