#!/system/bin/sh
# TEMPORARY network audit: records every TCP connection (state ESTABLISHED/SYN_SENT) and connected UDP
# socket with its owning UID, once a second for 5 minutes after boot, then deletes itself.
# Install as /data/adb/service.d/netcap-once.sh, reboot, read /data/local/tmp/netcap-boot.txt.
OUT=/data/local/tmp/netcap-boot.txt
echo "boot $(date)" > $OUT
end=$(( $(date +%s) + 300 ))
while [ "$(date +%s)" -lt "$end" ]; do
    t=$(date +%T)
    for f in /proc/net/tcp /proc/net/tcp6; do
        awk -v t="$t" 'NR > 1 && ($4 == "01" || $4 == "02") { print t, "tcp", $3, $8 }' $f
    done >> $OUT
    for f in /proc/net/udp /proc/net/udp6; do
        awk -v t="$t" 'NR > 1 && $3 !~ /^0+:0000$/ { print t, "udp", $3, $8 }' $f
    done >> $OUT
    sleep 1
done
echo "end $(date)" >> $OUT
rm -f "$0"
