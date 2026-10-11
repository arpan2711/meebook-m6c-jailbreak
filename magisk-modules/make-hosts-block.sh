#!/system/bin/sh
# Build the Magisk "hosts" module (systemless /system/etc/hosts) from hosts-block.txt. Run as root on the tablet:
#   su -c "sh make-hosts-block.sh /data/local/tmp/hosts-block.txt"   then reboot.
# Undo: rm -rf /data/adb/modules/hosts and reboot.
LIST=${1:-/data/local/tmp/hosts-block.txt}
M=/data/adb/modules/hosts
rm -rf $M; mkdir -p $M/system/etc
cat > $M/module.prop <<EOF
id=hosts
name=Systemless Hosts (Meebook phone-home block)
version=1
versionCode=1
author=meebook-jailbreak
description=Blocks Haoqing/OEM phone-home hosts (see hosts-block.txt). Remove the module to undo.
EOF
cp /system/etc/hosts $M/system/etc/hosts
echo "# --- meebook phone-home block ---" >> $M/system/etc/hosts
grep -v '^#' "$LIST" | grep -v '^[[:space:]]*$' | while read h; do
    echo "0.0.0.0 $h" >> $M/system/etc/hosts
    echo ":: $h" >> $M/system/etc/hosts
done
chmod 644 $M/system/etc/hosts
chcon u:object_r:system_file:s0 $M/system/etc/hosts
cat $M/system/etc/hosts
