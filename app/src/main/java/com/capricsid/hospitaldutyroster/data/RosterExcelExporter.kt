package com.capricsid.hospitaldutyroster.data

import com.capricsid.hospitaldutyroster.model.OpdTrack
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

private fun RosterPreview.toSpreadsheetXml(): String = buildString {
    appendLine("""<?xml version="1.0"?>""")
    appendLine("""<?mso-application progid="Excel.Sheet"?>""")
    appendLine("""<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"""")
    appendLine(""" xmlns:o="urn:schemas-microsoft-com:office:office"""")
    appendLine(""" xmlns:x="urn:schemas-microsoft-com:office:excel"""")
    appendLine(""" xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"""")
    appendLine(""" xmlns:html="http://www.w3.org/TR/REC-html40">""")
    appendLine("<Styles>")
    appendLine("""<Style ss:ID="Title"><Font ss:Bold="1" ss:Size="14"/><Alignment ss:Horizontal="Center"/></Style>""")
    appendLine("""<Style ss:ID="Header"><Font ss:Bold="1"/><Interior ss:Color="#D9EAF7" ss:Pattern="Solid"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("""<Style ss:ID="Section"><Font ss:Bold="1"/><Interior ss:Color="#DDEED6" ss:Pattern="Solid"/></Style>""")
    appendLine("""<Style ss:ID="Cell"><Alignment ss:Horizontal="Center"/><Borders><Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Left" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Right" ss:LineStyle="Continuous" ss:Weight="1"/><Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="1"/></Borders></Style>""")
    appendLine("</Styles>")
    appendLine("""<Worksheet ss:Name="Roster">""")
    appendLine("<Table>")
    appendLine("""<Column ss:Width="130"/><Column ss:Width="70"/>""")
    repeat(days.size) { appendLine("""<Column ss:Width="42"/>""") }
    appendLine("""<Column ss:Width="180"/>""")
    appendLine("""<Row><Cell ss:MergeAcross="${days.size + 2}" ss:StyleID="Title"><Data ss:Type="String">${title.xml()}</Data></Cell></Row>""")
    appendHeaderRows(this)
    appendSection("WARD TMOS", wardRows, days.size)
    appendSection("NURSERY TMOS", nurseryRows, days.size)
    appendSection("HOUSE OFFICER", hoRows, days.size)
    appendLine("""<Row><Cell ss:MergeAcross="${days.size + 2}" ss:StyleID="Section"><Data ss:Type="String">OPERATIONAL NOTES</Data></Cell></Row>""")
    notes.forEach { note ->
        appendLine("""<Row><Cell ss:MergeAcross="${days.size + 2}"><Data ss:Type="String">${note.xml()}</Data></Cell></Row>""")
    }
    appendOpd(opdTracks, days.size)
    appendLine("</Table>")
    appendLine("</Worksheet>")
    appendLine("</Workbook>")
}

private fun RosterPreview.appendHeaderRows(builder: StringBuilder) {
    builder.append("""<Row><Cell ss:StyleID="Header"><Data ss:Type="String">NAME</Data></Cell><Cell ss:StyleID="Header"><Data ss:Type="String">E.CODE</Data></Cell>""")
    days.forEach { day ->
        builder.append("""<Cell ss:StyleID="Header"><Data ss:Type="String">${day.dayLabel.xml()}</Data></Cell>""")
    }
    builder.appendLine("""<Cell ss:StyleID="Header"><Data ss:Type="String">SUMMARY</Data></Cell></Row>""")
    builder.append("""<Row><Cell ss:StyleID="Header"><Data ss:Type="String"></Data></Cell><Cell ss:StyleID="Header"><Data ss:Type="String"></Data></Cell>""")
    days.forEach { day ->
        builder.append("""<Cell ss:StyleID="Header"><Data ss:Type="String">${day.dayNumber.xml()}</Data></Cell>""")
    }
    builder.appendLine("""<Cell ss:StyleID="Header"><Data ss:Type="String">N / CT1 / CT2 / O / L</Data></Cell></Row>""")
}

private fun StringBuilder.appendSection(title: String, rows: List<PreviewRow>, dayCount: Int) {
    appendLine("""<Row><Cell ss:MergeAcross="${dayCount + 2}" ss:StyleID="Section"><Data ss:Type="String">${title.xml()}</Data></Cell></Row>""")
    rows.forEach { row ->
        append("""<Row><Cell ss:StyleID="Cell"><Data ss:Type="String">${row.label.xml()}</Data></Cell><Cell ss:StyleID="Cell"><Data ss:Type="String">${row.badge.xml()}</Data></Cell>""")
        row.cells.forEach { cell ->
            append("""<Cell ss:StyleID="Cell"><Data ss:Type="String">${cell.xml()}</Data></Cell>""")
        }
        appendLine("""<Cell ss:StyleID="Cell"><Data ss:Type="String">${row.summary.xml()}</Data></Cell></Row>""")
    }
}

private fun StringBuilder.appendOpd(tracks: List<OpdTrack>, dayCount: Int) {
    appendLine("""<Row><Cell ss:MergeAcross="${dayCount + 2}" ss:StyleID="Section"><Data ss:Type="String">OPD ROSTER</Data></Cell></Row>""")
    tracks.forEach { track ->
        append("""<Row><Cell ss:StyleID="Header"><Data ss:Type="String">${track.label.xml()}</Data></Cell><Cell ss:StyleID="Header"><Data ss:Type="String"></Data></Cell>""")
        track.dates.forEach { date ->
            append("""<Cell ss:StyleID="Header"><Data ss:Type="String">${date.xml()}</Data></Cell>""")
        }
        appendLine("</Row>")
        append("""<Row><Cell><Data ss:Type="String"></Data></Cell><Cell><Data ss:Type="String"></Data></Cell>""")
        track.assignments.forEach { name ->
            append("""<Cell ss:StyleID="Cell"><Data ss:Type="String">${name.xml()}</Data></Cell>""")
        }
        appendLine("</Row>")
    }
}

private fun String.xml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")
