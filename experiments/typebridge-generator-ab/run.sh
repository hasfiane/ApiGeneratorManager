#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXP="$ROOT/experiments/typebridge-generator-ab"
BUILD="$EXP/build"
TB="$ROOT/experiments/typebridge-lombok/build/typebridge-lombok-probe.jar"
rm -rf "$BUILD"
mkdir -p "$BUILD/harness" "$BUILD/baseline-classes" "$BUILD/strong-no-plugin" "$BUILD/strong-classes" "$BUILD/cross-baseline" "$BUILD/cross-strong"

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
BASELINE_RUNNER="$EXP/BaselineRunner.java"
STRONG_RUNNER="$EXP/StrongRunner.java"
BASELINE_CROSS="$EXP/BaselineCrossIdAccepted.java"
STRONG_CROSS="$EXP/StrongCrossIdRejected.java"
COMMON_CP="$LOMBOK:$JPA:$TB"

JVM_EXPORTS=(
  -J--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED
  -J--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED
  -J--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED
  -J--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED
)

# A: current generator. The exact mapper accepts UUID directly without TypeBridge.
javac --release 17 \
  -cp "$COMMON_CP" \
  -processorpath "$LOMBOK" \
  -d "$BUILD/baseline-classes" \
  $BASELINE_SOURCES "$FIXTURE" "$BASELINE_RUNNER"
BASELINE_OUTPUT=$(java -cp "$BUILD/baseline-classes:$COMMON_CP" experiment.ab.BaselineRunner)

# Baseline safety control: an order UUID can be passed as a customer UUID because both are UUID.
javac --release 17 \
  -cp "$COMMON_CP" \
  -processorpath "$LOMBOK" \
  -d "$BUILD/cross-baseline" \
  $BASELINE_SOURCES "$BASELINE_CROSS"
echo "PASS: baseline accepts semantic order-id/customer-id confusion because both are UUID"

# B negative control: same mapper + strong generated entity must fail with TypeBridge disabled.
set +e
javac --release 17 \
  -cp "$COMMON_CP" \
  -processorpath "$LOMBOK" \
  -d "$BUILD/strong-no-plugin" \
  $STRONG_SOURCES "$FIXTURE" "$STRONG_RUNNER" \
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

# B: exact same mapper, TypeBridge elaborates UUID -> CustomerId after Lombok generated the builder.
javac --release 17 "${JVM_EXPORTS[@]}" \
  -cp "$COMMON_CP" \
  -processorpath "$LOMBOK:$TB" \
  -Xplugin:TypeBridgeLombokProbe \
  -d "$BUILD/strong-classes" \
  $STRONG_SOURCES "$FIXTURE" "$STRONG_RUNNER"
STRONG_OUTPUT=$(java -cp "$BUILD/strong-classes:$COMMON_CP" experiment.ab.StrongRunner)

# Strong-type safety control: TypeBridge must not turn OrdersId into CustomerId.
set +e
javac --release 17 "${JVM_EXPORTS[@]}" \
  -cp "$COMMON_CP" \
  -processorpath "$LOMBOK:$TB" \
  -Xplugin:TypeBridgeLombokProbe \
  -d "$BUILD/cross-strong" \
  $STRONG_SOURCES "$STRONG_CROSS" \
  >"$BUILD/cross-strong.log" 2>&1
CROSS_STATUS=$?
set -e
if [[ "$CROSS_STATUS" -eq 0 ]]; then
  echo "FAIL: OrdersId -> CustomerId confusion unexpectedly compiled with TypeBridge" >&2
  exit 8
fi
if ! grep -Eq 'OrdersId|CustomerId|incompatible types' "$BUILD/cross-strong.log"; then
  cat "$BUILD/cross-strong.log" >&2
  echo "FAIL: cross-id rejection failed for an unexpected reason" >&2
  exit 9
fi
echo "PASS: strong IDs reject OrdersId -> CustomerId confusion even with TypeBridge enabled"

if [[ "$BASELINE_OUTPUT" != "$STRONG_OUTPUT" ]]; then
  echo "FAIL: runtime behavior changed: baseline=$BASELINE_OUTPUT strong=$STRONG_OUTPUT" >&2
  exit 10
fi

EXPECTED="123e4567-e89b-12d3-a456-426614174000"
if [[ "$STRONG_OUTPUT" != "$EXPECTED" ]]; then
  echo "FAIL: unexpected runtime value: $STRONG_OUTPUT" >&2
  exit 11
fi

echo "PASS: baseline and strong-id+TypeBridge mapper source are identical"
echo "PASS: baseline and strong-id+TypeBridge runtime output are identical ($STRONG_OUTPUT)"
echo "PASS: strong-domain exit stays explicit in StrongRunner via .value()"
echo "PASS: JPA embedded strong IDs compile with the generated entities"

# Compile-cost microbenchmark on the actual generated source sets. This is deliberately
# reported as a tiny-project CI metric, not a full-build estimate.
BASE_TIMES="$BUILD/baseline-compile-ms.txt"
STRONG_TIMES="$BUILD/strong-compile-ms.txt"
: > "$BASE_TIMES"
: > "$STRONG_TIMES"
for i in 1 2 3 4 5; do
  OUT="$BUILD/timing-base-$i"
  mkdir -p "$OUT"
  START=$(date +%s%N)
  javac --release 17 -cp "$COMMON_CP" -processorpath "$LOMBOK" -d "$OUT" \
    $BASELINE_SOURCES "$FIXTURE" "$BASELINE_RUNNER" >/dev/null 2>&1
  END=$(date +%s%N)
  echo $(( (END - START) / 1000000 )) >> "$BASE_TIMES"
done
for i in 1 2 3 4 5; do
  OUT="$BUILD/timing-strong-$i"
  mkdir -p "$OUT"
  START=$(date +%s%N)
  javac --release 17 "${JVM_EXPORTS[@]}" -cp "$COMMON_CP" -processorpath "$LOMBOK:$TB" \
    -Xplugin:TypeBridgeLombokProbe -d "$OUT" \
    $STRONG_SOURCES "$FIXTURE" "$STRONG_RUNNER" >/dev/null 2>&1
  END=$(date +%s%N)
  echo $(( (END - START) / 1000000 )) >> "$STRONG_TIMES"
done

BASE_MEDIAN=$(sort -n "$BASE_TIMES" | sed -n '3p')
STRONG_MEDIAN=$(sort -n "$STRONG_TIMES" | sed -n '3p')
OVERHEAD=$(awk -v b="$BASE_MEDIAN" -v s="$STRONG_MEDIAN" 'BEGIN { if (b == 0) print "0.0"; else printf "%.1f", ((s-b)*100.0/b) }')
echo "METRIC baseline_compile_median_ms=$BASE_MEDIAN"
echo "METRIC strong_typebridge_compile_median_ms=$STRONG_MEDIAN"
echo "METRIC tiny_project_compile_overhead_percent=$OVERHEAD"
