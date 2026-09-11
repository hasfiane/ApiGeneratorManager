#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXP="$ROOT/experiments/typebridge-lombok"
BUILD="$EXP/build"
rm -rf "$BUILD"
mkdir -p "$BUILD/probe" "$BUILD/no-plugin" "$BUILD/with-plugin" "$BUILD/implicit-exit" "$BUILD/typed-no-plugin" "$BUILD/typed-with-plugin"

JAVA_FEATURE=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
if [[ -z "$JAVA_FEATURE" || "$JAVA_FEATURE" -lt 21 ]]; then
  echo "TypeBridge experiment requires JDK 21+; detected: $(java -version 2>&1 | head -1)" >&2
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
TYPED_FIXTURE="$EXP/fixture/experiment/TypedDomainFixture.java"
NEGATIVE_EXIT="$EXP/fixture/experiment/ImplicitUnwrapRejected.java"

# Control: the positive fixture must be invalid with Lombok alone.
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

# Positive: generic engine discovers the strong type and Lombok builder target.
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

echo "PASS: generalized engine discovered real Lombok 1.18.42 builder members"
echo "PASS: generalized engine elaborated declared UUID -> CustomerId relation"
echo "PASS: runtime output is unchanged ($OUTPUT)"

# Safety: the inverse relation must NOT be inferred, even inside AdaptationScope.
set +e
javac --release 17 "${JVM_EXPORTS[@]}" \
  -cp "$LOMBOK:$BUILD/typebridge-lombok-probe.jar" \
  -processorpath "$LOMBOK:$BUILD/typebridge-lombok-probe.jar" \
  -Xplugin:TypeBridgeLombokProbe \
  -d "$BUILD/implicit-exit" "$NEGATIVE_EXIT" \
  >"$BUILD/implicit-exit.log" 2>&1
IMPLICIT_EXIT_STATUS=$?
set -e
if [[ "$IMPLICIT_EXIT_STATUS" -eq 0 ]]; then
  echo "FAIL: implicit CustomerId -> UUID exit unexpectedly compiled" >&2
  exit 7
fi
if ! grep -Eq 'CustomerId|UUID|incompatible types' "$BUILD/implicit-exit.log"; then
  cat "$BUILD/implicit-exit.log" >&2
  echo "FAIL: implicit-exit rejection failed for an unexpected reason" >&2
  exit 8
fi

echo "PASS: strong -> raw remains explicit inside AdaptationScope"

# Typed-domain A/B based on the real customers/orders schema.
# Baseline control: concise raw -> strong mapper calls must fail without TypeBridge.
set +e
javac --release 17 \
  -cp "$LOMBOK:$BUILD/typebridge-lombok-probe.jar" \
  -processorpath "$LOMBOK" \
  -d "$BUILD/typed-no-plugin" "$TYPED_FIXTURE" \
  >"$BUILD/typed-no-plugin.log" 2>&1
TYPED_NO_PLUGIN_STATUS=$?
set -e
if [[ "$TYPED_NO_PLUGIN_STATUS" -eq 0 ]]; then
  echo "FAIL: typed-domain fixture unexpectedly compiled without TypeBridge" >&2
  exit 9
fi
if ! grep -Eq 'CustomerId|Money|incompatible types' "$BUILD/typed-no-plugin.log"; then
  cat "$BUILD/typed-no-plugin.log" >&2
  echo "FAIL: typed-domain baseline failed for an unexpected reason" >&2
  exit 10
fi

echo "PASS: typed-domain baseline requires explicit wrappers"

javac --release 17 "${JVM_EXPORTS[@]}" \
  -cp "$LOMBOK:$BUILD/typebridge-lombok-probe.jar" \
  -processorpath "$LOMBOK:$BUILD/typebridge-lombok-probe.jar" \
  -Xplugin:TypeBridgeLombokProbe \
  -d "$BUILD/typed-with-plugin" "$TYPED_FIXTURE"

TYPED_OUTPUT=$(java -cp "$BUILD/typed-with-plugin:$BUILD/typebridge-lombok-probe.jar" experiment.TypedDomainFixture)
TYPED_EXPECTED="41|ORD-001|42.50|41|42.50"
if [[ "$TYPED_OUTPUT" != "$TYPED_EXPECTED" ]]; then
  echo "FAIL: typed-domain runtime mismatch: expected $TYPED_EXPECTED, got $TYPED_OUTPUT" >&2
  exit 11
fi

echo "PASS: typed-domain raw Long -> CustomerId and BigDecimal -> Money wrappers were elaborated"
echo "PASS: typed-domain persistence exits remain explicit .value() calls"
echo "PASS: typed-domain runtime output is unchanged ($TYPED_OUTPUT)"
