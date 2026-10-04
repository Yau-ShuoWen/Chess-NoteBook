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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.shuowen.chess.chess.PlayedMove
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
    var setupMode by remember { mutableStateOf(false) }
    var setupBoard by remember { mutableStateOf(game.position.board) }
    var setupPiece by remember { mutableStateOf<Piece?>(Piece(Color.WHITE, PieceType.PAWN)) }
    var setupTurn by remember { mutableStateOf(Color.WHITE) }
    var setupError by remember { mutableStateOf<String?>(null) }
    var boardFlipped by remember { mutableStateOf(false) }
    var animatedMove by remember { mutableStateOf<Move?>(null) }
    var moveAnimationKey by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    val position = remember(revision) { game.position }
    val legalTargets = selected?.let { game.legalMoves(it).map(Move::to).toSet() }.orEmpty()
    val candidateTargets = selected?.let { game.candidateMoves(it).map(Move::to).toSet() }.orEmpty()
    val forbiddenTargets = candidateTargets - legalTargets
    val history = remember(revision) { game.history.toList() }
    val outsideTapSource = remember { MutableInteractionSource() }

    Scaffold(topBar = { TopAppBar(title = { Text("国际象棋笔记") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .clickable(interactionSource = outsideTapSource, indication = null) { selected = null }
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EditorToolbar(
                position = position,
                setupMode = setupMode,
                onFlip = {
                    boardFlipped = !boardFlipped
                    selected = null
                },
                onToggleSetup = {
                    if (setupMode) {
                        val validation = ChessRules.validateSetup(setupBoard)
                        if (validation.isValid) {
                            game.replacePosition(setupBoard, setupTurn)
                            revision++
                            setupMode = false
                            setupError = null
                        } else {
                            setupError = validation.errors.joinToString("\n")
                        }
                    } else {
                        setupBoard = position.board
                        setupTurn = position.turn
                        setupMode = true
                        setupError = null
                    }
                    selected = null
                },
                onNewGame = {
                    game.reset()
                    setupBoard = game.position.board
                    selected = null
                    setupMode = false
                    revision++
                },
            )

            ChessBoard(
                position = if (setupMode) position.copy(board = setupBoard) else position,
                selected = selected,
                legalTargets = if (setupMode) emptySet() else legalTargets,
                forbiddenTargets = if (setupMode) emptySet() else forbiddenTargets,
                flipped = boardFlipped,
                showGameStatus = !setupMode,
                animatedMove = animatedMove,
                moveAnimationKey = moveAnimationKey,
            ) { square ->
                if (setupMode) {
                    setupBoard = setupBoard.toMutableMap().apply {
                        if (setupPiece == null) remove(square) else put(square, setupPiece!!)
                    }
                    setupError = null
                } else if (selected == square) {
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
                                if (isCapture) vibrateCapture(context)
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

            if (setupMode) {
                setupError?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    )
                }
                SetupControls(
                    selected = setupPiece,
                    turn = setupTurn,
                    onPiece = { setupPiece = it },
                    onTurn = { setupTurn = it },
                    onClear = {
                        setupBoard = emptyMap()
                        setupError = null
                    },
                    onStandard = {
                        setupBoard = Position.initial().board
                        setupError = null
                    },
                )
            } else {
                MoveHistory(history) { ply ->
                    val castleMove = when {
                        ply == game.currentPly - 1 -> game.history.getOrNull(game.currentPly - 1)
                            ?.move?.takeIf(Move::isCastle)?.let { it.copy(from = it.to, to = it.from) }
                        ply == game.currentPly + 1 -> game.history.getOrNull(ply - 1)
                            ?.move?.takeIf(Move::isCastle)
                        else -> null
                    }
                    if (game.goTo(ply)) {
                        animatedMove = castleMove
                        if (castleMove != null) moveAnimationKey++
                        selected = null
                        revision++
                    }
                }
            }

            Spacer(Modifier.weight(1f))
            Text(
                text = "点选棋子，再点目标格。棋谱会自动记录。",
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
                    if (isCapture) vibrateCapture(context)
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
    setupMode: Boolean,
    onFlip: () -> Unit,
    onToggleSetup: () -> Unit,
    onNewGame: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(statusText(position), fontWeight = FontWeight.SemiBold)
        Row {
            TextButton(onClick = onFlip) { Text("翻转") }
            TextButton(onClick = onToggleSetup) { Text(if (setupMode) "完成摆放" else "摆放棋子") }
            TextButton(onClick = onNewGame) { Text("新对局") }
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
    moveAnimationKey: Int = 0,
    verticalPadding: Dp = 8.dp,
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
    val moveProgress = remember(moveAnimationKey) {
        Animatable(if (animatedMove == null) 1f else 0f)
    }
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
    val castleMove = animatedMove?.takeIf(Move::isCastle)
    val reverseCastle = castleMove?.to?.file == 4
    LaunchedEffect(moveAnimationKey) {
        if (castleMove != null) {
            val firstDuration = if (reverseCastle) 180 else 280
            val secondDuration = if (reverseCastle) 280 else 180
            moveProgress.animateTo(1f, animationSpec = tween(durationMillis = firstDuration))
            moveProgress.animateTo(2f, animationSpec = tween(durationMillis = secondDuration))
        } else if (animatedMove != null) {
            moveProgress.animateTo(1f, animationSpec = tween(durationMillis = 220))
        }
        completedMoveKey = moveAnimationKey
    }
    val rookMotion = castleMove?.let { move ->
        val castleFile = if (reverseCastle) move.from.file else move.to.file
        val rookStartFile = if (castleFile == 6) 7 else 0
        val rookEndFile = if (castleFile == 6) 5 else 3
        if (reverseCastle) {
            Square(rookEndFile, move.to.rank) to Square(rookStartFile, move.to.rank)
        } else {
            Square(rookStartFile, move.to.rank) to Square(rookEndFile, move.to.rank)
        }
    }
    val animationEnd = if (castleMove != null) 2f else 1f
    val isAnimating = animatedMove != null && moveProgress.value < animationEnd
    val rookStageActive = isAnimating && castleMove != null &&
        (if (reverseCastle) moveProgress.value < 1f else moveProgress.value >= 1f)
    val kingProgress = if (reverseCastle) {
        (moveProgress.value - 1f).coerceIn(0f, 1f)
    } else {
        moveProgress.value.coerceIn(0f, 1f)
    }
    val rookProgress = if (reverseCastle) {
        moveProgress.value.coerceIn(0f, 1f)
    } else {
        (moveProgress.value - 1f).coerceIn(0f, 1f)
    }
    val ranks = if (flipped) 0..7 else 7 downTo 0
    val files = if (flipped) 7 downTo 0 else 0..7
    val checkedKing = if (showGameStatus && ChessRules.isInCheck(position, position.turn)) {
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
                val movingAcrossThisRow = isAnimating && animatedMove?.to?.rank == rank
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
                            .zIndex(
                                when {
                                    !isAnimating -> 0f
                                    square == animatedMove?.to -> 2f
                                    square == rookMotion?.second -> if (rookStageActive) 3f else 1f
                                    else -> 0f
                                },
                            )
                            .onSizeChanged { cellSize = it }
                            .clickable { onSquare(square) },
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
                        position.board[square]?.let { piece ->
                            val move = animatedMove
                            val motion = when {
                                !isAnimating -> null
                                square == move?.to -> move.from to
                                    (if (castleMove != null) kingProgress else moveProgress.value)
                                rookMotion != null && square == rookMotion.second && piece.type == PieceType.ROOK ->
                                    rookMotion.first to rookProgress
                                else -> null
                            }
                            val fileDirection = if (flipped) -1 else 1
                            val rankDirection = if (flipped) 1 else -1
                            val pieceModifier = if (motion != null) {
                                Modifier.offset {
                                    IntOffset(
                                        x = ((motion.first.file - square.file) * fileDirection * cellSize.width * (1f - motion.second)).toInt(),
                                        y = ((motion.first.rank - square.rank) * rankDirection * cellSize.height * (1f - motion.second)).toInt(),
                                    )
                                }
                            } else {
                                Modifier
                            }
                            pieceImages[piece]?.let { image ->
                                Image(
                                    bitmap = image,
                                    contentDescription = null,
                                    modifier = pieceModifier
                                        .fillMaxSize()
                                        .graphicsLayer(scaleX = 1.08f, scaleY = 1.08f),
                                    contentScale = ContentScale.Fit,
                                )
                            } ?: Text(pieceGlyph(piece), fontSize = 34.sp, modifier = pieceModifier)
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
private fun SetupControls(
    selected: Piece?,
    turn: Color,
    onPiece: (Piece?) -> Unit,
    onTurn: (Color) -> Unit,
    onClear: () -> Unit,
    onStandard: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text("选择棋子后点棋盘放置", fontWeight = FontWeight.Medium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    FilterChip(selected = selected == null, onClick = { onPiece(null) }, label = { Text("擦除") })
                }
                val pieces = Color.entries.flatMap { color -> PieceType.entries.map { Piece(color, it) } }
                items(pieces.size) { index ->
                    val piece = pieces[index]
                    FilterChip(
                        selected = selected == piece,
                        onClick = { onPiece(piece) },
                        label = { Text(pieceGlyph(piece), fontSize = 24.sp) },
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("轮到：")
                FilterChip(turn == Color.WHITE, onClick = { onTurn(Color.WHITE) }, label = { Text("白方") })
                Spacer(Modifier.width(6.dp))
                FilterChip(turn == Color.BLACK, onClick = { onTurn(Color.BLACK) }, label = { Text("黑方") })
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("清空") }
                TextButton(onClick = onStandard) { Text("标准布局") }
            }
        }
    }
}

@Composable
private fun MoveHistory(history: List<PlayedMove>, onGoTo: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("棋谱", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { AssistChip(onClick = { onGoTo(0) }, label = { Text("起始") }) }
            itemsIndexed(history) { index, move ->
                val label = if (index % 2 == 0) "${index / 2 + 1}. ${move.notation}" else move.notation
                AssistChip(onClick = { onGoTo(index + 1) }, label = { Text(label) })
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
