"""Pair the PC and the Meebook in Syncthing and create the shared folders via the REST API.

usage: python syncthing_setup.py
Tablet GUI must be forwarded first: adb forward tcp:18384 tcp:8384
"""
import json
import ssl
import urllib.request

CTX = ssl._create_unverified_context()  # Syncthing GUI uses a self-signed cert on localhost

PC = {"url": "https://127.0.0.1:8384", "key": "rAZ725jmeFWTu63pUVw9U2Hm6woHCgcK",
      "id": "<SYNCTHING-DEVICE-ID>", "name": "PC"}
TAB = {"url": "https://127.0.0.1:18384", "key": "57xctzzA3iw3ApEfwvsftFJWLPdWHqNX",
       "id": "<SYNCTHING-DEVICE-ID>", "name": "Meebook M6C"}

TRASH = {"type": "trashcan", "params": {"cleanoutDays": "30"}, "cleanupIntervalS": 3600}


def call(side, method, path, body=None):
    req = urllib.request.Request(side["url"] + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"X-API-Key": side["key"], "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=20, context=CTX) as r:
        data = r.read()
        return json.loads(data) if data else None


def add_device(side, other):
    call(side, "PUT", "/rest/config/devices/" + other["id"],
         {"deviceID": other["id"], "name": other["name"], "addresses": ["dynamic"], "autoAcceptFolders": False})


def add_folder(side, fid, label, path, ftype, versioning):
    call(side, "PUT", "/rest/config/folders/" + fid, {
        "id": fid, "label": label, "path": path, "type": ftype,
        "devices": [{"deviceID": PC["id"]}, {"deviceID": TAB["id"]}],
        "fsWatcherEnabled": True, "rescanIntervalS": 3600,
        "versioning": versioning or {"type": ""},
    })


for side, other in ((PC, TAB), (TAB, PC)):
    add_device(side, other)

add_folder(PC, "meebook-books", "Meebook Books", r"C:\Users\<you>\Meebook\Books", "sendreceive", TRASH)
add_folder(TAB, "meebook-books", "Meebook Books", "/storage/emulated/0/Books", "sendreceive", TRASH)
add_folder(PC, "wordtrail", "WordTrail", r"C:\Users\<you>\Meebook\WordTrail", "receiveonly", None)
add_folder(TAB, "wordtrail", "WordTrail", "/storage/emulated/0/WordTrail", "sendonly", None)

for side in (PC, TAB):
    cfg = call(side, "GET", "/rest/config")
    print(side["name"], "devices:", [d["name"] for d in cfg["devices"]],
          "folders:", [(f["id"], f["type"], f["path"]) for f in cfg["folders"]])
