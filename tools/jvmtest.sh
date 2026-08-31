#!/usr/bin/env bash
# Compila e roda os testes de `domain/` e `core/` em JVM pura, sem Gradle.
#
# ponytail: paliativo. Existe porque `Selector.open()` do JDK falha nesta
# máquina e o daemon do Gradle não sobe. Apagar assim que `./gradlew test`
# funcionar — o Gradle continua sendo a fonte de verdade, e o CI só roda ele.
#
# Não substitui o Gradle: não vê recursos Android, Room, Hilt nem Compose.
# Serve exatamente para o que a constituição manda testar em JVM (Art. 8):
# regra de negócio em Kotlin puro.
#
# Uso:  bash tools/jvmtest.sh
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$RAIZ"

STUDIO="/c/Program Files/Android/Android Studio"
KOTLINC="$STUDIO/plugins/Kotlin/kotlinc/lib"
JAVA="$STUDIO/jbr/bin/java"
CACHE="$HOME/.gradle/caches/modules-2"

achar() {  # achar <nome-do-jar-glob> — primeiro resultado, sem -sources
  find "$CACHE" -name "$1" -not -name "*sources*" 2>/dev/null | head -1
}

JUNIT="$(achar 'junit-4.13*.jar')"
HAMCREST="$(achar 'hamcrest-core-*.jar')"
for v in JAVA KOTLINC JUNIT HAMCREST; do
  [ -n "${!v}" ] && [ -e "${!v}" ] || { echo "faltando: $v (${!v:-vazio})" >&2; exit 1; }
done

w() { cygpath -w "$1" 2>/dev/null || echo "$1"; }
CP="$(w "$JUNIT");$(w "$HAMCREST");$(w "$KOTLINC/kotlin-stdlib.jar")"
OUT="$RAIZ/build/jvmtest"
rm -rf "$OUT"; mkdir -p "$OUT"

# Só o que é Kotlin puro. Nada que importe android.* compila aqui — e isso é
# uma checagem de graça do Art. 8: se um arquivo de domain/ passar a importar
# Android, este script quebra.
mapfile -t FONTES < <(find app/src/main/java/app/financepro/core \
                            app/src/main/java/app/financepro/domain \
                            -name '*.kt' 2>/dev/null | sort)
mapfile -t TESTES < <(find app/src/test/java/app/financepro/core \
                            app/src/test/java/app/financepro/domain \
                            -name '*.kt' 2>/dev/null | sort)

[ "${#TESTES[@]}" -gt 0 ] || { echo "nenhum teste encontrado"; exit 1; }
echo "compilando ${#FONTES[@]} fontes e ${#TESTES[@]} testes..."

MSYS_NO_PATHCONV=1 "$JAVA" -cp "$(w "$KOTLINC/kotlin-compiler.jar")" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -cp "$CP" -d "$(w "$OUT")" -jvm-target 17 -nowarn \
  "${FONTES[@]}" "${TESTES[@]}"

# Nome de classe a partir do caminho: .../java/<pacote>/Nome.kt
CLASSES=()
for t in "${TESTES[@]}"; do
  CLASSES+=("$(echo "${t#app/src/test/java/}" | sed 's|/|.|g; s|\.kt$||')")
done

echo
MSYS_NO_PATHCONV=1 "$JAVA" -cp "$(w "$OUT");$CP" \
  org.junit.runner.JUnitCore "${CLASSES[@]}"
