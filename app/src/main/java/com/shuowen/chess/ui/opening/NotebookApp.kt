package com.shuowen.chess.ui.opening

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.shuowen.chess.opening.displayName
import com.shuowen.chess.ui.appearance.AppearancePack
import com.shuowen.chess.ui.appearance.ChessAppearanceController
import com.shuowen.chess.ui.appearance.LocalChessAppearance
import com.shuowen.chess.ui.appearance.rememberChessAppearanceController
import com.shuowen.chess.ui.editor.ChessBoard
import com.shuowen.chess.ui.editor.ChessEditor
import com.shuowen.chess.ui.editor.statusText
import com.shuowen.chess.ui.editor.vibrateCapture

@Composable
fun NotebookApp() {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { OpeningRepository(context) }
    val documents = remember { repository.load() }
    var selectedDocument by remember { mutableStateOf<OpeningDocument?>(null) }
    var showBoardTool by remember { mutableStateOf(false) }
    var showAppearance by remember { mutableStateOf(false) }
    var listRevision by remember { mutableIntStateOf(0) }
    val appearanceController = rememberChessAppearanceController()

    CompositionLocalProvider(LocalChessAppearance provides appearanceController.appearance) {
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
                onOpenAppearance = { showAppearance = true },
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

        if (showAppearance) {
            AppearanceDialog(
                controller = appearanceController,
                onDismiss = { showAppearance = false },
            )
        }
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
    onOpenAppearance: () -> Unit,
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
                    Row(modifier = Modifier.align(Alignment.End)) {
                        TextButton(onClick = onOpenAppearance) { Text("外观") }
                        TextButton(onClick = onOpenBoardTool) { Text("打开局面编辑器") }
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

@Composable
private fun AppearanceDialog(
    controller: ChessAppearanceController,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("棋盘主题") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppearanceOptions(
                    packs = controller.themePacks,
                    selectedId = controller.selectedThemeId,
                    onSelect = controller::selectTheme,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

@Composable
private fun AppearanceOptions(
    packs: List<AppearancePack>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        packs.forEach { pack ->
            FilterChip(
                selected = pack.id == selectedId,
                onClick = { onSelect(pack.id) },
                label = { Text(pack.name) },
            )
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
    var reviewPromotionNodes by remember { mutableStateOf<List<OpeningNode>>(emptyList()) }
    var reviewBranchChoices by remember(document.id) { mutableStateOf(emptyList<ReviewBranchChoice>()) }
    var flipped by remember { mutableStateOf(false) }
    var reviewMode by remember { mutableStateOf(true) }
    var editMetadata by remember { mutableStateOf(document.name == "未命名开局" && document.description.isBlank()) }
    var editNode by remember { mutableStateOf(false) }
    var confirmDeleteNode by remember { mutableStateOf(false) }
    var animatedMove by remember { mutableStateOf<Move?>(null) }
    var moveAnimationKey by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    fun refresh(save: Boolean = false, treeChanged: Boolean = false) {
        selected = null
        screenRevision++
        if (treeChanged) treeRevision++
        if (save) onSave()
    }

    BackHandler(onBack = onBack)
    val position = remember(screenRevision) { tree.currentPosition }
    val childNodes = tree.children()
    val possibleMoves = childNodes.mapNotNull { it.move }
    val legalTargets = selected?.let { from ->
        if (reviewMode) {
            possibleMoves.filter { it.from == from }.map { it.to }.toSet()
        } else {
            com.shuowen.chess.chess.ChessRules.legalMoves(position)
                .filter { it.from == from }
                .map { it.to }
                .toSet()
        }
    }.orEmpty()

    fun playMove(move: Move) {
        val isCapture = position.board[move.to] != null || move.isEnPassant
        if (tree.play(move)) {
            if (isCapture) vibrateCapture(context)
            animatedMove = move
            moveAnimationKey++
            refresh(save = true, treeChanged = true)
        }
    }

    fun navigateReview(nodeId: String, rememberBranchChoice: Boolean = false) {
        val currentNode = tree.currentNode
        val targetNode = tree.node(nodeId) ?: return
        if (rememberBranchChoice && childNodes.size > 1) {
            reviewBranchChoices = reviewBranchChoices + ReviewBranchChoice(
                branchNodeId = currentNode.id,
                selectedPathName = targetNode.displayName,
            )
        }
        val navigationMove = when {
            targetNode.parentId == currentNode.id -> targetNode.move
            currentNode.parentId == targetNode.id -> currentNode.move?.let { move ->
                move.copy(from = move.to, to = move.from)
            }
            else -> null
        }
        tree.goTo(nodeId)
        animatedMove = navigationMove
        if (navigationMove != null) moveAnimationKey++
        refresh()
    }

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
                    TextButton(onClick = {
                        reviewMode = !reviewMode
                        selected = null
                        promotionMoves = emptyList()
                        reviewPromotionNodes = emptyList()
                    }) {
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
                legalTargets = legalTargets,
                forbiddenTargets = emptySet(),
                flipped = flipped,
                showGameStatus = true,
                possibleMoves = possibleMoves,
                lastMove = tree.currentNode.move,
                animatedMove = animatedMove,
                moveAnimationKey = moveAnimationKey,
                verticalPadding = 0.dp,
            ) { square ->
                if (selected == square) {
                    selected = null
                } else if (selected == null) {
                    if (reviewMode) {
                        if (possibleMoves.any { it.from == square }) selected = square
                    } else if (position.board[square]?.color == position.turn) {
                        selected = square
                    }
                } else if (reviewMode) {
                    val candidates = childNodes.filter { node ->
                        node.move?.let { it.from == selected && it.to == square } == true
                    }
                    when {
                        candidates.size > 1 -> reviewPromotionNodes = candidates
                        candidates.size == 1 -> {
                            navigateReview(candidates.single().id, rememberBranchChoice = true)
                        }
                        possibleMoves.any { it.from == square } -> selected = square
                        else -> selected = null
                    }
                } else {
                    val candidates = com.shuowen.chess.chess.ChessRules.legalMoves(position)
                        .filter { it.from == selected && it.to == square }
                    when {
                        candidates.size > 1 -> promotionMoves = candidates
                        candidates.size == 1 -> playMove(candidates.single())
                        position.board[square]?.color == position.turn -> selected = square
                        else -> selected = null
                    }
                }
            }
            if (reviewMode) {
                ReviewNavigator(
                    tree = tree,
                    currentNodeId = tree.currentNodeId,
                    branchChoices = reviewBranchChoices,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    onBranchChoicesChange = { reviewBranchChoices = it },
                    onNode = { navigateReview(it) },
                    onNextNode = { navigateReview(it, rememberBranchChoice = true) },
                )
            } else {
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
    }

    if (promotionMoves.isNotEmpty()) {
        PromotionChoice(
            moves = promotionMoves,
            turn = position.turn,
            onDismiss = { promotionMoves = emptyList() },
            onSelect = { move ->
                playMove(move)
                promotionMoves = emptyList()
            },
        )
    }
    if (reviewPromotionNodes.isNotEmpty()) {
        PromotionChoice(
            moves = reviewPromotionNodes.mapNotNull { it.move },
            turn = position.turn,
            onDismiss = { reviewPromotionNodes = emptyList() },
            onSelect = { move ->
                reviewPromotionNodes.firstOrNull { it.move == move }?.let { nextNode ->
                    navigateReview(nextNode.id, rememberBranchChoice = true)
                }
                reviewPromotionNodes = emptyList()
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
            initialAnalysis = tree.currentNode.analysis.orEmpty(),
            onDismiss = { editNode = false },
            canDelete = tree.currentNodeId != tree.rootId,
            onDelete = {
                editNode = false
                confirmDeleteNode = true
            },
            onSave = { name, analysis ->
                tree.currentNode.label = name.trim().takeIf { it.isNotEmpty() }
                tree.currentNode.analysis = analysis.trim().takeIf { it.isNotEmpty() }
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

@Composable
private fun NodeNameDialog(
    standardName: String,
    initialName: String,
    initialAnalysis: String,
    onDismiss: () -> Unit,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var analysis by remember(initialAnalysis) { mutableStateOf(initialAnalysis) }
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
                HorizontalDivider()
                OutlinedTextField(
                    value = analysis,
                    onValueChange = { analysis = it },
                    label = { Text("分析（可选）") },
                    minLines = 3,
                    maxLines = 5,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, analysis) }) { Text("保存") } },
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
