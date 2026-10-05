package com.shuowen.chess.ui.editor

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.shuowen.chess.chess.ChessGame
import com.shuowen.chess.chess.ChessRules
import com.shuowen.chess.chess.Color
import com.shuowen.chess.chess.Move
import com.shuowen.chess.chess.Piece
import com.shuowen.chess.chess.PieceType
import com.shuowen.chess.chess.Position
import com.shuowen.chess.chess.Square
import com.shuowen.chess.ui.appearance.LocalChessAppearance
import com.shuowen.chess.ui.appearance.boardColor
import com.shuowen.chess.ui.appearance.boardImage
import com.shuowen.chess.ui.appearance.pieceImage
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessEditor() {
    val game = remember { ChessGame() }
    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Square?>(null) }
    var promotionMoves by remember { mutableStateOf<List<Move>>(emptyList()) }
    var boardFlipped by remember { mutableStateOf(false) }
    var animatedMove by remember { mutableStateOf<Move?>(null) }
    var animationStartPosition by remember { mutableStateOf<Position?>(null) }
    var vibrateWhenAnimationFinishes by remember { mutableStateOf(false) }
    var moveAnimationKey by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val position = remember(revision) { game.position }
    val legalTargets = selected?.let { game.legalMoves(it).map(Move::to).toSet() }.orEmpty()
    val candidateTargets = selected?.let { game.candidateMoves(it).map(Move::to).toSet() }.orEmpty()
    val forbiddenTargets = candidateTargets - legalTargets
    val outsideTapSource = remember { MutableInteractionSource() }

    Scaffold(topBar = { TopAppBar(title = { Text("模拟棋局") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(interactionSource = outsideTapSource, indication = null) { selected = null }
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EditorToolbar(
                position = animationStartPosition ?: position,
                enabled = animationStartPosition == null,
                onFlip = {
                    boardFlipped = !boardFlipped
                    selected = null
                },
                onNewGame = {
                    game.reset()
                    selected = null
                    revision++
                },
            )

            ChessBoard(
                position = position,
                selected = selected,
                legalTargets = legalTargets,
                forbiddenTargets = forbiddenTargets,
                flipped = boardFlipped,
                showGameStatus = true,
                animatedMove = animatedMove,
                animationStartPosition = animationStartPosition,
                moveAnimationKey = moveAnimationKey,
                onMoveAnimationFinished = {
                    if (vibrateWhenAnimationFinishes) vibrateCapture(context)
                    vibrateWhenAnimationFinishes = false
                    animationStartPosition = null
                },
            ) { square ->
                if (selected == square) {
                    selected = null
                } else if (selected == null) {
                    if (position.board[square]?.color == position.turn) selected = square
                } else {
                    val candidates = game.legalMoves(selected).filter { it.to == square }
                    when {
                        candidates.size > 1 -> promotionMoves = candidates
                        candidates.size == 1 -> {
                            val move = candidates.single()
                            val isCapture = position.board[move.to] != null || move.isEnPassant
                            if (game.play(move)) {
                                vibrateWhenAnimationFinishes = isCapture
                                animationStartPosition = position
                                animatedMove = move
                                moveAnimationKey++
                                selected = null
                                revision++
                            }
                        }
                        position.board[square]?.color == position.turn -> selected = square
                        else -> selected = null
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = "轮流操作白方和黑方，点选棋子后再点目标格。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        }
    }

    if (promotionMoves.isNotEmpty()) {
        PromotionDialog(
            moves = promotionMoves,
            turn = position.turn,
            onDismiss = { promotionMoves = emptyList() },
            onSelect = { move ->
                val isCapture = position.board[move.to] != null || move.isEnPassant
                if (game.play(move)) {
                    vibrateWhenAnimationFinishes = isCapture
                    animationStartPosition = position
                    animatedMove = move
                    moveAnimationKey++
                    selected = null
                    promotionMoves = emptyList()
                    revision++
                }
            },
        )
    }
}

@Composable
private fun EditorToolbar(
    position: Position,
    enabled: Boolean,
    onFlip: () -> Unit,
    onNewGame: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(statusText(position), fontWeight = FontWeight.SemiBold)
        Row {
            TextButton(onClick = onFlip, enabled = enabled) { Text("翻转") }
            TextButton(onClick = onNewGame, enabled = enabled) { Text("新对局") }
        }
    }
}

@Composable
internal fun ChessBoard(
    position: Position,
    selected: Square?,
    legalTargets: Set<Square>,
    forbiddenTargets: Set<Square>,
    flipped: Boolean,
    showGameStatus: Boolean,
    possibleMoves: List<Move> = emptyList(),
    lastMove: Move? = null,
    animatedMove: Move? = null,
    animationStartPosition: Position? = null,
    moveAnimationKey: Int = 0,
    verticalPadding: Dp = 8.dp,
    onMoveAnimationFinished: () -> Unit = {},
    onSquare: (Square) -> Unit,
) {
    val context = LocalContext.current
    val appearance = LocalChessAppearance.current
    val boardImages = remember(appearance.board.id) {
        val light = appearance.board.boardImage(context, light = true)
        val dark = appearance.board.boardImage(context, light = false)
        if (light != null && dark != null) light to dark else null
    }
    val boardColors = remember(appearance.board.id) {
        val light = appearance.board.boardColor(light = true)
        val dark = appearance.board.boardColor(light = false)
        if (light != null && dark != null) light to dark else null
    }
    val pieceImages = remember(appearance.pieces.id) {
        val images = Color.entries.flatMap { color -> PieceType.entries.map { type -> Piece(color, type) } }
            .associateWith { piece -> appearance.pieces.pieceImage(context, piece) }
        if (images.values.all { it != null }) images else emptyMap()
    }
    val animation = remember(animatedMove) { animatedMove?.let(::boardMoveAnimation) }
    val moveProgress = remember(moveAnimationKey) { Animatable(if (animation == null) 1f else 0f) }
    var displayedArrows by remember { mutableStateOf(possibleMoves to lastMove) }
    var completedMoveKey by remember { mutableIntStateOf(moveAnimationKey) }
    val holdArrows = animatedMove != null && moveAnimationKey != completedMoveKey
    val (visiblePossibleMoves, visibleLastMove) = if (holdArrows) {
        displayedArrows
    } else {
        possibleMoves to lastMove
    }
    SideEffect {
        if (!holdArrows) displayedArrows = possibleMoves to lastMove
    }
    var cellSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(moveAnimationKey) {
        animation?.stageDurations?.forEachIndexed { stage, duration ->
            moveProgress.animateTo((stage + 1).toFloat(), animationSpec = tween(durationMillis = duration))
        }
        completedMoveKey = moveAnimationKey
        if (animation != null) onMoveAnimationFinished()
    }
    val isAnimating = animation != null && moveProgress.value < animation.endProgress
    val ranks = if (flipped) 0..7 else 7 downTo 0
    val files = if (flipped) 7 downTo 0 else 0..7
    val checkedKing = if (!isAnimating && showGameStatus && ChessRules.isInCheck(position, position.turn)) {
        position.board.entries.firstOrNull { it.value == Piece(position.turn, PieceType.KING) }?.key
    } else {
        null
    }
    val checkmated = checkedKing != null && ChessRules.isCheckmate(position)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(vertical = verticalPadding)
            .border(1.dp, UiColor(0xFFD5D8DA)),
    ) {
        Column(Modifier.fillMaxSize()) {
            for (rank in ranks) {
                val movingAcrossThisRow = isAnimating && animation?.move?.to?.rank == rank
                Row(Modifier.weight(1f).zIndex(if (movingAcrossThisRow) 1f else 0f)) {
                    for (file in files) {
                    val square = Square(file, rank)
                    val isLightSquare = (file + rank) % 2 == 0
                    val isCapture = square in legalTargets && position.board[square] != null
                    val baseColor = when {
                        square == selected -> UiColor(0xFFE5C65C)
                        isLightSquare -> UiColor(0xFFF0D9B5)
                        else -> UiColor(0xFFB58863)
                    }
                    val displayColor = when {
                        !isCapture -> baseColor
                        isLightSquare -> UiColor(0xFFC3A77E)
                        else -> UiColor(0xFF815D45)
                    }
                    val squareImage = boardImages?.let { (light, dark) -> if (isLightSquare) light else dark }
                    val squareColor = boardColors?.let { (light, dark) -> if (isLightSquare) light else dark }
                    val hasCustomBoard = squareImage != null || squareColor != null
                    val squareModifier = Modifier.weight(1f).fillMaxHeight().background(squareColor ?: displayColor)

                    Box(
                        modifier = squareModifier
                            .zIndex(if (isAnimating) animation?.layerAt(square, moveProgress.value) ?: 0f else 0f)
                            .onSizeChanged { cellSize = it }
                            .clickable(enabled = !isAnimating) { onSquare(square) },
                        contentAlignment = Alignment.Center,
                    ) {
                        squareImage?.let { image ->
                            Image(
                                bitmap = image,
                                contentDescription = null,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        if (appearance.board.usesIntersectionGrid) {
                            val shownFile = if (flipped) 7 - square.file else square.file
                            val shownRank = if (flipped) square.rank else 7 - square.rank
                            Canvas(Modifier.matchParentSize()) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val lineColor = UiColor(0xCC3F291B)
                                val stroke = 1.dp.toPx()
                                if (shownFile > 0) drawLine(lineColor, center, Offset(0f, center.y), stroke)
                                if (shownFile < 7) drawLine(lineColor, center, Offset(size.width, center.y), stroke)
                                if (shownRank > 0) drawLine(lineColor, center, Offset(center.x, 0f), stroke)
                                if (shownRank < 7) drawLine(lineColor, center, Offset(center.x, size.height), stroke)
                            }
                        }
                        val pieceScale = if (appearance.pieces.usesIntersectionGrid) 0.88f else 1.08f
                        val oldPiece = animationStartPosition?.board?.get(square)
                        val finalPiece = position.board[square]?.takeUnless {
                            isAnimating && oldPiece == null && animation?.endsAt(square) != true
                        }
                        val capturedPiece = oldPiece?.takeIf {
                            isAnimating && it != finalPiece && animation?.startsAt(square) != true
                        }
                        capturedPiece?.let { piece ->
                            pieceImages[piece]?.let { image ->
                                Image(
                                    bitmap = image,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = pieceScale, scaleY = pieceScale),
                                    contentScale = ContentScale.Fit,
                                )
                            } ?: Text(pieceGlyph(piece), fontSize = 34.sp)
                        }
                        finalPiece?.let { piece ->
                            val motion = if (isAnimating) animation?.motionAt(square, piece) else null
                            val fraction = motion?.let { animation?.fraction(it, moveProgress.value) } ?: 1f
                            val displayedPiece = if (
                                motion != null &&
                                motion.pieceType != PieceType.ROOK &&
                                animation?.move?.promotion != null
                            ) Piece(piece.color, PieceType.PAWN) else piece
                            val fileDirection = if (flipped) -1 else 1
                            val rankDirection = if (flipped) 1 else -1
                            val pieceModifier = if (motion != null) {
                                Modifier.offset {
                                    IntOffset(
                                        x = ((motion.from.file - square.file) * fileDirection * cellSize.width * (1f - fraction)).toInt(),
                                        y = ((motion.from.rank - square.rank) * rankDirection * cellSize.height * (1f - fraction)).toInt(),
                                    )
                                }
                            } else {
                                Modifier
                            }
                            pieceImages[displayedPiece]?.let { image ->
                                Image(
                                    bitmap = image,
                                    contentDescription = null,
                                    modifier = pieceModifier
                                        .fillMaxSize()
                                        .graphicsLayer(scaleX = pieceScale, scaleY = pieceScale),
                                    contentScale = ContentScale.Fit,
                                )
                            } ?: Text(pieceGlyph(displayedPiece), fontSize = 34.sp, modifier = pieceModifier)
                        }
                        when {
                            square in legalTargets && !isCapture -> Box(
                                Modifier.size(12.dp).background(UiColor(0x99606060), CircleShape),
                            )
                            square in forbiddenTargets -> Text(
                                text = "×",
                                color = UiColor(0xFF555555),
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        val squareOverlay = when {
                            square == checkedKing && checkmated -> UiColor(0x55B71C1C)
                            hasCustomBoard && isCapture -> UiColor(0x22000000)
                            hasCustomBoard && square == selected -> UiColor(0x4DE5C65C)
                            else -> null
                        }
                        squareOverlay?.let { color ->
                            Box(Modifier.matchParentSize().background(color))
                        }
                        if (square == checkedKing && !checkmated) {
                            Box(Modifier.matchParentSize().border(1.dp, UiColor(0xFFD32F2F)))
                        }
                    }
                    }
                }
            }
        }
        if (visiblePossibleMoves.isNotEmpty() || visibleLastMove != null) {
            Canvas(Modifier.matchParentSize()) {
                fun center(square: Square): Offset {
                    val shownFile = if (flipped) 7 - square.file else square.file
                    val shownRank = if (flipped) square.rank else 7 - square.rank
                    return Offset(
                        x = (shownFile + 0.5f) * size.width / 8f,
                        y = (shownRank + 0.5f) * size.height / 8f,
                    )
                }

                fun drawMoveArrow(move: Move, color: UiColor, stroke: Float) {
                    val start = center(move.from)
                    val end = center(move.to)
                    drawLine(color, start, end, strokeWidth = stroke, cap = StrokeCap.Round)

                    val angle = atan2(end.y - start.y, end.x - start.x)
                    val headLength = 10.dp.toPx()
                    val spread = 0.48f
                    val left = Offset(
                        end.x - headLength * cos(angle - spread),
                        end.y - headLength * sin(angle - spread),
                    )
                    val right = Offset(
                        end.x - headLength * cos(angle + spread),
                        end.y - headLength * sin(angle + spread),
                    )
                    drawLine(color, end, left, strokeWidth = stroke, cap = StrokeCap.Round)
                    drawLine(color, end, right, strokeWidth = stroke, cap = StrokeCap.Round)
                }

                visiblePossibleMoves.forEach { move ->
                    drawMoveArrow(move, UiColor(0xE600A86B), 2.5.dp.toPx())
                }
                visibleLastMove?.let { move ->
                    drawMoveArrow(move, UiColor(0xE6E53935), 2.5.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun PromotionDialog(
    moves: List<Move>,
    turn: Color,
    onDismiss: () -> Unit,
    onSelect: (Move) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择升变棋子") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                moves.forEach { move ->
                    Text(
                        text = pieceGlyph(Piece(turn, move.promotion!!)),
                        fontSize = 38.sp,
                        modifier = Modifier.clickable { onSelect(move) }.padding(8.dp),
                    )
                }
            }
        },
        confirmButton = {},
    )
}

internal fun statusText(position: Position) = when {
    ChessRules.isCheckmate(position) -> if (position.turn == Color.WHITE) "白方被将死" else "黑方被将死"
    ChessRules.isStalemate(position) -> "和棋：无子可动"
    ChessRules.isInCheck(position, position.turn) -> if (position.turn == Color.WHITE) "白方被将军" else "黑方被将军"
    position.turn == Color.WHITE -> "白方走棋"
    else -> "黑方走棋"
}

internal fun pieceGlyph(piece: Piece): String = when (piece.color to piece.type) {
    Color.WHITE to PieceType.KING -> "♔"
    Color.WHITE to PieceType.QUEEN -> "♕"
    Color.WHITE to PieceType.ROOK -> "♖"
    Color.WHITE to PieceType.BISHOP -> "♗"
    Color.WHITE to PieceType.KNIGHT -> "♘"
    Color.WHITE to PieceType.PAWN -> "♙"
    Color.BLACK to PieceType.KING -> "♚"
    Color.BLACK to PieceType.QUEEN -> "♛"
    Color.BLACK to PieceType.ROOK -> "♜"
    Color.BLACK to PieceType.BISHOP -> "♝"
    Color.BLACK to PieceType.KNIGHT -> "♞"
    Color.BLACK to PieceType.PAWN -> "♟"
    else -> ""
}

internal fun vibrateCapture(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.takeIf { it.hasVibrator() }?.vibrate(
        VibrationEffect.createOneShot(60L, VibrationEffect.DEFAULT_AMPLITUDE),
    )
}
