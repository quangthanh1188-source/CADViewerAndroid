package com.cadviewer.dwg.cad

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min

/**
 * Bộ phân tích tệp AutoCAD DXF (ASCII R12 - R2018+) viết thuần Kotlin.
 * Hỗ trợ LAYER, LINE, LWPOLYLINE (kèm Bulge), CIRCLE, ARC, TEXT, MTEXT, INSERT.
 */
object DxfParser {

    fun parse(inputStream: InputStream, fileName: String): CadDocument {
        val pairs = readGroupPairs(inputStream)
        val layers = linkedMapOf<String, CadLayer>()
        val entities = mutableListOf<CadEntity>()
        var detectedUnit = DrawingUnit.MM

        var section = ""
        var i = 0
        while (i < pairs.size) {
            val (code, value) = pairs[i]

            if (code == 0 && value == "SECTION" && i + 1 < pairs.size && pairs[i + 1].first == 2) {
                section = pairs[i + 1].second.uppercase()
                i += 2
                continue
            }
            if (code == 0 && value == "ENDSEC") {
                section = ""
                i++
                continue
            }

            when (section) {
                "HEADER" -> {
                    if (code == 9 && value == "\$INSUNITS" && i + 1 < pairs.size && pairs[i + 1].first == 70) {
                        detectedUnit = when (pairs[i + 1].second.toIntOrNull()) {
                            4 -> DrawingUnit.MM
                            5 -> DrawingUnit.CM
                            6 -> DrawingUnit.M
                            else -> DrawingUnit.MM
                        }
                        i += 2
                        continue
                    }
                }

                "TABLES" -> {
                    if (code == 0 && value == "LAYER") {
                        val (layer, nextIdx) = parseLayer(pairs, i + 1)
                        if (layer != null) {
                            layers[layer.name] = layer
                        }
                        i = nextIdx
                        continue
                    }
                }

                "ENTITIES" -> {
                    if (code == 0) {
                        val (entity, nextIdx) = parseEntity(value.uppercase(), pairs, i + 1)
                        if (entity != null) {
                            entities += entity
                            if (!layers.containsKey(entity.layer)) {
                                layers[entity.layer] = CadLayer(
                                    name = entity.layer,
                                    colorArgb = AciColors.toArgb(layers.size + 1)
                                )
                            }
                        }
                        i = nextIdx
                        continue
                    }
                }
            }
            i++
        }

        if (layers.isEmpty()) {
            layers["0"] = CadLayer("0", AciColors.toArgb(7))
        }

        return CadDocument(
            fileName = fileName,
            layers = layers,
            entities = entities,
            bounds = computeBounds(entities),
            unit = detectedUnit,
            statusMessage = "Đã nạp ${entities.size} đối tượng trên ${layers.size} layer."
        )
    }

    private fun readGroupPairs(inputStream: InputStream): List<Pair<Int, String>> {
        val result = ArrayList<Pair<Int, String>>(4096)
        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            while (true) {
                val codeLine = reader.readLine() ?: break
                val valLine = reader.readLine() ?: break
                val code = codeLine.trim().toIntOrNull() ?: continue
                result.add(code to valLine.trim())
            }
        }
        return result
    }

    private fun parseLayer(pairs: List<Pair<Int, String>>, startIdx: Int): Pair<CadLayer?, Int> {
        var idx = startIdx
        var name = ""
        var color = 7
        while (idx < pairs.size && pairs[idx].first != 0) {
            val (c, v) = pairs[idx]
            when (c) {
                2 -> name = v
                62 -> color = v.toIntOrNull() ?: 7
            }
            idx++
        }
        val layer = if (name.isNotEmpty()) {
            CadLayer(name = name, colorArgb = AciColors.toArgb(color), visible = color >= 0)
        } else null
        return layer to idx
    }

    private fun parseEntity(
        type: String,
        pairs: List<Pair<Int, String>>,
        startIdx: Int
    ): Pair<CadEntity?, Int> {
        var idx = startIdx
        var layer = "0"
        var colorArgb: Long? = null

        var x1 = 0.0; var y1 = 0.0
        var x2 = 0.0; var y2 = 0.0
        var radius = 0.0
        var startAngle = 0.0; var endAngle = 360.0
        var text = ""
        var textHeight = 200.0
        var blockName = ""
        var flags = 0

        val vertices = mutableListOf<PolyVertex>()
        var pendingX: Double? = null
        var pendingY: Double? = null
        var pendingBulge = 0.0

        fun flushVertex() {
            val vx = pendingX
            val vy = pendingY
            if (vx != null && vy != null) {
                vertices += PolyVertex(vx, vy, pendingBulge)
                pendingX = null
                pendingY = null
                pendingBulge = 0.0
            }
        }

        while (idx < pairs.size && pairs[idx].first != 0) {
            val (c, v) = pairs[idx]
            when (c) {
                8 -> layer = v.ifEmpty { "0" }
                62 -> v.toIntOrNull()?.let { if (it in 1..255) colorArgb = AciColors.toArgb(it) }
                10 -> {
                    if (type == "LWPOLYLINE") {
                        flushVertex()
                        pendingX = v.toDoubleOrNull() ?: 0.0
                    } else {
                        x1 = v.toDoubleOrNull() ?: 0.0
                    }
                }
                20 -> {
                    if (type == "LWPOLYLINE") {
                        pendingY = v.toDoubleOrNull() ?: 0.0
                    } else {
                        y1 = v.toDoubleOrNull() ?: 0.0
                    }
                }
                42 -> if (type == "LWPOLYLINE") pendingBulge = v.toDoubleOrNull() ?: 0.0
                11 -> x2 = v.toDoubleOrNull() ?: 0.0
                21 -> y2 = v.toDoubleOrNull() ?: 0.0
                40 -> {
                    val d = v.toDoubleOrNull() ?: 0.0
                    radius = d
                    textHeight = d
                }
                50 -> startAngle = v.toDoubleOrNull() ?: 0.0
                51 -> endAngle = v.toDoubleOrNull() ?: 360.0
                70 -> flags = v.toIntOrNull() ?: 0
                1, 3 -> text += v
                2 -> blockName = v
            }
            idx++
        }
        flushVertex()

        val entity: CadEntity? = when (type) {
            "LINE" -> CadEntity.Line(layer, colorArgb, Point2D(x1, y1), Point2D(x2, y2))
            "LWPOLYLINE" -> if (vertices.size >= 2) {
                CadEntity.Polyline(layer, colorArgb, vertices, closed = (flags and 1) != 0)
            } else null
            "CIRCLE" -> if (radius > 0.0) {
                CadEntity.Circle(layer, colorArgb, Point2D(x1, y1), radius)
            } else null
            "ARC" -> if (radius > 0.0) {
                CadEntity.Arc(layer, colorArgb, Point2D(x1, y1), radius, startAngle, endAngle)
            } else null
            "TEXT", "MTEXT" -> if (text.isNotBlank()) {
                CadEntity.Text(layer, colorArgb, Point2D(x1, y1), cleanMText(text), textHeight)
            } else null
            "INSERT" -> if (blockName.isNotBlank()) {
                CadEntity.BlockInsert(layer, colorArgb, blockName, Point2D(x1, y1))
            } else null
            else -> null
        }

        return entity to idx
    }

    private fun cleanMText(raw: String): String {
        return raw
            .replace("\\P", " ")
            .replace(Regex("\\[A-Za-z][^;]*;"), "")
            .replace("{", "")
            .replace("}", "")
            .trim()
    }

    fun computeBounds(entities: List<CadEntity>): BoundingBox {
        if (entities.isEmpty()) return BoundingBox(0.0, 0.0, 1000.0, 1000.0)
        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY

        fun include(x: Double, y: Double) {
            minX = min(minX, x)
            minY = min(minY, y)
            maxX = max(maxX, x)
            maxY = max(maxY, y)
        }

        for (e in entities) {
            when (e) {
                is CadEntity.Line -> {
                    include(e.start.x, e.start.y)
                    include(e.end.x, e.end.y)
                }
                is CadEntity.Polyline -> e.vertices.forEach { include(it.x, it.y) }
                is CadEntity.Circle -> {
                    include(e.center.x - e.radius, e.center.y - e.radius)
                    include(e.center.x + e.radius, e.center.y + e.radius)
                }
                is CadEntity.Arc -> {
                    include(e.center.x - e.radius, e.center.y - e.radius)
                    include(e.center.x + e.radius, e.center.y + e.radius)
                }
                is CadEntity.Text -> include(e.position.x, e.position.y)
                is CadEntity.BlockInsert -> include(e.position.x, e.position.y)
            }
        }

        if (!minX.isFinite()) return BoundingBox(0.0, 0.0, 1000.0, 1000.0)
        return BoundingBox(minX, minY, maxX, maxY)
    }
}
