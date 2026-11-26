package com.cadviewer.dwg.cad

import org.json.JSONObject
import java.io.File

/**
 * Cầu nối JNI sang C++ (GNU LibreDWG) để giải mã trực tiếp đối tượng vector trong file .DWG.
 */
object DwgNativeBridge {
    private var libraryLoaded = false

    init {
        try {
            System.loadLibrary("dwgbridge")
            libraryLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            libraryLoaded = false
        }
    }

    external fun isLibreDwgLinked(): Boolean
    external fun parseDwgFileToJson(filePath: String): String

    fun isAvailable(): Boolean = libraryLoaded && try {
        isLibreDwgLinked()
    } catch (_: Throwable) {
        false
    }

    fun loadDwg(file: File, displayName: String): CadDocument {
        val metadata = DwgHeaderInspector.inspect(file)

        if (!isAvailable()) {
            return CadDocument(
                fileName = displayName,
                layers = emptyMap(),
                entities = emptyList(),
                bounds = BoundingBox(0.0, 0.0, 100.0, 100.0),
                dwgMetadata = metadata,
                statusMessage = "Đã đọc thông tin ${metadata.autoCadVersion}. Bản build hiện tại chưa nhúng libredwg.a nên hiển thị ảnh xem trước (Preview). Hãy build qua GitHub Actions hoặc lưu bản vẽ dạng DXF để xem đầy đủ vector & bóc tách."
            )
        }

        val rawJson = parseDwgFileToJson(file.absolutePath)
        val root = JSONObject(rawJson)
        if (!root.optBoolean("ok", false)) {
            return CadDocument(
                fileName = displayName,
                layers = emptyMap(),
                entities = emptyList(),
                bounds = BoundingBox(0.0, 0.0, 100.0, 100.0),
                dwgMetadata = metadata,
                statusMessage = root.optString("error", "Không thể giải mã DWG")
            )
        }

        val entities = mutableListOf<CadEntity>()
        val layers = linkedMapOf<String, CadLayer>()
        val arr = root.optJSONArray("entities")

        if (arr != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val layer = obj.optString("layer", "0")
                if (!layers.containsKey(layer)) {
                    layers[layer] = CadLayer(layer, AciColors.toArgb(layers.size + 1))
                }
                when (obj.optString("type")) {
                    "LINE" -> entities += CadEntity.Line(
                        layer = layer,
                        start = Point2D(obj.getDouble("x1"), obj.getDouble("y1")),
                        end = Point2D(obj.getDouble("x2"), obj.getDouble("y2"))
                    )
                    "CIRCLE" -> entities += CadEntity.Circle(
                        layer = layer,
                        center = Point2D(obj.getDouble("cx"), obj.getDouble("cy")),
                        radius = obj.getDouble("r")
                    )
                    "ARC" -> entities += CadEntity.Arc(
                        layer = layer,
                        center = Point2D(obj.getDouble("cx"), obj.getDouble("cy")),
                        radius = obj.getDouble("r"),
                        startAngleDeg = obj.getDouble("startAngle"),
                        endAngleDeg = obj.getDouble("endAngle")
                    )
                    "LWPOLYLINE" -> {
                        val pts = obj.getJSONArray("points")
                        val verts = mutableListOf<PolyVertex>()
                        for (p in 0 until pts.length()) {
                            val pt = pts.getJSONArray(p)
                            verts += PolyVertex(pt.getDouble(0), pt.getDouble(1))
                        }
                        entities += CadEntity.Polyline(
                            layer = layer,
                            vertices = verts,
                            closed = obj.optBoolean("closed", false)
                        )
                    }
                    "INSERT" -> entities += CadEntity.BlockInsert(
                        layer = layer,
                        blockName = obj.optString("name", "BLOCK"),
                        position = Point2D(obj.getDouble("x"), obj.getDouble("y"))
                    )
                }
            }
        }

        val bounds = DxfParser.computeBounds(entities)
        return CadDocument(
            fileName = displayName,
            layers = layers,
            entities = entities,
            bounds = bounds,
            dwgMetadata = metadata.copy(parsedVectorNative = true),
            statusMessage = "Đã giải mã ${entities.size} đối tượng từ ${metadata.autoCadVersion} bằng GNU LibreDWG."
        )
    }
}
