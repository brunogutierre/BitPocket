#!/bin/sh
# Fails if production Kotlin code references Bitcoin mainnet.
#
# BitPocket is a study wallet: mainnet is disabled by design. The only place
# allowed to name a BDK network is the SupportedNetwork mapping below, which
# maps the app's own enum (Signet/Testnet4) to BDK types. Any other mainnet
# reference in src/main is a bug and must fail CI before review.
#
# Usage: sh scripts/check-no-mainnet.sh   (run from the repository root)
set -eu

ALLOWED="./core/src/main/kotlin/io/github/brunogutierre/bitpocket/core/network/SupportedNetwork.kt"
# Network.BITCOIN (BDK mainnet) and NetworkKind.MAIN, as whole identifiers.
PATTERN='(Network\.BITCOIN|NetworkKind\.MAIN)([^A-Za-z0-9_]|$)'

matches=$(
    find . -path '*/build' -prune -o -path '*/src/main/*' -name '*.kt' -type f -print |
        grep -v -x -F "$ALLOWED" |
        while IFS= read -r file; do
            grep -n -E "$PATTERN" "$file" | sed "s|^|$file:|" || true
        done
)

if [ -n "$matches" ]; then
    echo "Mainnet references are not allowed outside $ALLOWED:" >&2
    echo "$matches" >&2
    exit 1
fi

echo "OK: no mainnet references found."
