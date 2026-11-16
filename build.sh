#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"

GRADLE_VERSION="9.2.1"
CACHE_DIR="${HOME}/.gradle/wheat-sweep"
GRADLE_HOME="${CACHE_DIR}/gradle-${GRADLE_VERSION}"
ARCHIVE="${CACHE_DIR}/gradle-${GRADLE_VERSION}-bin.zip"
URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

if ! command -v java >/dev/null 2>&1; then
  echo "Не найдена Java. Установи JDK 21 и повтори ./build.sh." >&2
  exit 1
fi

if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  mkdir -p "${CACHE_DIR}"
  echo "Скачиваю Gradle ${GRADLE_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL --retry 3 "$URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$URL"
  else
    echo "Для скачивания Gradle нужен curl или wget." >&2
    exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$ARCHIVE" -d "$CACHE_DIR"
  elif command -v python3 >/dev/null 2>&1; then
    python3 -c 'import sys,zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' "$ARCHIVE" "$CACHE_DIR"
  else
    echo "Нужен unzip или Python 3 для распаковки Gradle." >&2
    exit 1
  fi
  rm -f "$ARCHIVE"
fi

exec "${GRADLE_HOME}/bin/gradle" build "$@"
