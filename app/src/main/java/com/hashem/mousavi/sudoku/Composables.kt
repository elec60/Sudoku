package com.hashem.mousavi.sudoku

import android.annotation.SuppressLint
import android.view.animation.AnticipateInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class MoveState {
    Moving,
    Moved,
    NotStarted
}

private val BIG_CELL_SIZE = 50.dp
private val MOVING_CELL_SIZE = 65.dp
private const val MOVE_DURATION = 500
private const val MOVING_CELL_SCALE_ANIMATION_DURATION = 300


@Composable
fun Sudoku(
    puzzle: SudokuPuzzle,
    moveInfo: MoveInfo,
    onTapped: (positionInRoot: Offset, positionIn2DArray: Pair<Int, Int>, value: Int) -> Unit,
    onTappedFromDock: (positionInRoot: Offset, value: Int) -> Unit,
    onMoveFinished: (positionIn2DArray: Pair<Int, Int>, value: Int) -> Unit,
    onUndoClicked: () -> Unit,
    onGoBackToDifficultyPageClicked: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var moveAnimationState by remember {
        mutableStateOf(MoveState.NotStarted)
    }

    val scaleAnimatable = remember {
        Animatable(initialValue = 0f)
    }

    var movingDockCellIndex by remember {
        mutableStateOf(-1)
    }

    val moveAnimation = animateFloatAsState(
        targetValue = if (moveAnimationState == MoveState.Moving) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (moveAnimationState == MoveState.Moving) MOVE_DURATION else 0,
            easing = {
                AnticipateInterpolator().getInterpolation(it)
            }
        ),
        finishedListener = {
            if (it == 1.0f) {
                scope.launch {
                    scaleAnimatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = MOVING_CELL_SCALE_ANIMATION_DURATION)
                    )
                    delay(MOVING_CELL_SCALE_ANIMATION_DURATION.toLong())
                    movingDockCellIndex = -1
                    scaleAnimatable.snapTo(0f)
                }
                moveAnimationState = MoveState.Moved
                onMoveFinished(moveInfo.posIn2DArray, moveInfo.value)
            }
        }
    )

    var rootPositionInRoot by remember {
        mutableStateOf(Offset.Zero)
    }

    var cellSize by remember {
        mutableStateOf(0.dp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                rootPositionInRoot = it.positionInRoot()
            }
    ) {
        TextButton(
            onClick = {
                onGoBackToDifficultyPageClicked()
            },
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(
                text = "Go back to difficulty page",
                color = Color(0xFFFF790F),
                fontSize = 12.sp
            )
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceAround
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UndoButton(puzzle, onUndoClicked)
                Board(
                    puzzle = puzzle,
                    onTapped = onTapped,
                    onCellSizeCalculated = {
                        cellSize = it
                    }
                )
            }

            Dock(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                scaleAnimatable = scaleAnimatable,
                moveAnimationState = moveAnimationState,
                movingDockCellIndex = movingDockCellIndex,
                onTappedFromDock = onTappedFromDock,
                isEmptyCellSelected = puzzle.emptyCellSelected,
                onMovingDockCellIndexChanged = {
                    movingDockCellIndex = it
                }
            )
        }

        if (moveAnimationState == MoveState.Moving) {
            Cell(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            -rootPositionInRoot.x.toInt() + moveInfo.fromPosition.x.toInt(),
                            -rootPositionInRoot.y.toInt() + moveInfo.fromPosition.y.toInt()
                        )
                    }
                    .graphicsLayer {
                        translationX =
                            moveAnimation.value * (moveInfo.toOffset.x - moveInfo.fromPosition.x - (MOVING_CELL_SIZE - cellSize).toPx() / 2)
                        translationY =
                            moveAnimation.value * (moveInfo.toOffset.y - moveInfo.fromPosition.y - (MOVING_CELL_SIZE - cellSize).toPx() / 2)
                    },
                size = MOVING_CELL_SIZE,
                isSelected = true,
                value = moveInfo.value,
                color = Color(0xFF6D4A03),
                onTapped = {}
            )
        }

        LaunchedEffect(moveInfo) {
            // preventing from triggering move at first composition
            if (moveInfo.fromPosition == Offset.Zero) return@LaunchedEffect

            moveAnimationState = MoveState.Moving
        }
    }
}

@Composable
private fun UndoButton(
    puzzle: SudokuPuzzle,
    onUndoClicked: () -> Unit
) {
    val enabledUndo = puzzle.undoList.isNotEmpty()
    IconButton(
        enabled = enabledUndo,
        onClick = {
            onUndoClicked()
        }
    ) {
        Icon(
            modifier = Modifier.size(24.dp),
            painter = painterResource(id = R.drawable.sudoku_undo),
            contentDescription = null,
            tint = if (enabledUndo) Color.Blue else Color.Gray,
        )
    }
}

private val DOCK_CELLS_SPACING = 2.dp

@Composable
private fun Dock(
    modifier: Modifier,
    scaleAnimatable: Animatable<Float, AnimationVector1D>,
    moveAnimationState: MoveState,
    movingDockCellIndex: Int,
    onTappedFromDock: (positionInRoot: Offset, value: Int) -> Unit,
    isEmptyCellSelected: Boolean,
    onMovingDockCellIndexChanged: (index: Int) -> Unit
) {
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DOCK_CELLS_SPACING)) {
            for (i in 1..5) {
                Cell(
                    modifier = Modifier.graphicsLayer {
                        if (movingDockCellIndex == i) {
                            scaleX = scaleAnimatable.value
                            scaleY = scaleAnimatable.value
                        }
                    },
                    size = BIG_CELL_SIZE,
                    isSelected = false,
                    value = i,
                    isInvisible = (moveAnimationState == MoveState.Moving && movingDockCellIndex == i),
                    onTapped = {
                        if (moveAnimationState != MoveState.Moving && isEmptyCellSelected) {
                            onMovingDockCellIndexChanged(i)
                            onTappedFromDock(it, i)
                            scope.launch {
                                scaleAnimatable.snapTo(0f)
                            }
                        }
                    }
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(DOCK_CELLS_SPACING)
        ) {
            for (i in (6..9)) {
                Cell(
                    modifier = Modifier.graphicsLayer {
                        if (movingDockCellIndex == i) {
                            scaleX = scaleAnimatable.value
                            scaleY = scaleAnimatable.value
                        }
                    },
                    size = BIG_CELL_SIZE,
                    isSelected = false,
                    value = i,
                    isInvisible = (moveAnimationState == MoveState.Moving && movingDockCellIndex == i),
                    onTapped = {
                        if (moveAnimationState != MoveState.Moving && isEmptyCellSelected) {
                            onMovingDockCellIndexChanged(i)
                            onTappedFromDock(it, i)
                            scope.launch {
                                scaleAnimatable.snapTo(0f)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun Board(
    puzzle: SudokuPuzzle,
    onTapped: (positionInRoot: Offset, positionIn2DArray: Pair<Int, Int>, value: Int) -> Unit,
    onCellSizeCalculated: (cellSize: Dp) -> Unit
) {
    var cellSize by remember {
        mutableStateOf(0.dp)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
    ) {

        val barWidth = 8.dp
        val width = this.maxWidth // puzzle is square

        cellSize = (width - barWidth * 2) / 9
        LaunchedEffect(true) {
            onCellSizeCalculated(cellSize)
        }

        val content: @Composable () -> Unit = {
            puzzle.board.forEachIndexed { index1, array ->
                array.forEachIndexed { index2, value ->
                    Cell(
                        size = cellSize,
                        isSelected = puzzle.selectedCells.contains(Pair(index1, index2)),
                        value = value,
                        onTapped = { positionInRoot: Offset -> onTapped(positionInRoot, Pair(index1, index2), value) }
                    )
                }
            }
        }

        Layout(content = content) { measurables, constraints ->

            val placeables = measurables.map {
                it.measure(constraints)
            }

            layout(width = width.roundToPx(), height = width.roundToPx()) {
                var x = 0
                var y = 0
                var row = -1
                placeables.forEachIndexed { index, placeable ->
                    var isFirstColumn = false
                    if (index % 9 == 0) {
                        row++
                        isFirstColumn = true
                        x = 0
                        if (index > 0) {
                            y += placeable.height
                            if (row % 3 == 0) {
                                y += barWidth.roundToPx()
                            }
                        }
                    } else {
                        x += placeable.width
                    }

                    if (index % 3 == 0 && !isFirstColumn) {
                        x += barWidth.roundToPx()
                    }

                    placeable.place(x = x, y = y)
                }
            }
        }
    }
}

@Composable
fun Cell(
    modifier: Modifier = Modifier,
    size: Dp,
    isSelected: Boolean,
    value: Int,
    cornerRadius: Dp = 8.dp,
    color: Color = Color(0xFF6D4A03),
    isInvisible: Boolean = false,
    onTapped: (positionInRoot: Offset) -> Unit
) {
    var offset by remember {
        mutableStateOf(Offset.Zero)
    }
    val borderColor = if (isSelected && value == 0) {
        Color.Red
    } else if (isSelected) {
        Color.Green
    } else {
        Color.Transparent
    }
    val padding = 1.dp
    Card(
        modifier = modifier
            .padding(all = padding)
            .size(size - padding * 2)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                onTapped(offset)
            }
            .onGloballyPositioned {
                val (x, y) = it.positionInRoot()
                offset = Offset(x = x, y = y)
            },
        shape = RoundedCornerShape(size = cornerRadius),
        border = BorderStroke(width = 1.dp, color = borderColor),
        elevation = 0.dp,
        backgroundColor = Color.Transparent
    ) {
        if (value != 0) {
            Text(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (!isInvisible) color else Color.Transparent)
                    .wrapContentHeight(),
                text = if (!isInvisible) value.toString() else "",
                fontSize = 24.sp,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        } else {
            Box(
                modifier = Modifier
                    .background(color = if (!isInvisible) Color.Black.copy(alpha = 0.15f) else Color.Transparent)
                    .fillMaxSize()
            )
        }
    }
}

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun DifficultyLevel(
    onDifficultyLevelChosen: (Difficulty) -> Unit
) {
    Scaffold(

    ) {
        Column(
            modifier = Modifier
                .padding(all = 16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(space = 10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for (d in Difficulty.values()) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onDifficultyLevelChosen(d) }
                ){
                    Text(text =  d.name)
                }
            }
        }
    }
}

@Preview
@Composable
fun CellEmptyUnselectedPreview() {
    Cell(size = 48.dp, isSelected = false, value = 0) { }
}

@Preview
@Composable
fun CellEmptySelectedPreview() {
    Cell(size = 48.dp, isSelected = true, value = 10) { }
}

@Preview
@Composable
fun CellNotEmptySelectedPreview() {
    Cell(size = 48.dp, isSelected = true, value = 2) {}
}

@Preview
@Composable
fun CellNotEmptyNotSelectedPreview() {
    Cell(size = 48.dp, isSelected = false, value = 2) { }
}
