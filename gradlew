#!/bin/sh
##############################################################################
# Self-bootstrapping Gradle wrapper
# Downloads Gradle 8.6 automatically on first run — no local Gradle needed.
##############################################################################

GRADLE_VERSION="8.6"
GRADLE_DIST="gradle-${GRADLE_VERSION}-bin"
GRADLE_URL="https://services.gradle.org/distributions/${GRADLE_DIST}.zip"

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="${APP_HOME}/gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPS="${APP_HOME}/gradle/wrapper/gradle-wrapper.properties"

# ── If wrapper jar exists, use it normally ───────────────────────────────────
if [ -f "$WRAPPER_JAR" ]; then
    exec java \
        -classpath "$WRAPPER_JAR" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"
fi

# ── Otherwise, bootstrap: download Gradle and run it directly ────────────────
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
GRADLE_INSTALL_DIR="${GRADLE_USER_HOME}/wrapper/dists/${GRADLE_DIST}"
GRADLE_BIN="${GRADLE_INSTALL_DIR}/${GRADLE_DIST}/bin/gradle"

if [ ! -f "$GRADLE_BIN" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    mkdir -p "${GRADLE_INSTALL_DIR}"
    TMPZIP="${GRADLE_INSTALL_DIR}/${GRADLE_DIST}.zip"

    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "${GRADLE_URL}" -o "${TMPZIP}"
    elif command -v wget >/dev/null 2>&1; then
        wget -q "${GRADLE_URL}" -O "${TMPZIP}"
    else
        echo "ERROR: neither curl nor wget found. Cannot download Gradle."
        exit 1
    fi

    echo "Extracting Gradle..."
    unzip -q "${TMPZIP}" -d "${GRADLE_INSTALL_DIR}"
    rm "${TMPZIP}"
    chmod +x "${GRADLE_INSTALL_DIR}/${GRADLE_DIST}/bin/gradle"
    echo "Gradle ${GRADLE_VERSION} ready."
fi

exec "${GRADLE_BIN}" "$@"
