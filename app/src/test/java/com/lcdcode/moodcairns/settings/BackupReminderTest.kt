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
    fun fresh_install_is_not_due_and_starts_the_countdown() {
        val reminder = reminder()
        assertFalse(reminder.due.value)
        assertEquals(start + BackupReminder.INTERVAL_MS, storedDueAt())
    }

    @Test
    fun not_due_one_millisecond_before_the_interval_ends() {
        val reminder = reminder()
        now = start + BackupReminder.INTERVAL_MS - 1
        reminder.refresh()
        assertFalse(reminder.due.value)
    }

    @Test
    fun due_once_the_interval_has_passed() {
        val reminder = reminder()
        now = start + BackupReminder.INTERVAL_MS
        reminder.refresh()
        assertTrue(reminder.due.value)
    }

    @Test
    fun postpone_hides_the_card_for_another_interval() {
        val reminder = reminder()
        now = start + 3 * BackupReminder.INTERVAL_MS
        reminder.refresh()
        assertTrue(reminder.due.value)

        reminder.postpone()

        assertFalse(reminder.due.value)
        assertEquals(now + BackupReminder.INTERVAL_MS, storedDueAt())
    }

    @Test
    fun clock_set_backwards_clamps_the_due_time_to_one_interval_ahead() {
        reminder()
        now = start - 365 * BackupReminder.INTERVAL_MS
        val reminder = reminder()
        assertFalse(reminder.due.value)
        assertEquals(now + BackupReminder.INTERVAL_MS, storedDueAt())
    }

    @Test
    fun due_state_survives_a_new_instance() {
        reminder()
        now = start + BackupReminder.INTERVAL_MS + 1
        assertTrue(reminder().due.value)
    }

    @Test
    fun reminder_is_enabled_by_default() {
        assertTrue(reminder().enabled.value)
    }

    @Test
    fun disabling_hides_a_due_card_and_keeps_it_hidden() {
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
    fun disabled_setting_persists_across_instances() {
        reminder().setEnabled(false)
        now = start + 10 * BackupReminder.INTERVAL_MS
        val reminder = reminder()
        assertFalse(reminder.enabled.value)
        assertFalse(reminder.due.value)
        assertEquals(false, prefs.values[BackupReminder.KEY_ENABLED])
    }

    @Test
    fun re_enabling_starts_a_fresh_countdown_instead_of_showing_at_once() {
        val reminder = reminder()
        reminder.setEnabled(false)
        now = start + 10 * BackupReminder.INTERVAL_MS

        reminder.setEnabled(true)

        assertTrue(reminder.enabled.value)
        assertFalse(reminder.due.value)
        assertEquals(now + BackupReminder.INTERVAL_MS, storedDueAt())
    }
}
