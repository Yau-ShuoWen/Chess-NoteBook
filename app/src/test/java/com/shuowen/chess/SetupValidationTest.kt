package com.shuowen.chess

import com.shuowen.chess.chess.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupValidationTest {
    @Test
    fun standardPositionIsValid() {
        assertTrue(ChessRules.validateSetup(Position.initial().board).isValid)
    }

    @Test
    fun missingKingPawnOnLastRankAndAttackedKingAreRejected() {
        val missingKing = mapOf(Square(4, 0) to Piece(Color.WHITE, PieceType.KING))
        assertFalse(ChessRules.validateSetup(missingKing).isValid)

        val pawnOnLastRank = mapOf(
            Square(4, 0) to Piece(Color.WHITE, PieceType.KING),
            Square(4, 7) to Piece(Color.BLACK, PieceType.KING),
            Square(0, 7) to Piece(Color.WHITE, PieceType.PAWN),
        )
        assertFalse(ChessRules.validateSetup(pawnOnLastRank).isValid)

        val checkedKing = mapOf(
            Square(4, 0) to Piece(Color.WHITE, PieceType.KING),
            Square(0, 7) to Piece(Color.BLACK, PieceType.KING),
            Square(4, 7) to Piece(Color.BLACK, PieceType.ROOK),
        )
        assertFalse(ChessRules.validateSetup(checkedKing).isValid)
    }

    @Test
    fun sameColorBishopsRequireAMissingPawnForPromotion() {
        fun board(pawnCount: Int) = buildMap {
            put(Square(0, 0), Piece(Color.WHITE, PieceType.KING))
            put(Square(7, 7), Piece(Color.BLACK, PieceType.KING))
            put(Square(2, 0), Piece(Color.WHITE, PieceType.BISHOP))
            put(Square(4, 2), Piece(Color.WHITE, PieceType.BISHOP))
            repeat(pawnCount) { put(Square(it, 1), Piece(Color.WHITE, PieceType.PAWN)) }
        }

        assertFalse(ChessRules.validateSetup(board(8)).isValid)
        assertTrue(ChessRules.validateSetup(board(7)).isValid)
    }
}
