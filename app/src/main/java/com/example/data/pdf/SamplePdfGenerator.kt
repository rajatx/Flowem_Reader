package com.example.data.pdf

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SamplePdfGenerator {

    suspend fun getOrCreateSampleBook(context: Context): Uri = withContext(Dispatchers.IO) {
        val sampleFile = File(context.filesDir, "The_Art_of_Mindful_Reading.pdf")
        if (sampleFile.exists() && sampleFile.length() > 1000) {
            return@withContext Uri.fromFile(sampleFile)
        }

        val pdfDocument = PdfDocument()
        val pageWidth = 595 // Standard A4 points at 72dpi
        val pageHeight = 842

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(30, 32, 36)
            textSize = 28f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(110, 115, 122)
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val chapterHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(40, 44, 52)
            textSize = 20f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(45, 48, 55)
            textSize = 12.5f
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        }

        val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 95, 105)
            textSize = 12f
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }

        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(150, 155, 165)
            textSize = 10f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        val pagesContent = listOf(
            // Page 1: Title Cover
            listOf(
                "COVER" to "",
                "The Art of Mindful Reading" to "title",
                "A Distraction-Free Philosophy for Deep Thinkers" to "subtitle",
                "By Marcus Aurelius & Seneca" to "author"
            ),
            // Page 2: Chapter 1
            listOf(
                "CHAPTER 1" to "chapter_num",
                "The Sanctuary of Attention" to "chapter_title",
                "\"To read with whole attention is to offer the highest reverence to another mind.\"" to "quote",
                "In an age consumed by fractured notifications and perpetual interruption, the simple act of sitting with a book for undisturbed hours has become a radical sanctuary. When you open a quiet page, you step away from the relentless churn of the world and enter a spacious room built solely of thought, intention, and calm." to "body",
                "Reading deeply is not a sprint toward arbitrary metrics or page counts; it is an intimate conversation between your consciousness and the ideas formed across centuries. To rush through paragraphs is to miss the resonance of the language and the subtlety of insight." to "body",
                "When you read for hours at a time, your nervous system gently shifts into a lower frequency. The shallow breathing of modern digital anxiety gives way to a rhythmic, steady cadence. Words begin to paint landscapes behind your eyelids. You are no longer merely parsing syntax—you are dwelling in understanding." to "body"
            ),
            // Page 3: Chapter 2
            listOf(
                "CHAPTER 2" to "chapter_num",
                "The Architecture of Long Reading" to "chapter_title",
                "\"A book must be the axe for the frozen sea within us.\" — Franz Kafka" to "quote",
                "True reading comfort depends upon the physical and optical environment. Our human eyes did not evolve to stare into harsh blue glare; they were sculpted by millennia of sunlight filtering through leaves onto textured papyrus and linen paper." to "body",
                "This is why the finest reading experiences feel soft and unobtrusive. When contrast is gentle—whether warm sepia in late afternoon, or velvety charcoal with soft slate glyphs under the dim glow of a bedtime lamp—the eye muscles relax, allowing the mind to wander deep into the narrative without burning exhaustion." to "body",
                "Notice the margins around these words. White space is not absence; it is breathing room. It protects the text from the noise of the edges, framing every thought with dignity and patience." to "body"
            ),
            // Page 4: Chapter 3
            listOf(
                "CHAPTER 3" to "chapter_num",
                "The Practice of Marginalia" to "chapter_title",
                "\"I love marginalia: the faint pencil underlines, the exclamation marks, the sudden notes written in the fervor of agreement.\" — E.B. White" to "quote",
                "To underline a passage or attach a quiet note is to leave footprints along the trail of your thought. A book that has been highlighted with intention ceases to be an anonymous object from a printing press; it becomes a personal record of your encounters with truth." to "body",
                "A soft amber or sage highlight does not deface the book—it illuminates a landmark. Months or years later, when you revisit these pages, those highlighted sentences will shine like embers in a fireplace, instantly rekindling the memories of who you were when you first read them." to "body"
            ),
            // Page 5: Chapter 4
            listOf(
                "CHAPTER 4" to "chapter_num",
                "Silence, Rest, and the Final Word" to "chapter_title",
                "\"He that is well employed in his reading will never find himself solitary.\" — Thomas Fuller" to "quote",
                "Every thoughtful reader learns the wisdom of the pause. When a sentence strikes with profound weight, stop. Close your eyes for thirty seconds. Let the concept ripple across the quiet pond of your mind before moving to the next paragraph." to "body",
                "Rest your gaze upon the distant horizon. Give thanks for the silence. And when you are ready, turn the page and read on." to "body"
            )
        )

        for ((index, pageItems) in pagesContent.withIndex()) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Fill warm background
            canvas.drawColor(Color.rgb(250, 249, 246))

            if (index == 0) {
                // Cover Page
                var y = 280f
                canvas.drawText("THE ART OF", 60f, y, subtitlePaint)
                y += 45f
                canvas.drawText("MINDFUL READING", 60f, y, titlePaint)
                y += 35f
                canvas.drawText("A Distraction-Free Philosophy for Deep Thinkers", 60f, y, subtitlePaint)
                y += 60f
                // Decorative divider line
                val linePaint = Paint().apply {
                    color = Color.rgb(215, 175, 130)
                    strokeWidth = 2.5f
                }
                canvas.drawLine(60f, y, 140f, y, linePaint)
                y += 80f
                canvas.drawText("By Marcus Aurelius & Seneca", 60f, y, subtitlePaint)
                canvas.drawText("Minimalist Edition • Classical Texts", 60f, pageHeight - 80f, footerPaint)
            } else {
                var y = 90f
                for ((text, type) in pageItems) {
                    when (type) {
                        "chapter_num" -> {
                            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = Color.rgb(180, 130, 80)
                                textSize = 11f
                                letterSpacing = 0.15f
                                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                            }
                            canvas.drawText(text, 60f, y, numPaint)
                            y += 24f
                        }
                        "chapter_title" -> {
                            canvas.drawText(text, 60f, y, chapterHeaderPaint)
                            y += 32f
                        }
                        "quote" -> {
                            val quoteLines = wrapText(text, 475f, quotePaint)
                            for (line in quoteLines) {
                                canvas.drawText(line, 60f, y, quotePaint)
                                y += 18f
                            }
                            y += 18f
                        }
                        "body" -> {
                            val bodyLines = wrapText(text, 475f, bodyPaint)
                            for (line in bodyLines) {
                                canvas.drawText(line, 60f, y, bodyPaint)
                                y += 19f
                            }
                            y += 16f
                        }
                    }
                }
                // Page number footer
                val pageStr = "— ${index + 1} —"
                val textW = footerPaint.measureText(pageStr)
                canvas.drawText(pageStr, (pageWidth - textW) / 2f, pageHeight - 45f, footerPaint)
            }

            pdfDocument.finishPage(page)
        }

        FileOutputStream(sampleFile).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        Uri.fromFile(sampleFile)
    }

    private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = ""
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }
}
