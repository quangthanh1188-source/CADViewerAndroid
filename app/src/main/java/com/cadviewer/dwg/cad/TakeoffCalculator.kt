package com.cadviewer.dwg.cad

import java.io.File
import java.util.Locale

object TakeoffCalculator {

    fun calculateLayerRows(
        doc: CadDocument,
        unit: DrawingUnit,
        wastePercent: Double
    ): List<LayerTakeoffRow> {
        val factor = unit.toMetersFactor
        val wasteMultiplier = 1.0 + (wastePercent.coerceAtLeast(0.0) / 100.0)

        return doc.entities
            .filter { doc.layers[it.layer]?.visible != false }
            .groupBy { it.layer }
            .map { (layerName, items) ->
                var lengthRaw = 0.0
                var blocks = 0
                for (item in items) {
                    when (item) {
                        is CadEntity.Line -> lengthRaw += item.length()
                        is CadEntity.Polyline -> lengthRaw += item.length()
                        is CadEntity.Arc -> lengthRaw += item.length()
                        is CadEntity.Circle -> lengthRaw += item.circumference()
                        is CadEntity.BlockInsert -> blocks++
                        is CadEntity.Text -> Unit
                    }
                }
                val meters = lengthRaw * factor
                LayerTakeoffRow(
                    layerName = layerName,
                    linearLengthMeters = meters,
                    linearWithWasteMeters = meters * wasteMultiplier,
                    entityCount = items.size,
                    blockCount = blocks
                )
            }
            .sortedByDescending { it.linearLengthMeters }
    }

    fun calculateBlockRows(doc: CadDocument): List<BlockTakeoffRow> {
        return doc.entities
            .filterIsInstance<CadEntity.BlockInsert>()
            .filter { doc.layers[it.layer]?.visible != false }
            .groupBy { it.blockName to it.layer }
            .map { (key, list) ->
                BlockTakeoffRow(blockName = key.first, layerName = key.second, count = list.size)
            }
            .sortedByDescending { it.count }
    }

    fun exportCsv(
        outputFile: File,
        doc: CadDocument,
        unit: DrawingUnit,
        wastePercent: Double
    ): File {
        val layerRows = calculateLayerRows(doc, unit, wastePercent)
        val blockRows = calculateBlockRows(doc)

        outputFile.parentFile?.mkdirs()
        outputFile.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
            // UTF-8 BOM để Excel hiển thị chuẩn tiếng Việt
            writer.write("﻿")
            writer.write("BẢNG BÓC TÁCH KHỐI LƯỢNG BẢN VẼ,${escapeCsv(doc.fileName)}
")
            writer.write("Đơn vị bản vẽ,${unit.label},Hao hụt theo mét dài (%),${fmt(wastePercent)}

")
            writer.write("Layer,Số đối tượng,Chiều dài thực (m),Chiều dài gồm hao hụt (m),Số Block
")
            for (row in layerRows) {
                writer.write(
                    "${escapeCsv(row.layerName)},${row.entityCount},${fmt(row.linearLengthMeters)},${fmt(row.linearWithWasteMeters)},${row.blockCount}
"
                )
            }
            val totalRaw = layerRows.sumOf { it.linearLengthMeters }
            val totalWaste = layerRows.sumOf { it.linearWithWasteMeters }
            writer.write("TỔNG CỘNG,${layerRows.sumOf { it.entityCount }},${fmt(totalRaw)},${fmt(totalWaste)},${layerRows.sumOf { it.blockCount }}

")

            if (blockRows.isNotEmpty()) {
                writer.write("THỐNG KÊ THIẾT BỊ (BLOCK)
")
                writer.write("Tên Block,Layer,Số lượng
")
                for (b in blockRows) {
                    writer.write("${escapeCsv(b.blockName)},${escapeCsv(b.layerName)},${b.count}
")
                }
            }
        }
        return outputFile
    }

    private fun fmt(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun escapeCsv(text: String): String {
        return if (text.contains(",") || text.contains(""") || text.contains("
")) {
            ""${text.replace(""", """")}""
        } else text
    }
}
