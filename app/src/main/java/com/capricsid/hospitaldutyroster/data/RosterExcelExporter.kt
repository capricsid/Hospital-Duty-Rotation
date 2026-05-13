package com.capricsid.hospitaldutyroster.data

import com.capricsid.hospitaldutyroster.model.PreviewRow
import com.capricsid.hospitaldutyroster.model.RosterPreview
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RosterExcelExporter {
    fun export(preview: RosterPreview, outputStream: OutputStream) {
        ZipOutputStream(outputStream).use { zip ->
            zip.writeEntry("[Content_Types].xml", contentTypesXml())
            zip.writeEntry("_rels/.rels", packageRelsXml())
            zip.writeEntry("docProps/app.xml", appPropertiesXml())
            zip.writeEntry("docProps/core.xml", corePropertiesXml())
            zip.writeEntry("xl/workbook.xml", workbookXml())
            zip.writeEntry("xl/_rels/workbook.xml.rels", workbookRelsXml())
            zip.writeEntry("xl/styles.xml", stylesXml())
            zip.writeEntry("xl/worksheets/sheet1.xml", preview.toWorksheetXml())
        }
    }
}

private enum class XlsxStyle(val id: Int) {
    Default(0),
    Title(1),
    Header(2),
    Section(3),
    Cell(4),
    NameCell(5),
    Note(6),
    Signature(7)
}

private data class XlsxCell(
    val value: String = "",
    val style: XlsxStyle = XlsxStyle.Cell,
    val columnSpan: Int = 1
)

private data class XlsxRow(
    val cells: List<XlsxCell>,
    val height: Int? = null
)

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

private fun RosterPreview.toWorksheetXml(): String {
    val rows = buildRows()
    val mergeRefs = mutableListOf<String>()
    val maxColumns = 2 + days.size + SummaryColumnCount

    return buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        appendLine("""<sheetViews><sheetView workbookViewId="0"/></sheetViews>""")
        appendLine("""<sheetFormatPr defaultRowHeight="15"/>""")
        appendColumns(maxColumns)
        appendLine("<sheetData>")
        rows.forEachIndexed { rowIndex, row ->
            val rowNumber = rowIndex + 1
            val height = row.height?.let { """ ht="$it" customHeight="1"""" }.orEmpty()
            append("""<row r="$rowNumber"$height>""")
            var column = 1
            row.cells.forEach { cell ->
                val ref = "${columnName(column)}$rowNumber"
                append(xlsxCell(ref, cell))
                if (cell.columnSpan > 1) {
                    mergeRefs += "$ref:${columnName(column + cell.columnSpan - 1)}$rowNumber"
                }
                column += cell.columnSpan
            }
            appendLine("</row>")
        }
        appendLine("</sheetData>")
        if (mergeRefs.isNotEmpty()) {
            appendLine("""<mergeCells count="${mergeRefs.size}">""")
            mergeRefs.forEach { ref -> appendLine("""<mergeCell ref="$ref"/>""") }
            appendLine("</mergeCells>")
        }
        appendLine("""<pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>""")
        appendLine("</worksheet>")
    }
}

private fun RosterPreview.buildRows(): List<XlsxRow> {
    val totalColumns = 2 + days.size + SummaryColumnCount
    val rows = mutableListOf<XlsxRow>()

    rows += XlsxRow(
        cells = listOf(XlsxCell(title, XlsxStyle.Title, totalColumns)),
        height = 32
    )
    rows += XlsxRow(
        cells = listOf(XlsxCell("NAME", XlsxStyle.Header), XlsxCell("E.CODE", XlsxStyle.Header)) +
            days.map { XlsxCell(it.dayLabel, XlsxStyle.Header) } +
            listOf(
                XlsxCell("N", XlsxStyle.Header),
                XlsxCell("CT1", XlsxStyle.Header),
                XlsxCell("CT2", XlsxStyle.Header),
                XlsxCell("OFF", XlsxStyle.Header),
                XlsxCell("TOTAL DUTIES", XlsxStyle.Header)
            )
    )
    rows += XlsxRow(
        cells = listOf(XlsxCell("", XlsxStyle.Header), XlsxCell("", XlsxStyle.Header)) +
            days.map { XlsxCell(it.dayNumber, XlsxStyle.Header) } +
            listOf(
                XlsxCell("N", XlsxStyle.Header),
                XlsxCell("CT1", XlsxStyle.Header),
                XlsxCell("CT2", XlsxStyle.Header),
                XlsxCell("OFF", XlsxStyle.Header),
                XlsxCell("TOTAL", XlsxStyle.Header)
            )
    )

    rows.addSection("WARD TMOS", wardRows, includeSummary = true, totalColumns = totalColumns)
    rows.addSection("NURSERY TMOS", nurseryRows, includeSummary = true, totalColumns = totalColumns)
    rows += blankRow(totalColumns)
    notes.forEach { note ->
        rows += XlsxRow(listOf(XlsxCell(note, XlsxStyle.Note, totalColumns)))
    }
    rows += blankRow(totalColumns)
    rows.addSection("HOUSE OFFICER", hoRows, includeSummary = false, totalColumns = totalColumns)
    rows += blankRow(totalColumns)
    rows.addOpdRows(this, totalColumns)
    rows += blankRow(totalColumns)
    rows += blankRow(totalColumns)
    rows += XlsxRow(
        listOf(
            XlsxCell("DMC SIGNATURE: _______________________", XlsxStyle.Signature, totalColumns / 2),
            XlsxCell("REGISTRAR SIGNATURE: ____________________", XlsxStyle.Signature, totalColumns - totalColumns / 2)
        )
    )

    return rows
}

private fun MutableList<XlsxRow>.addSection(
    title: String,
    previewRows: List<PreviewRow>,
    includeSummary: Boolean,
    totalColumns: Int
) {
    this += XlsxRow(listOf(XlsxCell(title, XlsxStyle.Section, totalColumns)))
    previewRows.forEach { row ->
        val summary = row.summaryCounts()
        this += XlsxRow(
            cells = listOf(XlsxCell(row.label, XlsxStyle.NameCell), XlsxCell(row.badge, XlsxStyle.Cell)) +
                row.cells.map { XlsxCell(it, XlsxStyle.Cell) } +
                if (includeSummary) {
                    listOf(
                        XlsxCell(summary.nights.toString(), XlsxStyle.Cell),
                        XlsxCell(summary.ct1.toString(), XlsxStyle.Cell),
                        XlsxCell(summary.ct2.toString(), XlsxStyle.Cell),
                        XlsxCell(summary.off.toString(), XlsxStyle.Cell),
                        XlsxCell(summary.totalDuties.toString(), XlsxStyle.Cell)
                    )
                } else {
                    List(SummaryColumnCount) { XlsxCell("", XlsxStyle.Cell) }
                }
        )
    }
}

private fun MutableList<XlsxRow>.addOpdRows(preview: RosterPreview, totalColumns: Int) {
    val opdDateLabels = preview.opdTracks.flatMap { it.dates }.distinct()
    val blocks = listOf(opdDateLabels.take(14), opdDateLabels.drop(14)).filter { it.isNotEmpty() }

    blocks.forEachIndexed { blockIndex, block ->
        if (blockIndex > 0) this += blankRow(totalColumns)
        addOpdBlock(preview, block, totalColumns)
    }
}

private fun MutableList<XlsxRow>.addOpdBlock(
    preview: RosterPreview,
    opdDateLabels: List<String>,
    totalColumns: Int
) {
    val daysByLabel = preview.days.associateBy { "${it.dayLabel} ${it.dayNumber}" }
    val usedColumns = 1 + opdDateLabels.size * OpdDateColumnSpan
    val trailing = (totalColumns - usedColumns).coerceAtLeast(0)

    this += XlsxRow(listOf(XlsxCell("OPD ROSTER", XlsxStyle.Section, totalColumns)))
    this += XlsxRow(
        cells = listOf(XlsxCell("", XlsxStyle.Cell)) +
            opdDateLabels.map { XlsxCell(daysByLabel[it]?.dayLabel.orEmpty(), XlsxStyle.Header, OpdDateColumnSpan) } +
            List(trailing) { XlsxCell("", XlsxStyle.Cell) }
    )
    this += XlsxRow(
        cells = listOf(XlsxCell("", XlsxStyle.Cell)) +
            opdDateLabels.map { XlsxCell(daysByLabel[it]?.dayNumber.orEmpty(), XlsxStyle.Header, OpdDateColumnSpan) } +
            List(trailing) { XlsxCell("", XlsxStyle.Cell) }
    )
    preview.opdTracks.forEach { track ->
        val assignmentsByDate = track.dates.zip(track.assignments).toMap()
        this += XlsxRow(
            cells = listOf(XlsxCell(track.label, XlsxStyle.NameCell)) +
                opdDateLabels.map { XlsxCell(assignmentsByDate[it].orEmpty(), XlsxStyle.Cell, OpdDateColumnSpan) } +
                List(trailing) { XlsxCell("", XlsxStyle.Cell) }
        )
    }
}

private fun blankRow(totalColumns: Int): XlsxRow =
    XlsxRow(List(totalColumns) { XlsxCell("", XlsxStyle.Cell) })

private fun StringBuilder.appendColumns(maxColumns: Int) {
    appendLine("<cols>")
    appendLine("""<col min="1" max="1" width="18" customWidth="1"/>""")
    appendLine("""<col min="2" max="2" width="9" customWidth="1"/>""")
    appendLine("""<col min="3" max="${2 + 31}" width="5" customWidth="1"/>""")
    appendLine("""<col min="34" max="37" width="7" customWidth="1"/>""")
    appendLine("""<col min="38" max="$maxColumns" width="13" customWidth="1"/>""")
    appendLine("</cols>")
}

private fun xlsxCell(ref: String, cell: XlsxCell): String {
    val style = cell.style.id
    if (cell.value.isBlank()) {
        return """<c r="$ref" s="$style"/>"""
    }
    return """<c r="$ref" s="$style" t="inlineStr"><is><t>${cell.value.xml()}</t></is></c>"""
}

private fun ZipOutputStream.writeEntry(path: String, content: String) {
    putNextEntry(ZipEntry(path))
    write(content.toByteArray(Charsets.UTF_8))
    closeEntry()
}

private fun columnName(columnNumber: Int): String {
    var number = columnNumber
    val name = StringBuilder()
    while (number > 0) {
        val remainder = (number - 1) % 26
        name.insert(0, ('A'.code + remainder).toChar())
        number = (number - 1) / 26
    }
    return name.toString()
}

private fun contentTypesXml(): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>
<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

private fun packageRelsXml(): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""

private fun workbookXml(): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets><sheet name="Roster" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

private fun workbookRelsXml(): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

private fun appPropertiesXml(): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties" xmlns:vt="http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes">
<Application>Hospital Duty Roster</Application>
</Properties>"""

private fun corePropertiesXml(): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:dcmitype="http://purl.org/dc/dcmitype/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
<dc:creator>Hospital Duty Roster</dc:creator>
<cp:lastModifiedBy>Hospital Duty Roster</cp:lastModifiedBy>
</cp:coreProperties>"""

private fun stylesXml(): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<fonts count="3"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="14"/><name val="Calibri"/></font></fonts>
<fills count="4"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFEAF2D3"/><bgColor indexed="64"/></patternFill></fill><fill><patternFill patternType="solid"><fgColor rgb="FFD9EAD3"/><bgColor indexed="64"/></patternFill></fill></fills>
<borders count="2"><border><left/><right/><top/><bottom/><diagonal/></border><border><left style="thin"><color auto="1"/></left><right style="thin"><color auto="1"/></right><top style="thin"><color auto="1"/></top><bottom style="thin"><color auto="1"/></bottom><diagonal/></border></borders>
<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
<cellXfs count="8">
<xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
<xf numFmtId="0" fontId="2" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="1" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="center" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
<xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyBorder="1" applyAlignment="1"><alignment horizontal="left" vertical="center" wrapText="1"/></xf>
<xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1" applyAlignment="1"><alignment horizontal="left" vertical="center"/></xf>
</cellXfs>
<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
</styleSheet>"""

private fun String.xml(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private const val SummaryColumnCount = 5
private const val OpdDateColumnSpan = 2
