#!/bin/sh
# Fails if Android code mentions SlotId (ADR 0009).
#
# Slot A holds the real wallet and slot B the duress decoy. Only :core's SlotWalletAccess may
# know which is which; if the UI ever branched on the slot, the decoy could look different from
# the real wallet. The app sees only the unlocked session.
#
# Usage: sh scripts/check-no-slot-in-app.sh   (run from the repository root)
set -eu

matches=$(grep -rn --include='*.kt' -E '(^|[^A-Za-z0-9_])SlotId([^A-Za-z0-9_]|$)' app/src/main || true)

if [ -n "$matches" ]; then
    echo "SlotId must not appear in app/src/main:" >&2
    echo "$matches" >&2
    exit 1
fi

echo "OK: the app does not know which slot is open."
