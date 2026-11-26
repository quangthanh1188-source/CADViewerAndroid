package com.cadviewer.dwg.cad

import android.graphics.Bitmap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.hypot
import kotlin.math.sin

data class Point2D(val x: Double, val y: Double)

data class PolyVertex(
    val x: Double,
    val y: Double,
    val bulge: Double = 0.0
)

data class BoundingBox(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double
) {
    val width: Double get() = (maxX - minX).coerceAtLeast(1.0)
    val height: Double get() = (maxY - minY).coerceAtLeast(1.0)
    val centerX: Double get() = (minX + maxX) / 2.0
    val centerY: Double get() = (minY + maxY) / 2.0
}

data class CadLayer(
    val name: String,
    val colorArgb: Long,
    val visible: Boolean = true
)

sealed class CadEntity {
    abstract val layer: String
    abstract val colorArgb: Long?

    data class Line(
        override val layer: String,
        override val colorArgb: Long? = null,
        val start: Point2D,
        val end: Point2D
    ) : CadEntity() {
        fun length(): Double = hypot(end.x - start.x, end.y - start.y)
    }

    data class Polyline(
        override val layer: String,
        override val colorArgb: Long? = null,
        val vertices: List<PolyVertex>,
        val closed: Boolean = false
    ) : CadEntity() {
        fun length(): Double {
            if (vertices.size < 2) return 0.0
            var total = 0.0
            val count = if (closed) vertices.size else vertices.size - 1
            for (i in 0 until count) {
                val a = vertices[i]
                val b = vertices[(i + 1) % vertices.size]
                val chord = hypot(b.x - a.x, b.y - a.y)
                if (abs(a.bulge) < 1e-6 || chord < 1e-9) {
                    total += chord
                } else {
                    val theta = 4.0 * atan(abs(a.bulge))
                    val radius = chord / (2.0 * sin(theta / 2.0))
                    total += radius * theta
                }
            }
            return total
        }
    }

    data class Circle(
        override val layer: String,
        override val colorArgb: Long? = null,
        val center: Point2D,
        val radius: Double
    ) : CadEntity() {
        fun circumference(): Double = 2.0 * PI * radius
    }

    data class Arc(
        override val layer: String,
        override val colorArgb: Long? = null,
        val center: Point2D,
        val radius: Double,
        val startAngleDeg: Double,
        val endAngleDeg: Double
    ) : CadEntity() {
        fun length(): Double {
            var sweep = endAngleDeg - startAngleDeg
            while (sweep <= 0.0) sweep += 360.0
            return radius * (sweep * PI / 180.0)
        }
    }

    data class Text(
        override val layer: String,
        override val colorArgb: Long? = null,
        val position: Point2D,
        val content: String,
        val height: Double = 200.0
    ) : CadEntity()

    data class BlockInsert(
        override val layer: String,
        override val colorArgb: Long? = null,
        val blockName: String,
        val position: Point2D
    ) : CadEntity()
}

enum class DrawingUnit(val label: String, val toMetersFactor: Double) {
    MM("mm", 0.001),
    CM("cm", 0.01),
    M("m", 1.0)
}

data class DwgMetadata(
    val versionCode: String,
    val autoCadVersion: String,
    val fileSizeBytes: Long,
    val previewBitmap: Bitmap? = null,
    val parsedVectorNative: Boolean = false
)

data class CadDocument(
    val fileName: String,
    val layers: Map<String, CadLayer>,
    val entities: List<CadEntity>,
    val bounds: BoundingBox,
    val unit: DrawingUnit = DrawingUnit.MM,
    val dwgMetadata: DwgMetadata? = null,
    val statusMessage: String? = null
)

data class LayerTakeoffRow(
    val layerName: String,
    val linearLengthMeters: Double,
    val linearWithWasteMeters: Double,
    val entityCount: Int,
    val blockCount: Int
)

data class BlockTakeoffRow(
    val blockName: String,
    val layerName: String,
    val count: Int
)

object AciColors {
    fun toArgb(aci: Int): Long = when (abs(aci) % 10) {
        1 -> 0xFFFF5252
        2 -> 0xFFFFD740
        3 -> 0xFF69F0AE
        4 -> 0xFF40C4FF
        5 -> 0xFF536DFE
        6 -> 0xFFE040FB
        7 -> 0xFFF1F5F9
        8 -> 0xFF94A3B8
        9 -> 0xFFCBD5E1
        else -> 0xFF38BDF8
    }
}
