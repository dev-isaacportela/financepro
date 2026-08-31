#!/usr/bin/env python3
"""Verificador de rastreabilidade (constitution.md Art. 4).

Confere que:
  1. Todo REQ MUST tem ao menos uma task que o cita.
  2. Toda REQ citada em tasks.md existe em spec.md.
  3. Toda REQ MUST com classe de teste nomeada tem @Req("REQ-...") no codigo.
  4. Todo @Req no codigo aponta para uma REQ que existe.
  5. Dependencias entre tasks existem e nao formam ciclo.
  6. Links e ancoras entre os documentos resolvem.
  7. Guardas da constituicao: Art. 6 (dinheiro sem ponto flutuante)
     e Art. 12 (sem migracao destrutiva).

Uso:
    python tools/trace.py                # so o que ja foi implementado
    python tools/trace.py --phase F0     # exige testes ate a F0, inclusive
    python tools/trace.py --report       # inventario, sempre sai 0

Sem dependencias. Roda em qualquer Python 3.8+.
"""

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs" / "spec.md"
TASKS = ROOT / "docs" / "tasks.md"
SRC = ROOT / "app" / "src"

RE_REQ_HEAD = re.compile(r"^### (REQ-[A-Z0-9]+-\d{3})\s+—\s+(.+?)\s*$")
RE_REQ_META = re.compile(r"`(F\d)`\s*·\s*`(MUST|SHOULD)`\s*·\s*Teste:\s*`([^`]+)`")
RE_TASK_HEAD = re.compile(r"^### (T-\d{3})\s+—\s+(.+?)\s*$")
RE_TASK_META = re.compile(r"\*\*Fase\*\*\s*(F\d)\b")
RE_TASK_DEPS = re.compile(r"\*\*Depende de\*\*\s*(.+?)\s*·")
RE_TASK_REQS = re.compile(r"\*\*REQ\*\*\s*(.*)$")
RE_ID = re.compile(r"REQ-[A-Z0-9]+-\d{3}")
RE_TID = re.compile(r"T-\d{3}")
RE_ANNOT = re.compile(r'@Req\(\s*"(REQ-[A-Z0-9]+-\d{3})"')
RE_MDLINK = re.compile(r"\[[^\]]*\]\(([^)]+)\)")
RE_HEADING = re.compile(r"^#{1,6}\s+(.*?)\s*$")
RE_FLOAT = re.compile(r"\b(?:Double|Float|BigDecimal)\b|\.to(?:Double|Float)\s*\(")

# Caminhos por onde dinheiro trafega (Art. 6). Relativos ao pacote raiz.
MONEY_PATHS = ("/core/money/", "/domain/", "/data/import/")

PHASES = ["F0", "F1", "F2", "F3", "F4"]


def read(path):
    """Linhas do arquivo, fora de blocos de codigo cercados.

    Os exemplos de formato em spec.md e tasks.md moram em ``` e nao sao dados.
    """
    if not path.exists():
        sys.exit(f"ERRO: arquivo nao encontrado: {path}")
    lines, fenced = [], False
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.lstrip().startswith("```"):
            fenced = not fenced
            continue
        if not fenced:
            lines.append(line)
    return lines


def parse_spec():
    """-> {req_id: {'title','phase','priority','test'}}"""
    reqs, current = {}, None
    for line in read(SPEC):
        head = RE_REQ_HEAD.match(line)
        if head:
            current = head.group(1)
            reqs[current] = {"title": head.group(2), "phase": None,
                             "priority": None, "test": None}
            continue
        if current and reqs[current]["phase"] is None:
            meta = RE_REQ_META.search(line)
            if meta:
                reqs[current].update(phase=meta.group(1),
                                     priority=meta.group(2),
                                     test=meta.group(3))
    return reqs


def parse_tasks():
    """-> {task_id: {'title','phase','deps',[reqs]}}"""
    tasks, current = {}, None
    for line in read(TASKS):
        head = RE_TASK_HEAD.match(line)
        if head:
            current = head.group(1)
            tasks[current] = {"title": head.group(2), "phase": None,
                              "deps": [], "reqs": []}
            continue
        if not current:
            continue
        t = tasks[current]
        if "**Fase**" in line and t["phase"] is None:
            phase = RE_TASK_META.search(line)
            if phase:
                t["phase"] = phase.group(1)
            deps = RE_TASK_DEPS.search(line)
            if deps:
                t["deps"] = RE_TID.findall(deps.group(1))
            reqs = RE_TASK_REQS.search(line)
            if reqs:
                t["reqs"] = RE_ID.findall(reqs.group(1))
    return tasks


def strip_comments(text):
    """Remove comentarios de bloco e de linha do fonte Kotlin.

    Sem isto, o exemplo de uso dentro do KDoc de Req.kt e contado como
    anotacao real -- o que fez a ferramenta relatar cobertura que nao existia.
    """
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def scan_annotations():
    """-> {req_id: [arquivos]}. Vazio enquanto nao houver testes anotados."""
    found = {}
    if not SRC.exists():
        return found
    for path in SRC.rglob("*.kt"):
        try:
            text = strip_comments(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError):
            continue
        for req in RE_ANNOT.findall(text):
            found.setdefault(req, []).append(str(path.relative_to(ROOT)))
    return found


def slug(text):
    """Ancora no estilo do GitHub: minusculas, pontuacao fora, espaco -> hifen.

    Cada espaco vira um hifen (nao colapsa), por isso "003 — Titulo" produz
    "003--titulo": o travessao sai e os dois espacos viram dois hifens.
    """
    text = re.sub(r"[^\w\s-]", "", text.strip().lower())
    return re.sub(r"\s", "-", text)


def check_links(errors):
    """Links relativos e ancoras entre os documentos."""
    docs = [(ROOT / "README.md").resolve()]
    docs += sorted(p.resolve() for p in (ROOT / "docs").glob("*.md"))
    docs = [p for p in docs if p.exists()]

    headings = {}
    for path in docs:
        found = set()
        for line in read(path):
            head = RE_HEADING.match(line)
            if head:
                found.add(slug(head.group(1)))
        headings[path] = found

    for path in docs:
        rel = path.relative_to(ROOT)
        for line in read(path):
            for target in RE_MDLINK.findall(line):
                if target.startswith(("http://", "https://", "mailto:")):
                    continue
                file_part, _, anchor = target.partition("#")
                dest = (path.parent / file_part).resolve() if file_part else path
                if not dest.exists():
                    errors.append(f"{rel}: link quebrado -> {target}")
                elif anchor and dest in headings and slug(anchor) not in headings[dest]:
                    errors.append(f"{rel}: ancora inexistente -> {target}")


def check_constitution(errors):
    """Guardas dos Arts. 6 e 12, sobre o fonte sem comentarios.

    Ficam aqui, e nao como grep no workflow, porque o KDoc de Money.kt explica
    por que Double e proibido -- e um grep cru reprovava justamente o arquivo
    que implementa a regra. Punir quem documenta a regra e uma guarda ruim.
    """
    if not SRC.exists():
        return

    for path in SRC.rglob("*.kt"):
        rel = path.relative_to(ROOT).as_posix()
        try:
            code = strip_comments(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError):
            continue

        # Art. 12 — vale para todo o codigo, inclusive testes.
        if "fallbackToDestructiveMigration" in code:
            errors.append(
                f"{rel}: fallbackToDestructiveMigration apaga dado financeiro "
                f"do usuario (Art. 12, REQ-DATA-001)")

        # Art. 6 — so em caminho de dinheiro, e so em src/main. Fora dai
        # toFloat e legitimo (alpha, progresso de animacao), e os testes usam
        # Double de proposito, para mostrar o erro que ele produz.
        if "/src/main/" not in rel:
            continue
        if not any(seg in rel for seg in MONEY_PATHS):
            continue
        for m in RE_FLOAT.finditer(code):
            line = code[:m.start()].count("\n") + 1
            errors.append(
                f"{rel}:{line}: '{m.group(0)}' em caminho de dinheiro "
                f"(Art. 6, ADR-002)")


def find_cycle(tasks):
    """Menor ciclo de dependencia, ou None."""
    WHITE, GRAY, BLACK = 0, 1, 2
    color = {t: WHITE for t in tasks}

    def visit(node, stack):
        color[node] = GRAY
        for dep in tasks[node]["deps"]:
            if dep not in tasks:
                continue
            if color[dep] == GRAY:
                return stack[stack.index(dep):] + [dep]
            if color[dep] == WHITE:
                cycle = visit(dep, stack + [dep])
                if cycle:
                    return cycle
        color[node] = BLACK
        return None

    for task in tasks:
        if color[task] == WHITE:
            cycle = visit(task, [task])
            if cycle:
                return cycle
    return None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--phase", choices=PHASES,
                    help="exige cobertura de teste ate esta fase, inclusive")
    ap.add_argument("--report", action="store_true",
                    help="imprime inventario e sai com 0")
    args = ap.parse_args()

    reqs, tasks, annots = parse_spec(), parse_tasks(), scan_annotations()
    errors, warnings = [], []

    # Requisitos sem metadados: erro de formatacao da propria spec.
    for rid, r in sorted(reqs.items()):
        if r["phase"] is None:
            errors.append(f"{rid}: sem linha de metadados `Fn` · `MUST` · Teste: `X`")

    covered = {rid for t in tasks.values() for rid in t["reqs"]}

    # 1. MUST sem task.
    for rid, r in sorted(reqs.items()):
        if r["priority"] == "MUST" and rid not in covered:
            errors.append(f"{rid} ({r['phase']} MUST): nenhuma task o cita")
        elif r["priority"] == "SHOULD" and rid not in covered:
            warnings.append(f"{rid} ({r['phase']} SHOULD): nenhuma task o cita")

    # 2. Task citando requisito inexistente.
    for tid, t in sorted(tasks.items()):
        for rid in t["reqs"]:
            if rid not in reqs:
                errors.append(f"{tid}: cita {rid}, que nao existe em spec.md")

    # 3. Teste nomeado sem @Req no codigo.
    #    So e cobrado com --phase, explicitamente. A versao anterior inferia
    #    "ja tem codigo?" pela presenca de qualquer anotacao, e passava a cobrar
    #    o projeto inteiro assim que a primeira aparecia -- quebrando o gate por
    #    conta propria. Qual fase esta pronta e decisao humana, nao heuristica.
    limit = PHASES.index(args.phase) if args.phase else -1
    for rid, r in sorted(reqs.items()):
        if r["priority"] != "MUST" or r["test"] in (None, "manual"):
            continue
        if limit >= 0 and PHASES.index(r["phase"]) <= limit and rid not in annots:
            errors.append(
                f"{rid} ({r['phase']} MUST): sem @Req no codigo "
                f"(esperado em {r['test']})")

    # 4. Anotacao orfa.
    for rid, files in sorted(annots.items()):
        if rid not in reqs:
            errors.append(f"@Req(\"{rid}\") em {files[0]}: nao existe em spec.md")

    # 5. Dependencias de tasks.
    for tid, t in sorted(tasks.items()):
        for dep in t["deps"]:
            if dep not in tasks:
                errors.append(f"{tid}: depende de {dep}, que nao existe")
    check_links(errors)
    check_constitution(errors)

    cycle = find_cycle(tasks)
    if cycle:
        errors.append("ciclo de dependencia entre tasks: " + " -> ".join(cycle))

    # Relatorio.
    print(f"spec.md   {len(reqs)} requisitos")
    print(f"tasks.md  {len(tasks)} tasks, cobrindo {len(covered & set(reqs))}")
    print(f"codigo    {len(annots)} requisitos com @Req")
    print()
    for phase in PHASES:
        in_phase = [r for r in reqs.values() if r["phase"] == phase]
        if not in_phase:
            continue
        must = sum(1 for r in in_phase if r["priority"] == "MUST")
        auto = sum(1 for r in in_phase if r["test"] not in (None, "manual"))
        print(f"  {phase}  {len(in_phase):>3} requisitos "
              f"({must} MUST, {auto} com teste automatizado)")

    if args.report:
        return 0

    if warnings:
        print(f"\n{len(warnings)} aviso(s):")
        for w in warnings:
            print(f"  ! {w}")

    if errors:
        print(f"\n{len(errors)} erro(s):")
        for e in errors:
            print(f"  x {e}")
        print("\nFALHOU")
        return 1

    print("\nOK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
