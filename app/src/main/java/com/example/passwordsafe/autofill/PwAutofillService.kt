package com.example.passwordsafe.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import com.example.passwordsafe.R
import com.example.passwordsafe.core.PwsDatabase
import com.example.passwordsafe.core.PwsRecord
import com.example.passwordsafe.data.Prefs
import com.example.passwordsafe.data.Vault

class PwAutofillService : AutofillService() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        val form = runCatching { StructureParser.parse(structure) }.getOrNull()
        if (form == null || form.isEmpty || form.packageName == packageName) return callback.onSuccess(null)

        val response = FillResponse.Builder()
        val db = Vault.session.value?.db
        if (db != null) {
            Matcher.matches(db, form).take(5).forEach { r -> response.addDataset(datasetFor(this, db, r, form)) }
        }

        val intent = Intent(this, AutofillActivity::class.java).apply {
            putParcelableArrayListExtra(AutofillActivity.EXTRA_USER_IDS, ArrayList(form.usernameIds))
            putParcelableArrayListExtra(AutofillActivity.EXTRA_PASS_IDS, ArrayList(form.passwordIds))
            putExtra(AutofillActivity.EXTRA_DOMAIN, form.webDomain)
            putExtra(AutofillActivity.EXTRA_PACKAGE, form.packageName)
        }
        val pi = PendingIntent.getActivity(
            this, (System.currentTimeMillis() and 0x7fffffff).toInt(), intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val auth = Dataset.Builder(
            presentation(this, "Сейф паролей", if (db == null) "Разблокировать и выбрать запись" else "Выбрать другую запись"),
        )
        form.allIds.forEach { auth.setValue(it, null) }
        auth.setAuthentication(pi.intentSender)
        response.addDataset(auth.build())

        callback.onSuccess(response.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess()
    }

    companion object {
        fun presentation(ctx: Context, title: String, subtitle: String): RemoteViews =
            RemoteViews(ctx.packageName, R.layout.autofill_item).apply {
                setTextViewText(R.id.title, title)
                setTextViewText(R.id.subtitle, subtitle)
            }

        fun datasetFor(ctx: Context, db: PwsDatabase, r: PwsRecord, form: ParsedForm): Dataset =
            datasetFor(ctx, db, r, form.usernameIds, form.passwordIds)

        fun datasetFor(ctx: Context, db: PwsDatabase, r: PwsRecord, userIds: List<AutofillId>, passIds: List<AutofillId>): Dataset {
            val user = db.resolveUsername(r).ifEmpty { r.email }
            val pw = db.resolvePassword(r)
            val b = Dataset.Builder(presentation(ctx, r.title.ifEmpty { "(без названия)" }, user.ifEmpty { "без логина" }))
            userIds.forEach { b.setValue(it, AutofillValue.forText(user)) }
            passIds.forEach { b.setValue(it, AutofillValue.forText(pw)) }
            return b.build()
        }
    }
}
