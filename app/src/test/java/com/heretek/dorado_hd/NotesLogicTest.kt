package com.heretek.dorado_hd

import com.heretek.dorado_hd.ui.apps.ChecklistItem
import com.heretek.dorado_hd.ui.apps.NoteDoc
import com.heretek.dorado_hd.ui.apps.NoteKind
import com.heretek.dorado_hd.ui.apps.NoteSort
import com.heretek.dorado_hd.ui.apps.NoteTextSize
import com.heretek.dorado_hd.ui.apps.NotesEngine
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesLogicTest {

    private val zone: ZoneId = ZoneId.of("UTC")

    private fun note(
        id: Long,
        title: String,
        updated: Long,
        created: Long = updated,
        body: String = "",
        items: List<ChecklistItem> = emptyList(),
        kind: NoteKind = NoteKind.NOTE,
    ) = NoteDoc(id, title, body, items, created, updated, kind)

    @Test
    fun `plain body decodes as a free note`() {
        val doc = NotesEngine.decode(7, "shopping", "milk and eggs", 1234)
        assertEquals(NoteKind.NOTE, doc.kind)
        assertEquals("milk and eggs", doc.body)
        assertEquals(1234, doc.createdAt)
        assertEquals(1234, doc.updatedAt)
    }

    @Test
    fun `checklist round trips strikethrough state`() {
        val doc = note(
            id = 3,
            title = "pack",
            updated = 500,
            kind = NoteKind.LIST,
            items = listOf(ChecklistItem("passport", true), ChecklistItem("charger", false)),
        )
        val decoded = NotesEngine.decode(doc.id, doc.title, NotesEngine.encode(doc), doc.updatedAt)
        assertEquals(NoteKind.LIST, decoded.kind)
        assertEquals(doc.items, decoded.items)
        assertEquals(doc.createdAt, decoded.createdAt)
    }

    @Test
    fun `toggle add and delete items`() {
        var doc = note(1, "l", 1, kind = NoteKind.LIST, items = listOf(ChecklistItem("a", false)))
        doc = NotesEngine.toggleItem(doc, 0)
        assertTrue(doc.items[0].checked)
        doc = NotesEngine.addItem(doc, "b")
        assertEquals(2, doc.items.size)
        doc = NotesEngine.deleteItem(doc, 0)
        assertEquals(listOf("b"), doc.items.map { it.text })
        assertEquals(doc, NotesEngine.deleteItem(doc, 9))
    }

    @Test
    fun `list caps at one hundred items`() {
        var doc = note(1, "l", 1, kind = NoteKind.LIST)
        repeat(120) { doc = NotesEngine.addItem(doc, "item $it") }
        assertEquals(NotesEngine.MAX_LIST_ITEMS, doc.items.size)
    }

    @Test
    fun `sort last updated is newest first`() {
        val docs = listOf(note(1, "a", 10), note(2, "b", 30), note(3, "c", 20))
        assertEquals(listOf(2L, 3L, 1L), NotesEngine.sort(docs, NoteSort.LAST_UPDATED).map { it.id })
    }

    @Test
    fun `sort created on is newest first`() {
        val docs = listOf(
            note(1, "a", 10, created = 100),
            note(2, "b", 30, created = 50),
            note(3, "c", 20, created = 200),
        )
        assertEquals(listOf(3L, 1L, 2L), NotesEngine.sort(docs, NoteSort.CREATED_ON).map { it.id })
    }

    @Test
    fun `alphabetical is case insensitive with timestamp tie break`() {
        val docs = listOf(
            note(1, "banana", 10),
            note(2, "Apple", 20),
            note(3, "apple", 30),
        )
        assertEquals(listOf(3L, 2L, 1L), NotesEngine.sort(docs, NoteSort.ALPHABETICAL).map { it.id })
    }

    @Test
    fun `search matches title body and checklist items case insensitively`() {
        val docs = listOf(
            note(1, "groceries", 1, kind = NoteKind.LIST, items = listOf(ChecklistItem("Milk", false))),
            note(2, "work", 2, body = "email the team"),
            note(3, "misc", 3),
        )
        assertEquals(listOf(1L), NotesEngine.search(docs, "mILk").map { it.id })
        assertEquals(listOf(2L), NotesEngine.search(docs, "TEAM").map { it.id })
        assertEquals(3, NotesEngine.search(docs, "  ").size)
        assertTrue(NotesEngine.search(docs, "zzz").isEmpty())
    }

    @Test
    fun `blank notes and lists drop on exit`() {
        assertNull(NotesEngine.saveOnExit(null, note(0, "", 0, body = "   "), 99))
        assertNull(NotesEngine.saveOnExit(null, note(0, "  ", 0, kind = NoteKind.LIST), 99))
    }

    @Test
    fun `blank list title becomes untitled and blank items are filtered`() {
        val saved = NotesEngine.saveOnExit(
            null,
            note(0, "   ", 0, kind = NoteKind.LIST, items = listOf(ChecklistItem("a", false), ChecklistItem(" ", false))),
            99,
        )!!
        assertEquals("untitled", saved.title)
        assertEquals(listOf("a"), saved.items.map { it.text })
    }

    @Test
    fun `unchanged documents keep their timestamp and changes bump it`() {
        val original = note(1, "t", 100, body = "same")
        val unchanged = NotesEngine.saveOnExit(original, original, 999)!!
        assertEquals(100L, unchanged.updatedAt)
        val changed = NotesEngine.saveOnExit(original, original.copy(body = "different"), 999)!!
        assertEquals(999L, changed.updatedAt)
    }

    @Test
    fun `timestamp formats today yesterday and older`() {
        val now = java.time.LocalDateTime.of(2026, 1, 15, 12, 0).atZone(zone).toInstant().toEpochMilli()
        val today = java.time.LocalDateTime.of(2026, 1, 15, 9, 30).atZone(zone).toInstant().toEpochMilli()
        val yesterday = java.time.LocalDateTime.of(2026, 1, 14, 14, 0).atZone(zone).toInstant().toEpochMilli()
        val older = java.time.LocalDateTime.of(2026, 1, 10, 8, 5).atZone(zone).toInstant().toEpochMilli()
        assertEquals("Today 9:30 am", NotesEngine.formatTimestamp(today, now, zone))
        assertEquals("Yesterday 2:00 pm", NotesEngine.formatTimestamp(yesterday, now, zone))
        assertEquals("Jan 10 8:05 am", NotesEngine.formatTimestamp(older, now, zone))
    }

    @Test
    fun `settings blob round trips`() {
        val blob = NotesEngine.encodeSettings(NoteSort.ALPHABETICAL, NoteTextSize.LARGE)
        assertEquals(NoteSort.ALPHABETICAL to NoteTextSize.LARGE, NotesEngine.decodeSettings(blob))
        assertEquals(NoteSort.LAST_UPDATED to NoteTextSize.MEDIUM, NotesEngine.decodeSettings(null))
    }

    @Test
    fun `display title falls back to untitled`() {
        assertEquals("untitled", note(1, "   ", 1).displayTitle())
        assertEquals("kept", note(1, " kept ", 1).displayTitle())
    }
}
