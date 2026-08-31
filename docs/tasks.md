# Backlog executável

Decomposição de [spec.md](spec.md) em tarefas ordenadas. Cada task declara suas
dependências, os requisitos que satisfaz, e uma definição de pronto verificável.

**Regras de uso**

- Task só começa quando **todas** as suas dependências estão fechadas.
- Task com `[NEEDS-CLARIFICATION]` em algum requisito seu fica **bloqueada**
  (Art. 5), não "resolvida por suposição".
- Commit cita os `REQ-*` da task (Art. 2).
- `python tools/trace.py` precisa passar antes do merge.
- `⇉` marca tasks sem dependência entre si — podem ir em paralelo.

**Formato**

```
### T-000 — Título
**Fase** F0 · **Depende de** T-000 · **REQ** REQ-XXX-000, REQ-YYY-000
```

---

# F0 — Fundação e núcleo

Objetivo da fase: registrar e consultar transações com saldo correto. Sem cartão,
sem orçamento, sem importação. Ao fim da F0 o app já substitui a planilha.

### T-001 — Bootstrap do projeto
**Fase** F0 · **Depende de** — · **REQ** —

Projeto Gradle, `libs.versions.toml`, `minSdk 26`, JVM 17, Compose, Hilt, Room,
DataStore, detekt.

**Pronto quando**
- [ ] Repositório git inicializado, com `.gitignore` de Android — sem ele o Art. 2
      (commit cita `REQ-*`) não tem onde ser aplicado, e nada acima está versionado
- [ ] `./gradlew assembleDebug` e `./gradlew test` passam num app vazio
- [ ] Version catalog é a única fonte de versões — nenhuma versão literal em `build.gradle.kts`
- [ ] `room.schemaLocation` aponta para `app/schemas/`

### T-002 — Núcleo de dinheiro ⇉
**Fase** F0 · **Depende de** T-001 · **REQ** REQ-CORE-001, REQ-CORE-004, REQ-CORE-005, REQ-IMP-004

`core/money/Money.kt`: parse de texto para centavos e formatação pt-BR.

Já entrega `REQ-IMP-004` porque o parser de importação (F2) usa exatamente esta
função — escrever duas conversões texto→centavos seria criar duas fontes de
verdade para a regra mais sensível do app.

**Pronto quando**
- [ ] `parseCents(String): Long?` cobre as 6 linhas da tabela de REQ-IMP-004
- [ ] Nenhuma ocorrência de `toDouble`/`toFloat` no arquivo — verificado por detekt
- [ ] `formatBRL(Long)` produz `R$ 1.234,56` e `−R$ 18,50`
- [ ] `MoneyTest`, `MoneyFormatTest`, `MoneyParseTest` com `@Req`

### T-003 — Núcleo de datas ⇉
**Fase** F0 · **Depende de** T-001 · **REQ** REQ-CORE-003

`core/time/MonthRange.kt`: período mensal a partir de `monthStartDay`, com clamp.

**Pronto quando**
- [ ] `MonthRangeTest` cobre as 3 linhas da tabela de REQ-CORE-003, incluindo `monthStartDay = 31`
- [ ] Nenhum uso de `Calendar` ou `Date` — só `java.time`

### T-004 — Schema e DAOs
**Fase** F0 · **Depende de** T-001 · **REQ** REQ-ACC-001, REQ-ACC-002, REQ-CAT-001, REQ-CAT-002, REQ-TXN-001, REQ-DATA-001, REQ-DATA-002, REQ-DATA-003

Entidades, DAOs, índices e converters conforme [arquitetura.md](arquitetura.md) §4.

**Pronto quando**
- [ ] `PRAGMA foreign_keys = ON` no `onOpen`, com teste que prova que `RESTRICT` dispara
- [ ] Todos os índices da §4.1 criados
- [ ] Schema v1 exportado e commitado em `app/schemas/`
- [ ] `fallbackToDestructiveMigration` não existe no código — verificado por detekt

### T-005 — Criptografia do banco
**Fase** F0 · **Depende de** T-004 · **REQ** REQ-SEC-001, REQ-SEC-002

SQLCipher com chave de 32 bytes gerada por `SecureRandom` no primeiro boot,
guardada no Android Keystore.

**Pronto quando**
- [ ] Arquivo `.db` extraído do aparelho não é legível por `sqlite3`
- [ ] Chave não aparece em `SharedPreferences`, DataStore, código ou log
- [ ] Reinstalação com dados preservados ainda abre o banco

### T-006 — Sementes ⇉
**Fase** F0 · **Depende de** T-004 · **REQ** REQ-CAT-004, REQ-ACT-003

Categorias padrão e ~40 regras de estabelecimento.

**Pronto quando**
- [ ] `SeedTest` verifica as 10 categorias e o volume mínimo de `payee_rule`
- [ ] Seed roda uma única vez, na criação do banco

### T-007 — Validação de transação
**Fase** F0 · **Depende de** T-002, T-004 · **REQ** REQ-CORE-002, REQ-ACC-006, REQ-CAT-003, REQ-TXN-004, REQ-TXN-005, REQ-TXN-013

`domain/usecase/ValidateTxn.kt`. Kotlin puro, sem Android.

**Pronto quando**
- [ ] Cada regra da §5 da spec tem um caso de teste com a mensagem exata
- [ ] Retorna lista de erros, não lança exceção — a UI mostra todos de uma vez
- [ ] `ValidateTxnTest` com `@Req` para os 6 requisitos

### T-008 — Cálculo de saldo
**Fase** F0 · **Depende de** T-004 · **REQ** REQ-ACC-003, REQ-ACC-004, REQ-ACC-007, REQ-TXN-002, REQ-TXN-003, REQ-TXN-006, REQ-CARD-009

`AccountBalance`, com a fórmula de dois termos de [ADR-003](decisoes.md#adr-003--transferência-é-uma-linha-não-duas).

**Pronto quando**
- [ ] `AccountBalanceTest` cobre as 4 linhas da tabela de REQ-ACC-004
- [ ] Teste explícito do invariante: transferência não altera a soma dos saldos (Art. 7)
- [ ] Saldo total do dashboard exclui `CREDIT_CARD`
- [ ] `TransferTest` e `TxnSignTest` com `@Req`

### T-009 — Repositórios e DI
**Fase** F0 · **Depende de** T-005, T-007, T-008 · **REQ** —

Repositórios expondo `Flow`, módulos Hilt, `Application`.

**Pronto quando**
- [ ] Nenhum repositório tem interface (Art. 10)
- [ ] Nenhum arquivo em `domain/` importa `android.*` ou `androidx.*` — verificado por detekt

### T-010 — Sistema visual Slush ⇉
**Fase** F0 · **Depende de** T-001 · **REQ** REQ-UI-007, REQ-A11Y-002, REQ-A11Y-005, REQ-DS-001, REQ-DS-002, REQ-DS-003, REQ-DS-004, REQ-DS-005, REQ-DS-006, REQ-DS-007, REQ-DS-008, REQ-DS-009, REQ-DS-010

Tokens, tipografia, formas e componentes base conforme [design.md](design.md).

**Pronto quando**
- [ ] Antonio e Inter empacotadas em `res/font/` — nenhuma Downloadable Font
- [ ] `SlushColors` com `paper`/`ink`; contorno inverte junto com o tema, sem condicional nas telas
- [ ] `ContrastTest` recalcula da paleta e falha se um sticker virar cor de texto
- [ ] `TypographyTest` exige `lineHeight ≤ 0.85.em` e `includeFontPadding = false` nos três estilos display
- [ ] detekt proíbe `Color(0x` fora de `core/ui/theme/`, elevação ≠ 0 e `Brush.*Gradient`
- [ ] `Card`, `Button`, `FAB` e `Surface` com elevação e `tonalElevation` zerados explicitamente
- [ ] Componentes base nascem com alvo ≥ 48dp via `minimumInteractiveComponentSize()`
- [ ] `MoneyText` centraliza formatação, sinal e `tnum` — nenhuma tela formata dinheiro à mão
- [ ] Duas renderizações WebP da fita 3D, ≤ 120KB cada, em `nodpi`

### T-011 — Navegação
**Fase** F0 · **Depende de** T-010 · **REQ** REQ-UI-001

Bottom bar de 4 destinos, rotas type-safe.

### T-012 — Onboarding
**Fase** F0 · **Depende de** T-009, T-011 · **REQ** REQ-UI-005

**Pronto quando**
- [ ] Uma única tela; app utilizável em menos de 30 segundos após instalar
- [ ] Cria `CASH` e `CHECKING` com o saldo informado em `initialBalanceCents`

### T-013 — Lançamento rápido
**Fase** F0 · **Depende de** T-009, T-011 · **REQ** REQ-UI-002, REQ-UI-003, REQ-CAT-006

O fluxo mais importante do app (Art. 18).

**Pronto quando**
- [ ] Despesa registrada em 3 toques a partir do dashboard, cronometrada
- [ ] Bottom sheet, nunca tela cheia; teclado numérico em foco na abertura
- [ ] Campos condicionais de REQ-UI-003 aparecem e somem corretamente
- [ ] Grid ordenado por `useCount`, incrementado ao salvar

### T-014 — Lista de transações
**Fase** F0 · **Depende de** T-013 · **REQ** REQ-TXN-010, REQ-TXN-011, REQ-TXN-012

**Pronto quando**
- [ ] Agrupada por dia com total do dia no cabeçalho
- [ ] Swipe-excluir com desfazer de 5s, sem diálogo
- [ ] Filtros de mês, conta, categoria e tipo, e busca por texto ou valor
- [ ] Comentário `ponytail:` documentando a ausência de Paging ([ADR-009](decisoes.md#adr-009--sem-paging-3-no-mvp))

### T-015 — Contas ⇉
**Fase** F0 · **Depende de** T-009, T-011 · **REQ** REQ-ACC-005

CRUD, lista com saldo, extrato com saldo corrente acumulado linha a linha.

### T-016 — Categorias ⇉
**Fase** F0 · **Depende de** T-009, T-011 · **REQ** REQ-CAT-005

**Pronto quando**
- [ ] Exclusão de categoria com transações é bloqueada pelo banco, e a UI oferece recategorização em lote

### T-017 — Dashboard
**Fase** F0 · **Depende de** T-014, T-015 · **REQ** REQ-UI-004, REQ-UI-006

Blocos de cartão e orçamento entram vazios na F0 e são preenchidos na F1.

### T-018 — Segurança do app ⇉
**Fase** F0 · **Depende de** T-011 · **REQ** REQ-SEC-003, REQ-SEC-004, REQ-SEC-005, REQ-SEC-006

Biometria opcional, `allowBackup="false"`, `FLAG_SECURE`, expurgo de logs.

**Pronto quando**
- [ ] Regra de detekt proíbe `Log.*` com interpolação de valor monetário
- [ ] App aparece em branco na lista de recentes com bloqueio ativo

### T-019 — Guarda de manifesto ⇉
**Fase** F0 · **Depende de** T-001 · **REQ** REQ-SEC-007

`ManifestTest` lê o manifesto mesclado e falha se `INTERNET` estiver presente.

Manifesto mesclado, não o do módulo: a permissão costuma entrar por dependência
transitiva, sem ninguém declarar.

### T-020 — Acessibilidade das telas F0
**Fase** F0 · **Depende de** T-017 · **REQ** REQ-A11Y-001, REQ-A11Y-003, REQ-A11Y-004, REQ-A11Y-006

Passagem por todas as telas da F0 (Art. 17 — é auditoria da fase, não adiamento).

**Pronto quando**
- [ ] TalkBack lê valores por extenso
- [ ] Fonte a 200% sem truncar nem sobrepor
- [ ] Nenhuma informação transmitida só por cor

### T-021 — Rastreabilidade no CI ⇉
**Fase** F0 · **Depende de** T-001 · **REQ** —

`tools/trace.py` no pipeline, e anotação `@Req` disponível para os testes.

**Pronto quando**
- [ ] CI falha se um `MUST` da fase entregue estiver sem task ou sem teste
- [ ] Roda em segundos, sem dependência externa (Art. 4)

---

# F1 — Cartão, orçamento, recorrência

### T-022 — Configuração de cartão
**Fase** F1 · **Depende de** T-015 · **REQ** REQ-CARD-001, REQ-CARD-002

**Pronto quando**
- [ ] `ValidateAccountTest` recusa dia 0, 29, 31 e negativo
- [ ] Campos de cartão só aparecem para `CREDIT_CARD`

### T-023 — Competência e vencimento
**Fase** F1 · **Depende de** T-003 · **REQ** REQ-CARD-003, REQ-CARD-004

`invoiceMonthFor` e `dueDateFor`, Kotlin puro.

**Pronto quando**
- [ ] `InvoiceMonthTest` cobre as 6 linhas de REQ-CARD-003 e as 4 de REQ-CARD-004
- [ ] Virada de ano testada explicitamente (dez → jan)

### T-024 — Fatura
**Fase** F1 · **Depende de** T-022, T-023 · **REQ** REQ-CARD-005, REQ-CARD-007, REQ-CARD-008

Composição, status derivado e limite disponível.

**Pronto quando**
- [ ] Fatura exclui `TRANSFER` — teste prova que pagamento não entra na fatura
- [ ] `InvoiceStatusTest` cobre as 3 condições de status
- [ ] Nada de status persistido em coluna

### T-025 — Tela de cartão
**Fase** F1 · **Depende de** T-024 · **REQ** REQ-CARD-006

Seletor de mês, itens por categoria, botão pagar fatura.

**Pronto quando**
- [ ] Pagar fatura gera uma `TRANSFER` de `paymentAccountId` para o cartão
- [ ] Valor editável (pagamento parcial)
- [ ] `InvoicePaymentTest`: após pagamento integral, a dívida da fatura zera sem código especial de cartão no cálculo de saldo

### T-026 — Divisão de parcelas
**Fase** F1 · **Depende de** T-002 · **REQ** REQ-TXN-007, REQ-TXN-008

**Pronto quando**
- [ ] `SplitInstallmentsTest` verifica `soma == total` para **todo** `n` de 1 a 72, sobre um conjunto de totais que inclui 1, 10, 7, 100000
- [ ] Sobra alocada na última parcela, nunca na primeira
- [ ] Cobre as 4 linhas da tabela de REQ-TXN-008

### T-027 — UI de parcelamento
**Fase** F1 · **Depende de** T-026, T-013 · **REQ** REQ-TXN-009

**Pronto quando**
- [ ] Campo de parcelas só para `CREDIT_CARD`
- [ ] Editar/excluir parcela pergunta escopo, e `InstallmentScopeTest` prova que só o escopo escolhido muda

### T-028 — Orçamento: dados e cálculo ⇉
**Fase** F1 · **Depende de** T-003, T-004 · **REQ** REQ-BUD-001, REQ-BUD-002, REQ-BUD-004

**Pronto quando**
- [ ] `BudgetProgressTest` prova que despesa em subcategoria conta no teto da mãe
- [ ] Transferência nunca entra no consumo
- [ ] Período respeita `monthStartDay`

### T-029 — Tela de orçamento
**Fase** F1 · **Depende de** T-028 · **REQ** REQ-BUD-003, REQ-BUD-005

**Pronto quando**
- [ ] Âmbar em 80%, vermelho em 100%, **com ícone** além da cor
- [ ] Copiar tetos do mês anterior em uma ação

### T-030 — Expansão de recorrência ⇉
**Fase** F1 · **Depende de** T-003 · **REQ** REQ-REC-001, REQ-REC-002, REQ-REC-006

Cálculo puro das datas de ocorrência.

**Pronto quando**
- [ ] `RecurrenceExpansionTest` cobre as 4 linhas de clamp de REQ-REC-006, incluindo ano bissexto
- [ ] `interval` testado para as 4 frequências

### T-031 — Geração de ocorrências
**Fase** F1 · **Depende de** T-030 · **REQ** REQ-REC-003, REQ-REC-004, REQ-REC-005, REQ-REC-007

Worker diário + gatilho na abertura do app.

**Pronto quando**
- [ ] `GenerateRecurringTest` roda a geração 3× no mesmo dia e prova conjunto idêntico ao da 1ª
- [ ] Nada gerado além de hoje + 60 dias
- [ ] Alterar a regra não toca em ocorrência `cleared = 1`

### T-032 — Tela de recorrências
**Fase** F1 · **Depende de** T-031 · **REQ** REQ-REC-008

Lista de regras, próxima ocorrência, e bloco "Próximas contas" no dashboard com
ação de efetivar.

### T-033 — Relatórios ⇉
**Fase** F1 · **Depende de** T-017 · **REQ** REQ-RPT-001, REQ-RPT-002, REQ-RPT-003, REQ-RPT-004

**Pronto quando**
- [ ] Transferências excluídas de todos os três relatórios
- [ ] Toque na fatia navega para a lista já filtrada

### T-034 — Exportação ⇉
**Fase** F1 · **Depende de** T-009 · **REQ** REQ-BAK-001

CSV de transações e JSON da base, via `ACTION_CREATE_DOCUMENT`.

**Pronto quando**
- [ ] Round-trip: exportar JSON e reimportar produz base idêntica
- [ ] CSV abre no Excel em pt-BR sem quebrar acento nem decimal

### T-035 — Backup e restauração
**Fase** F1 · **Depende de** T-034 · **REQ** REQ-BAK-002, REQ-BAK-003, REQ-BAK-004

**Pronto quando**
- [ ] AES-256 com chave PBKDF2; aviso de que senha perdida é backup perdido
- [ ] Restaurar informa quantos registros serão substituídos antes de confirmar
- [ ] Apagar tudo exige confirmação por digitação e sugere exportar antes

---

# F2 — Importação de arquivo

Design em [ingestao.md](ingestao.md).

### T-036 — Normalização e chave de dedupe ⇉
**Fase** F2 · **Depende de** T-002 · **REQ** REQ-IMP-008, REQ-ACT-004

Uma única função `normalize`, usada por dedupe e por auto-categorização.

**Pronto quando**
- [ ] `NormalizeTest` cobre as 4 linhas da tabela de REQ-IMP-008
- [ ] Uma só implementação no código — duas fariam dedupe e aprendizado discordarem

### T-037 — Parser OFX ⇉
**Fase** F2 · **Depende de** T-002 · **REQ** REQ-IMP-002, REQ-IMP-003

**Pronto quando**
- [ ] `OfxParserTest` com arquivos reais de ao menos 3 bancos, como fixtures
- [ ] OFX 1.x SGML e 2.x XML pelo mesmo caminho de código
- [ ] Fixture com `CHARSET:1252` e acento produz "ALIMENTAÇÃO" correto
- [ ] Arquivo multi-conta importa só a conta selecionada
- [ ] `FITID` duplicado dentro do próprio arquivo não quebra a importação

### T-038 — Parser CSV ⇉
**Fase** F2 · **Depende de** T-002 · **REQ** REQ-IMP-006

**Pronto quando**
- [ ] `CsvSniffTest` acerta separador, formato de data e decimal em amostras de 3 bancos
- [ ] Sem biblioteca de CSV adicionada (Art. 10 / ladder)

### T-039 — Motor de deduplicação
**Fase** F2 · **Depende de** T-036, T-037 · **REQ** REQ-IMP-007, REQ-IMP-009, REQ-IMP-012

Três níveis: `FITID`, hash exato, janela difusa ±3 dias.

**Pronto quando**
- [ ] `DedupeTest`: reimportar o mesmo OFX duas vezes não cria nenhuma transação nova
- [ ] Nível 3 **nunca** descarta sozinho — marca e devolve o par para decisão
- [ ] Índice único parcial rejeita duplicata mesmo com a checagem em código desativada no teste

### T-040 — Auto-categorização
**Fase** F2 · **Depende de** T-036 · **REQ** REQ-ACT-001, REQ-ACT-002

**Pronto quando**
- [ ] `PayeeRuleTest`: salvar 3 transações do mesmo estabelecimento faz a 4ª importação já vir categorizada
- [ ] Categoria sugerida é sempre editável na revisão

### T-041 — Fluxo de importação
**Fase** F2 · **Depende de** T-038, T-039, T-040 · **REQ** REQ-IMP-001, REQ-IMP-005, REQ-IMP-010

Picker SAF, mapeamento de colunas, tela de revisão.

**Pronto quando**
- [ ] Nenhuma gravação sem confirmação explícita (Art. 14)
- [ ] Possíveis duplicatas exibidas lado a lado
- [ ] Mapeamento de CSV salvo e reaproveitado
- [ ] `READ_EXTERNAL_STORAGE` não existe no manifesto

### T-042 — Lotes e desfazer
**Fase** F2 · **Depende de** T-041 · **REQ** REQ-IMP-011

**Pronto quando**
- [ ] `ImportBatchTest`: desfazer remove exatamente as transações do lote
- [ ] Transação editada manualmente após a importação **não** é removida pelo desfazer

---

# F3 — Captura por notificação

### T-043 — Serviço e allowlist
**Fase** F3 · **Depende de** T-039 · **REQ** REQ-NOT-001, REQ-NOT-006

**Pronto quando**
- [ ] `NotificationFilterTest` prova que o teste de allowlist é a primeira instrução, antes de qualquer leitura de conteúdo
- [ ] Nenhum texto bruto persistido nem logado
- [ ] Tela de ajustes com escolha explícita dos apps, tudo desligado por padrão

### T-044 — Parsers por banco
**Fase** F3 · **Depende de** T-043 · **REQ** REQ-NOT-002, REQ-NOT-004

**Pronto quando**
- [ ] `NotificationParserTest` com textos reais de ao menos 3 bancos como fixtures
- [ ] Texto não reconhecido é descartado em silêncio, sem notificar

### T-045 — Confirmação
**Fase** F3 · **Depende de** T-044 · **REQ** REQ-NOT-003

Transação `cleared = 0` + notificação própria com ação de confirmar e categoria rápida.

**Pronto quando**
- [ ] Confirmar pela notificação não abre o app
- [ ] Transação capturada passa pelo mesmo dedupe da F2

### T-046 — Detector de parser morto ⇉
**Fase** F3 · **Depende de** T-044 · **REQ** REQ-NOT-005

App da allowlist sem match há 30 dias gera aviso ao usuário.

---

# F4 — Open Finance

Bloqueada por fatores externos ([ADR-007](decisoes.md#adr-007--ingestão-em-três-camadas-open-finance-isolado-na-f4)).
Nenhuma task desta fase começa antes do T-047.

### T-047 — Decisão go / no-go
**Fase** F4 · **Depende de** T-046 · **REQ** —

Spike de decisão, não de código. Antes de qualquer investimento:

**Pronto quando**
- [ ] Medido quanto lançamento manual ainda resta com F2 + F3 em uso real por 30 dias
- [ ] Custo mensal de agregador cotado (Pluggy, Belvo, Klavi) para o volume esperado
- [ ] Custo de operar backend estimado: hospedagem, autenticação, LGPD como operador
- [ ] Decisão registrada como novo ADR, incluindo a opção de **não fazer**

Se F2 e F3 já reduziram o lançamento manual a um nível aceitável, `no-go` é o
resultado correto e encerra a fase.

### T-048 — Backend intermediário
**Fase** F4 · **Depende de** T-047 (go) · **REQ** REQ-OF-002

Serviço próprio guardando as credenciais do agregador. Escopo detalhado só após
o `go`.

### T-049 — Conexão e sincronização
**Fase** F4 · **Depende de** T-048 · **REQ** REQ-OF-001, REQ-OF-003, REQ-OF-004

**Pronto quando**
- [ ] Recurso desligado por padrão; app 100% funcional sem ele
- [ ] Aviso de renovação de consentimento antes dos 12 meses
- [ ] Transações sincronizadas passam pela mesma tela de revisão (Art. 14)
- [ ] Permissão `INTERNET` adicionada **nesta task**, e `ManifestTest` (T-019) atualizado para permitir apenas a partir da F4
