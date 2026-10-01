package com.shuowen.chess

import com.shuowen.chess.chess.Square
import com.shuowen.chess.opening.OpeningTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningTreeTest {
    @Test
    fun back_keeps_branch_and_reentering_move_reuses_it() {
        val tree = OpeningTree()
        val e4 = tree.currentPosition.let { position ->
            com.shuowen.chess.chess.ChessRules.legalMoves(position)
                .single { it.from == Square(4, 1) && it.to == Square(4, 3) }
        }

        assertTrue(tree.play(e4))
        val e4Node = tree.currentNodeId
        assertTrue(tree.back())
        assertEquals(2, tree.size)
        assertTrue(tree.play(e4))
        assertEquals(e4Node, tree.currentNodeId)
        assertEquals(2, tree.size)
    }

    @Test
    fun playing_from_old_node_creates_a_sibling_branch() {
        val tree = OpeningTree()
        val moves = com.shuowen.chess.chess.ChessRules.legalMoves(tree.currentPosition)
        val e4 = moves.single { it.from == Square(4, 1) && it.to == Square(4, 3) }
        val d4 = moves.single { it.from == Square(3, 1) && it.to == Square(3, 3) }

        tree.play(e4)
        tree.back()
        tree.play(d4)

        assertEquals(2, tree.children(tree.rootId).size)
    }

    @Test
    fun undo_deletes_only_current_subtree() {
        val tree = OpeningTree()
        val e4 = com.shuowen.chess.chess.ChessRules.legalMoves(tree.currentPosition)
            .single { it.from == Square(4, 1) && it.to == Square(4, 3) }
        tree.play(e4)
        val c5 = com.shuowen.chess.chess.ChessRules.legalMoves(tree.currentPosition)
            .single { it.from == Square(2, 6) && it.to == Square(2, 4) }
        tree.play(c5)

        assertTrue(tree.undoBranch())
        assertEquals(2, tree.size)
        assertEquals("e4", tree.currentNode.notation)
        assertFalse(tree.undoBranch().not())
        assertEquals(1, tree.size)
    }
}
