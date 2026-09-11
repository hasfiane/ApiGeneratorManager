#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXP="$ROOT/experiments/typebridge-generator-ab"
BUILD="$EXP/build"
TB="$ROOT/experiments/typebridge-lombok/build/typebridge-lombok-probe.jar"
rm -rf "$BUILD"
mkdir -p "$BUILD/harness" "$BUILD/baseline-classes" "$BUILD/strong-no-plugin" "$BUILD/strong-classes"

[[ -d "$ROOT/api-generator-core/target/classes" ]] || {
  echo "api-generator-core must be built before the A/B benchmark" >&2
  exit 2
}
[[ -f "$TB" ]] || {
  echo "TypeBridge Lombok probe jar missing; run experiments/typebridge-lombok/run.sh first" >&2
  exit 3
}

LOMBOK=$(find "$HOME/.m2/repository/org/projectlombok/lombok" -name 'lombok-*.jar' -type f | sort -V | tail -1)
JPA=$(find "$HOME/.m2/repository/jakarta/persistence/jakarta.persistence-api" -name '*.jar' -type f | sort -V | tail -1)
[[ -f "$LOMBOK" ]] || { echo "Lombok jar not found" >&2; exit 4; }
[[ -f "$JPA" ]] || { echo "Jakarta Persistence jar not found" >&2; exit 5; }

javac --release 17 \
  -cp "$ROOT/api-generator-core/target/classes" \
  -d "$BUILD/harness" \
  "$EXP/GeneratorAb.java"

java -cp "$BUILD/harness:$ROOT/api-generator-core/target/classes" \
  experiment.ab.GeneratorAb "$BUILD" | tee "$BUILD/metrics.log"

BASELINE_SOURCES=$(find "$BUILD/baseline-src" -name '*.java' -print)
STRONG_SOURCES=$(find "$BUILD/strong-src" -name '*.java' -print)
FIXTURE="$EXP/MapperFixture.java"
COMMON_CP="$LOMBOK:$JPA:$TB"

# A: current generator. The mapper accepts UUID directly and compiles without TypeBridge.
javac --release 17 \
  -cp "$COMMON_CP" \
  -processorpath "$LOMBOK" \
  -d "$BUILD/baseline-classes" \
  $BASELINE_SOURCES "$FIXTURE"
BASELINE_OUTPUT=$(java -cp "$BUILD/baseline-classes:$COMMON_CP" experiment.ab.MapperFixture)

# B negative control: same mapper + strong generated entity must fail with TypeBridge disabled.
set +e
javac --release 17 \
  -cp "$COMMON_CP" \
  -processorpath "$LOMBOK" \
  -d "$BUILD/strong-no-plugin" \
  $STRONG_SOURCES "$FIXTURE" \
  >"$BUILD/strong-no-plugin.log" 2>&1
NO_PLUGIN_STATUS=$?
set -e
if [[ "$NO_PLUGIN_STATUS" -eq 0 ]]; then
  echo "FAIL: strong-id mapper unexpectedly compiled without TypeBridge" >&2
  exit 6
fi
if ! grep -Eq 'CustomerId|incompatible types' "$BUILD/strong-no-plugin.log"; then
  cat "$BUILD/strong-no-plugin.log" >&2
  echo "FAIL: strong-id negative control failed for an unexpected reason" >&2
  exit 7
fi

echo "PASS: strong-id variant requires an explicit wrap when TypeBridge is disabled"

JVM_EXPORTS=(
  -J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
  -J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED
  -J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
  -J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
)

# B: same mapper, but TypeBridge elaborates UUID -> CustomerId after Lombok generated the builder.
javac --release 17 "${JVM_EXPORTS[@]}" \
  -cp "$COMMON_CP" \
  -processorpath "$LOMBOK:$TB" \
  -Xplugin:TypeBridgeLombokProbe \
  -d "$BUILD/strong-classes" \
  $STRONG_SOURCES "$FIXTURE"
STRONG_OUTPUT=$(java -cp "$BUILD/strong-classes:$COMMON_CP" experiment.ab.MapperFixture)

if [[ "$BASELINE_OUTPUT" != "$STRONG_OUTPUT" ]]; then
  echo "FAIL: runtime behavior changed: baseline=$BASELINE_OUTPUT strong=$STRONG_OUTPUT" >&2
  exit 8
fi

EXPECTED="123e4567-e89b-12d3-a456-426614174000"
if [[ "$STRONG_OUTPUT" != "$EXPECTED" ]]; then
  echo "FAIL: unexpected runtime value: $STRONG_OUTPUT" >&2
  exit 9
fi

echo "PASS: baseline and strong-id+TypeBridge mapper source are identical"
echo "PASS: baseline and strong-id+TypeBridge runtime output are identical ($STRONG_OUTPUT)"
echo "PASS: JPA strong-id AttributeConverters compile with the generated entities"
