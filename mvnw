#!/bin/sh

# Minimal Maven wrapper script that downloads the official wrapper JAR on demand.

set -e

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$BASE_DIR/.mvn/wrapper/maven-wrapper.jar"
WRAPPER_PROPERTIES="$BASE_DIR/.mvn/wrapper/maven-wrapper.properties"
WRAPPER_MAIN_CLASS="org.apache.maven.wrapper.MavenWrapperMain"

if [ -z "$JAVA_HOME" ]; then
  JAVA_EXEC="java"
else
  JAVA_EXEC="$JAVA_HOME/bin/java"
fi

download_wrapper() {
  # shellcheck disable=SC2039
  if [ -n "$MVNW_VERBOSE" ]; then
    echo "Downloading Maven wrapper JAR..."
  fi
  WRAPPER_URL=$(grep -E "^wrapperUrl=" "$WRAPPER_PROPERTIES" | cut -d'=' -f2-)
  if [ -z "$WRAPPER_URL" ]; then
    echo "wrapperUrl not set in $WRAPPER_PROPERTIES" >&2
    exit 1
  fi

  mkdir -p "$(dirname "$WRAPPER_JAR")"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$WRAPPER_URL" -o "$WRAPPER_JAR"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$WRAPPER_URL" -O "$WRAPPER_JAR"
  else
    echo "Neither curl nor wget is available to download Maven wrapper" >&2
    exit 1
  fi
}

if [ ! -f "$WRAPPER_PROPERTIES" ]; then
  cat <<'EOF' >"$WRAPPER_PROPERTIES"
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip
wrapperUrl=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
EOF
fi

if [ ! -f "$WRAPPER_JAR" ]; then
  download_wrapper
fi

if [ ! -x "$JAVA_EXEC" ]; then
  echo "Java executable not found. Please set JAVA_HOME." >&2
  exit 1
fi

exec "$JAVA_EXEC" ${JVM_OPTS:-} \
  -Dmaven.multiModuleProjectDirectory="$BASE_DIR" \
  -classpath "$WRAPPER_JAR" \
  "$WRAPPER_MAIN_CLASS" "$@"
