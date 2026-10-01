package com.shuowen.chess.opening

import com.shuowen.chess.chess.ChessRules
import com.shuowen.chess.chess.Move
import com.shuowen.chess.chess.Position
import java.util.UUID

data class OpeningNode(
    val id: String,
    val parentId: String?,
    val move: Move?,
    val notation: String?,
    val position: Position,
    val children: MutableList<String> = mutableListOf(),
    var label: String? = null,
)

val OpeningNode.displayName: String get() = label ?: notation ?: "起始"

class OpeningTree(
    val rootPosition: Position = Position.initial(),
    rootId: String = UUID.randomUUID().toString(),
) {
    val rootId: String = rootId
    private val nodes = linkedMapOf(rootId to OpeningNode(rootId, null, null, null, rootPosition))
    var currentNodeId: String = rootId
        private set

    val currentNode: OpeningNode get() = nodes.getValue(currentNodeId)
    val currentPosition: Position get() = currentNode.position
    val size: Int get() = nodes.size

    fun node(id: String): OpeningNode? = nodes[id]

    fun allNodes(): List<OpeningNode> = nodes.values.toList()

    fun children(id: String = currentNodeId): List<OpeningNode> =
        nodes[id]?.children.orEmpty().mapNotNull(nodes::get)

    fun pathTo(id: String = currentNodeId): List<OpeningNode> {
        val result = mutableListOf<OpeningNode>()
        var cursor = nodes[id]
        while (cursor != null && cursor.parentId != null) {
            result += cursor
            cursor = nodes[cursor.parentId]
        }
        return result.asReversed()
    }

    fun play(move: Move): Boolean {
        val legal = ChessRules.legalMoves(currentPosition).firstOrNull { it == move } ?: return false
        val existing = children().firstOrNull { it.move == legal }
        if (existing != null) {
            currentNodeId = existing.id
            return true
        }
        val id = UUID.randomUUID().toString()
        val child = OpeningNode(
            id = id,
            parentId = currentNodeId,
            move = legal,
            notation = ChessRules.san(currentPosition, legal),
            position = ChessRules.applyMove(currentPosition, legal),
        )
        nodes.getValue(currentNodeId).children += id
        nodes[id] = child
        currentNodeId = id
        return true
    }

    /** Moves to an existing node without changing the tree. */
    fun goTo(id: String): Boolean {
        if (id !in nodes) return false
        currentNodeId = id
        return true
    }

    /** Moves to the parent without deleting the current branch. */
    fun back(): Boolean {
        val parentId = currentNode.parentId ?: return false
        currentNodeId = parentId
        return true
    }

    /** Deletes the current node and its descendants, then moves to its parent. */
    fun undoBranch(): Boolean {
        val removed = currentNode
        val parentId = removed.parentId ?: return false
        nodes[parentId]?.children?.remove(removed.id)
        removeSubtree(removed.id)
        currentNodeId = parentId
        return true
    }

    private fun removeSubtree(id: String) {
        nodes[id]?.children?.toList()?.forEach(::removeSubtree)
        nodes.remove(id)
    }
}

data class OpeningDocument(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "未命名开局",
    var description: String = "",
    val tree: OpeningTree = OpeningTree(),
)
