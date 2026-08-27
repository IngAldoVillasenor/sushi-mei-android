package com.restaurant.sushimei.frontend.ui.screens

import com.restaurant.sushimei.frontend.data.local.PrintAttemptEntity
import com.restaurant.sushimei.frontend.data.model.PrintAttemptType

object DashboardAttemptSelector {
    /**
     * Given a list of attempts (already ordered DESC by startedAt from the DAO),
     * returns the latest REPRINT attempt.
     */
    fun latestReprintAttempt(attempts: List<PrintAttemptEntity>): PrintAttemptEntity? {
        return attempts.firstOrNull { it.type == PrintAttemptType.REPRINT }
    }
}
