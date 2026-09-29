package com.shuowen.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as UiColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shuowen.chess.chess.*
import com.shuowen.chess.ui.theme.ChessNotebookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ChessNotebookTheme(dynamicColor = false) { ChessEditor() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChessEditor() {
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
    val position = remember(revision) { game.position }
    val legalTargets = selected?.let { game.legalMoves(it).map(Move::to).toSet() } ?: emptySet()
    val candidateTargets = selected?.let { game.candidateMoves(it).map(Move::to).toSet() } ?: emptySet()
    val forbiddenTargets = candidateTargets - legalTargets
    val history = remember(revision) { game.history.toList() }
    val outsideTapSource = remember { MutableInteractionSource() }

    Scaffold(topBar = { TopAppBar(title = { Text("国际象棋笔记") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).clickable(interactionSource = outsideTapSource, indication = null) { selected = null }
            .padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(statusText(position), fontWeight = FontWeight.SemiBold)
                Row {
                    TextButton(onClick = { boardFlipped = !boardFlipped; selected = null }) { Text("翻转") }
                    TextButton(onClick = {
                        if (setupMode) {
                            val validation = ChessRules.validateSetup(setupBoard)
                            if (validation.isValid) {
                                game.replacePosition(setupBoard, setupTurn); revision++; setupMode = false; setupError = null
                            } else setupError = validation.errors.joinToString("\n")
                        } else {
                            setupBoard = position.board; setupTurn = position.turn; setupMode = true; setupError = null
                        }
                        selected = null
                    }) { Text(if (setupMode) "完成摆放" else "摆放棋子") }
                    TextButton(onClick = { game.reset(); setupBoard = game.position.board; selected = null; setupMode = false; revision++ }) { Text("新对局") }
                }
            }
            ChessBoard(if (setupMode) position.copy(board = setupBoard) else position, selected,
                if (setupMode) emptySet() else legalTargets, if (setupMode) emptySet() else forbiddenTargets, boardFlipped) { square ->
                if (setupMode) { setupBoard = setupBoard.toMutableMap().apply { if (setupPiece == null) remove(square) else put(square, setupPiece!!) }; setupError = null }
                else {
                    val piece = position.board[square]
                    if (selected == square) selected = null
                    else if (selected == null) { if (piece?.color == position.turn) selected = square }
                    else {
                        val candidates = game.legalMoves(selected).filter { it.to == square }
                        when { candidates.size > 1 -> promotionMoves = candidates
                            candidates.size == 1 -> { game.play(candidates.single()); selected = null; revision++ }
                            piece?.color == position.turn -> selected = square
                            else -> selected = null }
                    }
                }
            }
            if (setupMode) {
                setupError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) }
                SetupControls(setupPiece, setupTurn, { setupPiece = it }, { setupTurn = it },
                    { setupBoard = emptyMap(); setupError = null }, { setupBoard = Position.initial().board; setupError = null })
            }
            else MoveHistory(history) { ply -> game.goTo(ply); selected = null; revision++ }
            Spacer(Modifier.weight(1f))
            Text("点选棋子，再点目标格。棋谱会自动记录。", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        }
    }
    if (promotionMoves.isNotEmpty()) AlertDialog(onDismissRequest = { promotionMoves = emptyList() }, title = { Text("选择升变棋子") },
        text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { promotionMoves.forEach { move ->
            Text(pieceGlyph(Piece(position.turn, move.promotion!!)), fontSize = 38.sp, modifier = Modifier.clickable { game.play(move); selected = null; promotionMoves = emptyList(); revision++ }.padding(8.dp))
        } } }, confirmButton = {})
}

@Composable
private fun ChessBoard(position: Position, selected: Square?, legalTargets: Set<Square>, forbiddenTargets: Set<Square>, flipped: Boolean, onSquare: (Square) -> Unit) {
    val ranks = if (flipped) 0..7 else 7 downTo 0
    val files = if (flipped) 7 downTo 0 else 0..7
    Column(Modifier.fillMaxWidth().aspectRatio(1f).padding(vertical = 8.dp)) {
        for (rank in ranks) Row(Modifier.weight(1f)) {
            for (file in files) { val square = Square(file, rank); val color = when {
                square == selected -> UiColor(0xFFE5C65C)
                (file + rank) % 2 == 0 -> UiColor(0xFFF0D9B5); else -> UiColor(0xFFB58863) }
                val isCapture = square in legalTargets && position.board[square] != null
                val displayColor = if (isCapture) when {
                    (file + rank) % 2 == 0 -> UiColor(0xFFC3A77E)
                    else -> UiColor(0xFF815D45)
                } else color
                Box(Modifier.weight(1f).fillMaxHeight().background(displayColor).clickable { onSquare(square) }, contentAlignment = Alignment.Center) {
                    position.board[square]?.let { Text(pieceGlyph(it), fontSize = 34.sp) }
                    if (square in legalTargets && !isCapture) Box(Modifier.size(12.dp).background(UiColor(0x99606060), CircleShape))
                    else if (square in forbiddenTargets) Text("×", color = UiColor(0xFF555555), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SetupControls(selected: Piece?, turn: Color, onPiece: (Piece?) -> Unit, onTurn: (Color) -> Unit, onClear: () -> Unit, onStandard: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) {
        Text("选择棋子后点棋盘放置", fontWeight = FontWeight.Medium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            item { FilterChip(selected == null, onClick = { onPiece(null) }, label = { Text("擦除") }) }
            val pieces = Color.entries.flatMap { color -> PieceType.entries.map { Piece(color, it) } }
            items(pieces.size) { index -> val piece = pieces[index]; FilterChip(selected == piece, onClick = { onPiece(piece) }, label = { Text(pieceGlyph(piece), fontSize = 24.sp) }) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("轮到："); FilterChip(turn == Color.WHITE, onClick = { onTurn(Color.WHITE) }, label = { Text("白方") }); Spacer(Modifier.width(6.dp))
            FilterChip(turn == Color.BLACK, onClick = { onTurn(Color.BLACK) }, label = { Text("黑方") }); Spacer(Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("清空") }; TextButton(onClick = onStandard) { Text("标准布局") }
        }
    } }
}

@Composable
private fun MoveHistory(history: List<PlayedMove>, onGoTo: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) { Text("棋谱", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { AssistChip(onClick = { onGoTo(0) }, label = { Text("起始") }) }
            itemsIndexed(history) { index, move -> AssistChip(onClick = { onGoTo(index + 1) }, label = { Text(if (index % 2 == 0) "${index / 2 + 1}. ${move.notation}" else move.notation) }) }
        }
    }
}

private fun statusText(position: Position) = when {
    ChessRules.isCheckmate(position) -> if (position.turn == Color.WHITE) "白方被将死" else "黑方被将死"
    ChessRules.isStalemate(position) -> "和棋：无子可动"
    ChessRules.isInCheck(position, position.turn) -> if (position.turn == Color.WHITE) "白方被将军" else "黑方被将军"
    position.turn == Color.WHITE -> "白方走棋"; else -> "黑方走棋"
}

private fun pieceGlyph(piece: Piece): String = when (piece.color to piece.type) {
    Color.WHITE to PieceType.KING -> "♔"; Color.WHITE to PieceType.QUEEN -> "♕"; Color.WHITE to PieceType.ROOK -> "♖"
    Color.WHITE to PieceType.BISHOP -> "♗"; Color.WHITE to PieceType.KNIGHT -> "♘"; Color.WHITE to PieceType.PAWN -> "♙"
    Color.BLACK to PieceType.KING -> "♚"; Color.BLACK to PieceType.QUEEN -> "♛"; Color.BLACK to PieceType.ROOK -> "♜"
    Color.BLACK to PieceType.BISHOP -> "♝"; Color.BLACK to PieceType.KNIGHT -> "♞"; Color.BLACK to PieceType.PAWN -> "♟"; else -> ""
}
