package com.restaurant.sushimei.frontend.ui.screens

import com.restaurant.sushimei.frontend.data.local.PrintAttemptEntity
import com.restaurant.sushimei.frontend.data.model.PrintAttemptStatus
import com.restaurant.sushimei.frontend.data.model.PrintAttemptType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardAttemptSelectorTest {

    @Test
    fun `latestReprintAttempt selects first REPRINT and ignores non-REPRINT types`() {
        // DAO returns ordered DESC by startedAt. First element is newest.
        val attempts = listOf(
            PrintAttemptEntity("att-3", "job-1", PrintAttemptType.ORIGINAL, PrintAttemptStatus.PRINTING, 300L, null, null),
            PrintAttemptEntity("att-2", "job-1", PrintAttemptType.REPRINT, PrintAttemptStatus.FAILED, 200L, 210L, "Error"),
            PrintAttemptEntity("att-1", "job-1", PrintAttemptType.REPRINT, PrintAttemptStatus.SUCCEEDED, 100L, 110L, null)
        )

        val selected = DashboardAttemptSelector.latestReprintAttempt(attempts)
        // It should pick att-2, the first REPRINT in the list
        assertEquals("att-2", selected?.id)
        assertEquals(PrintAttemptStatus.FAILED, selected?.status)
    }

    @Test
    fun `latestReprintAttempt returns null if no REPRINT attempts exist`() {
        val attempts = listOf(
            PrintAttemptEntity("att-3", "job-1", PrintAttemptType.ORIGINAL, PrintAttemptStatus.FAILED, 300L, null, null),
            PrintAttemptEntity("att-2", "job-1", PrintAttemptType.RETRY, PrintAttemptStatus.FAILED, 200L, 210L, "Error")
        )

        val selected = DashboardAttemptSelector.latestReprintAttempt(attempts)
        assertNull(selected)
    }
}
