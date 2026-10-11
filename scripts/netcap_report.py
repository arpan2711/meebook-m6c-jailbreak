"""Summarise a /proc/net capture from device/netcap-once.sh: remote endpoints per app, with reverse DNS.

  python -I scripts/netcap_report.py <netcap-boot.txt> <uid-map.txt> [resolved.txt]

uid-map.txt = output of `cmd package list packages -U`; resolved.txt = "host ip" lines (optional) to
label IPs with the hostnames the device resolved.
"""
import collections
import socket
import sys


def addr(hexaddr):
    ip, port = hexaddr.split(":")
    port = int(port, 16)
    if len(ip) == 8:
        b = bytes.fromhex(ip)[::-1]
        return socket.inet_ntop(socket.AF_INET, b), port
    raw = bytes.fromhex(ip)
    words = b"".join(raw[i:i + 4][::-1] for i in range(0, 16, 4))
    if words[:12] == b"\0" * 10 + b"\xff\xff":
        return socket.inet_ntop(socket.AF_INET, words[12:]), port
    return socket.inet_ntop(socket.AF_INET6, words), port


uids = collections.defaultdict(list)
for line in open(sys.argv[2], encoding="utf-8", errors="replace"):
    if "uid:" in line:
        pkg = line.split("package:")[1].split()[0]
        uids[line.rsplit("uid:", 1)[1].strip()].append(pkg)
names = {}
if len(sys.argv) > 3:
    for line in open(sys.argv[3], encoding="utf-8"):
        parts = line.split()
        if len(parts) == 2:
            names.setdefault(parts[1], set()).add(parts[0])

seen = collections.defaultdict(lambda: [None, None, 0])
for line in open(sys.argv[1], encoding="utf-8", errors="replace"):
    f = line.split()
    if len(f) != 4 or f[1] not in ("tcp", "udp"):
        continue
    ip, port = addr(f[2])
    if ip.startswith(("127.", "0.0.0.0", "::")) and ip != "::" or ip in ("::1",):
        continue
    key = (f[1], ip, port, f[3])
    s = seen[key]
    s[0] = s[0] or f[0]
    s[1] = f[0]
    s[2] += 1

rev = {}
for (_, ip, _, _) in seen:
    if ip not in rev:
        try:
            rev[ip] = socket.gethostbyaddr(ip)[0]
        except OSError:
            rev[ip] = "-"

for (proto, ip, port, uid), (first, last, n) in sorted(seen.items(), key=lambda kv: kv[1][0]):
    who = ",".join(uids.get(uid, [])[:3]) or f"uid {uid}"
    if uid == "1000":
        who = "system (uid 1000)"
    label = "/".join(sorted(names.get(ip, []))) or rev[ip]
    print(f"{first}-{last}  {proto} {ip}:{port:<5} {label[:45]:45}  {who}")
