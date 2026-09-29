package com.shuowen.chess

import com.shuowen.chess.chess.ChessGame
import com.shuowen.chess.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessGameHistoryTest {
    @Test
    fun historyCanBeVisitedRepeatedlyAndOnlyTruncatesWhenANewMoveIsPlayed() {
        val game = ChessGame()
        play(game, "e2", "e4")
        play(game, "e7", "e5")
        play(game, "g1", "f3")

        assertTrue(game.goTo(0))
        assertTrue(game.goTo(2))
        assertTrue(game.goTo(1))
        assertEquals(3, game.history.size)

        play(game, "c7", "c5")
        assertEquals(2, game.history.size)
        assertEquals("c5", game.history.last().notation)
    }

    private fun play(game: ChessGame, from: String, to: String) {
        val move = game.legalMoves(square(from)).first { it.to == square(to) }
        assertTrue(game.play(move))
    }

    private fun square(name: String) = Square(name[0] - 'a', name[1].digitToInt() - 1)
}
