#!/usr/bin/env sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/course-release-logbook-classes"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
javac -d "$BUILD_DIR" $(find src/main/java -name '*.java')
cp -R src/main/resources/. "$BUILD_DIR/"
java -cp "$BUILD_DIR" education.devtools.CourseReleaseJob \
  algebra-101 a18c0de course-release-2026-09-29 true false
