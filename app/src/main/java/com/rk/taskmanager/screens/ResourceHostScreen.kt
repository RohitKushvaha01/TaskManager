package com.rk.taskmanager.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.rk.taskmanager.ProcessViewModel
import com.rk.taskmanager.R
import com.rk.taskmanager.screens.cpu.CPU
import com.rk.taskmanager.screens.gpu.GPU
import com.rk.taskmanager.screens.gpu.GpuViewModel
import com.rk.taskmanager.screens.ram.RAM

private val globalExpandedCards = mutableStateOf(setOf("CPU"))

@Composable
fun ResourceHostScreen(modifier: Modifier = Modifier, viewModel: ProcessViewModel, gpuViewModel: GpuViewModel) {
    var expandedCards by remember { globalExpandedCards }

    // 【修改点 1】：彻底移除了 BoxWithConstraints 和高度计算逻辑
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ExpandableResourceCard(
            title = "CPU",
            iconRes = R.drawable.cpu_24px,
            isExpanded = expandedCards.contains("CPU"),
            onExpandClick = {
                expandedCards = if (expandedCards.contains("CPU")) expandedCards - "CPU" else expandedCards + "CPU"
            }
        ) {
            // 【修改点 2】：不再传入固定 height，仅仅给一点内边距，它会自动撑开到最完美的高度！
            CPU(
                modifier = Modifier.padding(8.dp),
                viewModel = viewModel
            )
        }

        ExpandableResourceCard(
            title = "RAM",
            iconRes = R.drawable.memory_alt_24px,
            isExpanded = expandedCards.contains("RAM"),
            onExpandClick = {
                expandedCards = if (expandedCards.contains("RAM")) expandedCards - "RAM" else expandedCards + "RAM"
            }
        ) {
            RAM(
                modifier = Modifier.padding(8.dp),
                viewModel = viewModel
            )
        }

        ExpandableResourceCard(
            title = "GPU",
            iconRes = R.drawable.cpu_24px,
            isExpanded = expandedCards.contains("GPU"),
            onExpandClick = {
                expandedCards = if (expandedCards.contains("GPU")) expandedCards - "GPU" else expandedCards + "GPU"
            }
        ) {
            GPU(
                modifier = Modifier.padding(8.dp),
                gpuViewModel
            )
        }
    }
}

@Composable
fun ExpandableResourceCard(
    title: String,
    iconRes: Int,
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    onExpandClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "iconRotation"
    )

    Card(
        // 【修改点3】：这里不再接收和随时变更 weight(1f)，让 animateContentSize 能够安稳发挥作用
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(text = title) },
                leadingContent = { Icon(painter = painterResource(iconRes), contentDescription = null) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand/Collapse",
                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                    )
                },
                modifier = Modifier.clickable { onExpandClick() }
            )

            AnimatedVisibility(visible = isExpanded,
                enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 如果展开则正常显示，如果闭合则强行设为 0.dp
                            .then(if (isExpanded) Modifier else Modifier.height(0.dp))
                            // 必须加上裁剪，防止 0.dp 时图表内容溢出绘制
                            .clipToBounds()
                    ) {
                        content()
                    }
                }
            }
        }
    }
}