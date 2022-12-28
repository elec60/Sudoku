package com.hashem.mousavi.sudoku

import androidx.compose.ui.geometry.Offset

data class SudokuPuzzle(
    val board: Array<IntArray> = emptyArray(),
    val answer: Array<IntArray> = emptyArray(),
    val selectedCells: List<Pair<Int, Int>> = emptyList(),
    val undoList: List<Pair<Int, Int>> = arrayListOf(),
    val emptyCellSelected: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SudokuPuzzle

        if (!board.contentDeepEquals(other.board)) return false
        if (!answer.contentDeepEquals(other.answer)) return false
        if (selectedCells != other.selectedCells) return false
        if (undoList != other.undoList) return false
        if (emptyCellSelected != other.emptyCellSelected) return false

        return true
    }

    override fun hashCode(): Int {
        var result = board.contentDeepHashCode()
        result = 31 * result + answer.contentDeepHashCode()
        result = 31 * result + selectedCells.hashCode()
        result = 31 * result + undoList.hashCode()
        result = 31 * result + emptyCellSelected.hashCode()
        return result
    }
}

data class MoveInfo(
    val fromPosition: Offset = Offset.Zero,
    val toOffset: Offset = Offset.Zero,
    val value: Int = 0,
    val posIn2DArray: Pair<Int, Int> = Pair(0, 0)
)

enum class Difficulty {
    Beginner,
    Easy,
    Medium,
    Hard,
    Expert
}
