package com.cardovia.merkon.app.ui.screens

import com.cardovia.merkon.app.data.local.PrintAttemptEntity
import com.cardovia.merkon.app.data.model.PrintAttemptType

object DashboardAttemptSelector {
    /**
     * Given a list of attempts (already ordered DESC by startedAt from the DAO),
     * returns the latest REPRINT attempt.
     */
    fun latestReprintAttempt(attempts: List<PrintAttemptEntity>): PrintAttemptEntity? {
        return attempts.firstOrNull { it.type == PrintAttemptType.REPRINT }
    }
}
