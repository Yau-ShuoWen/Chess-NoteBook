package com.shuowen.chess.ui.opening

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shuowen.chess.opening.OpeningDocument
import com.shuowen.chess.opening.OpeningNode
import com.shuowen.chess.opening.OpeningTree
import com.shuowen.chess.opening.displayName

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun VariationTree(
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
            TreeNodeCard(layout.single(), currentNodeId, setOf(tree.rootId), onNode, onEditNode)
        }
        return
    }

    val diagramWidth = layout.maxOf { point ->
        point.row * TREE_COLUMN_WIDTH + TREE_MAX_NODE_WIDTH + TREE_PADDING * 2
    }.dp
    val diagramHeight = layout.maxOf { point ->
        point.depth * TREE_ROW_HEIGHT + nodeHeight(point.node) + 6
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
            .pointerInput(document.id) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val oldScale = scale
                    val newScale = (oldScale * zoomChange).coerceIn(0.5f, 2.5f)
                    val appliedZoom = newScale / oldScale
                    pan = centroid + panChange - (centroid - pan) * appliedZoom
                    scale = newScale
                }
            },
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    translationX = pan.x
                    translationY = pan.y
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
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
                    val start = Offset(parentCenterX, parentY + nodeHeight(parent.node).dp.toPx())
                    val end = Offset(childCenterX, childY)
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
    val width = nodeWidth(point.node)
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
            .height(nodeHeight(point.node).dp)
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
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = displayName,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            )
            point.node.analysis?.let { analysis ->
                HorizontalDivider(Modifier.fillMaxWidth(), thickness = 1.dp)
                Text(
                    text = analysis,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}

private const val TREE_COLUMN_WIDTH = 172
private const val TREE_ROW_HEIGHT = 124
private const val TREE_PADDING = 8
private const val TREE_MAX_NODE_WIDTH = 156

private fun nodeWidth(node: OpeningNode): Int {
    val contentLength = maxOf(
        node.displayName.length,
        node.analysis?.lineSequence()?.maxOfOrNull { it.length } ?: 0,
    )
    return (contentLength * 15 + 24).coerceIn(76, TREE_MAX_NODE_WIDTH)
}

private fun nodeHeight(node: OpeningNode): Int {
    val nameLines = ((node.displayName.length + 9) / 10).coerceIn(1, 2)
    val analysis = node.analysis ?: return (nameLines * 20 + 14).coerceAtLeast(36)
    val analysisLines = ((analysis.length + 14) / 15).coerceIn(1, 3)
    return 27 + nameLines * 20 + analysisLines * 17
}

private data class TreePoint(
    val node: OpeningNode,
    val parentId: String?,
    val depth: Int,
    val row: Float,
)

private fun buildTreeLayout(tree: OpeningTree): List<TreePoint> {
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
                compactRows += compactRows.last() + desiredGap.coerceIn(1f, 1.65f)
            }
        }
        val shift = desired.average().toFloat() - compactRows.average().toFloat()
        ordered.mapIndexed { index, point -> point.copy(row = compactRows[index] + shift) }
    }
}
