package com.shuowen.chess.chess

enum class Color { WHITE, BLACK; fun opposite() = if (this == WHITE) BLACK else WHITE }
enum class PieceType { KING, QUEEN, ROOK, BISHOP, KNIGHT, PAWN }
data class Piece(val color: Color, val type: PieceType)
data class Square(val file: Int, val rank: Int) {
    init { require(file in 0..7 && rank in 0..7) }
    val name get() = "${('a'.code + file).toChar()}${rank + 1}"
}
data class Move(val from: Square, val to: Square, val promotion: PieceType? = null, val isEnPassant: Boolean = false, val isCastle: Boolean = false)
data class CastlingRights(val whiteKingSide: Boolean = true, val whiteQueenSide: Boolean = true, val blackKingSide: Boolean = true, val blackQueenSide: Boolean = true)
data class Position(val board: Map<Square, Piece>, val turn: Color = Color.WHITE, val castling: CastlingRights = CastlingRights(), val enPassant: Square? = null, val halfMoveClock: Int = 0, val fullMoveNumber: Int = 1) {
    companion object {
        fun initial(): Position {
            val board = mutableMapOf<Square, Piece>()
            val back = listOf(PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN, PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK)
            for (file in 0..7) {
                board[Square(file, 0)] = Piece(Color.WHITE, back[file]); board[Square(file, 1)] = Piece(Color.WHITE, PieceType.PAWN)
                board[Square(file, 6)] = Piece(Color.BLACK, PieceType.PAWN); board[Square(file, 7)] = Piece(Color.BLACK, back[file])
            }
            return Position(board)
        }
    }
}
data class PlayedMove(val move: Move, val notation: String, val position: Position)

class ChessGame(start: Position = Position.initial()) {
    var position = start; private set
    private val timeline = mutableListOf(start)
    private val moves = mutableListOf<PlayedMove>()
    var currentPly = 0
        private set
    val history: List<PlayedMove> get() = moves
    fun reset(position: Position = Position.initial()) { this.position = position; timeline.clear(); timeline += position; moves.clear(); currentPly = 0 }
    fun replacePosition(board: Map<Square, Piece>, turn: Color) {
        fun has(square: Square, piece: Piece) = board[square] == piece
        val whiteKing = has(Square(4, 0), Piece(Color.WHITE, PieceType.KING))
        val blackKing = has(Square(4, 7), Piece(Color.BLACK, PieceType.KING))
        val rights = CastlingRights(
            whiteKingSide = whiteKing && has(Square(7, 0), Piece(Color.WHITE, PieceType.ROOK)),
            whiteQueenSide = whiteKing && has(Square(0, 0), Piece(Color.WHITE, PieceType.ROOK)),
            blackKingSide = blackKing && has(Square(7, 7), Piece(Color.BLACK, PieceType.ROOK)),
            blackQueenSide = blackKing && has(Square(0, 7), Piece(Color.BLACK, PieceType.ROOK)),
        )
        reset(Position(board, turn, rights))
    }
    fun goTo(ply: Int): Boolean {
        if (ply !in timeline.indices) return false
        currentPly = ply
        position = timeline[ply]
        return true
    }
    fun legalMoves(from: Square? = null) = ChessRules.legalMoves(position).let { all -> if (from == null) all else all.filter { it.from == from } }
    fun candidateMoves(from: Square) = ChessRules.candidateMoves(position, from)
    fun play(move: Move): Boolean {
        val legal = legalMoves().firstOrNull { it == move } ?: return false
        if (currentPly < moves.size) {
            while (moves.size > currentPly) moves.removeLast()
            while (timeline.size > currentPly + 1) timeline.removeLast()
        }
        val notation = ChessRules.san(position, legal); position = ChessRules.applyMove(position, legal)
        timeline += position; moves += PlayedMove(legal, notation, position); currentPly++
        return true
    }
}

object ChessRules {
    private val diagonals = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val straights = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)

    fun candidateMoves(position: Position, from: Square): List<Move> =
        if (position.board[from]?.color == position.turn) pseudoMoves(position, from) else emptyList()

    fun legalMoves(position: Position): List<Move> = position.board.filterValues { it.color == position.turn }
        .flatMap { (square, _) -> candidateMoves(position, square) }
        .filter { move -> castlePathIsSafe(position, move) && !isInCheck(applyMove(position, move, false), position.turn) }
    fun isInCheck(position: Position, color: Color): Boolean = position.board.entries.firstOrNull { it.value == Piece(color, PieceType.KING) }?.key?.let { isAttacked(position, it, color.opposite()) } ?: false
    fun isCheckmate(position: Position) = isInCheck(position, position.turn) && legalMoves(position).isEmpty()
    fun isStalemate(position: Position) = !isInCheck(position, position.turn) && legalMoves(position).isEmpty()

    fun san(position: Position, move: Move): String {
        val piece = position.board.getValue(move.from); val captured = position.board[move.to] != null || move.isEnPassant
        val base = if (move.isCastle) if (move.to.file == 6) "O-O" else "O-O-O" else {
            val letter = pieceLetter(piece.type)
            val same = legalMoves(position).filter { it != move && it.to == move.to && position.board[it.from]?.type == piece.type }
            val disambiguation = when {
                piece.type == PieceType.PAWN && captured -> "${('a'.code + move.from.file).toChar()}"
                same.isEmpty() -> ""
                same.none { it.from.file == move.from.file } -> "${('a'.code + move.from.file).toChar()}"
                same.none { it.from.rank == move.from.rank } -> "${move.from.rank + 1}"
                else -> move.from.name
            }
            "$letter$disambiguation${if (captured) "x" else ""}${move.to.name}${move.promotion?.let { "=${pieceLetter(it)}" } ?: ""}"
        }
        val next = applyMove(position, move)
        return base + when { isCheckmate(next) -> "#"; isInCheck(next, next.turn) -> "+"; else -> "" }
    }

    fun applyMove(position: Position, move: Move, switchTurn: Boolean = true): Position {
        val board = position.board.toMutableMap(); val piece = board.remove(move.from) ?: return position
        if (move.isEnPassant) board.remove(Square(move.to.file, move.from.rank))
        if (move.isCastle) {
            val rookFrom = Square(if (move.to.file == 6) 7 else 0, move.from.rank); val rookTo = Square(if (move.to.file == 6) 5 else 3, move.from.rank)
            board.remove(rookFrom)?.let { board[rookTo] = it }
        }
        board[move.to] = if (piece.type == PieceType.PAWN && move.to.rank in listOf(0, 7)) piece.copy(type = move.promotion ?: PieceType.QUEEN) else piece
        val captured = position.board[move.to]; var rights = position.castling
        if (piece.type == PieceType.KING) rights = if (piece.color == Color.WHITE) rights.copy(whiteKingSide = false, whiteQueenSide = false) else rights.copy(blackKingSide = false, blackQueenSide = false)
        if (piece.type == PieceType.ROOK) rights = removeRookRight(rights, move.from)
        if (captured?.type == PieceType.ROOK) rights = removeRookRight(rights, move.to)
        val ep = if (piece.type == PieceType.PAWN && kotlin.math.abs(move.to.rank - move.from.rank) == 2) Square(move.from.file, (move.from.rank + move.to.rank) / 2) else null
        return Position(board, if (switchTurn) position.turn.opposite() else position.turn, rights, ep,
            if (piece.type == PieceType.PAWN || captured != null || move.isEnPassant) 0 else position.halfMoveClock + 1,
            position.fullMoveNumber + if (switchTurn && position.turn == Color.BLACK) 1 else 0)
    }

    private fun removeRookRight(r: CastlingRights, s: Square) = when (s) {
        Square(0, 0) -> r.copy(whiteQueenSide = false); Square(7, 0) -> r.copy(whiteKingSide = false)
        Square(0, 7) -> r.copy(blackQueenSide = false); Square(7, 7) -> r.copy(blackKingSide = false); else -> r
    }
    private fun pseudoMoves(p: Position, from: Square): List<Move> { val piece = p.board[from] ?: return emptyList(); return when (piece.type) {
        PieceType.PAWN -> pawnMoves(p, from, piece.color)
        PieceType.KNIGHT -> jumpMoves(p, from, piece.color, listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2))
        PieceType.BISHOP -> slideMoves(p, from, piece.color, diagonals); PieceType.ROOK -> slideMoves(p, from, piece.color, straights)
        PieceType.QUEEN -> slideMoves(p, from, piece.color, diagonals + straights); PieceType.KING -> kingMoves(p, from, piece.color)
    } }
    private fun pawnMoves(p: Position, from: Square, color: Color): List<Move> {
        val result = mutableListOf<Move>(); val direction = if (color == Color.WHITE) 1 else -1; val start = if (color == Color.WHITE) 1 else 6; val promotionRank = if (color == Color.WHITE) 7 else 0
        fun add(to: Square, ep: Boolean = false) { if (to.rank == promotionRank) listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { result += Move(from, to, it, ep) } else result += Move(from, to, isEnPassant = ep) }
        square(from.file, from.rank + direction)?.let { one -> if (p.board[one] == null) { add(one); square(from.file, from.rank + 2 * direction)?.let { two -> if (from.rank == start && p.board[two] == null) result += Move(from, two) } } }
        for (df in listOf(-1, 1)) square(from.file + df, from.rank + direction)?.let { to -> if (p.board[to]?.color == color.opposite()) add(to) else if (to == p.enPassant) add(to, true) }
        return result
    }
    private fun jumpMoves(p: Position, from: Square, color: Color, offsets: List<Pair<Int, Int>>) = offsets.mapNotNull { (df, dr) -> square(from.file + df, from.rank + dr)?.takeIf { p.board[it]?.color != color }?.let { Move(from, it) } }
    private fun slideMoves(p: Position, from: Square, color: Color, directions: List<Pair<Int, Int>>): List<Move> { val result = mutableListOf<Move>(); for ((df, dr) in directions) for (step in 1..7) { val to = square(from.file + df * step, from.rank + dr * step) ?: break; val target = p.board[to]; if (target?.color == color) break; result += Move(from, to); if (target != null) break }; return result }
    private fun kingMoves(p: Position, from: Square, color: Color): List<Move> {
        val moves = jumpMoves(p, from, color, diagonals + straights).toMutableList(); val rank = if (color == Color.WHITE) 0 else 7
        if (from == Square(4, rank)) {
            val ks = if (color == Color.WHITE) p.castling.whiteKingSide else p.castling.blackKingSide
            if (ks && p.board[Square(5, rank)] == null && p.board[Square(6, rank)] == null && p.board[Square(7, rank)] == Piece(color, PieceType.ROOK)) moves += Move(from, Square(6, rank), isCastle = true)
            val qs = if (color == Color.WHITE) p.castling.whiteQueenSide else p.castling.blackQueenSide
            if (qs && p.board[Square(1, rank)] == null && p.board[Square(2, rank)] == null && p.board[Square(3, rank)] == null && p.board[Square(0, rank)] == Piece(color, PieceType.ROOK)) moves += Move(from, Square(2, rank), isCastle = true)
        }; return moves
    }
    private fun castlePathIsSafe(p: Position, move: Move): Boolean {
        if (!move.isCastle) return true
        val color = p.board[move.from]?.color ?: return false
        if (isInCheck(p, color)) return false
        val middleFile = if (move.to.file == 6) 5 else 3
        return !isAttacked(p, Square(middleFile, move.from.rank), color.opposite())
    }
    private fun isAttacked(p: Position, target: Square, by: Color): Boolean {
        val pawnDirection = if (by == Color.WHITE) 1 else -1
        for (df in listOf(-1, 1)) square(target.file - df, target.rank - pawnDirection)?.let { if (p.board[it] == Piece(by, PieceType.PAWN)) return true }
        val knightOffsets = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)
        if (knightOffsets.any { (df, dr) -> square(target.file + df, target.rank + dr)?.let { p.board[it] } == Piece(by, PieceType.KNIGHT) }) return true
        if ((diagonals + straights).any { (df, dr) -> (1..7).firstNotNullOfOrNull { step -> square(target.file + df * step, target.rank + dr * step)?.let { p.board[it] } }?.let { it.color == by && (it.type == PieceType.QUEEN || it.type == PieceType.BISHOP && df != 0 && dr != 0 || it.type == PieceType.ROOK && (df == 0 || dr == 0)) } == true }) return true
        return (diagonals + straights).any { (df, dr) -> square(target.file + df, target.rank + dr)?.let { p.board[it] } == Piece(by, PieceType.KING) }
    }
    private fun square(file: Int, rank: Int) = if (file in 0..7 && rank in 0..7) Square(file, rank) else null
    private fun pieceLetter(type: PieceType) = when (type) { PieceType.KING -> "K"; PieceType.QUEEN -> "Q"; PieceType.ROOK -> "R"; PieceType.BISHOP -> "B"; PieceType.KNIGHT -> "N"; PieceType.PAWN -> "" }
}
