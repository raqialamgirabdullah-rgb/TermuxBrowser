package com.rahcare.termuxbrowser

import android.content.Context

/** ব্রাউজারের হোম পেজ: Termux কমান্ড বাটন + চলমান কাজ */
object Home {
    const val BASE = "https://termux.home/"

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun jobsHtml(): String {
        if (Jobs.list.isEmpty()) return "<p class=m>এখনো কিছু চালানো হয়নি।</p>"
        return Jobs.list.asReversed().take(10).joinToString("") { j ->
            val dot = when (j.status) { "running" -> "#f59e0b"; "ok" -> "#16a34a"; else -> "#dc2626" }
            val st = when (j.status) { "running" -> "চলছে…"; "ok" -> "সম্পন্ন"; else -> "ব্যর্থ" }
            "<div class=job><b><span class=dot style='background:$dot'></span>${esc(j.title)}</b> <span class=m>$st</span>" +
                (if (j.out.isNotBlank()) "<pre>${esc(j.out)}</pre>" else "") + "</div>"
        }
    }

    fun html(c: Context): String {
        val ready = Runner.installed(c) && Runner.hasPermission(c)
        val cards = Store.all(c).joinToString("") { d ->
            "<div class=card><a class=run href='termux://run/${d.id}'><b>▶ ${esc(d.title)}</b><small>${esc(d.script)}</small></a>" +
                "<a class=ed href='termux://edit/${d.id}'>✎</a></div>"
        }
        val warn = if (ready) "" else
            "<div class=warn><b>সেটআপ দরকার</b><br>Termux (F-Droid) ইনস্টল করুন → Termux-এ চালান:<br><code>mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties</code><br>Termux রিস্টার্ট করুন, তারপর ⋮ মেনু › অ্যাপ অনুমতি থেকে \"Run commands in Termux environment\" দিন।</div>"
        return """<!doctype html><html><head><meta charset=utf-8><meta name=viewport content="width=device-width,initial-scale=1">
<style>
body{font-family:system-ui,sans-serif;margin:0;padding:14px;background:#f3f4f6;color:#111827}
h1{font-size:20px;margin:6px 2px 2px}h2{font-size:15px;margin:18px 2px 8px;color:#374151}
.m{color:#6b7280;font-size:13px}.warn{background:#fef3c7;color:#78350f;border-radius:12px;padding:12px;margin:10px 0;font-size:13.5px}
code{background:#fde68a;padding:2px 4px;border-radius:4px;word-break:break-all;font-size:12px}
.card{display:flex;background:#fff;border-radius:14px;margin-bottom:9px;overflow:hidden}
.run{flex:1;padding:12px 14px;color:#111827;text-decoration:none}.run small{display:block;color:#6b7280;font:11.5px monospace;margin-top:3px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;max-width:70vw}
.ed{padding:12px 16px;color:#6b7280;text-decoration:none;display:flex;align-items:center}
.add{display:block;text-align:center;padding:12px;background:#111827;color:#fff;border-radius:14px;text-decoration:none;margin-top:4px}
.job{background:#fff;border-radius:12px;padding:10px 12px;margin-bottom:8px}.dot{display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:7px}
pre{background:#111827;color:#d1fae5;border-radius:8px;padding:8px;font-size:11.5px;white-space:pre-wrap;word-break:break-all;margin:8px 0 0;max-height:220px;overflow:auto}
</style></head><body>
<h1>Termux Browser</h1><div class=m>এখানে ক্লিক করলে কাজ ব্যাকগ্রাউন্ডে চলে — আপনি ব্রাউজারেই থাকবেন।</div>
$warn
<h2>কমান্ড</h2>$cards<a class=add href='termux://edit/new'>＋ নতুন কমান্ড</a>
<h2>চলমান ও শেষ হওয়া কাজ</h2><div id=jobs>${jobsHtml()}</div>
</body></html>"""
    }
}
