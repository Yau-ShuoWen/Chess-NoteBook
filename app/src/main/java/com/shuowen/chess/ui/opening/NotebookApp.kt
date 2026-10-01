package com.shuowen.chess.ui.opening

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import com.shuowen.chess.chess.Move
import com.shuowen.chess.chess.Piece
import com.shuowen.chess.chess.PieceType
import com.shuowen.chess.chess.Square
import com.shuowen.chess.opening.OpeningDocument
import com.shuowen.chess.opening.OpeningNode
import com.shuowen.chess.opening.OpeningRepository
import com.shuowen.chess.opening.displayName
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
    var editNode by remember { mutableStateOf(false) }
    var confirmDeleteNode by remember { mutableStateOf(false) }

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
    val possibleMoves = tree.children().mapNotNull { it.move }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(document.name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
                        Text(
                            statusText(position),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    TextButton(onClick = { flipped = !flipped; selected = null }) { Text("翻转") }
                    TextButton(onClick = { reviewMode = !reviewMode; selected = null }) {
                        Text(if (reviewMode) "录入" else "查看")
                    }
                    TextButton(onClick = { editMetadata = true }) { Text("信息") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            ChessBoard(
                position = position,
                selected = selected,
                legalTargets = if (reviewMode) emptySet() else legalTargets,
                forbiddenTargets = emptySet(),
                flipped = flipped,
                showGameStatus = true,
                possibleMoves = possibleMoves,
                lastMove = tree.currentNode.move,
                verticalPadding = 0.dp,
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
            VariationTree(
                document = document,
                treeRevision = treeRevision,
                currentNodeId = tree.currentNodeId,
                modifier = Modifier.fillMaxWidth().weight(1f),
                onNode = { id ->
                    tree.goTo(id)
                    refresh()
                },
                onEditNode = { id ->
                    tree.goTo(id)
                    refresh()
                    editNode = true
                },
            )
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
    if (editNode) {
        NodeNameDialog(
            standardName = tree.currentNode.notation ?: "起始",
            initialName = tree.currentNode.label ?: if (
                tree.currentNode.move != null &&
                tree.currentNode.position.turn == com.shuowen.chess.chess.Color.WHITE
            ) "对方" else "",
            onDismiss = { editNode = false },
            canDelete = tree.currentNodeId != tree.rootId,
            onDelete = {
                editNode = false
                confirmDeleteNode = true
            },
            onSave = { name ->
                tree.currentNode.label = name.trim().takeIf { it.isNotEmpty() }
                editNode = false
                refresh(save = true, treeChanged = true)
            },
        )
    }
    if (confirmDeleteNode) {
        AlertDialog(
            onDismissRequest = { confirmDeleteNode = false },
            title = { Text("删除这个分支？") },
            text = { Text("这个节点以及它后面的所有节点都会被删除，并且无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteNode = false
                    if (tree.undoBranch()) refresh(save = true, treeChanged = true)
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteNode = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VariationTree(
    document: OpeningDocument,
    treeRevision: Int,
    currentNodeId: String,
    modifier: Modifier = Modifier,
    onNode: (String) -> Unit,
    onEditNode: (String) -> Unit,
) {
    val tree = document.tree
    val layout = remember(document.id, treeRevision) { buildTreeLayout(tree) }
    var pan by remember(document.id) { mutableStateOf(Offset.Zero) }
    var scale by remember(document.id) { mutableFloatStateOf(1f) }
    var viewportSize by remember(document.id) { mutableStateOf(IntSize.Zero) }
    var initiallyPositioned by remember(document.id) { mutableStateOf(false) }
    val density = LocalDensity.current
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 2.5f)
        pan += panChange
    }
    LaunchedEffect(layout, viewportSize) {
        if (!initiallyPositioned && layout.size > 1 && viewportSize != IntSize.Zero) {
            val root = layout.first { it.node.id == tree.rootId }
            val rootCenterX = with(density) {
                (root.row * TREE_COLUMN_WIDTH + TREE_PADDING + TREE_MAX_NODE_WIDTH / 2f).dp.toPx()
            }
            val rootTop = with(density) { (root.depth * TREE_ROW_HEIGHT + 2).dp.toPx() }
            pan = Offset(
                x = viewportSize.width / 2f - rootCenterX,
                y = with(density) { 8.dp.toPx() } - rootTop,
            )
            scale = 1f
            initiallyPositioned = true
        }
    }
    if (layout.size == 1) {
        Box(modifier, contentAlignment = Alignment.Center) {
            TreeNodeCard(
                point = layout.single(),
                currentNodeId = currentNodeId,
                currentPathIds = setOf(tree.rootId),
                onNode = onNode,
                onEditNode = onEditNode,
            )
        }
    } else {
        val diagramWidth = layout.maxOf { point ->
            point.row * TREE_COLUMN_WIDTH + TREE_MAX_NODE_WIDTH + TREE_PADDING * 2
        }.dp
        val diagramHeight = layout.maxOf { point ->
            point.depth * TREE_ROW_HEIGHT + nodeHeight(point.node.displayName) + 6
        }.dp
        val lineColor = MaterialTheme.colorScheme.outline
        val activeLineColor = MaterialTheme.colorScheme.primary
        val pointsById = remember(layout) { layout.associateBy { it.node.id } }
        val currentPathIds = remember(currentNodeId, treeRevision) {
            (tree.pathTo(currentNodeId).map { it.id } + tree.rootId).toSet()
        }
        Box(
            modifier
                .clipToBounds()
                .onSizeChanged { viewportSize = it }
                .transformable(transformState),
        ) {
            Box(
                Modifier
                .graphicsLayer {
                    translationX = pan.x
                    translationY = pan.y
                    scaleX = scale
                    scaleY = scale
                }
                .width(diagramWidth)
                .height(diagramHeight),
            ) {
            Canvas(Modifier.matchParentSize()) {
                layout.filter { it.parentId != null }.forEach { child ->
                    val parent = pointsById.getValue(child.parentId ?: return@forEach)
                    val parentCenterX = (
                        parent.row * TREE_COLUMN_WIDTH + TREE_PADDING + TREE_MAX_NODE_WIDTH / 2f
                    ).dp.toPx()
                    val parentY = (parent.depth * TREE_ROW_HEIGHT + 2).dp.toPx()
                    val childCenterX = (
                        child.row * TREE_COLUMN_WIDTH + TREE_PADDING + TREE_MAX_NODE_WIDTH / 2f
                    ).dp.toPx()
                    val childY = (child.depth * TREE_ROW_HEIGHT + 2).dp.toPx()
                    val start = Offset(
                        parentCenterX,
                        parentY + nodeHeight(parent.node.displayName).dp.toPx(),
                    )
                    val end = Offset(
                        childCenterX,
                        childY,
                    )
                    val verticalHandle = (end.y - start.y) * 0.2f
                    val edgeColor = if (child.node.id in currentPathIds) activeLineColor else lineColor
                    val path = Path().apply {
                        moveTo(start.x, start.y)
                        cubicTo(
                            start.x,
                            start.y + verticalHandle,
                            end.x,
                            end.y - verticalHandle,
                            end.x,
                            end.y,
                        )
                    }
                    drawPath(
                        path = path,
                        color = edgeColor,
                        style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            layout.forEach { point ->
                TreeNodeCard(point, currentNodeId, currentPathIds, onNode, onEditNode)
            }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TreeNodeCard(
    point: TreePoint,
    currentNodeId: String,
    currentPathIds: Set<String>,
    onNode: (String) -> Unit,
    onEditNode: (String) -> Unit,
) {
    val isCurrent = point.node.id == currentNodeId
    val isOnCurrentPath = point.node.id in currentPathIds
    val displayName = point.node.displayName
    val width = nodeWidth(displayName)
    Card(
        modifier = Modifier
            .offset(
                x = (
                    point.row * TREE_COLUMN_WIDTH + TREE_PADDING +
                        (TREE_MAX_NODE_WIDTH - width) / 2f
                ).dp,
                y = (point.depth * TREE_ROW_HEIGHT + 2).dp,
            )
            .width(width.dp)
            .height(nodeHeight(displayName).dp)
            .combinedClickable(
                onClick = { onNode(point.node.id) },
                onDoubleClick = { onEditNode(point.node.id) },
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer
            else if (isOnCurrentPath) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = displayName,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun NodeNameDialog(
    standardName: String,
    initialName: String,
    onDismiss: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑节点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("节点名称") },
                    placeholder = { Text(standardName) },
                    singleLine = true,
                )
                Text(
                    "填写名称后只显示自定义名称；清空则恢复显示 $standardName。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("保存") } },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text("删除节点", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

private const val TREE_COLUMN_WIDTH = 172
private const val TREE_ROW_HEIGHT = 88
private const val TREE_PADDING = 8
private const val TREE_MAX_NODE_WIDTH = 156

private fun nodeWidth(name: String): Int = (name.length * 15 + 24).coerceIn(76, TREE_MAX_NODE_WIDTH)

private fun nodeHeight(name: String): Int = when {
    name.length <= 8 -> 36
    name.length <= 18 -> 54
    else -> 72
}

private data class TreePoint(
    val node: OpeningNode,
    val parentId: String?,
    val depth: Int,
    val row: Float,
)

private fun buildTreeLayout(tree: com.shuowen.chess.opening.OpeningTree): List<TreePoint> {
    val raw = mutableListOf<TreePoint>()
    var nextLeafRow = 0f

    fun place(node: OpeningNode, depth: Int): Float {
        val children = tree.children(node.id)
        val row = if (children.isEmpty()) {
            nextLeafRow.also { nextLeafRow += 1f }
        } else {
            val childRows = children.map { child -> place(child, depth + 1) }
            (childRows.first() + childRows.last()) / 2f
        }
        raw += TreePoint(node, node.parentId, depth, row)
        return row
    }

    val rootRow = place(tree.node(tree.rootId)!!, 0)
    val maxDepth = raw.maxOf { it.depth }.coerceAtLeast(1)
    return raw.groupBy { it.depth }.values.flatMap { level ->
        val depthRatio = level.first().depth.toFloat() / maxDepth
        val compression = 0.35f + 0.65f * depthRatio
        val ordered = level.sortedBy { it.row }
        val desired = ordered.map { rootRow + (it.row - rootRow) * compression }
        val compactRows = mutableListOf<Float>()
        desired.forEachIndexed { index, row ->
            if (index == 0) {
                compactRows += row
            } else {
                val desiredGap = row - desired[index - 1]
                val gap = desiredGap.coerceIn(1f, 1.65f)
                compactRows += compactRows.last() + gap
            }
        }
        val shift = desired.average().toFloat() - compactRows.average().toFloat()
        ordered.mapIndexed { index, point ->
            point.copy(row = compactRows[index] + shift)
        }
    }
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
