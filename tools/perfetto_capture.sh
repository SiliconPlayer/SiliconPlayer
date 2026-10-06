#!/bin/bash
# Captures an on-device Perfetto trace (frame timeline, ftrace sched/freq,
# app atrace sections) for N seconds, then pulls it to the given path.
# Usage: tools/perfetto_capture.sh <seconds> <out.pftrace>
set -euo pipefail
SECONDS="$1"
OUT="$2"
cat > /tmp/jank_perfetto_config.txt <<'CFG'
buffers { size_kb: 63488 }
data_sources {
    config {
        name: "linux.ftrace"
        ftrace_config {
            ftrace_events: "sched/sched_switch"
            ftrace_events: "sched/sched_wakeup"
            ftrace_events: "sched/sched_wakeup_new"
            ftrace_events: "freq/cpu_frequency"
        }
    }
}
data_sources {
    config {
        name: "linux.process_stats"
        process_stats_config { scan_all_processes_on_start: true }
    }
}
data_sources { config { name: "android.surfaceflinger.frametimeline" } }
data_sources { config { name: "track_event" } }
duration_ms: __DURATION_MS__
write_into_file: true
output_path: "/data/misc/perfetto-traces/jank.pftrace"
CFG
sed -i "s/__DURATION_MS__/$((SECONDS * 1000))/" /tmp/jank_perfetto_config.txt
adb shell perfetto --txt -c /dev/stdin --txt -o /data/misc/perfetto-traces/jank.pftrace < /tmp/jank_perfetto_config.txt
adb pull /data/misc/perfetto-traces/jank.pftrace "$OUT" > /dev/null
echo "Trace saved to $OUT"
