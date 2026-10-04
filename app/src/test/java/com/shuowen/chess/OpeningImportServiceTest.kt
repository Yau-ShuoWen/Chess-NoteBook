package com.shuowen.chess

import com.shuowen.chess.chess.ChessRules
import com.shuowen.chess.chess.Square
import com.shuowen.chess.opening.OpeningDocument
import com.shuowen.chess.opening.OpeningImportService
import com.shuowen.chess.opening.TextConflictPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class OpeningImportServiceTest {
    @Test
    fun same_document_merges_unique_branches() {
        val local = OpeningDocument(id = "same", name = "测试")
        play(local, "e2", "e4")
        val imported = OpeningDocument(id = "same", name = "测试")
        play(imported, "d2", "d4")

        val result = OpeningImportService.apply(listOf(local), listOf(imported), TextConflictPolicy.KEEP_LOCAL)

        assertEquals(1, result.documents.size)
        assertEquals(1, result.mergedDocumentCount)
        assertEquals(1, result.addedNodeCount)
        assertEquals(2, result.documents.single().tree.children(result.documents.single().tree.rootId).size)
    }

    @Test
    fun text_conflict_uses_selected_policy() {
        val local = OpeningDocument(id = "same", name = "本地名称", description = "本地说明")
        val imported = OpeningDocument(id = "same", name = "导入名称", description = "导入说明")

        val preview = OpeningImportService.preview(listOf(local), listOf(imported))
        val keepLocal = OpeningImportService.apply(listOf(local), listOf(imported), TextConflictPolicy.KEEP_LOCAL)
        val useImported = OpeningImportService.apply(listOf(local), listOf(imported), TextConflictPolicy.USE_IMPORTED)

        assertEquals(2, preview.textConflictCount)
        assertEquals("本地名称", keepLocal.documents.single().name)
        assertEquals("本地说明", keepLocal.documents.single().description)
        assertEquals("导入名称", useImported.documents.single().name)
        assertEquals("导入说明", useImported.documents.single().description)
    }

    @Test
    fun different_document_with_same_name_is_renamed_without_changing_local() {
        val local = OpeningDocument(id = "local", name = "伦敦体系")
        val imported = OpeningDocument(id = "imported", name = "伦敦体系")

        val result = OpeningImportService.apply(listOf(local), listOf(imported), TextConflictPolicy.KEEP_LOCAL)

        assertEquals(listOf("伦敦体系", "伦敦体系（导入）"), result.documents.map { it.name })
        assertNotEquals("imported", result.documents.last().id)
    }

    private fun play(document: OpeningDocument, from: String, to: String) {
        val legal = ChessRules.legalMoves(document.tree.currentPosition).single {
            it.from == square(from) && it.to == square(to)
        }
        document.tree.play(legal)
    }

    private fun square(name: String) = Square(name[0] - 'a', name[1] - '1')
}
