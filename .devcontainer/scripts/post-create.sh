#!/usr/bin/env bash
set -euo pipefail

# Claude Code via the native installer, not npm or the devcontainer feature: those
# cannot auto-update here (NPM_CONFIG_MIN_RELEASE_AGE rejects every daily release,
# NPM_CONFIG_IGNORE_SCRIPTS skips the postinstall, and the feature installs as root).
# ~/.local is a persisted volume, so this downloads once; the auto-updater keeps it current.
if [ ! -x "$HOME/.local/bin/claude" ]; then
  curl -fsSL https://claude.ai/install.sh | bash
fi

# Playwright MCP server and browser; sync-claude-config.sh registers it with Claude Code.
# Not in the Dockerfile: npm arrives with the Node feature. ~/.npm and
# ~/.cache/ms-playwright are persisted volumes, so a rebuild downloads nothing.

# Pinned: NPM_CONFIG_MIN_RELEASE_AGE rejects `@latest` when it is under a week old.
MCP_VERSION=0.0.79

# Global so the MCP server starts without a network fetch (no npx).
npm install -g --prefer-offline "@playwright/mcp@${MCP_VERSION}"

# Browser only: OS deps come from the Dockerfile. Uses the MCP package's own
# playwright so the browser revision cannot drift from it.
"$(npm root -g)/@playwright/mcp/node_modules/.bin/playwright" install chromium

# Gradle: the JVM ignores HTTP(S)_PROXY, so the proxy from docker-compose.yml goes into
# ~/.gradle/gradle.properties (a persisted volume; written once). Dependencies then
# resolve through squid -- see proxy/allowed-domains.txt for Maven Central and the
# plugin portal.
GRADLE_PROPS="$HOME/.gradle/gradle.properties"
if ! grep -qs '^systemProp.https.proxyHost=' "$GRADLE_PROPS"; then
  proxy_host="$(sed -E 's#^https?://([^:/]+).*#\1#' <<< "${HTTPS_PROXY:?}")"
  proxy_port="$(sed -E 's#^https?://[^:/]+:([0-9]+).*#\1#' <<< "$HTTPS_PROXY")"
  cat >> "$GRADLE_PROPS" <<GRADLE_EOF
# Written by .devcontainer/scripts/post-create.sh: route Gradle through the squid proxy.
systemProp.http.proxyHost=$proxy_host
systemProp.http.proxyPort=$proxy_port
systemProp.https.proxyHost=$proxy_host
systemProp.https.proxyPort=$proxy_port
systemProp.http.nonProxyHosts=localhost|127.0.0.1
GRADLE_EOF
fi
