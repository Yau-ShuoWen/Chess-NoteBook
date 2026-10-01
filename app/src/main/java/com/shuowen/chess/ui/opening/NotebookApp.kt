package com.shuowen.chess.ui.opening

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuowen.chess.chess.Move
import com.shuowen.chess.chess.Piece
import com.shuowen.chess.chess.PieceType
import com.shuowen.chess.chess.Square
import com.shuowen.chess.opening.OpeningDocument
import com.shuowen.chess.opening.OpeningNode
import com.shuowen.chess.opening.OpeningRepository
import com.shuowen.chess.ui.editor.ChessBoard
import com.shuowen.chess.ui.editor.ChessEditor
import com.shuowen.chess.ui.editor.statusText

@Composable
fun NotebookApp() {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { OpeningRepository(context) }
    val documents = remember { repository.load() }
    var selectedDocument by remember { mutableStateOf<OpeningDocument?>(null) }
    var showBoardTool by remember { mutableStateOf(false) }
    var listRevision by remember { mutableIntStateOf(0) }

    if (showBoardTool) {
        BackHandler { showBoardTool = false }
        ChessEditor()
    } else if (selectedDocument == null) {
        DocumentList(
            documents = documents.toList(),
            revision = listRevision,
            onCreate = {
                val document = OpeningDocument()
                documents += document
                repository.save(documents)
                listRevision++
                selectedDocument = document
            },
            onOpen = { selectedDocument = it },
            onOpenBoardTool = { showBoardTool = true },
        )
    } else {
        OpeningEditor(
            document = selectedDocument!!,
            onSave = {
                repository.save(documents)
                listRevision++
            },
            onBack = {
                repository.save(documents)
                listRevision++
                selectedDocument = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocumentList(
    documents: List<OpeningDocument>,
    revision: Int,
    onCreate: () -> Unit,
    onOpen: (OpeningDocument) -> Unit,
    onOpenBoardTool: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("我的开局") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
                        Text("创建空白棋谱")
                    }
                    TextButton(onClick = onOpenBoardTool, modifier = Modifier.align(Alignment.End)) {
                        Text("打开局面编辑器")
                    }
                }
            }
            if (documents.isEmpty()) {
                item {
                    Text(
                        "还没有棋谱。先创建一个，再一边看视频一边走棋。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(documents, key = { "${it.id}-$revision" }) { document ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(document) }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(document.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (document.description.isNotBlank()) {
                            Text(document.description, modifier = Modifier.padding(top = 5.dp))
                        }
                        Text(
                            "${document.tree.size - 1} 步记录 · ${document.tree.children(document.tree.rootId).size} 个起始分支",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpeningEditor(document: OpeningDocument, onSave: () -> Unit, onBack: () -> Unit) {
    val tree = document.tree
    var screenRevision by remember(document.id) { mutableIntStateOf(0) }
    var treeRevision by remember(document.id) { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Square?>(null) }
    var promotionMoves by remember { mutableStateOf<List<Move>>(emptyList()) }
    var flipped by remember { mutableStateOf(false) }
    var reviewMode by remember { mutableStateOf(false) }
    var editMetadata by remember { mutableStateOf(document.name == "未命名开局" && document.description.isBlank()) }

    fun refresh(save: Boolean = false, treeChanged: Boolean = false) {
        selected = null
        screenRevision++
        if (treeChanged) treeRevision++
        if (save) onSave()
    }

    BackHandler(onBack = onBack)
    val position = remember(screenRevision) { tree.currentPosition }
    val legalTargets = selected?.let { from ->
        com.shuowen.chess.chess.ChessRules.legalMoves(position).filter { it.from == from }.map { it.to }.toSet()
    }.orEmpty()
    val recordedNextMoves = tree.children()
    val nextMoveBadges = recordedNextMoves
        .filter { it.move != null }
        .groupBy { it.move!!.from }
        .mapValues { (_, nodes) -> nodes.joinToString("/") { it.notation.orEmpty() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(document.name) },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = { TextButton(onClick = { editMetadata = true }) { Text("名称与说明") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(statusText(position), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { flipped = !flipped; selected = null }) { Text("翻转") }
                TextButton(onClick = { reviewMode = !reviewMode; selected = null }) {
                    Text(if (reviewMode) "录入" else "查看")
                }
            }
            ChessBoard(
                position = position,
                selected = selected,
                legalTargets = if (reviewMode) emptySet() else legalTargets,
                forbiddenTargets = emptySet(),
                flipped = flipped,
                showGameStatus = true,
                moveBadges = nextMoveBadges,
                lastMove = tree.currentNode.move,
            ) { square ->
                if (reviewMode) return@ChessBoard
                if (selected == square) {
                    selected = null
                } else if (selected == null) {
                    if (position.board[square]?.color == position.turn) selected = square
                } else {
                    val candidates = com.shuowen.chess.chess.ChessRules.legalMoves(position)
                        .filter { it.from == selected && it.to == square }
                    when {
                        candidates.size > 1 -> promotionMoves = candidates
                        candidates.size == 1 -> if (tree.play(candidates.single())) {
                            refresh(save = true, treeChanged = true)
                        }
                        position.board[square]?.color == position.turn -> selected = square
                        else -> selected = null
                    }
                }
            }
            LazyColumn(Modifier.weight(1f)) {
                item {
                    NavigationControls(
                        atRoot = tree.currentNodeId == tree.rootId,
                        nextCount = tree.children().size,
                        reviewMode = reviewMode,
                        onPrevious = { if (tree.back()) refresh() },
                        onNext = {
                            tree.children().singleOrNull()?.let { node ->
                                tree.goTo(node.id)
                                refresh()
                            }
                        },
                        onUndo = { if (tree.undoBranch()) refresh(save = true, treeChanged = true) },
                        onCreateBranch = { reviewMode = false; selected = null },
                    )
                }
                item {
                    Text("分支树", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                    VariationTree(
                        document = document,
                        treeRevision = treeRevision,
                        currentNodeId = tree.currentNodeId,
                    ) { id ->
                        tree.goTo(id)
                        refresh()
                    }
                }
                item { Spacer(Modifier.padding(12.dp)) }
            }
        }
    }

    if (promotionMoves.isNotEmpty()) {
        PromotionChoice(
            moves = promotionMoves,
            turn = position.turn,
            onDismiss = { promotionMoves = emptyList() },
            onSelect = { move ->
                if (tree.play(move)) refresh(save = true, treeChanged = true)
                promotionMoves = emptyList()
            },
        )
    }
    if (editMetadata) {
        MetadataDialog(
            initialName = document.name,
            initialDescription = document.description,
            onDismiss = { editMetadata = false },
            onSave = { name, description ->
                document.name = name.ifBlank { "未命名开局" }
                document.description = description
                editMetadata = false
                screenRevision++
                onSave()
            },
        )
    }
}

@Composable
private fun NavigationControls(
    atRoot: Boolean,
    nextCount: Int,
    reviewMode: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onUndo: () -> Unit,
    onCreateBranch: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = onPrevious, enabled = !atRoot, modifier = Modifier.weight(1f)) { Text("上一步") }
            Button(onClick = onNext, enabled = nextCount == 1, modifier = Modifier.weight(1f)) {
                Text(if (nextCount > 1) "请选择分支" else "下一步")
            }
            TextButton(onClick = onUndo, enabled = !atRoot) { Text("撤销") }
        }
        if (nextCount > 0) {
            TextButton(onClick = onCreateBranch, modifier = Modifier.fillMaxWidth()) {
                Text(if (reviewMode) "从当前位置创建新分支" else "走不同着法即可创建新分支")
            }
        }
        Text(
            if (reviewMode) "查看模式：选择下方候选着法继续。" else "录入模式：在棋盘走棋；回退不会删除已有棋谱。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VariationTree(
    document: OpeningDocument,
    treeRevision: Int,
    currentNodeId: String,
    onNode: (String) -> Unit,
) {
    val tree = document.tree
    val layout = remember(document.id, treeRevision) { buildTreeLayout(tree) }
    if (layout.size == 1) {
        Text("走出第一步后，分支会显示在这里。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        val horizontalScroll = rememberScrollState()
        val maxDepth = layout.maxOf { it.depth }
        val maxRow = layout.maxOf { it.row }
        val diagramWidth = (maxRow * 96 + 88).dp
        val diagramHeight = (maxDepth * 64 + 44).dp
        val lineColor = MaterialTheme.colorScheme.outline
        val activeLineColor = MaterialTheme.colorScheme.primary
        val pointsById = remember(layout) { layout.associateBy { it.node.id } }
        val currentPathIds = remember(currentNodeId, treeRevision) {
            (tree.pathTo(currentNodeId).map { it.id } + tree.rootId).toSet()
        }
        Box(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalScroll)
                .width(diagramWidth)
                .height(diagramHeight),
        ) {
            Canvas(Modifier.matchParentSize()) {
                layout.filter { it.parentId != null }.forEach { child ->
                    val parent = pointsById.getValue(child.parentId ?: return@forEach)
                    val start = Offset(parent.row * 96.dp.toPx() + 42.dp.toPx(), parent.depth * 64.dp.toPx() + 38.dp.toPx())
                    val end = Offset(child.row * 96.dp.toPx() + 42.dp.toPx(), child.depth * 64.dp.toPx() + 2.dp.toPx())
                    val middleY = (start.y + end.y) / 2
                    val edgeColor = if (child.node.id in currentPathIds) activeLineColor else lineColor
                    drawLine(edgeColor, start, Offset(start.x, middleY), strokeWidth = 2.dp.toPx())
                    drawLine(edgeColor, Offset(start.x, middleY), Offset(end.x, middleY), strokeWidth = 2.dp.toPx())
                    drawLine(edgeColor, Offset(end.x, middleY), end, strokeWidth = 2.dp.toPx())
                }
            }
            layout.forEach { point ->
                val isCurrent = point.node.id == currentNodeId
                val isOnCurrentPath = point.node.id in currentPathIds
                Card(
                    modifier = Modifier
                        .offset(x = (point.row * 96 + 4).dp, y = (point.depth * 64 + 2).dp)
                        .width(76.dp)
                        .height(36.dp)
                        .clickable { onNode(point.node.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
                        else if (isOnCurrentPath) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = point.node.notation ?: "起始",
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

private data class TreePoint(
    val node: OpeningNode,
    val parentId: String?,
    val depth: Int,
    val row: Float,
)

private fun buildTreeLayout(tree: com.shuowen.chess.opening.OpeningTree): List<TreePoint> {
    val result = mutableListOf<TreePoint>()
    var nextLeafRow = 0f

    fun place(node: OpeningNode, depth: Int): Float {
        val children = tree.children(node.id)
        val row = if (children.isEmpty()) {
            nextLeafRow.also { nextLeafRow += 1f }
        } else {
            val childRows = children.map { place(it, depth + 1) }
            (childRows.first() + childRows.last()) / 2f
        }
        result += TreePoint(node, node.parentId, depth, row)
        return row
    }

    place(tree.node(tree.rootId)!!, 0)
    return result
}

@Composable
private fun MetadataDialog(
    initialName: String,
    initialDescription: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("名称与说明") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("开局名称") }, singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("说明") }, minLines = 3)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), description.trim()) }) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PromotionChoice(
    moves: List<Move>,
    turn: com.shuowen.chess.chess.Color,
    onDismiss: () -> Unit,
    onSelect: (Move) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择升变棋子") },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                moves.forEach { move ->
                    TextButton(onClick = { onSelect(move) }) {
                        Text(com.shuowen.chess.ui.editor.pieceGlyph(Piece(turn, move.promotion ?: PieceType.QUEEN)))
                    }
                }
            }
        },
        confirmButton = {},
    )
}
