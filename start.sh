#!/bin/sh
set -eu
if [ -n "${TS_AUTHKEY:-}" ]; then
  tailscaled --tun=userspace-networking --state=mem: --socket=/tmp/ts.sock --socks5-server=127.0.0.1:1055 >/tmp/tailscaled.log 2>&1 &
  if timeout 45 tailscale --socket=/tmp/ts.sock up --auth-key="$TS_AUTHKEY" --hostname=uniq-service-desk --accept-dns=false >/tmp/ts-up.log 2>&1; then
    echo "Tailscale authentication successful"
    if curl --silent --show-error --max-time 8 --socks5-hostname 127.0.0.1:1055 telnet://100.81.28.119:1521 </dev/null >/dev/null 2>&1; then
      echo "Oracle TCP probe completed"
    else
      echo "Oracle TCP probe inconclusive; JDBC validation required"
    fi
  else
    echo "Tailscale authentication failed or timed out; starting existing portal"
  fi
fi
exec java -jar /app/app.jar
