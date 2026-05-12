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
    val leave: Int,
    val totalDuties: Int
)

private fun PreviewRow.summaryCounts(): SummaryCounts {
    val nights = cells.count { it == "N" }
    val ct1 = cells.count { it == "CT1" }
    val ct2 = cells.count { it == "CT2" }
    val off = cells.count { it == "O" }
    val leave = cells.count { it == "L" }
    return SummaryCounts(
        nights = nights,
        ct1 = ct1,
        ct2 = ct2,
        off = off,
        leave = leave,
        totalDuties = nights + ct1 + ct2
    )
}

private fun RosterPreview.toSpreadsheetXml(): String = buildString {
    val titleMergeAcross = days.size + 7

    appendLine("""<?xml version="1.0"?>""")
    appendLine("""<?mso-application progid="Excel.Sheet"?>""")
    appendLine("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"""")
    appendLine(""" xmlns:o="urn:schemas-microsoft-com:office:office"""")
    appendLine(""" xmlns:x="urn:schemas-microsoft-com:office:excel"""")
    appendLine(""" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"""")
    appendLine(""" xmlns:html="http://www.w3.org/TR/REC-html40">""")
    appendLine("<Styles>")
    appendLine("""<Style ss:ID="Title"><Alignment ss:Horizontal="Center" ss:Vertical="Center"/><Font ss:Bold="1" ss:Size="14"/></Style>""")
    appendLine("""<Style ss:ID="Header"><Alignment ss:Horizontal="Center" ss:Vertical="Center"/><Font ss:Bold="1"/><Interior ss:Color="#DCE6F1" ss:Pattern="Solid"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("""<Style ss:ID="Section"><Font ss:Bold="1"/><Interior ss:Color="#E2EFDA" ss:Pattern="Solid"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("""<Style ss:ID="Cell"><Alignment ss:Horizontal="Center" ss:Vertical="Center"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("""<Style ss:ID="NameCell"><Alignment ss:Horizontal="Left" ss:Vertical="Center"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("""<Style ss:ID="NoteCell"><Alignment ss:Horizontal="Left" ss:Vertical="Center" ss:WrapText="1"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("</Styles>")
    appendLine("""<Worksheet ss:Name="Sheet1">""")
    appendLine("<Table>")

    appendLine("""<Column ss:Width="140"/>""")
    appendLine("""<Column ss:Width="70"/>""")
    repeat(days.size) { appendLine("""<Column ss:Width="42"/>""") }
    appendLine("""<Column ss:Width="42"/>""")
    appendLine("""<Column ss:Width="48"/>""")
    appendLine("""<Column ss:Width="48"/>""")
    appendLine("""<Column ss:Width="48"/>""")
    appendLine("""<Column ss:Width="42"/>""")
    appendLine("""<Column ss:Width="72"/>""")

    appendLine("""<Row ss:Height="24"><Cell ss:MergeAcross="$titleMergeAcross" ss:StyleID="Title"><Data ss:Type="String">${title.xml()}</Data></Cell></Row>""")

    appendHeaderRows(this)
    appendSection(this, "WARD TMOS", wardRows)
    appendSection(this, "NURSERY TMOS", nurseryRows)
    appendNotesSection(this, notes)
    appendSection(this, "HOUSE OFFICER", hoRows)
    appendOpdSection(this)

    appendLine("</Table>")
    appendLine("</Worksheet>")
    appendLine("</Workbook>")
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
    builder.append(headerCell("L"))
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
    builder.append(headerCell("L"))
    builder.append(headerCell("TOTAL"))
    builder.appendLine("</Row>")
}

private fun RosterPreview.appendSection(builder: StringBuilder, title: String, rows: List<PreviewRow>) {
    builder.appendLine(sectionRow(title, days.size))
    rows.forEach { row ->
        val summary = row.summaryCounts()
        builder.append("""<Row>""")
        builder.append(nameCell(row.label))
        builder.append(cell(row.badge))
        row.cells.forEach { duty -> builder.append(cell(duty)) }
        builder.append(cell(summary.nights.toString()))
        builder.append(cell(summary.ct1.toString()))
        builder.append(cell(summary.ct2.toString()))
        builder.append(cell(summary.off.toString()))
        builder.append(cell(summary.leave.toString()))
        builder.append(cell(summary.totalDuties.toString()))
        builder.appendLine("</Row>")
    }
}

private fun RosterPreview.appendNotesSection(builder: StringBuilder, notes: List<String>) {
    builder.appendLine(sectionRow("OPERATIONAL NOTES", days.size))
    notes.forEach { note ->
        builder.append("""<Row>""")
        builder.append(noteCell(note))
        repeat(days.size + 7) { builder.append(cell("")) }
        builder.appendLine("</Row>")
    }
}

private fun RosterPreview.appendOpdSection(builder: StringBuilder) {
    builder.appendLine(sectionRow("OPD ROSTER", days.size))

    val opdLabelsByDay = days.associateBy(
        keySelector = { "${it.dayLabel} ${it.dayNumber}" },
        valueTransform = { it.dayLabel }
    )
    val opdNumbersByDay = days.associateBy(
        keySelector = { "${it.dayLabel} ${it.dayNumber}" },
        valueTransform = { it.dayNumber }
    )

    val allOpdDateLabels = opdTracks.flatMap { it.dates }.toSet()

    builder.append("""<Row>""")
    builder.append(cell(""))
    builder.append(cell(""))
    days.forEach { day ->
        val key = "${day.dayLabel} ${day.dayNumber}"
        builder.append(headerCell(if (key in allOpdDateLabels) opdLabelsByDay[key].orEmpty() else ""))
    }
    repeat(6) { builder.append(cell("")) }
    builder.appendLine("</Row>")

    builder.append("""<Row>""")
    builder.append(cell(""))
    builder.append(cell(""))
    days.forEach { day ->
        val key = "${day.dayLabel} ${day.dayNumber}"
        builder.append(headerCell(if (key in allOpdDateLabels) opdNumbersByDay[key].orEmpty() else ""))
    }
    repeat(6) { builder.append(cell("")) }
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
        repeat(6) { builder.append(cell("")) }
        builder.appendLine("</Row>")
    }
}

private fun headerCell(value: String): String =
    """<Cell ss:StyleID="Header"><Data ss:Type="String">${value.xml()}</Data></Cell>"""

private fun sectionRow(title: String, dayCount: Int): String =
    """<Row><Cell ss:MergeAcross="${dayCount + 7}" ss:StyleID="Section"><Data ss:Type="String">${title.xml()}</Data></Cell></Row>"""

private fun nameCell(value: String): String =
    """<Cell ss:StyleID="NameCell"><Data ss:Type="String">${value.xml()}</Data></Cell>"""

private fun noteCell(value: String): String =
    """<Cell ss:StyleID="NoteCell"><Data ss:Type="String">${value.xml()}</Data></Cell>"""

private fun cell(value: String): String =
    """<Cell ss:StyleID="Cell"><Data ss:Type="String">${value.xml()}</Data></Cell>"""

private fun String.xml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
