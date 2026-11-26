package com.cadviewer.dwg

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.cadviewer.dwg.cad.CadDocument
import com.cadviewer.dwg.cad.DrawingUnit
import com.cadviewer.dwg.cad.DwgNativeBridge
import com.cadviewer.dwg.cad.DxfParser
import com.cadviewer.dwg.cad.SampleDrawing
import com.cadviewer.dwg.cad.TakeoffCalculator
import com.cadviewer.dwg.ui.CadCanvasView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialUri: Uri? = intent?.data

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF38BDF8),
                    surface = Color(0xFF0F172A),
                    background = Color(0xFF0D1117)
                )
            ) {
                CadViewerApp(initialUri = initialUri)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CadViewerApp(initialUri: Uri?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var doc by remember { mutableStateOf(SampleDrawing.load()) }
    var unit by remember { mutableStateOf(doc.unit) }
    var wastePercent by remember { mutableDoubleStateOf(10.0) }
    var measureMode by remember { mutableStateOf(false) }
    var resetTrigger by remember { mutableIntStateOf(0) }
    var showLayersSheet by remember { mutableStateOf(false) }
    var showTakeoffSheet by remember { mutableStateOf(false) }

    fun loadUri(uri: Uri) {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val fileName = resolveFileName(context, uri)
                val lower = fileName.lowercase(Locale.US)
                if (lower.endsWith(".dwg")) {
                    val tempFile = File(context.cacheDir, "opened_drawing.dwg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    DwgNativeBridge.loadDwg(tempFile, fileName)
                } else {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        DxfParser.parse(input, fileName)
                    } ?: doc
                }
            }
            doc = loaded
            unit = loaded.unit
            resetTrigger++
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) loadUri(uri)
    }

    if (initialUri != null) {
        remember(initialUri) {
            loadUri(initialUri)
            true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F172A),
                    titleContentColor = Color(0xFFF8FAFC)
                ),
                title = {
                    Column {
                        Text(
                            text = doc.fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${doc.entities.size} đối tượng · ${doc.layers.size} layer",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Mở file DWG/DXF")
                    }
                    IconButton(onClick = { measureMode = !measureMode }) {
                        Icon(
                            Icons.Default.Straighten,
                            contentDescription = "Đo khoảng cách",
                            tint = if (measureMode) Color(0xFFFCD34D) else Color.White
                        )
                    }
                    IconButton(onClick = { resetTrigger++ }) {
                        Icon(Icons.Default.CenterFocusStrong, contentDescription = "Thu gọn toàn bản vẽ")
                    }
                    IconButton(onClick = { showLayersSheet = true }) {
                        Icon(Icons.Default.Layers, contentDescription = "Quản lý Layer")
                    }
                    IconButton(onClick = { showTakeoffSheet = true }) {
                        Icon(Icons.Default.Calculate, contentDescription = "Bóc tách khối lượng")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            doc.statusMessage?.let { msg ->
                Text(
                    text = msg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1E293B))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color(0xFFBAE6FD),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            CadCanvasView(
                doc = doc,
                unit = unit,
                measureMode = measureMode,
                resetTrigger = resetTrigger,
                modifier = Modifier.weight(1f)
            )
        }
    }

    if (showLayersSheet) {
        ModalBottomSheet(onDismissRequest = { showLayersSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Danh sách Layer (${doc.layers.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.height(340.dp)) {
                    items(doc.layers.values.toList()) { layer ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = doc.layers.toMutableMap()
                                    updated[layer.name] = layer.copy(visible = !layer.visible)
                                    doc = doc.copy(layers = updated)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = layer.visible,
                                onCheckedChange = { checked ->
                                    val updated = doc.layers.toMutableMap()
                                    updated[layer.name] = layer.copy(visible = checked)
                                    doc = doc.copy(layers = updated)
                                }
                            )
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .background(Color(layer.colorArgb))
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(layer.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    if (showTakeoffSheet) {
        val layerRows = remember(doc, unit, wastePercent) {
            TakeoffCalculator.calculateLayerRows(doc, unit, wastePercent)
        }
        val blockRows = remember(doc) {
            TakeoffCalculator.calculateBlockRows(doc)
        }

        ModalBottomSheet(onDismissRequest = { showTakeoffSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bóc tách khối lượng", style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = {
                        val exportFile = File(context.cacheDir, "exports/boc_tach_khoi_luong.csv")
                        TakeoffCalculator.exportCsv(exportFile, doc, unit, wastePercent)
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            exportFile
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Xuất bảng khối lượng CSV"))
                        Toast.makeText(context, "Đã tạo tệp CSV", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Xuất CSV")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DrawingUnit.entries.forEach { u ->
                        FilterChip(
                            selected = unit == u,
                            onClick = { unit = u },
                            label = { Text("Đơn vị: ${u.label}") }
                        )
                    }
                }

                Text(
                    text = String.format(Locale.US, "Hao hụt theo mét dài: %.0f%%", wastePercent),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8)
                )
                Slider(
                    value = wastePercent.toFloat(),
                    onValueChange = { wastePercent = it.toDouble() },
                    valueRange = 0f..25f
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(layerRows) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(row.layerName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${row.entityCount} nét · ${row.blockCount} block",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    String.format(Locale.US, "%.2f m", row.linearLengthMeters),
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    String.format(Locale.US, "+HH: %.2f m", row.linearWithWasteMeters),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF69F0AE)
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0x22FFFFFF))
                    }

                    if (blockRows.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(12.dp))
                            Text("Thống kê thiết bị (Block)", style = MaterialTheme.typography.titleSmall)
                        }
                        items(blockRows) { b ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${b.blockName} (${b.layerName})", style = MaterialTheme.typography.bodySmall)
                                Text("${b.count} cái", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveFileName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return uri.lastPathSegment ?: "ban_ve.dxf"
}
