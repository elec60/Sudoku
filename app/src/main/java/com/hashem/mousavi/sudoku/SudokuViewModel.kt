package com.hashem.mousavi.sudoku

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SudokuViewModel : ViewModel() {

    val uiStatus = mutableStateOf(SudokuPuzzle())
    val moveState = mutableStateOf(MoveInfo())
    private val _gameFinished = MutableSharedFlow<Boolean>()
    val gameFinished = _gameFinished.asSharedFlow()

    fun onCreate(context: Context, difficulty: Difficulty) {
        viewModelScope.launch(Dispatchers.IO) {
            val sudokuPuzzle = createPuzzle(context, difficulty)
            val board = sudokuPuzzle.board

            shuffle(board, sudokuPuzzle)

            uiStatus.value = sudokuPuzzle.copy(board = board)
        }
    }

    private suspend fun shuffle(
        board: Array<IntArray>,
        sudokuPuzzle: SudokuPuzzle
    ) {
        var i = 10
        while (i > 0) {
            val deepCopy = Array(9) {
                IntArray(9) { 0 }
            }
            board.forEachIndexed { index1, ints ->
                ints.forEachIndexed { index2, number ->
                    deepCopy[index1][index2] = number
                }
            }
            deepCopy.shuffle()
            for (ints in deepCopy) {
                ints.shuffle()
            }
            uiStatus.value = sudokuPuzzle.copy(board = deepCopy)
            delay(100)
            i--
        }
    }

    private var selectedEmptyCellPosition: Offset? = null
    private var positionIn2DArrayOfEmptyCell: Pair<Int, Int>? = null

    fun onCellTapped(position: Offset, positionIn2DArray: Pair<Int, Int>, tappedValue: Int) {
        if (selectedEmptyCellPosition == position) return

        // new empty cell has selected
        if (uiStatus.value.selectedCells.isNotEmpty() && tappedValue == 0) {
            selectedEmptyCellPosition = position
            positionIn2DArrayOfEmptyCell = positionIn2DArray
            uiStatus.value = uiStatus.value.copy(
                selectedCells = arrayListOf(positionIn2DArray),
                emptyCellSelected = isEmptyCellSelected()
            )
            return
        }

        if (uiStatus.value.selectedCells.isNotEmpty() && tappedValue != 0 && uiStatus.value.selectedCells.contains(positionIn2DArray)) {
            resetSelectionData()

            uiStatus.value = uiStatus.value.copy(
                selectedCells = emptyList(),
                emptyCellSelected = isEmptyCellSelected()
            )
            return
        }

        val board = uiStatus.value.board
        val selectedCells = mutableListOf<Pair<Int, Int>>().apply {
            add(positionIn2DArray)

            // highlight same values
            if (tappedValue != 0) {
                resetSelectionData()
                board.forEachIndexed { index1, array ->
                    array.forEachIndexed { index2, value ->
                        if (value == tappedValue && positionIn2DArray.first != index1 && positionIn2DArray.second != index2) {
                            add(Pair(index1, index2))
                        }
                    }
                }
            } else {
                selectedEmptyCellPosition = position
                positionIn2DArrayOfEmptyCell = positionIn2DArray
            }
        }

        uiStatus.value = uiStatus.value.copy(
            selectedCells = selectedCells,
            emptyCellSelected = isEmptyCellSelected()
        )
    }

    private fun isEmptyCellSelected() = selectedEmptyCellPosition != null

    fun onTappedFromDock(positionInRoot: Offset, value: Int) {
        val toPosition = selectedEmptyCellPosition
        val posIn2DArray = positionIn2DArrayOfEmptyCell
        if (toPosition != null && posIn2DArray != null) {
            moveState.value = MoveInfo(positionInRoot, toPosition, value, posIn2DArray)
        }
    }

    fun onMoveFinished(positionIn2DArray: Pair<Int, Int>, value: Int) {
        val board = uiStatus.value.board
        board[positionIn2DArray.first][positionIn2DArray.second] = value

        resetSelectionData()

        // highlight the same values as the chosen one
        val selectedCells = mutableListOf<Pair<Int, Int>>().apply {
            board.forEachIndexed { index1, array ->
                array.forEachIndexed { index2, number ->
                    if (number == value) {
                        add(Pair(index1, index2))
                    }
                }
            }
        }

        val undoList = uiStatus.value.undoList.toMutableList().apply {
            add(positionIn2DArray)
        }

        uiStatus.value = uiStatus.value.copy(
            board = board,
            selectedCells = selectedCells,
            undoList = undoList,
            emptyCellSelected = isEmptyCellSelected()
        )

        // check if game finished
        viewModelScope.launch(Dispatchers.IO) {
            val finished = !board.flatMap { it.map { it } }.contains(0)
            if (finished) {
                _gameFinished.emit(board.contentDeepEquals(uiStatus.value.answer))
            }
        }
    }

    fun onUndoClicked() {
        viewModelScope.launch(Dispatchers.IO) {
            val undoList = uiStatus.value.undoList.toMutableList()
            undoList.lastOrNull()?.let { pairToUndo ->
                val board = uiStatus.value.board
                board.forEachIndexed { index1, array ->
                    array.forEachIndexed { index2, _ ->
                        if (pairToUndo.first == index1 && pairToUndo.second == index2) {
                            resetSelectionData()
                            board[index1][index2] = 0
                            undoList.remove(pairToUndo)
                            uiStatus.value = uiStatus.value.copy(
                                board = board,
                                undoList = undoList,
                                selectedCells = emptyList(),
                                emptyCellSelected = isEmptyCellSelected()
                            )
                            return@launch
                        }
                    }
                }
            }
        }
    }

    private fun resetSelectionData() {
        selectedEmptyCellPosition = null
        positionIn2DArrayOfEmptyCell = null
    }

    private fun createPuzzle(context: Context, difficulty: Difficulty): SudokuPuzzle {
        val path = when (difficulty) {
            Difficulty.Beginner -> "puzzles/sud_beginner.txt"
            Difficulty.Easy -> "puzzles/sud_easy.txt"
            Difficulty.Medium -> "puzzles/sud_medium.txt"
            Difficulty.Hard -> "puzzles/sud_hard.txt"
            Difficulty.Expert -> "puzzles/sud_expert.txt"
        }
        try {
            val inputStream = context.assets.open(path)
            val size = inputStream.available()
            val byte = ByteArray(size)
            inputStream.read(byte)
            val str = String(byte)

            val randomPuzzle = str.split("\n").random()

            val pattern: List<String> = randomPuzzle.substring(0, 81).split("").filter { it.isNotEmpty() }
            val answer: List<String> = randomPuzzle.substring(81).split("").filter { it.isNotEmpty() }

            val pattern2D = Array(9) { index1 ->
                IntArray(9) { index2 ->
                    val start = index1 * 9
                    pattern[start + index2].toInt()
                }
            }
            val answer2D = Array(9) { index1 ->
                IntArray(9) { index2 ->
                    val start = index1 * 9
                    answer[start + index2].toInt()
                }
            }

            return SudokuPuzzle(
                board = pattern2D,
                answer = answer2D
            )
        } catch (e: Exception) {
            return SudokuPuzzle()
        }
    }

    fun resetStates() {
        uiStatus.value = SudokuPuzzle()
        moveState.value = MoveInfo()
    }
}
