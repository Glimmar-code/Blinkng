# Godot 4.3 TLS-only dedicated BLINK LIFE server. Requires Linux amd64.
# Source: https://github.com/godotengine/godot/releases/tag/4.3-stable
FROM debian:bookworm-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl unzip libatomic1 libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

RUN curl --fail --location --retry 3 \
      'https://github.com/godotengine/godot/releases/download/4.3-stable/Godot_v4.3-stable_linux.x86_64.zip' \
      -o /tmp/godot.zip \
    && unzip -q /tmp/godot.zip -d /usr/local/bin \
    && mv /usr/local/bin/Godot_v4.3-stable_linux.x86_64 /usr/local/bin/godot \
    && chmod 0755 /usr/local/bin/godot \
    && rm /tmp/godot.zip

RUN useradd -r -u 10001 -g nogroup -d /var/lib/blinklife blinklife \
    && mkdir -p /opt/blink-life /var/lib/blinklife \
    && chown -R blinklife:nogroup /var/lib/blinklife
COPY game3d/ /opt/blink-life/
USER blinklife
ENV HOME=/var/lib/blinklife XDG_DATA_HOME=/var/lib/blinklife
WORKDIR /opt/blink-life
# Requires the Godot process to refresh a server-only heartbeat every 5s.
# This does not expose a public metrics port or leak user data.
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
  CMD /bin/sh /opt/blink-life/scripts/server_healthcheck.sh
EXPOSE 24567/tcp
VOLUME ["/var/lib/blinklife"]
ENTRYPOINT ["godot", "--headless", "--path", "/opt/blink-life", "--", "--blink-dedicated"]
