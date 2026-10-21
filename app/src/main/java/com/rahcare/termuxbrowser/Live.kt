package com.rahcare.termuxbrowser

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Termux-এর কমান্ডের আউটপুট লাইভ পেতে: অ্যাপ শুধু 127.0.0.1-এ একটা পোর্ট খোলে,
 * কমান্ড নিজের আউটপুট bash-এর /dev/tcp দিয়ে সেখানে পাঠায় (বাইরের কোনো প্যাকেজ লাগে না)।
 */
object Live {
    @Volatile private var port = 0
    @Volatile var onUpdate: ((Job) -> Unit)? = null
    private val exit = Regex("@@EXIT:(-?\\d+)")

    @Synchronized private fun ensure(): Int {
        if (port != 0) return port
        val ss = ServerSocket(0, 30, InetAddress.getByName("127.0.0.1"))
        port = ss.localPort
        Thread {
            while (true) {
                try { val s = ss.accept(); Thread { serve(s) }.apply { isDaemon = true }.start() } catch (_: Exception) {}
            }
        }.apply { isDaemon = true }.start()
        return port
    }

    /** ব্যবহারকারীর স্ক্রিপ্টকে এমনভাবে মুড়ে দেয় যে আউটপুট লাইভ অ্যাপে আসে; সংযোগ না হলে স্ক্রিপ্ট সাধারণভাবেই চলে */
    fun wrap(job: Job, script: String): String {
        val port = ensure()
        val k = job.key
        // আউটপুট আগে ফাইলে লেখা হয়, তাই অ্যাপ মিনিমাইজ/ফ্রিজ হলেও কমান্ড আটকায় না;
        // tail আলাদা করে সেটা অ্যাপে পাঠায় (অ্যাপ ফ্রিজ থাকলে শুধু tail থামে, কমান্ড নয়)
        return "mkdir -p \"\$HOME/.tb\" 2>/dev/null\n" +
            "L=\"\$HOME/.tb/$k.log\"; : > \"\$L\"\n" +
            "termux-wake-lock 2>/dev/null\n" +
            // pkg প্রতিবার সব মিরর পরীক্ষা করে (ধীর নেটে বহু মিনিট); তাই pkg-কে সরাসরি apt দিয়ে বদলাই
            "export DEBIAN_FRONTEND=noninteractive\n" +
            "pkg() { c=\"\$1\"; shift; case \"\$c\" in\n" +
            "update|up) apt update \"\$@\" ;;\n" +
            "upgrade|full-upgrade) apt -o Dpkg::Options::=--force-confold upgrade \"\$@\" ;;\n" +
            "install|i|add) apt -o Dpkg::Options::=--force-confold install \"\$@\" ;;\n" +
            "*) command pkg \"\$c\" \"\$@\" ;; esac; }\n" +
            "FW=\n" +
            "if (exec 3<>/dev/tcp/127.0.0.1/$port) 2>/dev/null; then\n" +
            "exec 3<>/dev/tcp/127.0.0.1/$port\n" +
            "echo \"@@KEY:$k\" >&3\n" +
            "tail -n +1 -f \"\$L\" >&3 2>/dev/null &\n" +
            "FW=\$!\n" +
            "fi\n" +
            "(\n$script\n) >> \"\$L\" 2>&1\n" +
            "c=\$?\n" +
            "echo \"@@EXIT:\$c\" >> \"\$L\"\n" +
            "if [ -n \"\$FW\" ]; then sleep 2; kill \$FW 2>/dev/null; fi\n" +
            "termux-wake-unlock 2>/dev/null\n" +
            "tail -c 8000 \"\$L\" | sed '/^@@EXIT/d'\n" +
            "rm -f \"\$L\"\n" +
            "exit \$c\n"
    }

    private fun finish(job: Job, code: Int) {
        job.status = if (code == 0) "ok" else "fail"
        job.end = System.currentTimeMillis()
        job.out = job.out.trimEnd() + "\n(exit $code)"
    }

    private fun serve(s: Socket) {
        var job: Job? = null
        try {
            val r = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val first = r.readLine() ?: return
            if (!first.startsWith("@@KEY:")) return
            job = Jobs.find(first.removePrefix("@@KEY:")) ?: return
            val buf = CharArray(4096)
            while (true) {
                val n = r.read(buf)
                if (n < 0) break
                job.out = (job.out + String(buf, 0, n)).takeLast(60000)
                var done = false
                exit.find(job.out)?.let { m ->
                    job.out = job.out.replace(m.value, "")
                    finish(job, m.groupValues[1].toInt()); done = true
                }
                val now = System.currentTimeMillis()
                if (done || now - job.lastPush > 150) {
                    job.lastPush = now; job.refreshPct(); onUpdate?.invoke(job)
                }
            }
        } catch (_: Exception) {
        } finally {
            try { s.close() } catch (_: Exception) {}
            job?.let { it.refreshPct(); onUpdate?.invoke(it) }
        }
    }
}
