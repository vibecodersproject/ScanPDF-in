package id.rwi.scanpdfin.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Susun ulang isi kisi dengan seret: tahan sebuah kotak lalu geser ke posisi baru.
 * Tahan tanpa digeser = "tahan lama" biasa (mis. memilih). Ketuk = klik biasa.
 * Selama diseret urutan diubah di daftar lokal; pemanggil menyimpan sekali saat dilepas.
 */
@Stable
class GridReorderState(val grid: LazyGridState, private val scope: CoroutineScope) {
    /** kunci kotak yang sedang diseret */
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    /** geser kotak terseret dari slotnya sekarang (piksel) */
    var delta by mutableStateOf(Offset.Zero)
        private set
    internal var startIndex = -1
    internal var moved = false
    internal var longPressed = false
    internal var pointer = Offset.Zero
    private var autoScroll: Job? = null

    internal fun itemAt(p: Offset): LazyGridItemInfo? = grid.layoutInfo.visibleItemsInfo.firstOrNull {
        p.x >= it.offset.x && p.x <= it.offset.x + it.size.width && p.y >= it.offset.y && p.y <= it.offset.y + it.size.height
    }

    internal fun start(item: LazyGridItemInfo) { draggingKey = item.key; startIndex = item.index; delta = Offset.Zero; moved = false }

    /** Geser; bila pusat kotak masuk ke kotak lain → pindah. Mengembalikan pasangan (dari, ke) bila pindah. */
    internal fun drag(amount: Offset, onMove: (Int, Int) -> Unit) {
        val key = draggingKey ?: return
        delta += amount
        val cur = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return
        val center = Offset(cur.offset.x + delta.x + cur.size.width / 2f, cur.offset.y + delta.y + cur.size.height / 2f)
        val target = grid.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != key && it.contentType == cur.contentType &&
                center.x >= it.offset.x && center.x <= it.offset.x + it.size.width &&
                center.y >= it.offset.y && center.y <= it.offset.y + it.size.height
        } ?: return
        moved = true
        onMove(cur.index, target.index)
        // kotak akan dipindah ke slot target: jaga agar tetap di bawah jari
        delta += Offset((cur.offset.x - target.offset.x).toFloat(), (cur.offset.y - target.offset.y).toFloat())
    }

    /** Gulir otomatis saat jari mendekati tepi atas/bawah kisi. */
    internal fun edgeScroll(edgePx: Float, onMove: (Int, Int) -> Unit) {
        if (autoScroll?.isActive == true) return
        autoScroll = scope.launch {
            while (isActive && draggingKey != null) {
                val h = grid.layoutInfo.viewportSize.height
                val speed = when {
                    pointer.y < edgePx -> -(edgePx - pointer.y) / edgePx * 28f
                    pointer.y > h - edgePx -> (pointer.y - (h - edgePx)) / edgePx * 28f
                    else -> 0f
                }
                if (speed == 0f) break
                val used = grid.scrollBy(speed)
                if (used == 0f) break
                // kotak ikut isi yang bergulir; geser balik agar tetap di bawah jari
                drag(Offset(0f, used), onMove)
                kotlinx.coroutines.delay(16)
            }
        }
    }

    internal fun end() { draggingKey = null; delta = Offset.Zero; startIndex = -1; autoScroll?.cancel() }
}

@Composable
fun rememberGridReorderState(grid: LazyGridState): GridReorderState {
    val scope = rememberCoroutineScope()
    return remember(grid) { GridReorderState(grid, scope) }
}

/**
 * Pasang di LazyVerticalGrid. [onTap]/[onLongPress] menerima indeks kotak; [onMove] dipanggil tiap kotak
 * melewati kotak lain (ubah daftar lokal); [onDrop] (dari, ke) sekali saat dilepas bila urutan berubah.
 */
@Composable
fun Modifier.gridReorder(
    state: GridReorderState,
    enabled: Boolean = true,
    onTap: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDrop: (Int, Int) -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val tap by rememberUpdatedState(onTap)
    val longPress by rememberUpdatedState(onLongPress)
    val move by rememberUpdatedState(onMove)
    val drop by rememberUpdatedState(onDrop)
    if (!enabled) return this
    return this
        .pointerInput(state) {
            detectTapGestures(
                onPress = { state.longPressed = false },
                onTap = { p -> if (!state.longPressed) state.itemAt(p)?.let { tap(it.index) } },
            )
        }
        .pointerInput(state) {
            detectDragGesturesAfterLongPress(
                onDragStart = { p ->
                    state.longPressed = true
                    state.pointer = p
                    val item = state.itemAt(p) ?: return@detectDragGesturesAfterLongPress
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    state.start(item)
                },
                onDrag = { change, amount ->
                    change.consume()
                    state.pointer = change.position
                    state.drag(amount, move)
                    state.edgeScroll(edge, move)
                },
                onDragEnd = {
                    val from = state.startIndex
                    val to = state.grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == state.draggingKey }?.index ?: from
                    val wasMoved = state.moved
                    state.end()
                    if (from < 0) return@detectDragGesturesAfterLongPress
                    if (wasMoved && from != to) drop(from, to) else if (!wasMoved) longPress(from)
                },
                onDragCancel = {
                    val from = state.startIndex
                    val wasMoved = state.moved
                    state.end()
                    if (from >= 0 && !wasMoved) longPress(from)
                },
            )
        }
}

/** Tampilan kotak: yang diseret mengikuti jari, sedikit membesar dan berada di atas kotak lain. */
fun Modifier.reorderItem(state: GridReorderState, key: Any): Modifier {
    val dragging = state.draggingKey == key
    return if (!dragging) this else this.zIndex(1f).graphicsLayer {
        translationX = state.delta.x; translationY = state.delta.y
        scaleX = 1.06f; scaleY = 1.06f; shadowElevation = 16f
    }
}

@Suppress("unused")
private val keepIntOffset = IntOffset.Zero
