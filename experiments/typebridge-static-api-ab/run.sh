#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXP="$ROOT/experiments/typebridge-static-api-ab"
BUILD="$EXP/build"
TB="$ROOT/experiments/typebridge-lombok/build/typebridge-lombok-probe.jar"
rm -rf "$BUILD"
mkdir -p "$BUILD/generator-classes"

[[ -d "$ROOT/api-generator-core/target/classes" ]] || { echo "api-generator-core must be built first" >&2; exit 2; }
[[ -f "$TB" ]] || { echo "TypeBridge probe jar missing; run typebridge-lombok probe first" >&2; exit 3; }

# The runtime-driven generated project resolves the same local runtime used by the real product.
mvn -q -pl api-generator-core,api-generator-runtime -am install -DskipTests

# Make the experimental compiler plugin resolvable by the generated static project.
mvn -q install:install-file \
  -Dfile="$TB" \
  -DgroupId=dev.typebridge \
  -DartifactId=typebridge-probe \
  -Dversion=0.0.0-experiment \
  -Dpackaging=jar \
  -DgeneratePom=true

javac --release 17 \
  -cp "$ROOT/api-generator-core/target/classes" \
  -d "$BUILD/generator-classes" \
  "$EXP/StaticApiGenerator.java"

java -cp "$BUILD/generator-classes:$ROOT/api-generator-core/target/classes" \
  experiment.staticab.StaticApiGenerator "$BUILD" | tee "$BUILD/generation-metrics.log"

RUNTIME="$BUILD/runtime-api"
STATIC="$BUILD/static-api"

now_ms() { date +%s%3N; }

RUNTIME_START=$(now_ms)
mvn -q -f "$RUNTIME/pom.xml" clean verify
RUNTIME_VERIFY_MS=$(( $(now_ms) - RUNTIME_START ))
echo "METRIC runtime_clean_verify_ms=$RUNTIME_VERIFY_MS"

EXPORTS="--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED"
STATIC_START=$(now_ms)
MAVEN_OPTS="${MAVEN_OPTS:-} $EXPORTS" mvn -q -f "$STATIC/pom.xml" clean verify
STATIC_VERIFY_MS=$(( $(now_ms) - STATIC_START ))
echo "METRIC static_clean_verify_ms=$STATIC_VERIFY_MS"

# The controller source deliberately uses raw UUID at HTTP boundaries while service signatures use strong IDs.
if ! grep -q 'service.create(request.id(), request.customerId(), request.total())' "$STATIC/src/main/java/bench/staticapi/web/OrderController.java"; then
  echo "FAIL: static controller lost raw-boundary ergonomic call" >&2
  exit 4
fi
if grep -Eq 'new (CustomerId|OrdersId)\(' "$STATIC/src/main/java/bench/staticapi/web/"*.java; then
  echo "FAIL: explicit ID wrappers leaked into generated controller call sites" >&2
  exit 5
fi
if ! grep -q 'getId().value()' "$STATIC/src/main/java/bench/staticapi/web/OrderController.java"; then
  echo "FAIL: strong-domain exit is no longer explicit" >&2
  exit 6
fi

echo "PASS: HTTP boundary stays UUID while domain service signatures are strongly typed"
echo "PASS: TypeBridge removes raw -> strong wrappers from controller call sites"
echo "PASS: strong -> raw exits remain explicit via .value()"
echo "PASS: static API clean verify exercised Spring Boot + JPA + H2 + generated AttributeConverters"

# Security/type-safety control: TypeBridge must never bridge one semantic ID type to another.
cat > "$BUILD/WrongId.java" <<'JAVA'
import bench.staticapi.types.CustomerId;
import bench.staticapi.types.OrdersId;
final class WrongId {
    static void customer(CustomerId id) {}
    static void reject(OrdersId orderId) { customer(orderId); }
}
JAVA
set +e
javac --release 17 \
  -cp "$STATIC/target/classes:$TB" \
  -d "$BUILD/wrong-id-classes" \
  "$BUILD/WrongId.java" >"$BUILD/wrong-id.log" 2>&1
WRONG_STATUS=$?
set -e
if [[ "$WRONG_STATUS" -eq 0 ]]; then
  echo "FAIL: OrdersId -> CustomerId semantic confusion compiled" >&2
  exit 7
fi
grep -Eq 'OrdersId|CustomerId|incompatible types' "$BUILD/wrong-id.log" || { cat "$BUILD/wrong-id.log" >&2; exit 8; }
echo "PASS: OrdersId -> CustomerId confusion is rejected by javac"

median_ms() {
  printf '%s\n' "$@" | sort -n | sed -n '2p'
}

runtime_times=()
static_times=()
for i in 1 2 3; do
  s=$(now_ms); mvn -q -f "$RUNTIME/pom.xml" -DskipTests clean compile; runtime_times+=("$(( $(now_ms) - s ))")
  s=$(now_ms); MAVEN_OPTS="${MAVEN_OPTS:-} $EXPORTS" mvn -q -f "$STATIC/pom.xml" -DskipTests clean compile; static_times+=("$(( $(now_ms) - s ))")
done
RUNTIME_MEDIAN=$(median_ms "${runtime_times[@]}")
STATIC_MEDIAN=$(median_ms "${static_times[@]}")
OVERHEAD=$(awk -v a="$RUNTIME_MEDIAN" -v b="$STATIC_MEDIAN" 'BEGIN { if (a == 0) print "0.0"; else printf "%.1f", ((b-a)/a)*100 }')
echo "METRIC runtime_compile_median_ms=$RUNTIME_MEDIAN"
echo "METRIC static_typebridge_compile_median_ms=$STATIC_MEDIAN"
echo "METRIC static_compile_overhead_percent=$OVERHEAD"
