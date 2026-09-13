#!/usr/bin/env python3
"""Push a rich notification to a Portal panel through Home Assistant.

The payload carries the words; the panel asks Home Assistant to render them and plays the clip it
gets back. Nothing here talks to the broker directly, so no broker credentials are needed.

    export HA_URL=https://ha.example.org HA_TOKEN=eyJ...
    tools/portal_notify.py --device 117daa67dd009788 \
        --message "Un colis vient d'être livré" --icon mdi:package-variant-closed \
        --level warning --speak

Run with --list-devices to discover the panels and their device ids.
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

def call(ha, token, path, payload=None, timeout=60):
    url = f"{ha.rstrip('/')}{path}"
    data = json.dumps(payload).encode() if payload is not None else None
    req = urllib.request.Request(url, data=data, method="POST" if data else "GET")
    req.add_header("Authorization", f"Bearer {token}")
    req.add_header("Content-Type", "application/json")
    # Some reverse proxies (Cloudflare among them) answer 403/1010 to urllib's default agent.
    req.add_header("User-Agent", "portal-notify/1.0")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            body = r.read().decode()
    except urllib.error.HTTPError as e:
        sys.exit(f"Home Assistant refused {path}: {e.code} {e.read().decode()[:300]}")
    except OSError as e:
        sys.exit(f"Home Assistant unreachable at {url}: {e}")
    return json.loads(body) if body.strip() else None


def list_devices(ha, token):
    """Panel name → device id, read off the IP sensors every panel publishes."""
    found = []
    for state in call(ha, token, "/api/states"):
        entity = state["entity_id"]
        if entity.startswith("sensor.") and entity.endswith("_ip_address"):
            name = state["attributes"].get("friendly_name", entity)
            found.append((name, state["state"]))
    return found


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--ha", default=os.environ.get("HA_URL"), help="Home Assistant base URL (env HA_URL)")
    ap.add_argument("--token", default=os.environ.get("HA_TOKEN"), help="long-lived token (env HA_TOKEN)")
    ap.add_argument("--device", default=os.environ.get("PORTAL_DEVICE_ID"), help="panel device id (env PORTAL_DEVICE_ID)")
    ap.add_argument("--message", "-m", help="text shown on the panel")
    ap.add_argument("--title", help="line above the message")
    ap.add_argument("--icon", help="Home Assistant icon, e.g. mdi:package-variant-closed")
    ap.add_argument("--color", help="accent override, e.g. '#30D158'. Beats --level")
    ap.add_argument("--level", choices=("info", "warning", "critical"), default="info")
    ap.add_argument("--duration", type=int, help="milliseconds on screen (default: level, or the audio's length)")
    ap.add_argument("--tone", choices=("alert", "doorbell", "chime", "success", "error", "ping", "none"),
                    help="chime when there is no speech, or when the speech cannot be fetched")
    ap.add_argument("--no-wake", action="store_true", help="leave the screen asleep")
    ap.add_argument("--countdown", metavar="DURÉE",
                    help="compte à rebours affiché : 20s, 10m, 1h30m, 1:30, ou des secondes")
    ap.add_argument("--until", metavar="ISO",
                    help="instant de fin (prioritaire sur --countdown), ex. 2026-09-05T22:30:00+02:00")
    ap.add_argument("--end-message", help="message affiché quand le compteur atteint zéro")
    ap.add_argument("--end-tone", choices=("alert", "doorbell", "chime", "success", "error", "ping", "none"),
                    help="carillon joué à zéro (défaut : alert)")
    ap.add_argument("--speak", nargs="?", const="", metavar="TEXT",
                    help="say it out loud: the message, or TEXT when given")
    ap.add_argument("--engine", help="TTS entity (default: the panel picks the first one)")
    ap.add_argument("--language", help="langue du moteur, ex. fr-FR (défaut : celle du moteur)")
    ap.add_argument("--audio", help="ready-made clip URL, played instead of rendering speech")
    ap.add_argument("--list-devices", action="store_true", help="print the panels this HA knows about")
    ap.add_argument("--dry-run", action="store_true", help="print the payload, publish nothing")
    args = ap.parse_args()

    if not args.ha or not args.token:
        ap.error("--ha/--token (or HA_URL/HA_TOKEN) are required")

    if args.list_devices:
        for name, ip in list_devices(args.ha, args.token):
            print(f"{name:32} {ip}")
        print("\nThe device id is not an entity: read it off the panel "
              "(adb shell run-as com.iblu01.portallauncher cat shared_prefs/portal_launcher.xml).")
        return

    if not args.message and args.speak is None and not args.audio:
        ap.error("nothing to show: pass --message, --speak or --audio")
    if not args.device:
        ap.error("--device (or PORTAL_DEVICE_ID) is required")

    spoken = None
    if args.speak is not None and not args.audio:
        spoken = args.speak or args.message
        if not spoken:
            ap.error("--speak with no text needs --message to say")

    alert = {"message": args.message or "", "level": args.level, "wake": not args.no_wake}
    for key, value in (("title", args.title), ("icon", args.icon), ("color", args.color),
                       ("audio", args.audio), ("tts", spoken), ("engine", args.engine),
                       ("language", args.language if spoken else None),
                       ("tone", args.tone), ("duration", args.duration),
                       ("countdown", args.countdown), ("until", getattr(args, "until")),
                       ("end_message", args.end_message), ("end_tone", args.end_tone)):
        if value:
            alert[key] = value

    topic = f"portal/{args.device}/notification"
    payload = json.dumps(alert, ensure_ascii=False)
    if args.dry_run:
        print(topic)
        print(payload)
        return

    call(args.ha, args.token, "/api/services/mqtt/publish", {"topic": topic, "payload": payload})
    print(f"published to {topic}")
    print(payload)


if __name__ == "__main__":
    main()
