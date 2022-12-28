package com.hashem.mousavi.sudoku

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hashem.mousavi.sudoku.ui.theme.SudokuTheme

class MainActivity : ComponentActivity() {

    private val viewModel: SudokuViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SudokuTheme {

                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "select_difficulty") {
                    composable("select_difficulty") {
                        DifficultyLevel {
                            navController.navigate("sudoku_game/${it.name}")
                        }
                    }
                    composable(
                        "sudoku_game/{difficulty}",
                        arguments = listOf(navArgument("difficulty") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val diff = backStackEntry.arguments?.getString("difficulty") ?: "Beginner"
                        val context = LocalContext.current
                        LaunchedEffect(true) {
                            viewModel.onCreate(context, Difficulty.valueOf(diff))

                            viewModel.gameFinished.collect() { win ->
                                Toast.makeText(context, "You ${if (win) "win." else "loose!"}", Toast.LENGTH_LONG).show()
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(color = Color(0xFFFF790F).copy(alpha = 0.2f))
                                .padding(all = 16.dp)
                        ) {
                            Sudoku(
                                puzzle = viewModel.uiStatus.value,
                                moveInfo = viewModel.moveState.value,
                                onTapped = { positionInRoot, positionIn2DArray, value ->
                                    viewModel.onCellTapped(positionInRoot, positionIn2DArray, value)
                                },
                                onTappedFromDock = { positionInRoot, value ->
                                    viewModel.onTappedFromDock(positionInRoot, value)
                                },
                                onMoveFinished = { positionIn2DArray, value ->
                                    viewModel.onMoveFinished(positionIn2DArray, value)
                                },
                                onUndoClicked = {
                                    viewModel.onUndoClicked()
                                },
                                onGoBackToDifficultyPageClicked = {
                                    viewModel.resetStates()
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
