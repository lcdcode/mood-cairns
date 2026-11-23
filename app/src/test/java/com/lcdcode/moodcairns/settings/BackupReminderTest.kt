package com.lcdcode.moodcairns.settings

import com.lcdcode.moodcairns.testing.InMemoryPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupReminderTest {

    private val start = 1_700_000_000_000L
    private var now = start
    private val prefs = InMemoryPrefs()

    private fun reminder() = BackupReminder(prefs) { now }

    private fun storedDueAt() = prefs.values[BackupReminder.KEY_NEXT_DUE_AT]

    @Test
    fun `fresh install is not due and starts the countdown`() {
        val reminder = reminder()
        assertFalse(reminder.due.value)
        assertEquals(start + BackupReminder.INTERVAL_MS, storedDueAt())
    }

    @Test
    fun `not due one millisecond before the interval ends`() {
        val reminder = reminder()
        now = start + BackupReminder.INTERVAL_MS - 1
        reminder.refresh()
        assertFalse(reminder.due.value)
    }

    @Test
    fun `due once the interval has passed`() {
        val reminder = reminder()
        now = start + BackupReminder.INTERVAL_MS
        reminder.refresh()
        assertTrue(reminder.due.value)
    }

    @Test
    fun `postpone hides the card for another interval`() {
        val reminder = reminder()
        now = start + 3 * BackupReminder.INTERVAL_MS
        reminder.refresh()
        assertTrue(reminder.due.value)

        reminder.postpone()

        assertFalse(reminder.due.value)
        assertEquals(now + BackupReminder.INTERVAL_MS, storedDueAt())
    }

    @Test
    fun `clock set backwards clamps the due time to one interval ahead`() {
        reminder()
        now = start - 365 * BackupReminder.INTERVAL_MS
        val reminder = reminder()
        assertFalse(reminder.due.value)
        assertEquals(now + BackupReminder.INTERVAL_MS, storedDueAt())
    }

    @Test
    fun `due state survives a new instance`() {
        reminder()
        now = start + BackupReminder.INTERVAL_MS + 1
        assertTrue(reminder().due.value)
    }

    @Test
    fun `reminder is enabled by default`() {
        assertTrue(reminder().enabled.value)
    }

    @Test
    fun `disabling hides a due card and keeps it hidden`() {
        val reminder = reminder()
        now = start + BackupReminder.INTERVAL_MS
        reminder.refresh()
        assertTrue(reminder.due.value)

        reminder.setEnabled(false)
        assertFalse(reminder.due.value)

        now += 10 * BackupReminder.INTERVAL_MS
        reminder.refresh()
        assertFalse(reminder.due.value)
    }

    @Test
    fun `disabled setting persists across instances`() {
        reminder().setEnabled(false)
        now = start + 10 * BackupReminder.INTERVAL_MS
        val reminder = reminder()
        assertFalse(reminder.enabled.value)
        assertFalse(reminder.due.value)
        assertEquals(false, prefs.values[BackupReminder.KEY_ENABLED])
    }

    @Test
    fun `re-enabling starts a fresh countdown instead of showing at once`() {
        val reminder = reminder()
        reminder.setEnabled(false)
        now = start + 10 * BackupReminder.INTERVAL_MS

        reminder.setEnabled(true)

        assertTrue(reminder.enabled.value)
        assertFalse(reminder.due.value)
        assertEquals(now + BackupReminder.INTERVAL_MS, storedDueAt())
    }
}
