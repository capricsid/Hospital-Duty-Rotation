package com.capricsid.hospitaldutyroster.ui

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
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.ModeEdit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.capricsid.hospitaldutyroster.data.StaffRepository
import com.capricsid.hospitaldutyroster.model.ExperienceLevel
import com.capricsid.hospitaldutyroster.model.OpdCategory
import com.capricsid.hospitaldutyroster.model.PreviewRow
import com.capricsid.hospitaldutyroster.model.RosterPreview
import com.capricsid.hospitaldutyroster.model.StaffMember
import com.capricsid.hospitaldutyroster.model.StaffType
import com.capricsid.hospitaldutyroster.model.TmoSection
import com.capricsid.hospitaldutyroster.model.buildRosterPreview
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
    val loadedStaff = remember { repository.loadStaff().sortedBy { it.name } }
    val staff = remember { mutableStateListOf<StaffMember>().apply { addAll(loadedStaff) } }

    var selectedTab by rememberSaveable { mutableStateOf(AppTab.STAFF) }
    var showEditor by remember { mutableStateOf(false) }
    var editingStaffId by remember { mutableStateOf<String?>(null) }
    var selectedMonth by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var preview by remember { mutableStateOf(buildRosterPreview(selectedMonth, staff.toList())) }

    fun saveStaff(updated: List<StaffMember>) {
        staff.clear()
        staff.addAll(updated.sortedBy { it.name })
        repository.saveStaff(staff.toList())
        preview = buildRosterPreview(selectedMonth, staff.toList())
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hospital Duty Roster")
                        Text(
                            text = "v1.0.1 • Git-tracked APK foundation",
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
                onPreviousMonth = {
                    selectedMonth = selectedMonth.minusMonths(1)
                    preview = buildRosterPreview(selectedMonth, staff.toList())
                },
                onNextMonth = {
                    selectedMonth = selectedMonth.plusMonths(1)
                    preview = buildRosterPreview(selectedMonth, staff.toList())
                },
                onRefreshPreview = {
                    preview = buildRosterPreview(selectedMonth, staff.toList())
                }
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
                    updated += member
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
    preview: RosterPreview,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onRefreshPreview: () -> Unit
) {
    val activeStaff = staff.filter { it.active }
    val activeTmos = activeStaff.count { it.staffType == StaffType.TMO }
    val activeHos = activeStaff.count { it.staffType == StaffType.HO }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            SummaryHeroCard(
                title = "Excel-Style Roster Setup",
                subtitle = "This build keeps your saved staff, prepares a 24→23 roster cycle, and previews the same section order used by the current Excel roster files.",
                chips = listOf(
                    "Active TMOs $activeTmos",
                    "Active HOs $activeHos",
                    "Output APK folder ready"
                )
            )
        }

        item {
            MonthSelectorCard(
                selectedMonth = selectedMonth,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onRefreshPreview = onRefreshPreview
            )
        }

        item {
            SectionHeader("Preview")
            Text(
                "This first APK focuses on persistent staff and roster structure fidelity. The preview matches the workbook reading flow so the next milestone can plug in full rule-based duty assignment and Excel export.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            RosterPreviewCard(preview = preview)
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
    onRefreshPreview: () -> Unit
) {
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
            ElevatedAssistChip(
                onClick = onRefreshPreview,
                label = { Text("Refresh Excel-style preview") }
            )
        }
    }
}

@Composable
private fun RosterPreviewCard(preview: RosterPreview) {
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()

    OutlinedCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(preview.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(20.dp))
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(verticalScroll)
                        .horizontalScroll(horizontalScroll)
                ) {
                    HeaderRow(preview)
                    SectionHeaderRow("WARD TMOS")
                    preview.wardRows.forEach { PreviewDataRow(it) }
                    SectionHeaderRow("NURSERY TMOS")
                    preview.nurseryRows.forEach { PreviewDataRow(it) }
                    SectionHeaderRow("HOUSE OFFICER")
                    preview.hoRows.forEach { PreviewDataRow(it) }
                    SectionHeaderRow("OPD ROSTER")
                    preview.opdTracks.forEach { track ->
                        OpdTrackRow(track.label, track.dates, track.placeholder)
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
            PreviewCell("NOTES", 180.dp, true)
        }
        Row {
            PreviewCell("", 140.dp, false)
            PreviewCell("", 90.dp, false)
            preview.days.forEach { day ->
                PreviewCell(day.dayNumber, 56.dp, false)
            }
            PreviewCell("Rule bands and flags", 180.dp, false)
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        PreviewCell(row.label, 140.dp, false)
        PreviewCell(row.badge, 90.dp, false)
        row.cells.forEach { cell ->
            PreviewCell(cell, 56.dp, false)
        }
        PreviewCell(row.summary, 180.dp, false)
    }
}

@Composable
private fun OpdTrackRow(label: String, dates: List<String>, placeholder: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontWeight = FontWeight.Medium)
        Text(
            "Dates: ${dates.joinToString(", ")}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            placeholder,
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
