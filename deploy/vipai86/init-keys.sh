#!/bin/sh
set -eu
umask 077
mkdir -p /keys /game
private_tmp=/keys/.private.new.$$
public_tmp=/keys/.public.new.$$
trap 'rm -f "$private_tmp" "$public_tmp"' EXIT
if [ ! -f /keys/private.key ] && [ ! -f /keys/publickey.pem ]; then
    openssl genpkey -algorithm RSA -out "$private_tmp" -pkeyopt rsa_keygen_bits:2048
    openssl pkey -in "$private_tmp" -check -noout
    mv "$private_tmp" /keys/private.key
fi
if [ -s /keys/private.key ] && [ ! -e /keys/publickey.pem ]; then
    openssl pkey -in /keys/private.key -pubout -out "$public_tmp"
    mv "$public_tmp" /keys/publickey.pem
fi
test -s /keys/private.key && test -s /keys/publickey.pem
openssl pkey -in /keys/private.key -pubout -out /tmp/derived.pub
cmp -s /tmp/derived.pub /keys/publickey.pem || { echo 'RSA key pair mismatch' >&2; exit 1; }
if [ -e /game/publickey.pem ]; then
    cmp -s /keys/publickey.pem /game/publickey.pem || { echo 'Game public key differs; nothing overwritten' >&2; exit 1; }
else
    cp /keys/publickey.pem /game/publickey.pem
fi
chmod 600 /keys/private.key /keys/publickey.pem /game/publickey.pem

# The native gateway reuses the instance login identity.
case "${LAUNCHER_MODE:-dof}" in
    dof|native) ;;
    *) echo 'Invalid LAUNCHER_MODE' >&2; exit 1 ;;
esac
if [ "${RAID_EXTENSION:-off}" = on ] || [ "${LAUNCHER_MODE:-dof}" = native ]; then
    test -d /game/dof-easy
    mkdir -p /game/dof-easy/keys
    for entry in privatekey.pem publickey.pem; do
        source=/keys/publickey.pem
        [ "$entry" != privatekey.pem ] || source=/keys/private.key
        target=/game/dof-easy/keys/$entry
        test ! -L "$target"
        if [ -e "$target" ]; then
            cmp -s "$source" "$target" || { echo 'Native key differs; nothing overwritten' >&2; exit 1; }
        else
            cp "$source" "$target"
        fi
        chmod 600 "$target"
    done
fi
