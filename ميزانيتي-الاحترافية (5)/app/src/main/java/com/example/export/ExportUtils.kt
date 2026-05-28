package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.data.Transaction
import com.example.data.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    suspend fun exportToCSV(context: Context, transactions: List<Transaction>): Uri? = withContext(Dispatchers.IO) {
        try {
            val fileName = "Ledger_Export_${System.currentTimeMillis()}.csv"
            val file = File(context.cacheDir, fileName)
            val writer = file.bufferedWriter()
            
            // Write BOM for UTF-8 to support Arabic characters in MS Excel
            writer.write("\uFEFF")
            
            writer.write("التاريخ,النوع,المبلغ,العميل,البيان والعملة\n")
            
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            
            transactions.forEach { t ->
                val dateStr = dateFormat.format(Date(t.timestamp))
                val typeStr = if (t.type == TransactionType.ON_HIM) "عليه (+)" else "له (-)"
                val escapedName = "\"${t.accountName.replace("\"", "\"\"")}\""
                val escapedDetails = "\"${t.details.replace("\"", "\"\"")} (العملة: ${t.currency})\""
                writer.write("$dateStr,$typeStr,${t.amount},$escapedName,$escapedDetails\n")
            }
            writer.close()
            
            return@withContext FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun exportToPDF(context: Context, transactions: List<Transaction>): Uri? = withContext(Dispatchers.IO) {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size (595 x 842 pt)
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            
            val paint = Paint()
            val textPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 10f
                isAntiAlias = true
            }
            
            val titlePaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            val headerTextPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            var yPos = 30f
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            // 1. Draw PDF header banner (Indigo color theme)
            paint.color = Color.parseColor("#3949AB")
            canvas.drawRect(20f, yPos, 575f, yPos + 70f, paint)

            // 2. Draw Arabic title in header banner
            val titleString = "كشف حساب الحسابات والمعاملات المالي"
            val titleLayout = StaticLayout.Builder.obtain(titleString, 0, titleString.length, titlePaint, 535)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .build()
            
            canvas.save()
            canvas.translate(20f, yPos + 10f)
            titleLayout.draw(canvas)
            canvas.restore()

            // 3. Draw Subtitle with the current date
            val subTitleStr = "تاريخ التصدير: ${dateFormat.format(Date())}"
            val subTextPaint = TextPaint().apply {
                color = Color.parseColor("#E0E0E0")
                textSize = 10f
                isAntiAlias = true
            }
            val subTitleLayout = StaticLayout.Builder.obtain(subTitleStr, 0, subTitleStr.length, subTextPaint, 535)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .build()

            canvas.save()
            canvas.translate(20f, yPos + 40f)
            subTitleLayout.draw(canvas)
            canvas.restore()

            yPos += 90f

            // 4. Draw Table Header
            paint.color = Color.parseColor("#1A237E") // Darker navy for header
            canvas.drawRect(20f, yPos, 575f, yPos + 25f, paint)

            // Column bounds mapping (Total width 555)
            // Margins: Left = 20, Right = 575
            // Columns: Note (20-180) | Client (180-320) | Type (320-380) | Amount (380-470) | Date (470-575)
            val cols = listOf(
                ColumnDef("البيان والعملة", 20f, 160f, Layout.Alignment.ALIGN_OPPOSITE),
                ColumnDef("الطرف الآخر", 180f, 140f, Layout.Alignment.ALIGN_OPPOSITE),
                ColumnDef("نوع القيد", 320f, 60f, Layout.Alignment.ALIGN_CENTER),
                ColumnDef("القيمة", 380f, 90f, Layout.Alignment.ALIGN_CENTER),
                ColumnDef("التاريخ والوقت", 470f, 105f, Layout.Alignment.ALIGN_NORMAL)
            )

            cols.forEach { col ->
                val colTitleLayout = StaticLayout.Builder.obtain(col.title, 0, col.title.length, headerTextPaint, col.width.toInt())
                    .setAlignment(col.align)
                    .setTextDirection(TextDirectionHeuristics.RTL)
                    .build()
                canvas.save()
                canvas.translate(col.x, yPos + 5f)
                colTitleLayout.draw(canvas)
                canvas.restore()
            }

            yPos += 25f

            // 5. Draw Row transactions
            transactions.forEachIndexed { index, t ->
                // Paging control
                if (yPos > 800f) {
                    pdfDocument.finishPage(page)
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPos = 40f
                }

                // Alternating backgrounds
                if (index % 2 == 0) {
                    paint.color = Color.parseColor("#F5F5F5")
                    canvas.drawRect(20f, yPos, 575f, yPos + 28f, paint)
                }

                val dateStr = dateFormat.format(Date(t.timestamp))
                val isOnHim = t.type == TransactionType.ON_HIM
                val typeStr = if (isOnHim) "عليه (+)" else "له (-)"
                val typeColor = if (isOnHim) Color.parseColor("#C62828") else Color.parseColor("#2E7D32")
                val detailsStr = "${t.details} (${t.currency})"

                val rowData = listOf(
                    RowData(detailsStr, Color.BLACK, Layout.Alignment.ALIGN_OPPOSITE),
                    RowData(t.accountName, Color.BLACK, Layout.Alignment.ALIGN_OPPOSITE),
                    RowData(typeStr, typeColor, Layout.Alignment.ALIGN_CENTER),
                    RowData(String.format(Locale.US, "%,.2f", t.amount), typeColor, Layout.Alignment.ALIGN_CENTER, isBold = true),
                    RowData(dateStr, Color.DKGRAY, Layout.Alignment.ALIGN_NORMAL)
                )

                rowData.forEachIndexed { colIndex, r ->
                    val col = cols[colIndex]
                    val cellPaint = TextPaint().apply {
                        color = r.color
                        textSize = 9f
                        isAntiAlias = true
                        if (r.isBold) {
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                        }
                    }

                    val cellLayout = StaticLayout.Builder.obtain(r.text, 0, r.text.length, cellPaint, col.width.toInt())
                        .setAlignment(r.align)
                        .setTextDirection(TextDirectionHeuristics.RTL)
                        .build()

                    canvas.save()
                    canvas.translate(col.x, yPos + 6f)
                    cellLayout.draw(canvas)
                    canvas.restore()
                }

                // Border divider separating lines
                paint.color = Color.parseColor("#E0E0E0")
                paint.strokeWidth = 0.5f
                canvas.drawLine(20f, yPos + 28f, 575f, yPos + 28f, paint)

                yPos += 28f
            }

            pdfDocument.finishPage(page)
            
            val fileName = "Ledger_Export_${System.currentTimeMillis()}.pdf"
            val file = File(context.cacheDir, fileName)
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            
            return@withContext FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }
    
    fun shareFile(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة التقرير"))
    }

    private class ColumnDef(
        val title: String,
        val x: Float,
        val width: Float,
        val align: Layout.Alignment
    )

    private class RowData(
        val text: String,
        val color: Int,
        val align: Layout.Alignment,
        val isBold: Boolean = false
    )
}
