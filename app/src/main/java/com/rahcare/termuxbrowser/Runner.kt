package com.rahcare.termuxbrowser

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/** Termux-এর RUN_COMMAND ইন্টেন্ট দিয়ে কমান্ড চালায় */
object Runner {
    const val TERMUX = "com.termux"
    private const val SERVICE = "com.termux.app.RunCommandService"
    private const val ACTION = "com.termux.RUN_COMMAND"
    private const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    private const val HOME = "/data/data/com.termux/files/home"

    fun installed(c: Context) = try {
        c.packageManager.getPackageInfo(TERMUX, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun hasPermission(c: Context) =
        c.checkSelfPermission("com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED

    /** null = সফল, নইলে বাংলা ত্রুটির বার্তা */
    fun run(c: Context, cmd: Cmd): String? {
        if (!installed(c)) return "Termux ইনস্টল করা নেই (F-Droid থেকে ইনস্টল করুন)"
        if (!hasPermission(c)) return "Termux-এর RUN_COMMAND অনুমতি দেওয়া নেই"
        val i = Intent(ACTION).setClassName(TERMUX, SERVICE)
        i.putExtra("com.termux.RUN_COMMAND_PATH", BASH)
        i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", cmd.script))
        i.putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", cmd.background)
        var job: Job? = null
        if (cmd.background) {
            job = Jobs.start(cmd.title)
            val r = Intent(c, ResultReceiver::class.java).setData(Uri.parse("job://${job.key}")).putExtra("job", job.key)
            val pi = PendingIntent.getBroadcast(c, 0, r, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            i.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi)
        } else i.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
            null
        } catch (e: SecurityException) {
            job?.status = "fail"; job?.out = "অনুমতি নেই"
            "অনুমতি নেই। Termux-এ চালান: echo 'allow-external-apps=true' >> ~/.termux/termux.properties, তারপর Termux রিস্টার্ট করুন"
        } catch (e: ActivityNotFoundException) {
            job?.status = "fail"; "Termux সার্ভিস পাওয়া যায়নি"
        } catch (e: Exception) {
            job?.status = "fail"; "ত্রুটি: ${e.message}"
        }
    }
}
