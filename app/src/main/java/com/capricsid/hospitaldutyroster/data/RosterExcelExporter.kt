package com.capricsid.hospitaldutyroster.data

import com.capricsid.hospitaldutyroster.model.PreviewRow
import com.capricsid.hospitaldutyroster.model.RosterPreview
import java.io.OutputStream

class RosterExcelExporter {
    fun export(preview: RosterPreview, outputStream: OutputStream) {
        outputStream.writer(Charsets.UTF_8).use { writer ->
            writer.write(preview.toSpreadsheetXml())
        }
    }
}

private data class SummaryCounts(
    val nights: Int,
    val ct1: Int,
    val ct2: Int,
    val off: Int,
    val totalDuties: Int
)

private fun PreviewRow.summaryCounts(): SummaryCounts {
    val nights = cells.count { it == "N" }
    val ct1 = cells.count { it == "CT1" }
    val ct2 = cells.count { it == "CT2" }
    val off = cells.count { it == "O" }
    return SummaryCounts(
        nights = nights,
        ct1 = ct1,
        ct2 = ct2,
        off = off,
        totalDuties = nights + ct1 + ct2
    )
}

private fun RosterPreview.toSpreadsheetXml(): String = buildString {
    val summaryColumnCount = 5
    val totalColumns = 2 + days.size + summaryColumnCount
    val mergeAcross = totalColumns - 1

    appendLine("""<?xml version="1.0"?>""")
    appendLine("""<?mso-application progid="Excel.Sheet"?>""")
    appendLine(
        """<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet" """ +
            """xmlns:o="urn:schemas-microsoft-com:office:office" """ +
            """xmlns:x="urn:schemas-microsoft-com:office:excel" """ +
            """xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet" """ +
            """xmlns:html="http://www.w3.org/TR/REC-html40">"""
    )
    appendStyles()
    appendLine("""<Worksheet ss:Name="Roster">""")
    appendLine("<Table>")

    appendLine("""<Column ss:Width="120"/>""")
    appendLine("""<Column ss:Width="58"/>""")
    repeat(days.size) { appendLine("""<Column ss:Width="30"/>""") }
    appendLine("""<Column ss:Width="36"/>""")
    appendLine("""<Column ss:Width="40"/>""")
    appendLine("""<Column ss:Width="40"/>""")
    appendLine("""<Column ss:Width="40"/>""")
    appendLine("""<Column ss:Width="68"/>""")

    appendLine("""<Row ss:Height="32"><Cell ss:MergeAcross="$mergeAcross" ss:StyleID="Title"><Data ss:Type="String">${title.xml()}</Data></Cell></Row>""")

    appendHeaderRows(this)
    appendSection(this, "WARD TMOS", wardRows, includeSummary = true)
    appendSection(this, "NURSERY TMOS", nurseryRows, includeSummary = true)
    appendBlankRow(totalColumns)
    appendNotesSection(this, totalColumns)
    appendBlankRow(totalColumns)
    appendSection(this, "HOUSE OFFICER", hoRows, includeSummary = false)
    appendBlankRow(totalColumns)
    appendOpdSection(this, summaryColumnCount)
    appendBlankRow(totalColumns)
    appendBlankRow(totalColumns)
    appendSignatureRow(totalColumns)

    appendLine("</Table>")
    appendLine("</Worksheet>")
    appendLine("</Workbook>")
}

private fun StringBuilder.appendStyles() {
    appendLine("<Styles>")
    appendLine("""<Style ss:ID="Title"><Alignment ss:Horizontal="Center" ss:Vertical="Center"/><Font ss:Bold="1" ss:Size="14"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("""<Style ss:ID="Header"><Alignment ss:Horizontal="Center" ss:Vertical="Center"/><Font ss:Bold="1"/><Interior ss:Color="#EAF2D3" ss:Pattern="Solid"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("""<Style ss:ID="Section"><Alignment ss:Horizontal="Left" ss:Vertical="Center"/><Font ss:Bold="1"/><Interior ss:Color="#D9EAD3" ss:Pattern="Solid"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("""<Style ss:ID="Cell"><Alignment ss:Horizontal="Center" ss:Vertical="Center"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("""<Style ss:ID="NameCell"><Alignment ss:Horizontal="Left" ss:Vertical="Center"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("""<Style ss:ID="MergedNote"><Alignment ss:Horizontal="Left" ss:Vertical="Center" ss:WrapText="1"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("""<Style ss:ID="Signature"><Alignment ss:Horizontal="Left" ss:Vertical="Center"/><Font ss:Bold="1"/></Style>""")
    appendLine("</Styles>")
}

private fun RosterPreview.appendHeaderRows(builder: StringBuilder) {
    builder.append("""<Row>""")
    builder.append(headerCell("NAME"))
    builder.append(headerCell("E.CODE"))
    days.forEach { day -> builder.append(headerCell(day.dayLabel)) }
    builder.append(headerCell("N"))
    builder.append(headerCell("CT1"))
    builder.append(headerCell("CT2"))
    builder.append(headerCell("OFF"))
    builder.append(headerCell("TOTAL DUTIES"))
    builder.appendLine("</Row>")

    builder.append("""<Row>""")
    builder.append(headerCell(""))
    builder.append(headerCell(""))
    days.forEach { day -> builder.append(headerCell(day.dayNumber)) }
    builder.append(headerCell("N"))
    builder.append(headerCell("CT1"))
    builder.append(headerCell("CT2"))
    builder.append(headerCell("OFF"))
    builder.append(headerCell("TOTAL"))
    builder.appendLine("</Row>")
}

private fun RosterPreview.appendSection(
    builder: StringBuilder,
    title: String,
    rows: List<PreviewRow>,
    includeSummary: Boolean
) {
    builder.appendLine(sectionRow(title, 2 + days.size + 5))
    rows.forEach { row ->
        val summary = row.summaryCounts()
        builder.append("""<Row>""")
        builder.append(nameCell(row.label))
        builder.append(cell(row.badge))
        row.cells.forEach { duty -> builder.append(cell(duty)) }
        if (includeSummary) {
            builder.append(cell(summary.nights.toString()))
            builder.append(cell(summary.ct1.toString()))
            builder.append(cell(summary.ct2.toString()))
            builder.append(cell(summary.off.toString()))
            builder.append(cell(summary.totalDuties.toString()))
        } else {
            repeat(5) { builder.append(cell("")) }
        }
        builder.appendLine("</Row>")
    }
}

private fun RosterPreview.appendNotesSection(builder: StringBuilder, totalColumns: Int) {
    notes.forEach { note ->
        builder.appendLine(
            """<Row><Cell ss:MergeAcross="${totalColumns - 1}" ss:StyleID="MergedNote"><Data ss:Type="String">${note.xml()}</Data></Cell></Row>"""
        )
    }
}

private fun RosterPreview.appendOpdSection(builder: StringBuilder, summaryColumnCount: Int) {
    builder.appendLine(sectionRow("OPD ROSTER", 2 + days.size + summaryColumnCount))

    val allOpdDateLabels = opdTracks.flatMap { it.dates }.toSet()
    builder.append("""<Row>""")
    builder.append(cell(""))
    builder.append(cell(""))
    days.forEach { day ->
        val key = "${day.dayLabel} ${day.dayNumber}"
        builder.append(headerCell(if (key in allOpdDateLabels) day.dayLabel else ""))
    }
    repeat(summaryColumnCount) { builder.append(cell("")) }
    builder.appendLine("</Row>")

    builder.append("""<Row>""")
    builder.append(cell(""))
    builder.append(cell(""))
    days.forEach { day ->
        val key = "${day.dayLabel} ${day.dayNumber}"
        builder.append(headerCell(if (key in allOpdDateLabels) day.dayNumber else ""))
    }
    repeat(summaryColumnCount) { builder.append(cell("")) }
    builder.appendLine("</Row>")

    opdTracks.forEach { track ->
        val assignmentsByDate = track.dates.zip(track.assignments).toMap()
        builder.append("""<Row>""")
        builder.append(nameCell(track.label))
        builder.append(cell(""))
        days.forEach { day ->
            val key = "${day.dayLabel} ${day.dayNumber}"
            builder.append(cell(assignmentsByDate[key].orEmpty()))
        }
        repeat(summaryColumnCount) { builder.append(cell("")) }
        builder.appendLine("</Row>")
    }
}

private fun StringBuilder.appendBlankRow(totalColumns: Int) {
    append("""<Row>""")
    repeat(totalColumns) { append(cell("")) }
    appendLine("</Row>")
}

private fun StringBuilder.appendSignatureRow(totalColumns: Int) {
    val registrarStartIndex = totalColumns / 2 + 1
    val firstMergeAcross = registrarStartIndex - 2
    val secondMergeAcross = totalColumns - registrarStartIndex
    appendLine(
        """<Row><Cell ss:MergeAcross="$firstMergeAcross" ss:StyleID="Signature"><Data ss:Type="String">DMC SIGNATURE: _______________________</Data></Cell><Cell ss:Index="$registrarStartIndex" ss:MergeAcross="$secondMergeAcross" ss:StyleID="Signature"><Data ss:Type="String">REGISTRAR SIGNATURE: ____________________</Data></Cell></Row>"""
    )
}

private fun headerCell(value: String): String =
    """<Cell ss:StyleID="Header"><Data ss:Type="String">${value.xml()}</Data></Cell>"""

private fun sectionRow(title: String, totalColumns: Int): String =
    """<Row><Cell ss:MergeAcross="${totalColumns - 1}" ss:StyleID="Section"><Data ss:Type="String">${title.xml()}</Data></Cell></Row>"""

private fun nameCell(value: String): String =
    """<Cell ss:StyleID="NameCell"><Data ss:Type="String">${value.xml()}</Data></Cell>"""

private fun cell(value: String): String =
    """<Cell ss:StyleID="Cell"><Data ss:Type="String">${value.xml()}</Data></Cell>"""

private fun String.xml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
