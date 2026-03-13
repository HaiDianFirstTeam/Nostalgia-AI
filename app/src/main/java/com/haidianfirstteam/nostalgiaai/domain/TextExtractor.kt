package com.haidianfirstteam.nostalgiaai.domain

import android.content.Context
import android.net.Uri
import com.haidianfirstteam.nostalgiaai.util.FileUtil
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import android.util.Xml
import org.xmlpull.v1.XmlPullParser

object TextExtractor {

    data class ExtractResult(
        val ok: Boolean,
        val text: String,
        val error: String? = null
    )

    fun extract(context: Context, uri: Uri, mimeType: String): ExtractResult {
        return try {
            when {
                mimeType.equals("application/pdf", ignoreCase = true) -> extractPdf(context, uri)
                mimeType.startsWith("text/") -> extractText(context, uri)
                mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ignoreCase = true) -> {
                    extractDocx(context, uri)
                }
                else -> ExtractResult(false, "", "不支持的文件类型：$mimeType")
            }
        } catch (e: Exception) {
            ExtractResult(false, "", e.message)
        }
    }

    private fun extractText(context: Context, uri: Uri): ExtractResult {
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return ExtractResult(false, "", "无法读取文件")
            val br = BufferedReader(InputStreamReader(input))
            val sb = StringBuilder()
            var line: String?
            while (true) {
                line = br.readLine() ?: break
                sb.append(line).append('\n')
                if (sb.length > 200_000) {
                    sb.append("\n...（已截断）")
                    break
                }
            }
            return ExtractResult(true, sb.toString())
        }
    }

    private fun extractPdf(context: Context, uri: Uri): ExtractResult {
        // pdfbox-android requires init
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return ExtractResult(false, "", "无法读取PDF")
            val doc = PDDocument.load(input)
            doc.use {
                val stripper = PDFTextStripper()
                stripper.sortByPosition = true
                val text = stripper.getText(doc)
                val clipped = if (text.length > 300_000) text.substring(0, 300_000) + "\n...（已截断）" else text
                return ExtractResult(true, clipped)
            }
        }
    }

    private fun extractDocx(context: Context, uri: Uri): ExtractResult {
        // DOCX is a zip containing word/document.xml
        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) return ExtractResult(false, "", "无法读取DOCX")
            ZipInputStream(input).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.name == "word/document.xml") {
                        val text = parseDocxDocumentXml(zis)
                        val clipped = if (text.length > 300_000) text.substring(0, 300_000) + "\n...（已截断）" else text
                        return ExtractResult(true, clipped)
                    }
                }
            }
        }
        return ExtractResult(false, "", "DOCX 缺少 word/document.xml")
    }

    private fun parseDocxDocumentXml(input: java.io.InputStream): String {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(input, "UTF-8")

        val sb = StringBuilder()
        var event = parser.eventType
        var lastWasText = false

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    // w:t => text run
                    if (name == "t") {
                        // handled in TEXT event
                    }
                    // w:p => paragraph; add newline on end
                }
                XmlPullParser.TEXT -> {
                    val t = parser.text
                    if (!t.isNullOrEmpty()) {
                        sb.append(t)
                        lastWasText = true
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name
                    // Paragraph end
                    if (name == "p") {
                        if (lastWasText) sb.append('\n')
                        lastWasText = false
                    }
                    // Line break
                    if (name == "br") {
                        sb.append('\n')
                        lastWasText = false
                    }
                    if (sb.length > 400_000) break
                }
            }
            event = parser.next()
        }

        return sb.toString().trim()
    }
}
