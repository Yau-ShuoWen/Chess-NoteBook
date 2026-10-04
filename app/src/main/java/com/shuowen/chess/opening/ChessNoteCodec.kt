package com.shuowen.chess.opening

import com.shuowen.chess.chess.ChessRules
import com.shuowen.chess.chess.Move
import com.shuowen.chess.chess.PieceType
import com.shuowen.chess.chess.Square
import org.json.JSONArray
import org.json.JSONObject

object ChessNoteCodec {
    const val MIME_TYPE = "application/vnd.shuowen.chess-notebook+json"
    const val EXTENSION = ".chessnote"
    const val MAX_FILE_CHARS = 20 * 1024 * 1024
    const val MAX_DOCUMENTS = 1_000
    const val MAX_NODES_PER_DOCUMENT = 10_000
    const val MAX_TEXT_LENGTH = 100_000

    fun encode(documents: List<OpeningDocument>, exportedAt: String, appVersion: String): String {
        require(documents.size <= MAX_DOCUMENTS) { "棋谱数量过多，无法导出" }
        return JSONObject().apply {
            put("format", "chess-notebook")
            put("version", 1)
            put("exportedAt", exportedAt)
            put("appVersion", appVersion)
            put("documents", JSONArray().apply { documents.forEach { put(encodeDocument(it)) } })
        }.toString(2)
    }

    fun decode(source: String): List<OpeningDocument> = try {
        decodeValidated(source)
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (_: Exception) {
        throw IllegalArgumentException("备份文件结构不完整或已损坏")
    }

    private fun decodeValidated(source: String): List<OpeningDocument> {
        require(source.length <= MAX_FILE_CHARS) { "文件内容过大，无法导入" }
        val root = runCatching { JSONObject(source) }.getOrElse { throw IllegalArgumentException("文件内容不是有效的棋谱备份") }
        require(root.optString("format") == "chess-notebook") { "这不是本应用的棋谱备份文件" }
        val version = root.optInt("version", -1)
        require(version == 1) {
            if (version > 1) "备份文件版本过新，请升级应用后再试" else "不支持这个备份文件版本"
        }
        val array = root.optJSONArray("documents") ?: throw IllegalArgumentException("备份文件缺少棋谱内容")
        require(array.length() <= MAX_DOCUMENTS) { "备份中的棋谱数量过多，无法导入" }
        return List(array.length()) { index -> decodeDocument(array.getJSONObject(index)) }
    }

    private fun encodeDocument(document: OpeningDocument): JSONObject {
        require(document.tree.size <= MAX_NODES_PER_DOCUMENT) { "“${document.name}”的节点过多，无法导出" }
        validateText(document.name)
        validateText(document.description)
        return JSONObject().apply {
            put("id", document.id)
            put("name", document.name)
            put("description", document.description)
            put("currentPath", JSONArray().apply {
                document.tree.pathTo().forEach { node -> put(moveKey(requireNotNull(node.move))) }
            })
            put("root", encodeNode(document.tree, document.tree.rootId, includeMove = false))
        }
    }

    private fun encodeNode(tree: OpeningTree, id: String, includeMove: Boolean): JSONObject {
        val node = requireNotNull(tree.node(id))
        validateText(node.label.orEmpty())
        validateText(node.analysis.orEmpty())
        return JSONObject().apply {
            if (includeMove) put("move", encodeMove(requireNotNull(node.move)))
            putNullable("label", node.label)
            putNullable("analysis", node.analysis)
            put("children", JSONArray().apply {
                tree.children(id).forEach { child -> put(encodeNode(tree, child.id, includeMove = true)) }
            })
        }
    }

    private fun encodeMove(move: Move) = JSONObject().apply {
        put("from", move.from.name)
        put("to", move.to.name)
        putNullable("promotion", move.promotion?.name)
    }

    private fun decodeDocument(json: JSONObject): OpeningDocument {
        val id = requiredText(json, "id", allowBlank = false)
        val name = requiredText(json, "name", allowBlank = true).ifBlank { "未命名开局" }
        val description = json.optString("description", "").also(::validateText)
        val rootJson = json.optJSONObject("root") ?: throw IllegalArgumentException("“$name”缺少分支树")
        val tree = OpeningTree()
        tree.node(tree.rootId)?.apply {
            label = optionalText(rootJson, "label")
            analysis = optionalText(rootJson, "analysis")
        }
        var nodeCount = 1
        val pathIds = mutableMapOf("" to tree.rootId)

        fun decodeChildren(parentJson: JSONObject, parentId: String, parentPath: String) {
            val children = parentJson.optJSONArray("children") ?: JSONArray()
            val siblingMoves = mutableSetOf<String>()
            for (index in 0 until children.length()) {
                nodeCount++
                require(nodeCount <= MAX_NODES_PER_DOCUMENT) { "“$name”的节点过多，无法导入" }
                val childJson = children.getJSONObject(index)
                val requested = decodeMove(childJson.optJSONObject("move")
                    ?: throw IllegalArgumentException("“$name”包含缺少走法的节点"))
                val key = moveKey(requested)
                require(siblingMoves.add(key)) { "“$name”包含重复分支" }
                tree.goTo(parentId)
                val legal = ChessRules.legalMoves(tree.currentPosition).firstOrNull {
                    it.from == requested.from && it.to == requested.to && it.promotion == requested.promotion
                } ?: throw IllegalArgumentException("“$name”包含不合法走法：$key")
                check(tree.play(legal))
                val node = tree.currentNode
                node.label = optionalText(childJson, "label")
                node.analysis = optionalText(childJson, "analysis")
                val path = if (parentPath.isEmpty()) key else "$parentPath/$key"
                pathIds[path] = node.id
                decodeChildren(childJson, node.id, path)
            }
        }

        decodeChildren(rootJson, tree.rootId, "")
        val currentPath = json.optJSONArray("currentPath")
        if (currentPath != null) {
            val path = (0 until currentPath.length()).joinToString("/") { currentPath.getString(it) }
            tree.goTo(pathIds[path] ?: tree.rootId)
        }
        return OpeningDocument(id = id, name = name, description = description, tree = tree)
    }

    private fun decodeMove(json: JSONObject): Move = Move(
        from = decodeSquare(requiredText(json, "from", allowBlank = false)),
        to = decodeSquare(requiredText(json, "to", allowBlank = false)),
        promotion = optionalText(json, "promotion")?.let {
            runCatching { PieceType.valueOf(it) }.getOrElse { throw IllegalArgumentException("升变棋子无效") }
        },
    )

    private fun decodeSquare(value: String): Square {
        require(value.length == 2 && value[0] in 'a'..'h' && value[1] in '1'..'8') { "棋盘坐标无效：$value" }
        return Square(value[0] - 'a', value[1] - '1')
    }

    internal fun moveKey(move: Move): String = buildString {
        append(move.from.name)
        append(move.to.name)
        move.promotion?.let { append(it.name.first().lowercaseChar()) }
    }

    private fun requiredText(json: JSONObject, key: String, allowBlank: Boolean): String {
        require(json.has(key) && !json.isNull(key)) { "备份文件缺少 $key" }
        return json.getString(key).also {
            validateText(it)
            require(allowBlank || it.isNotBlank()) { "备份文件中的 $key 不能为空" }
        }
    }

    private fun optionalText(json: JSONObject, key: String): String? =
        if (!json.has(key) || json.isNull(key)) null else json.getString(key).also(::validateText)

    private fun validateText(value: String) {
        require(value.length <= MAX_TEXT_LENGTH) { "备份中有文字内容过长，无法处理" }
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }
}
