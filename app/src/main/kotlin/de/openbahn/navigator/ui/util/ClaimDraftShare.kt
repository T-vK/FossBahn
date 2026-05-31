package de.openbahn.navigator.ui.util

import android.content.Context
import android.content.Intent
import de.openbahn.rights.model.ClaimDraft

fun Context.shareClaimDraftEmail(draft: ClaimDraft) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "message/rfc822"
        putExtra(Intent.EXTRA_SUBJECT, draft.subject)
        putExtra(Intent.EXTRA_TEXT, draft.bodyText)
        draft.recipientEmail?.let { putExtra(Intent.EXTRA_EMAIL, arrayOf(it)) }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(Intent.createChooser(intent, null))
}
