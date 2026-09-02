# Arquitetura — financepro

## 1. Build

```
minSdk 26        // java.time nativo, sem core library desugaring
targetSdk 36
compileSdk 37
JVM target 17
```

`compileSdk` e `targetSdk` **não** andam juntos de propósito. O primeiro decide
contra qual API se compila; o segundo liga comportamentos novos de runtime.
Subir o primeiro é o que destrava biblioteca nova; subir o segundo pede uma
passada de teste própria, e vira decisão à parte.

Quatro dependências seguidas — Compose BOM, sqlcipher, navigation e
hilt-navigation-compose — exigiram `compileSdk 37` e AGP ≥ 9.1. Segurar cada uma
numa linha atrasada era imposto cobrado a cada task nova, então a plataforma
subiu de uma vez: AGP 9.3.2 sobre Gradle 9.5.0.

`minSdk 26` (Android 8.0) cobre >97% dos aparelhos ativos e elimina a
dependência de desugaring só para ter `LocalDate`. Um problema a menos.

Dependências declaradas em `gradle/libs.versions.toml` (version catalog).

## 2. Módulos

**Um único módulo Gradle (`:app`).** Separação por pacote, não por módulo.

Multi-módulo resolve tempo de build em projeto grande e reuso entre apps. Este
app não tem nenhum dos dois problemas. Quando o build incremental passar de ~1min,
o primeiro corte natural é `:core:database`.

```
app.financepro/
  core/
    money/          Money.kt (formatação, parse), extensões de Long
    time/           MonthRange.kt, clamps de dia do mês
    ui/             tema, componentes compartilhados, modifiers
  data/
    db/             AppDatabase, entities, DAOs, migrations, converters
    repo/           AccountRepository, TxnRepository, BudgetRepository, ...
    prefs/          SecurityPrefs (T-018), SettingsDataStore
    ingest/         parsers OFX/CSV, dedupe, ImportBatch  -> ver ingestao.md
    notif/          NotificationListenerService + parsers por banco
  domain/
    model/          modelos puros, sem anotação de Room
    usecase/        AccountBalance, CardInvoice, BudgetProgress,
                    GenerateRecurring, SplitInstallments, ValidateTxn
  feature/
    home/  txn/  accounts/  card/  budget/  recurring/
    reports/  importer/  settings/  onboarding/  lock/
  MainActivity.kt
  FinanceApp.kt
```

`data/ingest/` e não `data/import/`, como esta árvore dizia até a T-036: `import`
é palavra reservada em Kotlin, e um pacote com esse nome precisaria de crase em
todo arquivo que o referencia.

## 3. Camadas

```
Compose  →  ViewModel  →  UseCase  →  Repository  →  DAO / DataStore
           (StateFlow)   (puro)      (Flow)
```

Regras que não se negociam:

1. **`domain/` não importa nada de Android nem de Room.** É Kotlin puro. É o que
   torna toda regra de negócio testável em JVM, sem emulador, em milissegundos.
2. **Toda regra de dinheiro vive em `domain/usecase`**, nunca em ViewModel e nunca
   em `@Query`. Saldo, fatura, parcela e orçamento são funções puras testáveis.
3. **ViewModel expõe um único `StateFlow<UiState>`.** Nada de 6 `StateFlow` que
   emitem fora de sincronia e piscam a tela.
4. **Repository devolve `Flow`.** A UI nunca busca dado imperativamente.

Não há interface com uma única implementação. `TxnRepository` é uma classe
concreta injetada pelo Hilt. Interface entra quando existir a segunda
implementação — e para trocar por fake em teste, o Hilt já substitui o módulo inteiro.

## 4. Schema

```sql
CREATE TABLE account (
  id                  INTEGER PRIMARY KEY AUTOINCREMENT,
  name                TEXT    NOT NULL,
  type                TEXT    NOT NULL,   -- CHECKING|SAVINGS|CASH|CREDIT_CARD|INVESTMENT
  initialBalanceCents INTEGER NOT NULL DEFAULT 0,
  colorArgb           INTEGER NOT NULL,
  iconKey             TEXT    NOT NULL,
  archived            INTEGER NOT NULL DEFAULT 0,
  sortOrder           INTEGER NOT NULL DEFAULT 0,
  -- só CREDIT_CARD:
  creditLimitCents    INTEGER,
  closingDay          INTEGER,            -- 1..28
  dueDay              INTEGER,            -- 1..28
  paymentAccountId    INTEGER REFERENCES account(id) ON DELETE SET NULL
);

CREATE TABLE category (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  name      TEXT    NOT NULL,
  kind      TEXT    NOT NULL,             -- INCOME|EXPENSE
  parentId  INTEGER REFERENCES category(id) ON DELETE CASCADE,
  iconKey   TEXT    NOT NULL,
  colorArgb INTEGER NOT NULL,
  archived  INTEGER NOT NULL DEFAULT 0,
  useCount  INTEGER NOT NULL DEFAULT 0    -- ordena o grid do lançamento rápido
);

CREATE TABLE txn (
  id                 INTEGER PRIMARY KEY AUTOINCREMENT,
  accountId          INTEGER NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  counterAccountId   INTEGER          REFERENCES account(id) ON DELETE CASCADE,
  categoryId         INTEGER          REFERENCES category(id) ON DELETE RESTRICT,
  type               TEXT    NOT NULL, -- INCOME|EXPENSE|TRANSFER
  amountCents        INTEGER NOT NULL, -- efeito líquido em accountId; != 0
  date               INTEGER NOT NULL, -- epochDay
  description        TEXT    NOT NULL DEFAULT '',
  notes              TEXT,
  cleared            INTEGER NOT NULL DEFAULT 1,
  installmentGroupId TEXT,
  installmentIndex   INTEGER,
  installmentTotal   INTEGER,
  recurringRuleId    INTEGER REFERENCES recurring_rule(id) ON DELETE SET NULL,
  importBatchId      INTEGER REFERENCES import_batch(id)   ON DELETE SET NULL,
  dedupeKey          TEXT,             -- ver ingestao.md §3
  createdAt          INTEGER NOT NULL,
  updatedAt          INTEGER NOT NULL
);

CREATE TABLE budget (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  categoryId INTEGER NOT NULL REFERENCES category(id) ON DELETE CASCADE,
  yearMonth  INTEGER NOT NULL,          -- yyyyMM, ex: 202608
  limitCents INTEGER NOT NULL
);
CREATE UNIQUE INDEX idx_budget_cat_month ON budget(categoryId, yearMonth);

CREATE TABLE recurring_rule (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  accountId        INTEGER NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  counterAccountId INTEGER          REFERENCES account(id) ON DELETE CASCADE,
  categoryId       INTEGER          REFERENCES category(id) ON DELETE RESTRICT,
  type             TEXT    NOT NULL,
  amountCents      INTEGER NOT NULL,
  description      TEXT    NOT NULL,
  freq             TEXT    NOT NULL,     -- DAILY|WEEKLY|MONTHLY|YEARLY
  interval         INTEGER NOT NULL DEFAULT 1,
  dayOfMonth       INTEGER,              -- MONTHLY/YEARLY
  weekday          INTEGER,              -- WEEKLY (1=segunda)
  monthOfYear      INTEGER,              -- YEARLY
  startDate        INTEGER NOT NULL,     -- epochDay
  endDate          INTEGER,
  lastGeneratedDate INTEGER,
  autoPost         INTEGER NOT NULL DEFAULT 0,
  active           INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE import_batch (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  accountId  INTEGER NOT NULL REFERENCES account(id) ON DELETE CASCADE,
  sourceType TEXT    NOT NULL,           -- OFX|CSV|NOTIFICATION|OPEN_FINANCE
  sourceName TEXT    NOT NULL,
  importedAt INTEGER NOT NULL,
  txnCount   INTEGER NOT NULL
);

-- Regras de auto-categorização aprendidas. Ver ingestao.md §4.
CREATE TABLE payee_rule (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  normalizedKey TEXT    NOT NULL UNIQUE,
  categoryId    INTEGER NOT NULL REFERENCES category(id) ON DELETE CASCADE,
  hitCount      INTEGER NOT NULL DEFAULT 1
);
```

### 4.1 Índices

```sql
CREATE INDEX idx_txn_account_date  ON txn(accountId, date);
CREATE INDEX idx_txn_counter       ON txn(counterAccountId);
CREATE INDEX idx_txn_date          ON txn(date);
CREATE INDEX idx_txn_category_date ON txn(categoryId, date);
CREATE INDEX idx_txn_installment   ON txn(installmentGroupId);
CREATE INDEX idx_txn_cleared_date  ON txn(cleared, date);
CREATE UNIQUE INDEX idx_txn_dedupe ON txn(accountId, dedupeKey) WHERE dedupeKey IS NOT NULL;

-- Colunas filhas de FK. Cobrem o DELETE do lado pai, que sem índice varre a
-- tabela inteira, e calam o aviso do Room que apareceria em todo build.
CREATE INDEX idx_account_payment      ON account(paymentAccountId);
CREATE INDEX idx_category_parent      ON category(parentId);
CREATE INDEX idx_txn_recurring        ON txn(recurringRuleId);
CREATE INDEX idx_txn_batch            ON txn(importBatchId);
CREATE INDEX idx_rule_account         ON recurring_rule(accountId);
CREATE INDEX idx_rule_counter         ON recurring_rule(counterAccountId);
CREATE INDEX idx_rule_category        ON recurring_rule(categoryId);
CREATE INDEX idx_batch_account        ON import_batch(accountId);
CREATE INDEX idx_payee_rule_category  ON payee_rule(categoryId);
```

O índice único parcial em `dedupeKey` faz o banco recusar duplicata de importação
por conta. É a rede de segurança embaixo da checagem em código — se o dedupe da
aplicação falhar, o `INSERT` falha, em vez de sujar os dados silenciosamente.

No código ele é **total**, sem o `WHERE`: o `@Index` do Room não expressa índice
parcial, e no SQLite dois `NULL` nunca colidem dentro de um índice único — o
efeito é o mesmo. A alternativa seria criar o índice por `execSQL` num callback,
fora do schema exportado, e aí o Room reprovaria a validação de abertura por
encontrar um índice que ele não declarou.

`@Upsert` **não** é o caminho de escrita da importação. Em violação de índice
único ele cai para um `UPDATE` pela chave primária, que numa linha nova (`id = 0`)
não casa com nada e falha em silêncio — a duplicata sumiria sem erro. O import
usa `@Insert`, que aborta.

`ON DELETE RESTRICT` em `categoryId` é o que implementa a regra "não dá para
excluir categoria com transações" no nível do banco, sem código de verificação.

### 4.2 Foreign keys

`ON DELETE` só funciona com foreign keys ligadas. No Room isso não é o padrão:

```kotlin
Room.databaseBuilder(ctx, AppDatabase::class.java, "finance.db")
    .openHelperFactory(SupportOpenHelperFactory(passphrase))
    .addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            db.execSQL("PRAGMA foreign_keys = ON")
        }
    })
    .build()
```

## 5. Queries centrais

**Saldo de todas as contas em uma query** (§3.3 da spec):

```sql
SELECT a.id,
       a.initialBalanceCents
       + COALESCE((SELECT SUM(amountCents) FROM txn
                    WHERE accountId = a.id AND cleared = 1), 0)
       - COALESCE((SELECT SUM(amountCents) FROM txn
                    WHERE counterAccountId = a.id AND cleared = 1), 0) AS balanceCents
FROM account a
WHERE a.archived = 0;
```

`cleared = 1` no saldo: previstos não entram no saldo real. O dashboard mostra o
previsto como número separado, nunca somado ao saldo.

**Itens da fatura de um cartão.** O agrupamento por competência é feito em Kotlin
com `invoiceMonthFor` — SQLite não tem aritmética de data decente e replicar a
regra em SQL criaria uma segunda fonte de verdade. A query traz a janela ampla, o
domínio filtra:

```sql
SELECT * FROM txn
WHERE accountId = :cardId
  AND type != 'TRANSFER'
  AND date BETWEEN :windowStart AND :windowEnd
ORDER BY date DESC;
```

**Progresso do orçamento no mês:**

```sql
SELECT c.id, c.name, b.limitCents,
       COALESCE(SUM(-t.amountCents), 0) AS spentCents
FROM budget b
JOIN category c ON c.id = b.categoryId
LEFT JOIN txn t
       ON (t.categoryId = c.id OR t.categoryId IN
             (SELECT id FROM category WHERE parentId = c.id))
      AND t.type = 'EXPENSE'
      AND t.date BETWEEN :monthStart AND :monthEnd
WHERE b.yearMonth = :yearMonth
GROUP BY c.id;
```

## 6. Paginação

Não tem. A lista de transações é filtrada por mês por padrão (~100 linhas). Paging 3
resolve um problema que este app não tem.

```
// ponytail: lista carrega o mês inteiro em memória. Trocar por Paging 3
// se o filtro "todos os períodos" ficar lento com mais de ~5k linhas.
```

## 7. Migrations

Room com `Migration` explícita. **`fallbackToDestructiveMigration()` é proibido**
neste projeto — apagar dado financeiro do usuário num update não é uma opção.

Schema exportado (`room.schemaLocation`) versionado em `app/schemas/`, e
`MigrationTestHelper` valida cada salto de versão em teste instrumentado.

## 8. Segurança

Aqui não se corta caminho. É dado financeiro.

| Item | Como |
|---|---|
| Banco criptografado | SQLCipher (`net.zetetic:sqlcipher-android`) |
| Chave do banco | 32 bytes aleatórios no Android Keystore, gerados no primeiro boot |
| Bloqueio do app | `BiometricPrompt` com fallback para PIN do aparelho, opcional |
| Backup do sistema | `android:allowBackup="false"`, `dataExtractionRules` vazio |
| Screenshot / recents | `FLAG_SECURE` na `MainActivity` quando o bloqueio está ativo |
| Logs | Nenhum valor monetário ou descrição em log, nem em debug |
| Rede | Nenhuma permissão de `INTERNET` até a F4 |

O app **não pede `INTERNET`** nas fases F0–F3. Um app financeiro sem permissão de
rede é uma garantia verificável pelo usuário no próprio Android, e não uma promessa
na política de privacidade.

### 8.1 Backup e export

- **Export** — CSV (transações) e JSON (banco completo). Via `ACTION_CREATE_DOCUMENT`,
  o usuário escolhe onde salvar. Sem upload.
- **Backup** — arquivo `.fpbk` criptografado com senha do usuário (AES-256-GCM,
  chave derivada por PBKDF2-HMAC-SHA256 com 600 mil iterações). Restaurar
  substitui o banco inteiro, com confirmação explícita porque é destrutivo.

  Envelope: `FPBK | versão | sal (16) | IV (12) | cifrado`, com o cabeçalho como
  AAD do GCM. **Não é `.zip`**, como esta linha dizia até a T-035: um zip de um
  arquivo só é um nome de entrada e um diretório central em volta do mesmo gzip,
  e `GZIPOutputStream` é da biblioteca padrão. O conteúdo é o mesmo JSON do
  export (`data/export/Export.kt`), comprimido antes de cifrar.

## 9. Testes e rastreabilidade

### 9.1 A anotação `@Req`

É o elo entre [spec.md](spec.md) e o código. Sem ela, `tools/trace.py` não
consegue provar que um requisito foi implementado, e a rastreabilidade vira
promessa.

```kotlin
// core/testing/Req.kt
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class Req(vararg val ids: String)
```

`SOURCE` de propósito: serve à ferramenta e à leitura, não ao runtime. Não entra
no APK.

```kotlin
@Req("REQ-CARD-003")
class InvoiceMonthTest {

    @Test
    fun `compra ate o fechamento entra na fatura do mes`() {
        assertEquals(YearMonth.of(2026, 3), invoiceMonthFor(date(2026, 3, 10), closingDay = 10))
    }

    @Test
    fun `compra depois do fechamento vai para o mes seguinte`() {
        assertEquals(YearMonth.of(2026, 4), invoiceMonthFor(date(2026, 3, 11), closingDay = 10))
    }

    @Req("REQ-CARD-003")
    @Test
    fun `virada de ano`() {
        assertEquals(YearMonth.of(2027, 1), invoiceMonthFor(date(2026, 12, 15), closingDay = 10))
    }
}
```

Anotar a classe basta para o `trace.py`. Anotar o método individual é para quando
uma classe cobre mais de um requisito e a leitura fica ambígua.

Um `@Req` apontando para requisito inexistente é erro, não aviso: significa que
alguém renomeou ou removeu um requisito e deixou o teste órfão.

### 9.2 O que é testado

**JVM puro, sem emulador** — `domain/`, que é onde mora o risco. Cada linha aqui
sai de um critério de aceite da spec, não de intuição:

| Classe | Requisito | Casos que importam |
|---|---|---|
| `MoneyParseTest` | `REQ-IMP-004` | as 6 linhas da tabela; zero `toDouble` no caminho |
| `MonthRangeTest` | `REQ-CORE-003` | `monthStartDay` 1, 5 e 31 |
| `ValidateTxnTest` | `REQ-CORE-002`, `REQ-TXN-004/005/013`, `REQ-CAT-003`, `REQ-ACC-006` | cada linha da §5 da spec, com a mensagem exata |
| `AccountBalanceTest` | `REQ-ACC-004`, `REQ-ACC-007`, `REQ-CARD-009` | **transferência não altera a soma dos saldos** |
| `InvoiceMonthTest` | `REQ-CARD-003/004` | dia do fechamento, dia seguinte, virada de dezembro |
| `InvoiceStatusTest` | `REQ-CARD-007` | as 3 condições de status |
| `InvoicePaymentTest` | `REQ-CARD-006` | pagamento integral zera a dívida sem código especial |
| `SplitInstallmentsTest` | `REQ-TXN-008` | **soma == total** para todo `n` de 1 a 72 |
| `InstallmentScopeTest` | `REQ-TXN-009` | só o escopo escolhido muda |
| `GenerateRecurringTest` | `REQ-REC-003/004/005/007` | 3 execuções no mesmo dia = mesmo conjunto |
| `RecurrenceExpansionTest` | `REQ-REC-006` | dia 31 em fevereiro, inclusive bissexto |
| `BudgetProgressTest` | `REQ-BUD-002/004` | subcategoria conta no teto da mãe |
| `NormalizeTest` | `REQ-IMP-008`, `REQ-ACT-004` | as 4 linhas da tabela de normalização |
| `DedupeTest` | `REQ-IMP-007/008/009` | reimportar o mesmo OFX não cria nada |
| `OfxParserTest` | `REQ-IMP-002/003` | SGML e XML; `CHARSET:1252` com acento |
| `NotificationFilterTest` | `REQ-NOT-001` | allowlist checada antes de ler conteúdo |

As duas linhas em negrito são os invariantes do Art. 7 — dinheiro não some nem
aparece. Se algum dia só sobrar tempo para dois testes, são esses.

**Room in-memory** — DAOs: cascade de exclusão, o `RESTRICT` da categoria
(`REQ-CAT-005`), as FKs realmente ativas (`REQ-DATA-002`) e o índice único de
dedupe recusando duplicata (`REQ-IMP-012`).

**Instrumentado** — só as migrations (`REQ-DATA-001`).

**Sem emulador** — `ManifestTest` (`REQ-SEC-007`) lê o manifesto **mesclado** e
falha se `INTERNET` aparecer antes da F4. Mesclado, não o do módulo: a permissão
costuma entrar por dependência transitiva, sem ninguém declarar.

### 9.3 O que não é testado

Sem teste de UI no MVP. Compose UI test é caro de manter e as telas mudam toda
semana no começo — entra quando estabilizarem.

Requisitos marcados `Teste: manual` na spec são exatamente esses: navegação,
layout, acessibilidade e fluxos que só verificação humana alcança. Eles não
contam para a cobertura automatizada, e o `trace.py` não os cobra — mas continuam
sendo `MUST`, e a auditoria de acessibilidade (T-020) é a task que os fecha.

## 10. Performance

Nada de otimização especulativa. Os dois pontos que já se sabe que importam:

1. **Saldo não é recalculado a cada frame.** É um `Flow` do Room que só re-emite
   quando `txn` muda.
2. **O grid de categorias do lançamento rápido** ordena por `useCount`, que é
   incrementado no salvamento. Sem cálculo em tempo de render.
