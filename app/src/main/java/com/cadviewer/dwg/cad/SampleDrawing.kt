package com.cadviewer.dwg.cad

import java.io.ByteArrayInputStream

/**
 * Bản vẽ mẫu hệ thống ống gió điều hòa (đơn vị mm) giúp kiểm thử ngay khi cài đặt app.
 */
object SampleDrawing {

    fun load(): CadDocument {
        val dxf = buildString {
            appendLine("0"); appendLine("SECTION")
            appendLine("2"); appendLine("HEADER")
            appendLine("9"); appendLine("\$INSUNITS")
            appendLine("70"); appendLine("4")
            appendLine("0"); appendLine("ENDSEC")

            appendLine("0"); appendLine("SECTION")
            appendLine("2"); appendLine("TABLES")
            appendLine("0"); appendLine("TABLE")
            appendLine("2"); appendLine("LAYER")
            appendLayer("KIEN_TRUC", 8)
            appendLayer("ONG_GIO_CAP", 4)
            appendLayer("ONG_GIO_HOI", 3)
            appendLayer("CHAN_RE_D200", 2)
            appendLayer("THIET_BI_FCU", 1)
            appendLine("0"); appendLine("ENDTAB")
            appendLine("0"); appendLine("ENDSEC")

            appendLine("0"); appendLine("SECTION")
            appendLine("2"); appendLine("ENTITIES")
            // Mặt bằng phòng 12m x 8m (12000 x 8000 mm)
            appendPolyline("KIEN_TRUC", listOf(0 to 0, 12000 to 0, 12000 to 8000, 0 to 8000), closed = true)
            appendLineEntity("KIEN_TRUC", 6000, 0, 6000, 5500)

            // Tuyến ống gió cấp chính
            appendPolyline("ONG_GIO_CAP", listOf(1500 to 4000, 9500 to 4000, 9500 to 6500), closed = false)
            // Nhánh rẽ D200 ra miệng gió
            appendLineEntity("CHAN_RE_D200", 3500, 4000, 3500, 6200)
            appendLineEntity("CHAN_RE_D200", 7500, 4000, 7500, 6200)
            // Tuyến ống gió hồi
            appendPolyline("ONG_GIO_HOI", listOf(1500 to 2500, 8000 to 2500), closed = false)

            // Thiết bị FCU và miệng gió
            appendInsert("THIET_BI_FCU", "FCU_01_48000BTU", 1500, 3250)
            appendInsert("CHAN_RE_D200", "MIENG_GIO_D200", 3500, 6200)
            appendInsert("CHAN_RE_D200", "MIENG_GIO_D200", 7500, 6200)
            appendInsert("CHAN_RE_D200", "MIENG_GIO_D200", 9500, 6500)

            appendText("ONG_GIO_CAP", "ONG CAP 500x300", 4200, 4300)
            appendText("ONG_GIO_HOI", "ONG HOI 500x300", 4200, 2100)
            appendLine("0"); appendLine("ENDSEC")
            appendLine("0"); appendLine("EOF")
        }
        return DxfParser.parse(ByteArrayInputStream(dxf.toByteArray()), "Mau_MatBang_HVAC_Tang1.dxf")
    }

    private fun StringBuilder.appendLayer(name: String, color: Int) {
        appendLine("0"); appendLine("LAYER")
        appendLine("2"); appendLine(name)
        appendLine("62"); appendLine(color.toString())
    }

    private fun StringBuilder.appendLineEntity(layer: String, x1: Int, y1: Int, x2: Int, y2: Int) {
        appendLine("0"); appendLine("LINE")
        appendLine("8"); appendLine(layer)
        appendLine("10"); appendLine(x1.toString())
        appendLine("20"); appendLine(y1.toString())
        appendLine("11"); appendLine(x2.toString())
        appendLine("21"); appendLine(y2.toString())
    }

    private fun StringBuilder.appendPolyline(layer: String, pts: List<Pair<Int, Int>>, closed: Boolean) {
        appendLine("0"); appendLine("LWPOLYLINE")
        appendLine("8"); appendLine(layer)
        appendLine("70"); appendLine(if (closed) "1" else "0")
        for ((x, y) in pts) {
            appendLine("10"); appendLine(x.toString())
            appendLine("20"); appendLine(y.toString())
        }
    }

    private fun StringBuilder.appendInsert(layer: String, blockName: String, x: Int, y: Int) {
        appendLine("0"); appendLine("INSERT")
        appendLine("8"); appendLine(layer)
        appendLine("2"); appendLine(blockName)
        appendLine("10"); appendLine(x.toString())
        appendLine("20"); appendLine(y.toString())
    }

    private fun StringBuilder.appendText(layer: String, content: String, x: Int, y: Int) {
        appendLine("0"); appendLine("TEXT")
        appendLine("8"); appendLine(layer)
        appendLine("10"); appendLine(x.toString())
        appendLine("20"); appendLine(y.toString())
        appendLine("40"); appendLine("250")
        appendLine("1"); appendLine(content)
    }
}
