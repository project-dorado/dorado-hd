package com.heretek.dorado_hd.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heretek.dorado_hd.data.db.NoteEntity
import com.heretek.dorado_hd.design.DoradoTokens
import com.heretek.dorado_hd.design.LocalDoradoColors
import com.heretek.dorado_hd.design.Selawik
import com.heretek.dorado_hd.design.components.EdgeCropText
import com.heretek.dorado_hd.ui.LocalDoradoGraph
import com.heretek.dorado_hd.ui.components.DetailScaffold
import com.heretek.dorado_hd.ui.components.LocalContextMenu
import com.heretek.dorado_hd.ui.components.MenuAction
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/* ============================== Notes ============================== */

/** Free-text note or a strikethrough checklist (device: notes / lists). */
enum class NoteKind { NOTE, LIST }

enum class NoteSort { LAST_UPDATED, CREATED_ON, ALPHABETICAL }

enum class NoteTextSize { SMALL, MEDIUM, LARGE }

data class ChecklistItem(val text: String, val checked: Boolean)

data class NoteDoc(
    val id: Long,
    val title: String,
    val body: String,
    val items: List<ChecklistItem>,
    val createdAt: Long,
    val updatedAt: Long,
    val kind: NoteKind,
) {
    fun displayTitle(): String = title.trim().ifEmpty { "untitled" }

    fun checkedCount(): Int = items.count { it.checked }
}

/**
 * Notes engine: sorting, search, checklist parsing and the save-on-exit rules.
 * The checklist rides in the note body behind a `[[dorado-note:v1]]` header so
 * no Room schema change is needed; plain bodies decode as notes unchanged.
 */
object NotesEngine {

    const val HEADER = "[[dorado-note:v1]]"
    const val MAX_LIST_ITEMS = 100

    fun decode(id: Long, title: String, body: String, modifiedAt: Long): NoteDoc {
        if (!body.startsWith(HEADER)) {
            return NoteDoc(id, title, body, emptyList(), modifiedAt, modifiedAt, NoteKind.NOTE)
        }
        val nl = body.indexOf('\n')
        val header = if (nl < 0) body.substring(HEADER.length) else body.substring(HEADER.length, nl)
        val content = if (nl < 0) "" else body.substring(nl + 1)
        val fields = header.split(';').mapNotNull {
            val eq = it.indexOf('=')
            if (eq <= 0) null else it.substring(0, eq) to it.substring(eq + 1)
        }.toMap()
        val created = fields["created"]?.toLongOrNull() ?: modifiedAt
        return if (fields["kind"] == "list") {
            NoteDoc(id, title, "", parseItems(content), created, modifiedAt, NoteKind.LIST)
        } else {
            NoteDoc(id, title, content, emptyList(), created, modifiedAt, NoteKind.NOTE)
        }
    }

    fun encode(doc: NoteDoc): String {
        val header = buildString {
            append(HEADER)
            append("created=").append(doc.createdAt)
            append(";kind=").append(if (doc.kind == NoteKind.LIST) "list" else "note")
        }
        return when (doc.kind) {
            NoteKind.NOTE -> "$header\n${doc.body}"
            NoteKind.LIST -> header + "\n" + doc.items.joinToString("\n") {
                (if (it.checked) "[x] " else "[ ] ") + it.text.replace('\n', ' ')
            }
        }
    }

    fun parseItems(content: String): List<ChecklistItem> =
        content.split('\n').filter { it.isNotBlank() }.take(MAX_LIST_ITEMS).map { line ->
            when {
                line.startsWith("[x] ") -> ChecklistItem(line.removePrefix("[x] "), true)
                line.startsWith("[ ] ") -> ChecklistItem(line.removePrefix("[ ] "), false)
                else -> ChecklistItem(line, false)
            }
        }

    fun sort(docs: List<NoteDoc>, sort: NoteSort): List<NoteDoc> = when (sort) {
        NoteSort.LAST_UPDATED ->
            docs.sortedWith(compareByDescending<NoteDoc> { it.updatedAt }.thenBy { it.id })
        NoteSort.CREATED_ON ->
            docs.sortedWith(compareByDescending<NoteDoc> { it.createdAt }.thenBy { it.id })
        NoteSort.ALPHABETICAL ->
            docs.sortedWith(
                compareBy<NoteDoc> { it.displayTitle().trim().lowercase(Locale.US) }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.id },
            )
    }

    fun search(docs: List<NoteDoc>, query: String): List<NoteDoc> {
        val q = query.trim()
        if (q.isEmpty()) return docs
        return docs.filter { haystack(it).contains(q, ignoreCase = true) }
    }

    private fun haystack(doc: NoteDoc): String = buildString {
        append(doc.title).append('\n').append(doc.body)
        doc.items.forEach { append('\n').append(it.text) }
    }

    fun toggleItem(doc: NoteDoc, index: Int): NoteDoc {
        if (index !in doc.items.indices) return doc
        val items = doc.items.toMutableList()
        items[index] = items[index].copy(checked = !items[index].checked)
        return doc.copy(items = items)
    }

    fun addItem(doc: NoteDoc, text: String): NoteDoc =
        if (doc.items.size >= MAX_LIST_ITEMS) doc
        else doc.copy(items = doc.items + ChecklistItem(text.trim(), false))

    fun deleteItem(doc: NoteDoc, index: Int): NoteDoc =
        if (index !in doc.items.indices) doc
        else doc.copy(items = doc.items.filterIndexed { i, _ -> i != index })

    fun isBlank(doc: NoteDoc): Boolean = when (doc.kind) {
        NoteKind.NOTE -> doc.body.isBlank() && doc.title.isBlank()
        NoteKind.LIST -> doc.title.isBlank() && doc.items.all { it.text.isBlank() }
    }

    /**
     * Exit rules: blank documents drop, blank titles become "untitled",
     * unchanged documents keep their timestamp, changed ones get [now].
     */
    fun saveOnExit(original: NoteDoc?, edited: NoteDoc, now: Long): NoteDoc? {
        if (isBlank(edited)) return null
        val changed = original == null ||
            original.copy(updatedAt = 0L, title = original.title.trim()) !=
            edited.copy(updatedAt = 0L, title = edited.title.trim())
        return edited.copy(
            title = edited.title.trim().ifEmpty { "untitled" },
            updatedAt = if (changed) now else original?.updatedAt ?: now,
            items = edited.items.filter { it.text.isNotBlank() }.take(MAX_LIST_ITEMS),
        )
    }

    fun formatTimestamp(ts: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val date = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
            .format(Instant.ofEpochMilli(ts).atZone(zone))
            .lowercase(Locale.US)
        return when (date) {
            today -> "Today $time"
            today.minusDays(1) -> "Yesterday $time"
            else -> DateTimeFormatter.ofPattern("MMM d", Locale.US).format(date) + " $time"
        }
    }

    fun encodeSettings(sort: NoteSort, size: NoteTextSize): String =
        "sort=${sort.name};text=${size.name}"

    fun decodeSettings(blob: String?): Pair<NoteSort, NoteTextSize> {
        if (blob == null) return NoteSort.LAST_UPDATED to NoteTextSize.MEDIUM
        val fields = blob.split(';').mapNotNull {
            val eq = it.indexOf('=')
            if (eq <= 0) null else it.substring(0, eq) to it.substring(eq + 1)
        }.toMap()
        val sort = NoteSort.entries.firstOrNull { it.name == fields["sort"] } ?: NoteSort.LAST_UPDATED
        val size = NoteTextSize.entries.firstOrNull { it.name == fields["text"] } ?: NoteTextSize.MEDIUM
        return sort to size
    }
}

@Composable
fun NotesApp() {
    val graph = LocalDoradoGraph.current
    val menus = LocalContextMenu.current
    val colors = LocalDoradoColors.current
    val scope = rememberCoroutineScope()
    val notes by graph.notes.notes().collectAsState(initial = emptyList())
    val zone = remember { ZoneId.systemDefault() }
    var page by remember { mutableStateOf(NoteKind.NOTE) }
    var sort by remember { mutableStateOf(NoteSort.LAST_UPDATED) }
    var textSize by remember { mutableStateOf(NoteTextSize.MEDIUM) }
    var query by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<NoteDoc?>(null) }

    LaunchedEffect(Unit) {
        graph.appState.get("notes")?.let { blob ->
            val (s, t) = NotesEngine.decodeSettings(blob)
            sort = s
            textSize = t
        }
        loaded = true
    }
    LaunchedEffect(sort, textSize, loaded) {
        if (loaded) graph.appState.put("notes", NotesEngine.encodeSettings(sort, textSize))
    }

    val docs = remember(notes) { notes.map { NotesEngine.decode(it.id, it.title, it.body, it.modifiedAt) } }
    val now = AppClock.millis()

    if (selected != null) {
        val start = selected!!
        var title by remember(start.id, start) { mutableStateOf(start.title) }
        var body by remember(start.id, start) { mutableStateOf(start.body) }
        val items = remember(start.id, start) {
            mutableStateListOf<ChecklistItem>().also { list -> list.addAll(start.items) }
        }
        val persist: () -> Unit = {
            val edited = start.copy(title = title, body = body, items = items.toList())
            val saved = NotesEngine.saveOnExit(if (start.id == 0L) null else start, edited, AppClock.millis())
            scope.launch(kotlinx.coroutines.NonCancellable) {
                if (saved == null) {
                    if (start.id != 0L) graph.notes.delete(start.id)
                } else if (start.id == 0L) {
                    graph.notes.add(saved.title, NotesEngine.encode(saved))
                } else {
                    graph.notes.delete(start.id)
                    graph.notes.add(saved.title, NotesEngine.encode(saved))
                }
            }
            selected = null
        }
        DetailScaffold(title = "notes", onBack = persist) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(DoradoTokens.EDGE.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                EditableLine(value = title, onChange = { title = it }, placeholder = "title")
                Spacer(Modifier.height(8.dp))
                if (start.kind == NoteKind.NOTE) {
                    EditableLine(
                        value = body,
                        onChange = { body = it },
                        placeholder = "body",
                        multiLine = true,
                    )
                } else {
                    items.forEachIndexed { index, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .border(1.dp, colors.border)
                                    .background(if (item.checked) colors.accent else colors.elevated)
                                    .combinedClickable(
                                        onClick = { items[index] = item.copy(checked = !item.checked) },
                                        onLongClick = {},
                                    ),
                            )
                            Spacer(Modifier.width(8.dp))
                            EditableLine(
                                value = item.text,
                                onChange = { items[index] = items[index].copy(text = it) },
                                placeholder = "item",
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(8.dp))
                            EdgeCropText(
                                text = "x",
                                fontSize = DoradoTokens.TYPE_LIST.dp,
                                color = colors.textInactive,
                                modifier = Modifier
                                    .combinedClickable(onClick = { items.removeAt(index) }, onLongClick = {})
                                    .padding(4.dp),
                            )
                        }
                    }
                    if (items.size < NotesEngine.MAX_LIST_ITEMS) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(onClick = { items.add(ChecklistItem("", false)) }, onLongClick = {})
                                .padding(vertical = 8.dp),
                        ) {
                            EdgeCropText(
                                text = "+ item",
                                fontSize = DoradoTokens.TYPE_LIST.dp,
                                color = colors.accent,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(onClick = persist, onLongClick = {})
                        .padding(vertical = 8.dp),
                ) {
                    EdgeCropText(text = "save", fontSize = DoradoTokens.TYPE_LIST.dp, color = colors.accent)
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                scope.launch { if (start.id != 0L) graph.notes.delete(start.id) }
                                selected = null
                            },
                            onLongClick = {},
                        )
                        .padding(vertical = 8.dp),
                ) {
                    EdgeCropText(
                        text = "delete",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = colors.textInactive,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        return
    }

    val visible = NotesEngine.search(NotesEngine.sort(docs.filter { it.kind == page }, sort), query)

    DetailScaffold(title = "notes") {
        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(page) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (total < -80f) page = NoteKind.LIST
                            if (total > 80f) page = NoteKind.NOTE
                            total = 0f
                        },
                    ) { _, amount -> total += amount }
                },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = DoradoTokens.EDGE.dp),
            ) {
                listOf(NoteKind.NOTE, NoteKind.LIST).forEach { kind ->
                    val on = kind == page
                    EdgeCropText(
                        text = if (kind == NoteKind.NOTE) "notes" else "lists",
                        fontSize = DoradoTokens.TYPE_LIST.dp,
                        color = if (on) colors.accent else colors.textSecondary,
                        modifier = Modifier
                            .combinedClickable(onClick = { page = kind; query = "" }, onLongClick = {})
                            .padding(end = 12.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                EdgeCropText(
                    text = when (sort) {
                        NoteSort.LAST_UPDATED -> "sort: updated"
                        NoteSort.CREATED_ON -> "sort: created"
                        NoteSort.ALPHABETICAL -> "sort: a-z"
                    },
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                sort = when (sort) {
                                    NoteSort.LAST_UPDATED -> NoteSort.CREATED_ON
                                    NoteSort.CREATED_ON -> NoteSort.ALPHABETICAL
                                    NoteSort.ALPHABETICAL -> NoteSort.LAST_UPDATED
                                }
                            },
                            onLongClick = {},
                        )
                        .padding(4.dp),
                )
                EdgeCropText(
                    text = when (textSize) {
                        NoteTextSize.SMALL -> "size: s"
                        NoteTextSize.MEDIUM -> "size: m"
                        NoteTextSize.LARGE -> "size: l"
                    },
                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                textSize = when (textSize) {
                                    NoteTextSize.SMALL -> NoteTextSize.MEDIUM
                                    NoteTextSize.MEDIUM -> NoteTextSize.LARGE
                                    NoteTextSize.LARGE -> NoteTextSize.SMALL
                                }
                            },
                            onLongClick = {},
                        )
                        .padding(4.dp),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = DoradoTokens.EDGE.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = Selawik,
                        fontSize = DoradoTokens.TYPE_LIST.sp,
                        color = colors.textPrimary,
                    ),
                    cursorBrush = SolidColor(colors.accent),
                    decorationBox = { inner ->
                        Box(Modifier.padding(6.dp)) {
                            if (query.isEmpty()) {
                                EdgeCropText(
                                    text = "search",
                                    fontSize = DoradoTokens.TYPE_CAPTION.dp,
                                    color = colors.textInactive,
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.elevated),
                )
                Spacer(Modifier.width(8.dp))
                EdgeCropText(
                    text = "+",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.accent,
                    modifier = Modifier
                        .combinedClickable(
                            onClick = {
                                query = ""
                                val nowMs = AppClock.millis()
                                selected = if (page == NoteKind.NOTE) {
                                    NoteDoc(0L, "", "", emptyList(), nowMs, nowMs, NoteKind.NOTE)
                                } else {
                                    NoteDoc(0L, "", "", emptyList(), nowMs, nowMs, NoteKind.LIST)
                                }
                            },
                            onLongClick = {},
                        )
                        .padding(8.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            if (visible.isEmpty()) {
                EdgeCropText(
                    text = if (query.isNotBlank()) "no results" else "no items",
                    fontSize = DoradoTokens.TYPE_LIST.dp,
                    color = colors.textInactive,
                    modifier = Modifier.padding(horizontal = DoradoTokens.EDGE.dp, vertical = 8.dp),
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                visible.forEach { doc ->
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(DoradoTokens.ROW_HEIGHT.dp)
                            .combinedClickable(
                                onClick = { selected = doc },
                                onLongClick = {
                                    menus.show(
                                        title = doc.displayTitle(),
                                        actions = listOf(
                                            MenuAction("delete") { scope.launch { graph.notes.delete(doc.id) } },
                                        ),
                                    )
                                },
                            )
                            .padding(horizontal = DoradoTokens.EDGE.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                EdgeCropText(
                                    text = doc.displayTitle(),
                                    fontSize = textSize.fontSize(),
                                    color = colors.textPrimary,
                                )
                                val preview = if (doc.kind == NoteKind.LIST) {
                                    "${doc.checkedCount()}/${doc.items.size} done"
                                } else {
                                    doc.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(48)
                                }
                                if (preview.isNotEmpty()) {
                                    EdgeCropText(
                                        text = preview,
                                        fontSize = DoradoTokens.TYPE_CAPTION.dp,
                                        color = colors.textSecondary,
                                    )
                                }
                            }
                            EdgeCropText(
                                text = NotesEngine.formatTimestamp(doc.updatedAt, now, zone),
                                fontSize = DoradoTokens.TYPE_CAPTION.dp,
                                color = colors.textInactive,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun NoteTextSize.fontSize() = when (this) {
    NoteTextSize.SMALL -> DoradoTokens.TYPE_LIST_SECONDARY.dp
    NoteTextSize.MEDIUM -> DoradoTokens.TYPE_LIST.dp
    NoteTextSize.LARGE -> DoradoTokens.TYPE_CROSSBAR.dp
}

@Composable
fun EditableLine(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    multiLine: Boolean = false,
    textDecoration: TextDecoration? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDoradoColors.current
    var local by remember(value) { mutableStateOf(value) }
    Box(modifier.fillMaxWidth()) {
        BasicTextField(
            value = local,
            onValueChange = {
                local = it
                onChange(it)
            },
            singleLine = !multiLine,
            textStyle = TextStyle(
                fontFamily = Selawik,
                fontSize = if (multiLine) DoradoTokens.TYPE_LIST.sp else DoradoTokens.TYPE_NOW_META.sp,
                color = colors.textPrimary,
                textDecoration = textDecoration,
            ),
            cursorBrush = SolidColor(colors.accent),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.elevated)
                .padding(8.dp),
        )
        if (local.isEmpty() && placeholder.isNotEmpty()) {
            EdgeCropText(
                text = placeholder,
                fontSize = DoradoTokens.TYPE_NOW_META.dp,
                alpha = 0.4f,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
            )
        }
    }
}
