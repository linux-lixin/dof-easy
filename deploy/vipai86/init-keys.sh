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
