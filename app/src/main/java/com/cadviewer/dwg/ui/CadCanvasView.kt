package com.cadviewer.dwg.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.cadviewer.dwg.cad.CadDocument
import com.cadviewer.dwg.cad.CadEntity
import com.cadviewer.dwg.cad.DrawingUnit
import com.cadviewer.dwg.cad.Point2D
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.min

@Composable
fun CadCanvasView(
    doc: CadDocument,
    unit: DrawingUnit,
    measureMode: Boolean,
    resetTrigger: Int,
    modifier: Modifier = Modifier
) {
    // Nếu tệp DWG chưa có đối tượng vector nhưng có ảnh xem trước nhúng sẵn
    val previewBitmap = doc.dwgMetadata?.previewBitmap
    if (doc.entities.isEmpty() && previewBitmap != null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = previewBitmap.asImageBitmap(),
                contentDescription = "Ảnh xem trước bản vẽ DWG",
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentScale = ContentScale.Fit
            )
        }
        return
    }

    var viewportSize by remember { mutableStateOf(IntSize(1, 1)) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var measureStart by remember { mutableStateOf<Point2D?>(null) }
    var measureEnd by remember { mutableStateOf<Point2D?>(null) }

    fun fitToScreen(size: IntSize) {
        if (size.width <= 1 || size.height <= 1) return
        val pad = 0.86f
        val sx = (size.width * pad) / doc.bounds.width.toFloat()
        val sy = (size.height * pad) / doc.bounds.height.toFloat()
        val fitScale = min(sx, sy).coerceIn(1e-5f, 1e5f)
        scale = fitScale
        offset = Offset(
            x = size.width / 2f - doc.bounds.centerX.toFloat() * fitScale,
            y = size.height / 2f + doc.bounds.centerY.toFloat() * fitScale
        )
    }

    LaunchedEffect(doc.fileName, resetTrigger, viewportSize) {
        fitToScreen(viewportSize)
        measureStart = null
        measureEnd = null
    }

    fun worldToScreen(x: Double, y: Double): Offset {
        return Offset(
            x = (x.toFloat() * scale) + offset.x,
            y = (-y.toFloat() * scale) + offset.y
        )
    }

    fun screenToWorld(pt: Offset): Point2D {
        return Point2D(
            x = ((pt.x - offset.x) / scale).toDouble(),
            y = (-(pt.y - offset.y) / scale).toDouble()
        )
    }

    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#E2E8F0")
            textSize = 28f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .onSizeChanged { viewportSize = it }
            .pointerInput(doc.fileName) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1e-5f, 1e5f)
                    val ratio = newScale / scale
                    offset = Offset(
                        x = centroid.x - (centroid.x - offset.x) * ratio + pan.x,
                        y = centroid.y - (centroid.y - offset.y) * ratio + pan.y
                    )
                    scale = newScale
                }
            }
            .pointerInput(measureMode, scale, offset) {
                detectTapGestures { tapOffset ->
                    if (!measureMode) return@detectTapGestures
                    val worldPt = screenToWorld(tapOffset)
                    if (measureStart == null || measureEnd != null) {
                        measureStart = worldPt
                        measureEnd = null
                    } else {
                        measureEnd = worldPt
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Vẽ trục tọa độ gốc mờ
            val origin = worldToScreen(0.0, 0.0)
            drawLine(Color(0x22FFFFFF), Offset(0f, origin.y), Offset(size.width, origin.y), 1f)
            drawLine(Color(0x22FFFFFF), Offset(origin.x, 0f), Offset(origin.x, size.height), 1f)

            for (entity in doc.entities) {
                val layer = doc.layers[entity.layer]
                if (layer != null && !layer.visible) continue
                val strokeColor = Color(entity.colorArgb ?: layer?.colorArgb ?: 0xFFF1F5F9)

                when (entity) {
                    is CadEntity.Line -> {
                        drawLine(
                            color = strokeColor,
                            start = worldToScreen(entity.start.x, entity.start.y),
                            end = worldToScreen(entity.end.x, entity.end.y),
                            strokeWidth = 2.4f
                        )
                    }

                    is CadEntity.Polyline -> {
                        if (entity.vertices.size >= 2) {
                            val path = Path()
                            val first = worldToScreen(entity.vertices[0].x, entity.vertices[0].y)
                            path.moveTo(first.x, first.y)
                            for (i in 1 until entity.vertices.size) {
                                val p = worldToScreen(entity.vertices[i].x, entity.vertices[i].y)
                                path.lineTo(p.x, p.y)
                            }
                            if (entity.closed) path.close()
                            drawPath(path, color = strokeColor, style = Stroke(width = 2.6f))
                        }
                    }

                    is CadEntity.Circle -> {
                        val c = worldToScreen(entity.center.x, entity.center.y)
                        val r = (entity.radius.toFloat() * scale).coerceAtLeast(1f)
                        drawCircle(color = strokeColor, radius = r, center = c, style = Stroke(width = 2.2f))
                    }

                    is CadEntity.Arc -> {
                        val c = worldToScreen(entity.center.x, entity.center.y)
                        val r = (entity.radius.toFloat() * scale).coerceAtLeast(1f)
                        var sweep = entity.endAngleDeg - entity.startAngleDeg
                        while (sweep <= 0.0) sweep += 360.0
                        drawArc(
                            color = strokeColor,
                            startAngle = (-entity.startAngleDeg).toFloat(),
                            sweepAngle = (-sweep).toFloat(),
                            useCenter = false,
                            topLeft = Offset(c.x - r, c.y - r),
                            size = Size(r * 2f, r * 2f),
                            style = Stroke(width = 2.2f)
                        )
                    }

                    is CadEntity.BlockInsert -> {
                        val p = worldToScreen(entity.position.x, entity.position.y)
                        val marker = 10f
                        drawRect(
                            color = strokeColor,
                            topLeft = Offset(p.x - marker, p.y - marker),
                            size = Size(marker * 2f, marker * 2f),
                            style = Stroke(width = 2.4f)
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            entity.blockName,
                            p.x + marker + 6f,
                            p.y + 8f,
                            textPaint
                        )
                    }

                    is CadEntity.Text -> {
                        val p = worldToScreen(entity.position.x, entity.position.y)
                        drawContext.canvas.nativeCanvas.drawText(entity.content, p.x, p.y, textPaint)
                    }
                }
            }

            // Vẽ đường đo khoảng cách 2 điểm
            val m1 = measureStart
            val m2 = measureEnd
            if (m1 != null) {
                val s1 = worldToScreen(m1.x, m1.y)
                drawCircle(Color(0xFFF59E0B), radius = 8f, center = s1)
                if (m2 != null) {
                    val s2 = worldToScreen(m2.x, m2.y)
                    drawCircle(Color(0xFFF59E0B), radius = 8f, center = s2)
                    drawLine(Color(0xFFF59E0B), s1, s2, strokeWidth = 3.5f)
                }
            }
        }

        // Hiển thị kết quả đo khoảng cách
        if (measureMode) {
            val m1 = measureStart
            val m2 = measureEnd
            val bannerText = when {
                m1 == null -> "Chế độ đo: Chạm điểm thứ 1 trên bản vẽ"
                m2 == null -> "Chạm điểm thứ 2 để đo khoảng cách"
                else -> {
                    val distRaw = hypot(m2.x - m1.x, m2.y - m1.y)
                    val distM = distRaw * unit.toMetersFactor
                    String.format(
                        Locale.US,
                        "Khoảng cách: %.2f m (%.0f %s)",
                        distM,
                        distRaw,
                        unit.label
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .background(Color(0xCC1E293B))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(bannerText, color = Color(0xFFFCD34D), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
