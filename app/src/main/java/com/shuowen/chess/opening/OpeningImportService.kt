package com.shuowen.chess.opening

import java.util.UUID

enum class TextConflictPolicy { KEEP_LOCAL, USE_IMPORTED }

data class ImportPreview(
    val documentCount: Int,
    val newDocumentCount: Int,
    val mergedDocumentCount: Int,
    val textConflictCount: Int,
)

data class ImportResult(
    val documents: MutableList<OpeningDocument>,
    val newDocumentCount: Int,
    val mergedDocumentCount: Int,
    val addedNodeCount: Int,
    val textConflictCount: Int,
)

object OpeningImportService {
    fun preview(local: List<OpeningDocument>, imported: List<OpeningDocument>): ImportPreview {
        val localById = local.associateBy { it.id }
        var merged = 0
        var conflicts = 0
        imported.forEach { incoming ->
            val target = localById[incoming.id]
            if (target != null) {
                merged++
                conflicts += textConflictCount(target, incoming)
            }
        }
        return ImportPreview(imported.size, imported.size - merged, merged, conflicts)
    }

    fun apply(
        local: List<OpeningDocument>,
        imported: List<OpeningDocument>,
        policy: TextConflictPolicy,
    ): ImportResult {
        val result = local.mapTo(mutableListOf(), ::copyDocument)
        var newDocuments = 0
        var mergedDocuments = 0
        var addedNodes = 0
        var conflicts = 0

        imported.forEach { incoming ->
            val target = result.firstOrNull { it.id == incoming.id }
            if (target == null) {
                val usedNames = result.mapTo(mutableSetOf()) { it.name }
                val copied = if (incoming.name in usedNames) {
                    copyDocument(incoming, id = UUID.randomUUID().toString(), name = uniqueImportedName(incoming.name, usedNames))
                } else {
                    copyDocument(incoming)
                }
                result += copied
                newDocuments++
            } else {
                val merge = mergeDocument(target, incoming, policy)
                addedNodes += merge.first
                conflicts += merge.second
                mergedDocuments++
            }
        }
        return ImportResult(result, newDocuments, mergedDocuments, addedNodes, conflicts)
    }

    private fun mergeDocument(
        local: OpeningDocument,
        imported: OpeningDocument,
        policy: TextConflictPolicy,
    ): Pair<Int, Int> {
        var conflicts = 0
        fun mergeText(localValue: String?, importedValue: String?): String? {
            if (localValue.isNullOrEmpty()) return importedValue
            if (importedValue.isNullOrEmpty() || localValue == importedValue) return localValue
            conflicts++
            return if (policy == TextConflictPolicy.USE_IMPORTED) importedValue else localValue
        }

        local.name = mergeText(local.name, imported.name).orEmpty().ifBlank { "未命名开局" }
        local.description = mergeText(local.description, imported.description).orEmpty()
        val localRoot = requireNotNull(local.tree.node(local.tree.rootId))
        val importedRoot = requireNotNull(imported.tree.node(imported.tree.rootId))
        localRoot.label = mergeText(localRoot.label, importedRoot.label)
        localRoot.analysis = mergeText(localRoot.analysis, importedRoot.analysis)
        var added = 0

        fun mergeChildren(localParentId: String, importedParentId: String) {
            imported.tree.children(importedParentId).forEach { incomingChild ->
                val existing = local.tree.children(localParentId).firstOrNull { it.move == incomingChild.move }
                if (existing == null) {
                    local.tree.goTo(localParentId)
                    check(local.tree.play(requireNotNull(incomingChild.move)))
                    val created = local.tree.currentNode
                    created.label = incomingChild.label
                    created.analysis = incomingChild.analysis
                    added++
                    mergeChildren(created.id, incomingChild.id)
                } else {
                    existing.label = mergeText(existing.label, incomingChild.label)
                    existing.analysis = mergeText(existing.analysis, incomingChild.analysis)
                    mergeChildren(existing.id, incomingChild.id)
                }
            }
        }

        val originalCurrent = local.tree.currentNodeId
        mergeChildren(local.tree.rootId, imported.tree.rootId)
        local.tree.goTo(originalCurrent)
        return added to conflicts
    }

    private fun textConflictCount(local: OpeningDocument, imported: OpeningDocument): Int {
        var count = conflict(local.name, imported.name) + conflict(local.description, imported.description)
        val localRoot = requireNotNull(local.tree.node(local.tree.rootId))
        val importedRoot = requireNotNull(imported.tree.node(imported.tree.rootId))
        count += conflict(localRoot.label, importedRoot.label) + conflict(localRoot.analysis, importedRoot.analysis)

        fun visit(localParentId: String, importedParentId: String) {
            imported.tree.children(importedParentId).forEach { incoming ->
                local.tree.children(localParentId).firstOrNull { it.move == incoming.move }?.let { existing ->
                    count += conflict(existing.label, incoming.label) + conflict(existing.analysis, incoming.analysis)
                    visit(existing.id, incoming.id)
                }
            }
        }
        visit(local.tree.rootId, imported.tree.rootId)
        return count
    }

    private fun conflict(first: String?, second: String?): Int =
        if (!first.isNullOrEmpty() && !second.isNullOrEmpty() && first != second) 1 else 0

    private fun copyDocument(source: OpeningDocument, id: String = source.id, name: String = source.name): OpeningDocument {
        val target = OpeningDocument(id = id, name = name, description = source.description)
        val sourceRoot = requireNotNull(source.tree.node(source.tree.rootId))
        target.tree.node(target.tree.rootId)?.apply {
            label = sourceRoot.label
            analysis = sourceRoot.analysis
        }
        val idMap = mutableMapOf(source.tree.rootId to target.tree.rootId)
        fun copyChildren(sourceParentId: String, targetParentId: String) {
            source.tree.children(sourceParentId).forEach { child ->
                target.tree.goTo(targetParentId)
                check(target.tree.play(requireNotNull(child.move)))
                target.tree.currentNode.label = child.label
                target.tree.currentNode.analysis = child.analysis
                idMap[child.id] = target.tree.currentNodeId
                copyChildren(child.id, target.tree.currentNodeId)
            }
        }
        copyChildren(source.tree.rootId, target.tree.rootId)
        target.tree.goTo(idMap[source.tree.currentNodeId] ?: target.tree.rootId)
        return target
    }

    private fun uniqueImportedName(name: String, usedNames: Set<String>): String {
        val first = "$name（导入）"
        if (first !in usedNames) return first
        var index = 2
        while ("$name（导入 $index）" in usedNames) index++
        return "$name（导入 $index）"
    }
}
