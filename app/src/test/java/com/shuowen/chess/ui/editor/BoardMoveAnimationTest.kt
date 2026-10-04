package com.shuowen.chess.ui.editor

import com.shuowen.chess.chess.Color
import com.shuowen.chess.chess.Move
import com.shuowen.chess.chess.Piece
import com.shuowen.chess.chess.PieceType
import com.shuowen.chess.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardMoveAnimationTest {
    @Test
    fun castlingMovesKingThenRookAndReversesBothPathsAndOrder() {
        for (rank in listOf(0, 7)) {
            val color = if (rank == 0) Color.WHITE else Color.BLACK
            for (castleFile in listOf(2, 6)) {
                val kingStart = Square(4, rank)
                val kingEnd = Square(castleFile, rank)
                val rookStart = Square(if (castleFile == 6) 7 else 0, rank)
                val rookEnd = Square(if (castleFile == 6) 5 else 3, rank)
                val king = Piece(color, PieceType.KING)
                val rook = Piece(color, PieceType.ROOK)

                val forward = boardMoveAnimation(Move(kingStart, kingEnd, isCastle = true))
                assertEquals(listOf(280, 180), forward.stageDurations)
                assertEquals(PieceMotion(kingStart, kingEnd, 0, PieceType.KING), forward.motionAt(kingEnd, king))
                assertEquals(PieceMotion(rookStart, rookEnd, 1, PieceType.ROOK), forward.motionAt(rookEnd, rook))
                assertEquals(3f, forward.layerAt(rookEnd, 1.5f))

                val reverse = boardMoveAnimation(Move(kingEnd, kingStart, isCastle = true))
                assertEquals(listOf(180, 280), reverse.stageDurations)
                assertEquals(PieceMotion(rookEnd, rookStart, 0, PieceType.ROOK), reverse.motionAt(rookStart, rook))
                assertEquals(PieceMotion(kingEnd, kingStart, 1, PieceType.KING), reverse.motionAt(kingStart, king))
                assertEquals(3f, reverse.layerAt(rookStart, 0.5f))
            }
        }
    }
}
