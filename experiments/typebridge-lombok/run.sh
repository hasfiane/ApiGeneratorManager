#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXP="$ROOT/experiments/typebridge-lombok"
BUILD="$EXP/build"
rm -rf "$BUILD"
mkdir -p "$BUILD/probe" "$BUILD/no-plugin" "$BUILD/with-plugin"

JAVA_FEATURE=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
if [[ -z "$JAVA_FEATURE" || "$JAVA_FEATURE" -lt 21 ]]; then
  echo "TypeBridge probe requires JDK 21+; detected: $(java -version 2>&1 | head -1)" >&2
  exit 2
fi

mvn -q org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy \
  -Dartifact=org.projectlombok:lombok:1.18.42 \
  -DoutputDirectory="$BUILD"
LOMBOK="$BUILD/lombok-1.18.42.jar"
[[ -f "$LOMBOK" ]] || { echo "Lombok jar not found at $LOMBOK" >&2; exit 3; }

EXPORTS=(
  --add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
  --add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED
  --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
  --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
)

javac "${EXPORTS[@]}" -d "$BUILD/probe" $(find "$EXP/src" -name '*.java' -print)
cp -R "$EXP/resources/"* "$BUILD/probe/"
jar --create --file "$BUILD/typebridge-lombok-probe.jar" -C "$BUILD/probe" .

FIXTURE="$EXP/fixture/experiment/LombokFixture.java"

# Negative control: the exact same source must fail when only Lombok is active.
set +e
javac --release 17 \
  -cp "$LOMBOK:$BUILD/typebridge-lombok-probe.jar" \
  -processorpath "$LOMBOK" \
  -d "$BUILD/no-plugin" "$FIXTURE" \
  >"$BUILD/no-plugin.log" 2>&1
NO_PLUGIN_STATUS=$?
set -e
if [[ "$NO_PLUGIN_STATUS" -eq 0 ]]; then
  echo "FAIL: fixture unexpectedly compiled without TypeBridge" >&2
  exit 4
fi
if ! grep -Eq 'CustomerId|incompatible types' "$BUILD/no-plugin.log"; then
  cat "$BUILD/no-plugin.log" >&2
  echo "FAIL: negative control failed for an unexpected reason" >&2
  exit 5
fi

echo "PASS: Lombok-only compilation rejects UUID -> CustomerId as expected"

JVM_EXPORTS=(
  -J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
  -J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED
  -J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
  -J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
)

javac --release 17 "${JVM_EXPORTS[@]}" \
  -cp "$LOMBOK:$BUILD/typebridge-lombok-probe.jar" \
  -processorpath "$LOMBOK:$BUILD/typebridge-lombok-probe.jar" \
  -Xplugin:TypeBridgeLombokProbe \
  -d "$BUILD/with-plugin" "$FIXTURE"

OUTPUT=$(java -cp "$BUILD/with-plugin:$BUILD/typebridge-lombok-probe.jar" experiment.LombokFixture)
EXPECTED="123e4567-e89b-12d3-a456-426614174000"
if [[ "$OUTPUT" != "$EXPECTED" ]]; then
  echo "FAIL: runtime mismatch: expected $EXPECTED, got $OUTPUT" >&2
  exit 6
fi

echo "PASS: real Lombok 1.18.42 generated builder was visible after processing"
echo "PASS: TypeBridge probe elaborated UUID -> CustomerId before javac rejection"
echo "PASS: runtime output is unchanged ($OUTPUT)"
