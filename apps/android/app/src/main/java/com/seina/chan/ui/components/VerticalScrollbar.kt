package com.seina.chan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun VerticalScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier
) {
    val firstVisibleIndex = state.firstVisibleItemIndex
    val layoutInfo = state.layoutInfo
    val totalItems = layoutInfo.totalItemsCount
    if (totalItems <= 0) return

    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return

    val firstItem = visibleItems.first()
    val lastItem = visibleItems.last()
    val visibleCount = lastItem.index - firstItem.index + 1

    val thumbHeightFraction = (visibleCount.toFloat() / totalItems.toFloat()).coerceIn(0.05f, 1f)
    val scrollFraction = firstVisibleIndex.toFloat() / totalItems.toFloat()

    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            .width(16.dp)
            .pointerInput(totalItems) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        val trackHeight = size.height.toFloat()
                        val downY = down.position.y.coerceIn(0f, trackHeight)

                        val clickFraction = downY / trackHeight
                        val targetIndex = (clickFraction * totalItems)
                            .toInt()
                            .coerceIn(0, totalItems - 1)
                        coroutineScope.launch {
                            state.animateScrollToItem(targetIndex)
                        }

                        drag(down.id) { change ->
                            if (change.positionChange() != androidx.compose.ui.geometry.Offset.Zero) {
                                val dragY = change.position.y.coerceIn(0f, trackHeight)
                                val dragFraction = dragY / trackHeight
                                val dragTarget = (dragFraction * totalItems)
                                    .toInt()
                                    .coerceIn(0, totalItems - 1)
                                coroutineScope.launch {
                                    state.scrollToItem(dragTarget)
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.TopEnd
    ) {
        val trackHeight = maxHeight
        val thumbHeight = trackHeight * thumbHeightFraction
        val maxOffset = trackHeight - thumbHeight
        val offsetY = maxOffset * scrollFraction

        Box(
            modifier = Modifier
                .padding(end = 4.dp, top = 4.dp, bottom = 4.dp)
                .height(thumbHeight)
                .offset(y = offsetY)
                .width(3.dp)
                .background(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = CircleShape
                )
        )
    }
}
