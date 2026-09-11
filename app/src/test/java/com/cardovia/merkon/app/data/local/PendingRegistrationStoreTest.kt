package com.cardovia.merkon.app.data.local

import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Test

class PendingRegistrationStoreTest {

    @Test
    fun `store only saves pending email and exposes no secrets`() {
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.remove(any()) } returns mockEditor
        
        // Ensure initial read works
        every { mockPrefs.getString("pending_email", null) } returns null

        val store = PendingRegistrationStore(mockPrefs)
        
        // Save email
        store.saveEmail("test@example.com")
        
        // Verify it was saved to the exact key
        verify(exactly = 1) { mockEditor.putString("pending_email", "test@example.com") }
        verify(exactly = 0) { mockEditor.putString(neq("pending_email"), any()) }
        verify(exactly = 0) { mockEditor.putBoolean(any(), any()) }
        verify(exactly = 0) { mockEditor.putInt(any(), any()) }
        verify(exactly = 0) { mockEditor.putLong(any(), any()) }
        verify(exactly = 0) { mockEditor.putFloat(any(), any()) }
        verify(exactly = 0) { mockEditor.putStringSet(any(), any()) }
        verify { mockEditor.apply() }
        
        // Verify state exposed
        assertEquals("test@example.com", store.pendingEmail.value)
        
        // Verify that there are absolutely no methods on the store to save passwords or tokens.
        val publicMethods = store.javaClass.declaredMethods.filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
        val publicMethodNames = publicMethods.map { it.name }
        
        assertFalse("Should not have savePassword", publicMethodNames.contains("savePassword"))
        assertFalse("Should not have saveToken", publicMethodNames.contains("saveToken"))
        assertFalse("Should not have saveUrl", publicMethodNames.contains("saveUrl"))
        
        // Clear it
        store.clear()
        verify { mockEditor.remove("pending_email") }
        assertNull(store.pendingEmail.value)
    }
}
