package com.shuowen.chess.ui.editor

import com.shuowen.chess.chess.Move
import com.shuowen.chess.chess.Piece
import com.shuowen.chess.chess.PieceType
import com.shuowen.chess.chess.Square

internal data class PieceMotion(
    val from: Square,
    val to: Square,
    val stage: Int,
    val pieceType: PieceType? = null,
)

internal class BoardMoveAnimation(
    val move: Move,
    val stageDurations: List<Int>,
    private val motions: List<PieceMotion>,
) {
    val endProgress: Float get() = stageDurations.size.toFloat()

    fun motionAt(square: Square, piece: Piece): PieceMotion? =
        motions.firstOrNull { it.to == square && (it.pieceType == null || it.pieceType == piece.type) }

    fun fraction(motion: PieceMotion, progress: Float): Float =
        (progress - motion.stage).coerceIn(0f, 1f)

    fun layerAt(square: Square, progress: Float): Float {
        val motion = motions.firstOrNull { it.to == square } ?: return 0f
        return when {
            motion.pieceType == PieceType.ROOK && progress >= motion.stage && progress < motion.stage + 1 -> 3f
            motion.pieceType == PieceType.ROOK -> 1f
            else -> 2f
        }
    }
}

internal fun boardMoveAnimation(move: Move): BoardMoveAnimation {
    if (!move.isCastle) {
        return BoardMoveAnimation(move, listOf(220), listOf(PieceMotion(move.from, move.to, 0)))
    }

    val reverse = move.to.file == 4
    val castleFile = if (reverse) move.from.file else move.to.file
    val rookOriginalFile = if (castleFile == 6) 7 else 0
    val rookCastleFile = if (castleFile == 6) 5 else 3
    val rank = move.to.rank
    val rookFrom = Square(if (reverse) rookCastleFile else rookOriginalFile, rank)
    val rookTo = Square(if (reverse) rookOriginalFile else rookCastleFile, rank)

    return BoardMoveAnimation(
        move = move,
        stageDurations = if (reverse) listOf(180, 280) else listOf(280, 180),
        motions = listOf(
            PieceMotion(move.from, move.to, if (reverse) 1 else 0, PieceType.KING),
            PieceMotion(rookFrom, rookTo, if (reverse) 0 else 1, PieceType.ROOK),
        ),
    )
}
