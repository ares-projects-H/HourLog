package com.hourlog.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hourlog.app.R
import com.hourlog.app.domain.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HourLogApp(weekRequest: Int, vm: HourLogViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val overlap by vm.pendingOverlap.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var dayString by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var weekString by rememberSaveable { mutableStateOf(TimeCalculator.monday(LocalDate.now()).toString()) }
    var editId by rememberSaveable { mutableStateOf<String?>(null) }
    val edit = state.entries.find { it.id == editId }
    var adding by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = settings && !adding && edit == null) { settings = false }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(context.getString(it)) } }
    LaunchedEffect(weekRequest) {
        if (weekRequest > 0) { tab = 1; settings = false; weekString = TimeCalculator.monday(LocalDate.now()).toString() }
    }
    val dark = when (state.preferences.appearance) { Appearance.SYSTEM -> isSystemInDarkTheme(); Appearance.DARK -> true; Appearance.LIGHT -> false }
    val colors = hourLogColors(state.preferences.colorSeed, dark)
    MaterialTheme(colorScheme = colors) {
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(if (settings) R.string.settings else R.string.app_name)) },
                actions = { IconButton(onClick = { settings = !settings }) {
                    Icon(if (settings) Icons.Default.Close else Icons.Default.Settings,
                        stringResource(if (settings) R.string.close else R.string.settings))
                } })
        }, bottomBar = {
            if (!settings) NavigationBar {
                listOf(R.string.today to Icons.Default.Today, R.string.week to Icons.Default.DateRange, R.string.history to Icons.Default.History)
                    .forEachIndexed { i, (text, icon) -> NavigationBarItem(selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, null) }, label = { Text(stringResource(text)) }) }
            }
        }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                if (!state.loaded) CircularProgressIndicator(Modifier.padding(24.dp))
                else if (settings) SettingsScreen(state, busy, vm)
                else when (tab) {
                    0 -> TodayScreen(state, LocalDate.parse(dayString), busy,
                        onDay = { dayString = it.toString() }, onAdd = { adding = true },
                        onEdit = { editId = it.id }, onDelete = vm::delete,
                        onCopy = { vm.copyDay(LocalDate.parse(dayString).minusDays(1), LocalDate.parse(dayString)) })
                    1 -> WeekScreen(state, LocalDate.parse(weekString),
                        onWeek = { weekString = it.toString() }, onDay = { dayString = it.toString(); tab = 0 })
                    2 -> HistoryScreen(state, onWeek = { weekString = it.toString(); tab = 1 },
                        onDay = { dayString = it.toString(); tab = 0 })
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        if (adding || edit != null) EntryEditor(LocalDate.parse(dayString), edit, state.preferences, busy,
            onDismiss = { if (!busy) { adding = false; editId = null } },
            onSave = { vm.save(listOf(it), onSaved = { adding = false; editId = null }) })
        if (overlap != null) AlertDialog(onDismissRequest = { if (!busy) vm.dismissOverlap() },
            title = { Text(stringResource(R.string.overlap_title)) },
            text = { Text(stringResource(R.string.overlap_body)) },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                vm.save(overlap!!, true, onSaved = { adding = false; editId = null })
            }) { Text(stringResource(R.string.confirm_overlap)) } },
            dismissButton = { TextButton(enabled = !busy, onClick = vm::dismissOverlap) { Text(stringResource(R.string.cancel)) } })
        DataDialogs(vm, busy)
    }
}
