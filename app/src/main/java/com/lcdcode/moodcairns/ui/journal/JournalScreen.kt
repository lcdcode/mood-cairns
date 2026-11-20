package com.lcdcode.moodcairns.ui.journal

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lcdcode.moodcairns.R
import com.lcdcode.moodcairns.markdown.MarkdownParser
import com.lcdcode.moodcairns.markdown.MdTask
import com.lcdcode.moodcairns.ui.common.asString
import com.lcdcode.moodcairns.ui.common.rememberSkeletonDateFormat

private val PagePadding = 16.dp
private const val TITLE_DATE_SKELETON = "yMMMEd"
private const val TABLE_TEMPLATE_COLUMNS = 2

private enum class FormatAction(val icon: ImageVector, @StringRes val label: Int) {
    BOLD(Icons.Default.FormatBold, R.string.journal_format_bold),
    ITALIC(Icons.Default.FormatItalic, R.string.journal_format_italic),
    HEADING(Icons.Default.Title, R.string.journal_format_heading),
    BULLETS(Icons.AutoMirrored.Filled.FormatListBulleted, R.string.journal_format_bullet_list),
    NUMBERS(Icons.Default.FormatListNumbered, R.string.journal_format_numbered_list),
    CHECKLIST(Icons.Default.Checklist, R.string.journal_format_checklist),
    QUOTE(Icons.Default.FormatQuote, R.string.journal_format_quote),
    CODE(Icons.Default.Code, R.string.journal_format_code),
    TABLE(Icons.Default.TableChart, R.string.journal_format_table),
    ;

    /** [tableHeaders] are the localized placeholder headers used by [TABLE]. */
    fun apply(value: TextFieldValue, tableHeaders: List<String>): EditResult {
        val text = value.text
        val start = value.selection.start
        val end = value.selection.end
        return when (this) {
            BOLD -> toggleInlineMarker(text, start, end, InlineMarker.BOLD)
            ITALIC -> toggleInlineMarker(text, start, end, InlineMarker.ITALIC)
            CODE -> toggleInlineMarker(text, start, end, InlineMarker.CODE)
            HEADING -> toggleLinePrefix(text, start, end, LinePrefix.HEADING)
            BULLETS -> toggleLinePrefix(text, start, end, LinePrefix.BULLET)
            NUMBERS -> toggleLinePrefix(text, start, end, LinePrefix.NUMBERED)
            CHECKLIST -> toggleLinePrefix(text, start, end, LinePrefix.TASK)
            QUOTE -> toggleLinePrefix(text, start, end, LinePrefix.QUOTE)
            TABLE -> insertTableTemplate(text, start, end, tableHeaders)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    onBack: () -> Unit,
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dateFormat = rememberSkeletonDateFormat(TITLE_DATE_SKELETON)

    // Selection lives here, not in the ViewModel. Re-seeded from the ViewModel's body once the
    // page has loaded, after configuration changes, and when switching back from the read
    // view, where ticking a checkbox changes the body.
    var field by remember(state.loaded, state.readMode) {
        mutableStateOf(TextFieldValue(viewModel.state.value.body))
    }
    val tableHeaders = (1..TABLE_TEMPLATE_COLUMNS).map {
        stringResource(R.string.journal_table_column, it)
    }

    fun applyEdit(result: EditResult) {
        if (result.text.length > MAX_JOURNAL_BODY_CHARS) return
        field = TextFieldValue(result.text, TextRange(result.selStart, result.selEnd))
        viewModel.setBody(result.text)
    }

    val errorText = state.error?.asString()
    LaunchedEffect(errorText) {
        val message = errorText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.dismissError()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flush()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.flush()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(dateFormat.format(state.day)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    if (state.loaded) ReadModeToggle(state.readMode, viewModel::toggleReadMode)
                },
            )
        },
        bottomBar = {
            if (state.loaded && !state.readMode) {
                FormattingToolbar(onAction = { applyEdit(it.apply(field, tableHeaders)) })
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.loadFailed -> CenteredMessage(stringResource(R.string.journal_load_failed))
                !state.loaded -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.readMode -> ReadView(state.body, onToggleTask = viewModel::toggleTask)
                else -> Editor(
                    value = field,
                    onValueChange = { new ->
                        val clamped = clampJournalBody(new.text)
                        field = if (clamped.length == new.text.length) {
                            new
                        } else {
                            TextFieldValue(clamped, TextRange(clamped.length))
                        }
                        viewModel.setBody(field.text)
                    },
                )
            }
        }
    }
}

@Composable
private fun ReadModeToggle(readMode: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        if (readMode) {
            Icon(
                Icons.Default.Edit,
                contentDescription = stringResource(R.string.journal_show_edit),
            )
        } else {
            Icon(
                Icons.Default.Visibility,
                contentDescription = stringResource(R.string.journal_show_read),
            )
        }
    }
}

@Composable
private fun Editor(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit) {
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxSize().padding(PagePadding),
        decorationBox = { innerTextField ->
            Box {
                if (value.text.isEmpty()) {
                    Text(
                        stringResource(R.string.journal_placeholder),
                        style = textStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun ReadView(body: String, onToggleTask: (MdTask) -> Unit) {
    if (body.isBlank()) {
        CenteredMessage(stringResource(R.string.journal_empty_read))
        return
    }
    val blocks = remember(body) { MarkdownParser.parse(body) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(PagePadding)) {
        SelectionContainer { MarkdownView(blocks, onToggleTask = onToggleTask) }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize().padding(PagePadding), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FormattingToolbar(onAction: (FormatAction) -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .horizontalScroll(rememberScrollState()),
        ) {
            FormatAction.entries.forEach { action ->
                IconButton(onClick = { onAction(action) }) {
                    Icon(action.icon, contentDescription = stringResource(action.label))
                }
            }
        }
    }
}
