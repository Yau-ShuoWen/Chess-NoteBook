package com.shuowen.chess.opening

import android.content.Context
import com.shuowen.chess.chess.Move
import com.shuowen.chess.chess.PieceType
import com.shuowen.chess.chess.Square
import org.json.JSONArray
import org.json.JSONObject

class OpeningRepository(context: Context) {
    private val preferences = context.getSharedPreferences("opening_notebook", Context.MODE_PRIVATE)

    fun load(): MutableList<OpeningDocument> {
        val source = preferences.getString(KEY_DOCUMENTS, null) ?: return mutableListOf()
        return runCatching {
            val array = JSONArray(source)
            MutableList(array.length()) { index -> decodeDocument(array.getJSONObject(index)) }
        }.getOrDefault(mutableListOf())
    }

    fun save(documents: List<OpeningDocument>) {
        val array = JSONArray()
        documents.forEach { document -> array.put(encodeDocument(document)) }
        preferences.edit().putString(KEY_DOCUMENTS, array.toString()).apply()
    }

    private fun encodeDocument(document: OpeningDocument) = JSONObject().apply {
        put("id", document.id)
        put("name", document.name)
        put("description", document.description)
        put("current", document.tree.currentNodeId)
        put("nodes", JSONArray().apply {
            document.tree.allNodes().filter { it.move != null }.forEach { node ->
                put(JSONObject().apply {
                    put("id", node.id)
                    put("parent", node.parentId)
                    put("fromFile", node.move!!.from.file)
                    put("fromRank", node.move.from.rank)
                    put("toFile", node.move.to.file)
                    put("toRank", node.move.to.rank)
                    put("promotion", node.move.promotion?.name)
                    put("enPassant", node.move.isEnPassant)
                    put("castle", node.move.isCastle)
                })
            }
        })
    }

    private fun decodeDocument(json: JSONObject): OpeningDocument {
        val tree = OpeningTree()
        val idMap = mutableMapOf<String, String>()
        val nodes = json.getJSONArray("nodes")
        for (index in 0 until nodes.length()) {
            val node = nodes.getJSONObject(index)
            val oldParent = node.getString("parent")
            val newParent = idMap[oldParent] ?: tree.rootId
            tree.goTo(newParent)
            val promotion = node.optString("promotion").takeIf { it.isNotBlank() && it != "null" }
                ?.let(PieceType::valueOf)
            val move = Move(
                from = Square(node.getInt("fromFile"), node.getInt("fromRank")),
                to = Square(node.getInt("toFile"), node.getInt("toRank")),
                promotion = promotion,
                isEnPassant = node.optBoolean("enPassant"),
                isCastle = node.optBoolean("castle"),
            )
            if (tree.play(move)) idMap[node.getString("id")] = tree.currentNodeId
        }
        val oldCurrent = json.optString("current")
        tree.goTo(idMap[oldCurrent] ?: tree.rootId)
        return OpeningDocument(
            id = json.getString("id"),
            name = json.optString("name", "未命名开局"),
            description = json.optString("description"),
            tree = tree,
        )
    }

    private companion object {
        const val KEY_DOCUMENTS = "documents"
    }
}
