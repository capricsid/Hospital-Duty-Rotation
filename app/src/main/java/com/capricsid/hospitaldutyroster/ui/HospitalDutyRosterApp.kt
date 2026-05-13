package com.capricsid.hospitaldutyroster.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.ModeEdit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.capricsid.hospitaldutyroster.data.RosterExcelExporter
import com.capricsid.hospitaldutyroster.data.StaffRepository
import com.capricsid.hospitaldutyroster.model.ExperienceLevel
import com.capricsid.hospitaldutyroster.model.OpdCategory
import com.capricsid.hospitaldutyroster.model.PreviewRow
import com.capricsid.hospitaldutyroster.model.RosterRequest
import com.capricsid.hospitaldutyroster.model.RosterPreview
import com.capricsid.hospitaldutyroster.model.StaffMember
import com.capricsid.hospitaldutyroster.model.StaffType
import com.capricsid.hospitaldutyroster.model.TmoSection
import com.capricsid.hospitaldutyroster.model.buildRoster
import com.capricsid.hospitaldutyroster.model.defaultExportFileName
import com.capricsid.hospitaldutyroster.model.parseLeaveDates
import com.capricsid.hospitaldutyroster.model.sortedForRoster
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private enum class AppTab(val label: String) {
    STAFF("Staff"),
    ROSTER("Roster")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HospitalDutyRosterApp(repository: StaffRepository) {
    val loadedStaff = remember { repository.loadStaff().sortedForRoster() }
    val staff = remember { mutableStateListOf<StaffMember>().apply { addAll(loadedStaff) } }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.STAFF) }
    var showEditor by remember { mutableStateOf(false) }
    var editingStaffId by remember { mutableStateOf<String?>(null) }
    var selectedMonth by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var preview by remember { mutableStateOf<RosterPreview?>(null) }
    val includedStaff = remember {
        mutableStateMapOf<String, Boolean>().apply {
            loadedStaff.forEach { member -> put(member.id, member.active) }
        }
    }
    val leaveInputs = remember { mutableStateMapOf<String, String>() }
    val leaveEnabled = remember { mutableStateMapOf<String, Boolean>() }

    fun createRoster(): Result<RosterPreview> {
        val activeStaff = staff.filter { it.active }
        val selectedIds = activeStaff
            .filter { includedStaff[it.id] != false }
            .map { it.id }
            .toSet()
        if (selectedIds.isEmpty()) {
            return Result.failure(IllegalStateException("Select at least one doctor for this month."))
        }
        val leaves = activeStaff.associate { member ->
            member.id to if (leaveEnabled[member.id] == true) {
                parseLeaveDates(leaveInputs[member.id].orEmpty(), selectedMonth)
            } else {
                emptySet()
            }
        }
        val roster = buildRoster(
            RosterRequest(
                rosterMonth = selectedMonth,
                staff = staff.toList(),
                includedStaffIds = selectedIds,
                leavesByStaffId = leaves
            )
        )
        preview = roster
        return Result.success(roster)
    }

    fun saveStaff(updated: List<StaffMember>) {
        staff.clear()
        staff.addAll(updated.sortedForRoster())
        staff.forEach { member ->
            includedStaff.putIfAbsent(member.id, member.active)
            leaveEnabled.putIfAbsent(member.id, false)
        }
        repository.saveStaff(staff.toList())
        preview = null
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hospital Duty Roster")
                        Text(
                            text = "v1.0.11 - Template styled XLSX",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == AppTab.STAFF) {
                FloatingActionButton(onClick = {
                    editingStaffId = null
                    showEditor = true
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Add staff")
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == AppTab.STAFF,
                    onClick = { selectedTab = AppTab.STAFF },
                    icon = { Icon(Icons.Outlined.Group, contentDescription = null) },
                    label = { Text(AppTab.STAFF.label) }
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.ROSTER,
                    onClick = { selectedTab = AppTab.ROSTER },
                    icon = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                    label = { Text(AppTab.ROSTER.label) }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            AppTab.STAFF -> StaffScreen(
                modifier = Modifier.padding(padding),
                staff = staff,
                onEdit = { member ->
                    editingStaffId = member.id
                    showEditor = true
                },
                onToggleActive = { member ->
                    saveStaff(
                        staff.map {
                            if (it.id == member.id) it.copy(active = !it.active) else it
                        }
                    )
                }
            )

            AppTab.ROSTER -> RosterScreen(
                modifier = Modifier.padding(padding),
                staff = staff.toList(),
                selectedMonth = selectedMonth,
                preview = preview,
                includedStaff = includedStaff,
                leaveInputs = leaveInputs,
                leaveEnabled = leaveEnabled,
                onPreviousMonth = {
                    selectedMonth = selectedMonth.minusMonths(1)
                    preview = null
                },
                onNextMonth = {
                    selectedMonth = selectedMonth.plusMonths(1)
                    preview = null
                },
                onCreateRoster = ::createRoster
            )
        }
    }

    if (showEditor) {
        StaffEditorDialog(
            initial = staff.firstOrNull { it.id == editingStaffId },
            onDismiss = { showEditor = false },
            onSave = { member ->
                val updated = staff.toMutableList()
                val existingIndex = updated.indexOfFirst { it.id == member.id }
                if (existingIndex >= 0) {
                    updated[existingIndex] = member
                } else {
                    updated += member.copy(
                        displayOrder = (staff.maxOfOrNull { it.displayOrder } ?: -1) + 1
                    )
                }
                saveStaff(updated)
                showEditor = false
            }
        )
    }
}

@Composable
private fun StaffScreen(
    modifier: Modifier = Modifier,
    staff: List<StaffMember>,
    onEdit: (StaffMember) -> Unit,
    onToggleActive: (StaffMember) -> Unit
) {
    val activeCount = staff.count { it.active }
    val tmoCount = staff.count { it.active && it.staffType == StaffType.TMO }
    val hoCount = staff.count { it.active && it.staffType == StaffType.HO }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            SummaryHeroCard(
                title = "Persistent Master Staff List",
                subtitle = "Saved staff stay in the app for future rosters. You can add new names, edit details, or mark someone inactive without losing history.",
                chips = listOf(
                    "Active $activeCount",
                    "TMOs $tmoCount",
                    "HOs $hoCount"
                )
            )
        }

        item {
            SectionHeader("Current Staff")
        }

        items(staff, key = { it.id }) { member ->
            StaffCard(
                member = member,
                onEdit = { onEdit(member) },
                onToggleActive = { onToggleActive(member) }
            )
        }

        item {
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun StaffCard(
    member: StaffMember,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (member.active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(Modifier.weight(1f)) {
                    Text(member.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(member.staffType.name)
                            if (member.staffType == StaffType.TMO) {
                                append(" • ")
                                append(member.section?.name ?: "UNSET")
                                append(" • ")
                                append(member.employeeCode.ifBlank { "NO CODE" })
                            }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Outlined.ModeEdit, contentDescription = "Edit")
                }
            }

            StaffTagRow(member)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (member.active) "Included by default" else "Inactive", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = member.active, onCheckedChange = { onToggleActive() })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffTagRow(member: StaffMember) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(onClick = {}, label = { Text(member.opdCategory.label) })
        if (member.staffType == StaffType.TMO) {
            AssistChip(onClick = {}, label = { Text(member.experienceLevel.name) })
            if (!member.ct2Eligible) {
                AssistChip(onClick = {}, label = { Text("No CT2") })
            }
            if (member.reducedNights) {
                AssistChip(onClick = {}, label = { Text("Reduced nights") })
            }
            if (member.weekendPreferenceOff) {
                AssistChip(onClick = {}, label = { Text("Weekend off pref") })
            }
        }
    }
}

@Composable
private fun RosterScreen(
    modifier: Modifier = Modifier,
    staff: List<StaffMember>,
    selectedMonth: YearMonth,
    preview: RosterPreview?,
    includedStaff: MutableMap<String, Boolean>,
    leaveInputs: MutableMap<String, String>,
    leaveEnabled: MutableMap<String, Boolean>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCreateRoster: () -> Result<RosterPreview>
) {
    val context = LocalContext.current
    val exporter = remember { RosterExcelExporter() }
    var pendingExport by remember { mutableStateOf<RosterPreview?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        val roster = pendingExport
        if (uri != null && roster != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    exporter.export(roster, stream)
                } ?: error("Could not open export file")
            }.onSuccess {
                Toast.makeText(context, "Roster exported", Toast.LENGTH_SHORT).show()
            }.onFailure { error ->
                Toast.makeText(context, "Export failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }
        pendingExport = null
    }
    val activeStaff = staff.filter { it.active }
    val selectedDoctors = activeStaff.count { includedStaff[it.id] != false }
    val selectedTmos = activeStaff.count { it.staffType == StaffType.TMO && includedStaff[it.id] != false }
    val selectedHos = activeStaff.count { it.staffType == StaffType.HO && includedStaff[it.id] != false }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            SummaryHeroCard(
                title = "Present Month Roster",
                subtitle = "Select the doctors for this month, enter leaves, then create a 24 to 23 duty roster with an Excel export.",
                chips = listOf(
                    "Selected $selectedDoctors",
                    "TMOs $selectedTmos",
                    "HOs $selectedHos"
                )
            )
        }

        item {
            MonthSelectorCard(
                selectedMonth = selectedMonth,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onCreateRoster = onCreateRoster
            )
        }

        item {
            DoctorSelectionCard(
                staff = activeStaff,
                includedStaff = includedStaff,
                leaveInputs = leaveInputs,
                leaveEnabled = leaveEnabled
            )
        }

        if (preview == null) {
            item {
                SectionHeader("Roster")
                Text(
                    "No roster has been created yet for this month.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionHeader("Created Roster")
                    Button(
                        onClick = {
                            pendingExport = preview
                            exportLauncher.launch(defaultExportFileName(selectedMonth))
                        }
                    ) {
                        Icon(Icons.Outlined.Description, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Excel")
                    }
                }
            }

            item {
                RosterPreviewCard(preview = preview)
            }
        }

        item {
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun MonthSelectorCard(
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCreateRoster: () -> Result<RosterPreview>
) {
    val context = LocalContext.current
    OutlinedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Roster cycle", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onPreviousMonth) {
                    Text("Previous")
                }
                Text(
                    text = selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + selectedMonth.year,
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(onClick = onNextMonth) {
                    Text("Next")
                }
            }
            Button(
                onClick = {
                    runCatching { onCreateRoster() }
                        .fold(
                            onSuccess = { result ->
                                result.onFailure { error ->
                                    Toast.makeText(context, "Roster failed: ${error.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            onFailure = { error ->
                                Toast.makeText(context, "Roster failed: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                        )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Create roster")
            }
        }
    }
}

@Composable
private fun DoctorSelectionCard(
    staff: List<StaffMember>,
    includedStaff: MutableMap<String, Boolean>,
    leaveInputs: MutableMap<String, String>,
    leaveEnabled: MutableMap<String, Boolean>
) {
    OutlinedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Doctors and leaves", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Doctors can stay included in the roster by default. Turn on leave only for the staff who need leave dates or ranges.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            staff.sortedForRoster()
                .forEach { member ->
                    DoctorRosterInputRow(
                        member = member,
                        included = includedStaff[member.id] != false,
                        leaveSelected = leaveEnabled[member.id] == true,
                        leaveText = leaveInputs[member.id].orEmpty(),
                        onIncludedChange = { includedStaff[member.id] = it },
                        onLeaveSelectedChange = {
                            leaveEnabled[member.id] = it
                            if (!it) {
                                leaveInputs[member.id] = ""
                            }
                        },
                        onLeaveChange = { leaveInputs[member.id] = it }
                    )
                }
        }
    }
}

@Composable
private fun DoctorRosterInputRow(
    member: StaffMember,
    included: Boolean,
    leaveSelected: Boolean,
    leaveText: String,
    onIncludedChange: (Boolean) -> Unit,
    onLeaveSelectedChange: (Boolean) -> Unit,
    onLeaveChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Checkbox(checked = included, onCheckedChange = onIncludedChange)
                Column {
                    Text(member.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(member.staffType.name)
                            if (member.staffType == StaffType.TMO) {
                                append(" • ")
                                append(member.section?.name ?: "UNSET")
                                append(" • ")
                                append(member.experienceLevel.name)
                            }
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (included) "Included in roster" else "Excluded from roster",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Leave", style = MaterialTheme.typography.bodySmall)
                Checkbox(
                    checked = leaveSelected,
                    onCheckedChange = {
                        if (included) {
                            onLeaveSelectedChange(it)
                        }
                    },
                    enabled = included
                )
            }
        }
        if (included && leaveSelected) {
            OutlinedTextField(
                value = leaveText,
                onValueChange = onLeaveChange,
                label = { Text("Leave dates for ${member.name}") },
                placeholder = { Text("24, 28-30, 5, 2026-06-10") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun RosterPreviewCard(preview: RosterPreview) {
    val horizontalScroll = rememberScrollState()

    OutlinedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(preview.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.horizontalScroll(horizontalScroll)) {
                    HeaderRow(preview)
                    SectionHeaderRow("WARD TMOS")
                    preview.wardRows.forEach { PreviewDataRow(it) }
                    SectionHeaderRow("NURSERY TMOS")
                    preview.nurseryRows.forEach { PreviewDataRow(it) }
                    SectionHeaderRow("HOUSE OFFICER")
                    preview.hoRows.forEach { PreviewDataRow(it) }
                    SectionHeaderRow("OPD ROSTER")
                    preview.opdTracks.forEach { track ->
                        OpdTrackRow(track.label, track.dates, track.assignments)
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Operational notes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                preview.notes.forEach { note ->
                    Text("• $note", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(preview: RosterPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            PreviewCell("NAME", 140.dp, true)
            PreviewCell("E.CODE", 90.dp, true)
            preview.days.forEach { day ->
                PreviewCell(day.dayLabel, 56.dp, true)
            }
            PreviewCell("N", 48.dp, true)
            PreviewCell("CT1", 52.dp, true)
            PreviewCell("CT2", 52.dp, true)
            PreviewCell("OFF", 52.dp, true)
            PreviewCell("TOTAL", 64.dp, true)
        }
        Row {
            PreviewCell("", 140.dp, false)
            PreviewCell("", 90.dp, false)
            preview.days.forEach { day ->
                PreviewCell(day.dayNumber, 56.dp, false)
            }
            PreviewCell("N", 48.dp, false)
            PreviewCell("CT1", 52.dp, false)
            PreviewCell("CT2", 52.dp, false)
            PreviewCell("OFF", 52.dp, false)
            PreviewCell("TOTAL", 64.dp, false)
        }
    }
}

@Composable
private fun SectionHeaderRow(title: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .padding(top = 12.dp, bottom = 6.dp)
            .fillMaxWidth()
    ) {
        Text(
            title,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PreviewDataRow(row: PreviewRow) {
    val summary = row.cells.let { cells ->
        val nights = cells.count { it == "N" }
        val ct1 = cells.count { it == "CT1" }
        val ct2 = cells.count { it == "CT2" }
        val off = cells.count { it == "O" }
        val total = nights + ct1 + ct2
        listOf(nights.toString(), ct1.toString(), ct2.toString(), off.toString(), total.toString())
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PreviewCell(row.label, 140.dp, false)
        PreviewCell(row.badge, 90.dp, false)
        row.cells.forEach { cell ->
            PreviewCell(cell, 56.dp, false)
        }
        summary.forEachIndexed { index, value ->
            val width = when (index) {
                0 -> 48.dp
                4 -> 64.dp
                else -> 52.dp
            }
            PreviewCell(value, width, false)
        }
    }
}

@Composable
private fun OpdTrackRow(label: String, dates: List<String>, assignments: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.Medium)
        Text(
            "Dates: ${dates.joinToString(", ")}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "Assignments: ${assignments.joinToString(", ")}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
    }
}

@Composable
private fun PreviewCell(text: String, width: androidx.compose.ui.unit.Dp, emphasis: Boolean) {
    Surface(
        tonalElevation = if (emphasis) 2.dp else 0.dp,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text,
                style = if (emphasis) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodySmall,
                fontWeight = if (emphasis) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun SummaryHeroCard(title: String, subtitle: String, chips: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { chip ->
                    AssistChip(onClick = {}, label = { Text(chip) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffEditorDialog(
    initial: StaffMember?,
    onDismiss: () -> Unit,
    onSave: (StaffMember) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name.orEmpty()) }
    var employeeCode by remember(initial) { mutableStateOf(initial?.employeeCode.orEmpty()) }
    var staffType by remember(initial) { mutableStateOf(initial?.staffType ?: StaffType.TMO) }
    var section by remember(initial) { mutableStateOf(initial?.section ?: TmoSection.WARD) }
    var experienceLevel by remember(initial) { mutableStateOf(initial?.experienceLevel ?: ExperienceLevel.MID) }
    var ct2Eligible by remember(initial) { mutableStateOf(initial?.ct2Eligible ?: true) }
    var reducedNights by remember(initial) { mutableStateOf(initial?.reducedNights ?: false) }
    var weekendPreferenceOff by remember(initial) { mutableStateOf(initial?.weekendPreferenceOff ?: false) }
    var opdCategory by remember(initial) { mutableStateOf(initial?.opdCategory ?: OpdCategory.GENERAL) }
    var active by remember(initial) { mutableStateOf(initial?.active ?: true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            StaffMember(
                                id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                                name = name.trim().uppercase(Locale.ENGLISH),
                                employeeCode = employeeCode.trim(),
                                staffType = staffType,
                                section = if (staffType == StaffType.TMO) section else null,
                                displayOrder = initial?.displayOrder ?: Int.MAX_VALUE,
                                experienceLevel = experienceLevel,
                                ct2Eligible = ct2Eligible,
                                reducedNights = reducedNights,
                                weekendPreferenceOff = weekendPreferenceOff,
                                opdCategory = opdCategory,
                                active = active
                            )
                        )
                    }
                }
            ) {
                Text(if (initial == null) "Save" else "Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        title = { Text(if (initial == null) "Add staff" else "Edit staff") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = employeeCode,
                    onValueChange = { employeeCode = it },
                    label = { Text("Employee code") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Staff type", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StaffType.entries.forEach { option ->
                        FilterChip(
                            selected = staffType == option,
                            onClick = { staffType = option },
                            label = { Text(option.name) }
                        )
                    }
                }

                if (staffType == StaffType.TMO) {
                    Text("Section", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TmoSection.entries.forEach { option ->
                            FilterChip(
                                selected = section == option,
                                onClick = { section = option },
                                label = { Text(option.name) }
                            )
                        }
                    }

                    Text("Experience level", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExperienceLevel.entries.forEach { option ->
                            FilterChip(
                                selected = experienceLevel == option,
                                onClick = { experienceLevel = option },
                                label = { Text(option.name) }
                            )
                        }
                    }
                }

                Text("OPD category", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OpdCategory.entries.forEach { option ->
                        FilterChip(
                            selected = opdCategory == option,
                            onClick = { opdCategory = option },
                            label = { Text(option.label) }
                        )
                    }
                }

                if (staffType == StaffType.TMO) {
                    ToggleLine("CT2 eligible", ct2Eligible) { ct2Eligible = it }
                    ToggleLine("Reduced nights", reducedNights) { reducedNights = it }
                    ToggleLine("Weekend preference off", weekendPreferenceOff) { weekendPreferenceOff = it }
                }
                ToggleLine("Active in master list", active) { active = it }
            }
        }
    )
}

@Composable
private fun ToggleLine(label: String, value: Boolean, onValueChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = value, onCheckedChange = onValueChange)
    }
}
