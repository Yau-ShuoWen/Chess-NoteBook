package com.shuowen.chess.ui.opening

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shuowen.chess.opening.OpeningTree
import com.shuowen.chess.opening.displayName

@Composable
internal fun ReviewNavigator(
    tree: OpeningTree,
    currentNodeId: String,
    enabled: Boolean = true,
    branchChoices: List<ReviewBranchChoice>,
    modifier: Modifier = Modifier,
    onBranchChoicesChange: (List<ReviewBranchChoice>) -> Unit,
    onNode: (String) -> Unit,
    onNextNode: (String) -> Unit,
) {
    val currentNode = tree.node(currentNodeId) ?: return
    val children = tree.children(currentNodeId)
    var selectedNextId by remember(currentNodeId) {
        mutableStateOf(children.singleOrNull()?.id)
    }
    val step = tree.pathTo(currentNodeId).size

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (step == 0) "起始局面" else "第 $step 步 · ${currentNode.displayName}",
                    fontWeight = FontWeight.SemiBold,
                )
                currentNode.analysis?.let { analysis ->
                    Text(analysis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        when {
            children.isEmpty() -> Text(
                "当前路径已经结束",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            children.size == 1 -> Text(
                "下一步：${children.single().displayName}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            else -> {
                Text("这里有 ${children.size} 条路径，请选择下一步", fontWeight = FontWeight.Medium)
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(children, key = { it.id }) { child ->
                        FilterChip(
                            selected = selectedNextId == child.id,
                            enabled = enabled,
                            onClick = { selectedNextId = child.id },
                            label = { Text(child.displayName) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        if (children.size <= 1) Spacer(Modifier.weight(1f))
        if (branchChoices.isNotEmpty()) {
            val lastChoice = branchChoices.last()
            OutlinedButton(
                onClick = {
                    onBranchChoicesChange(branchChoices.dropLast(1))
                    onNode(lastChoice.branchNodeId)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
            ) {
                Text("返回上次选择：${lastChoice.selectedPathName}")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    currentNode.parentId?.let { parentId ->
                        if (branchChoices.lastOrNull()?.branchNodeId == parentId) {
                            onBranchChoicesChange(branchChoices.dropLast(1))
                        }
                        onNode(parentId)
                    }
                },
                enabled = enabled && currentNode.parentId != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("上一步")
            }
            Button(
                onClick = {
                    selectedNextId?.let(onNextNode)
                },
                enabled = enabled && selectedNextId != null,
                modifier = Modifier.weight(1f),
            ) {
                Text("下一步")
            }
        }
    }
}

internal data class ReviewBranchChoice(
    val branchNodeId: String,
    val selectedPathName: String,
)
