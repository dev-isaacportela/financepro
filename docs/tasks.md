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
- [x] Repositório git inicializado, com `.gitignore` e `.gitattributes` — sem ele o
      Art. 2 (commit cita `REQ-*`) não tem onde ser aplicado
- [x] `./gradlew assembleDebug` e `./gradlew test` passam num app vazio.
      Esteve bloqueado por `Unable to establish loopback connection` em toda
      invocação do Gradle. **Não era o Winsock:** a partir do JDK 16 o par
      interno de `Selector.open()` é um socket **AF_UNIX**, cujo arquivo nasce
      em `java.io.tmpdir` — e `connect()` devolve `EINVAL` para qualquer socket
      criado dentro de `%LOCALAPPDATA%\Temp` nesta máquina. Fora do `Temp`
      funciona. Correção é de máquina, não do repositório — caminho absoluto do
      Windows não pode entrar em arquivo versionado:

      ```
      JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=C:/Users/<curto>/.gradle/afunix-tmp
      ```

      Tem de ser variável de ambiente. `org.gradle.jvmargs` no
      `~/.gradle/gradle.properties` **não** serve: o `gradle.properties` do
      projeto sobrescreve o do home, e a propriedade some do daemon no primeiro
      reinício. `GRADLE_OPTS` também não basta, porque não alcança o worker de
      teste, que é forkado com args próprios
- [x] Version catalog é a única fonte de versões — nenhuma versão literal em `build.gradle.kts`
- [x] `room.schemaLocation` aponta para `app/schemas/`
- [x] `.gitattributes` força LF em `gradlew` — sem isso o CI Linux falha com
      "bad interpreter", erro que não parece ter relação com fim de linha

### T-002 — Núcleo de dinheiro ⇉
**Fase** F0 · **Depende de** T-001 · **REQ** REQ-CORE-001, REQ-CORE-004, REQ-CORE-005, REQ-IMP-004

`core/money/Money.kt`: parse de texto para centavos e formatação pt-BR.

Já entrega `REQ-IMP-004` porque o parser de importação (F2) usa exatamente esta
função — escrever duas conversões texto→centavos seria criar duas fontes de
verdade para a regra mais sensível do app.

**Pronto quando**
- [x] `parseCents(String): Long?` cobre as 6 linhas da tabela de REQ-IMP-004
- [x] Nenhum ponto flutuante em caminho de dinheiro — verificado por
      `tools/trace.py` (guarda do Art. 6), não por detekt: as regras equivalentes
      no detekt exigem resolução de tipos, que é frágil em projeto Android
- [x] `formatBRL(Long)` produz `R$ 1.234,56` e `−R$ 18,50`, com U+2212 e não hífen
- [x] `MoneyTest`, `MoneyFormatTest`, `MoneyParseTest` com `@Req` — 21 testes
- [x] `parseCents` aceita a saída de `formatBRL` (round-trip). O sinal U+2212 que
      `formatBRL` emite não era reconhecido na volta — bug encontrado ao escrever
      o teste, não depois

### T-003 — Núcleo de datas ⇉
**Fase** F0 · **Depende de** T-001 · **REQ** REQ-CORE-003

`core/time/MonthRange.kt`: período mensal a partir de `monthStartDay`, com clamp.

**Pronto quando**
- [x] `MonthRangeTest` cobre as 3 linhas da tabela de REQ-CORE-003, incluindo `monthStartDay = 31`
- [x] Nenhum uso de `Calendar` ou `Date` — só `java.time`
- [x] Períodos consecutivos não deixam buraco nem se sobrepõem, verificado em 24
      meses para 5 valores de `monthStartDay` — sem isso uma transação sumiria do
      orçamento ou seria contada duas vezes

`monthOf(date)` — descobrir a que mês uma data pertence — **não** entra aqui.
É de quem precisar primeiro (T-017 ou T-028), com o teste junto.

### T-004 — Schema e DAOs
**Fase** F0 · **Depende de** T-001 · **REQ** REQ-ACC-001, REQ-ACC-002, REQ-CAT-001, REQ-CAT-002, REQ-TXN-001, REQ-DATA-001, REQ-DATA-002, REQ-DATA-003

Entidades, DAOs, índices e converters conforme [arquitetura.md](arquitetura.md) §4.

**Os modelos de domínio não entram aqui.** A §3 da arquitetura já exige que
`domain/model/` seja Kotlin puro, sem anotação de Room — então eles são
entregues junto das regras que os usam (T-007 e T-008), e esta task passa a
tratar só do mapeamento para o banco. É o que permite validar saldo e validação
de transação sem emulador, que é justamente o objetivo do Art. 8.

**Pronto quando**
- [x] `PRAGMA foreign_keys = ON` no `onOpen`, com teste que prova que `RESTRICT`
      dispara. O callback é `AppDatabase.ForeignKeysOn`, e o teste abre o banco
      pelo mesmo objeto que a produção usa — se ele morasse só no módulo Hilt, o
      teste provaria `RESTRICT` num banco que ninguém abre desse jeito
- [x] Todos os índices da §4.1 criados, mais os das colunas filhas de FK que o
      Room exige. O único parcial de `dedupeKey` virou total: `@Index` não tem
      `WHERE`, e no SQLite dois `NULL` não colidem em índice único — mesmo efeito
- [x] Schema v1 exportado e commitado em `app/schemas/`
- [x] `fallbackToDestructiveMigration` não existe no código — verificado por
      `tools/trace.py`, não por detekt: a guarda do Art. 12 já mora lá junto com
      a do Art. 6, e duplicá-la em duas ferramentas só cria uma para desatualizar
- [x] `AccountDaoTest`, `CategoryDaoTest` e `TxnDaoTest` com `@Req` — 18 testes
      em JVM via Robolectric, sem emulador. Cobrem CASCADE, RESTRICT, `SET NULL`
      e o índice único de dedupe
- [x] Hierarquia de um nível (REQ-CAT-002) não é expressável em DDL: a FK garante
      que o pai existe, não que ele seja raiz. Fica em `upsertChecked`, dentro de
      `@Transaction`, porque entre a checagem e o `INSERT` o pai pode virar filho

**Gotchas de ambiente (Windows)** — nenhum deles é do projeto, e por isso a
correção mora em `~/.gradle/init.gradle.kts`, não no repositório:

- o worker de teste é forkado com args próprios e **não** herda
  `org.gradle.jvmargs`, então o contorno de AF_UNIX da T-001 precisa ser
  repassado a ele;
- o Robolectric monta o caminho do `android-all-instrumented.jar` a partir de uma
  URL sem decodificar, e um espaço no nome do usuário vira `%20` — a native
  runtime falha com `NoSuchFileException`. `user.home` no nome 8.3 resolve.

### T-005 — Criptografia do banco
**Fase** F0 · **Depende de** T-004 · **REQ** REQ-SEC-001, REQ-SEC-002

SQLCipher com chave de 32 bytes gerada por `SecureRandom` no primeiro boot,
guardada no Android Keystore.

**Guardada como, exatamente.** O provider `AndroidKeyStore` não devolve material
de chave — `SecretKey.getEncoded()` retorna `null` de propósito. Uma senha que
precisa ser lida de volta para abrir o banco, portanto, não pode morar *dentro*
do Keystore: ela é **embrulhada** por uma chave AES-GCM que mora lá e nunca sai.
O envelope (IV + texto cifrado) fica em `filesDir/db.key`. O efeito é o que o
[ADR-010](decisoes.md#adr-010--sqlcipher-e-sem-permissão-de-rede-até-a-f4) quer:
o arquivo copiado do aparelho não abre em outro lugar, porque a chave que o
decifra é inextraível e presa àquele hardware.

Sem `setUserAuthenticationRequired`: o worker de recorrência (T-031) abre o banco
com a tela bloqueada. O bloqueio biométrico é da tela (REQ-SEC-003), não do
arquivo.

**Pronto quando**
- [x] `sqlcipher-android` no APK, com `libsqlcipher.so` nas quatro ABIs —
      fixado na **4.17.0**: a 4.18.0 declara `minCompileSdk=37` e reprova o
      `checkAarMetadata`, mesmo motivo do bloco de Compose no version catalog
- [x] Falha em vez de gerar senha nova quando o envelope existe mas não decifra.
      Regenerar em silêncio transformaria um problema de chave na perda calada de
      todo o histórico financeiro
- [x] Escrita do envelope é `write` em `.tmp` + `rename`, que é atômico no mesmo
      volume: sem isso um desligamento no meio da gravação deixaria arquivo
      truncado, indistinguível de corrupção
- [ ] Arquivo `.db` extraído do aparelho não é legível por `sqlite3`
- [ ] Chave não aparece em `SharedPreferences`, DataStore, código ou log
- [ ] Reinstalação com dados preservados ainda abre o banco

Os três últimos são `Teste: manual` na spec e **só fecham no aparelho** — a
biblioteca nativa do SQLCipher não existe na JVM, e os testes de DAO montam o
banco em memória sem passar por `buildDatabase`. Roteiro:

```
adb shell run-as com.benenutri.finance cat files/../databases/finance.db > /tmp/f.db
sqlite3 /tmp/f.db .tables      # precisa falhar com "file is not a database"
adb shell run-as com.benenutri.finance ls -R shared_prefs files
```

### T-006 — Sementes ⇉
**Fase** F0 · **Depende de** T-004 · **REQ** REQ-CAT-004, REQ-ACT-003

Categorias padrão e ~40 regras de estabelecimento.

**Pronto quando**
- [x] `SeedTest` verifica as 10 categorias e o volume mínimo de `payee_rule` —
      56 regras, todas apontando para categoria existente
- [x] Seed roda uma única vez, na criação do banco. É `onCreate` do Room, não
      flag em DataStore: o ciclo de vida do banco já é a garantia, e uma flag
      separada é mais um estado para dessincronizar
- [x] O teste abre banco em **arquivo**, não em memória — é a única forma de
      provar que reabrir não semeia de novo
- [x] Chaves de `payee_rule` já na forma que `normalize` (T-036) produz:
      maiúsculas, sem acento, sem sequência de 4+ dígitos. Chave com acento
      nunca casaria, e o erro só apareceria na primeira importação da F2

O casamento dessas chaves fica na T-040, e precisa ancorar em limite de palavra:
elas são **palavra-chave**, não descrição inteira — o extrato traz
`UBER *TRIP HELP.UBER.COM`, e `TIM` dentro de `OTIMO` é o caso que prova.

### T-007 — Validação de transação
**Fase** F0 · **Depende de** T-002 · **REQ** REQ-CORE-002, REQ-ACC-006, REQ-CAT-003, REQ-TXN-004, REQ-TXN-005, REQ-TXN-013

`domain/usecase/ValidateTxn.kt`. Kotlin puro, sem Android.

**Pronto quando**
- [x] Cada regra da §5 da spec tem um caso de teste com a mensagem exata
- [x] Retorna lista de erros, não lança exceção — a UI mostra todos de uma vez,
      e há teste que prova os 4 erros saindo juntos
- [x] `ValidateTxnTest` com `@Req` para os 6 requisitos
- [x] `hoje` é injetado, não lido de `LocalDate.now()`: data do sistema dentro de
      função pura tornaria o teste dependente do dia em que roda

### T-008 — Cálculo de saldo
**Fase** F0 · **Depende de** T-002, T-003 · **REQ** REQ-ACC-003, REQ-ACC-004, REQ-ACC-007, REQ-TXN-002, REQ-TXN-003, REQ-TXN-006, REQ-CARD-009

`AccountBalance`, com a fórmula de dois termos de [ADR-003](decisoes.md#adr-003--transferência-é-uma-linha-não-duas).

**Pronto quando**
- [x] `AccountBalanceTest` cobre as 4 linhas da tabela de REQ-ACC-004
- [x] Teste explícito do invariante: transferência não altera a soma dos saldos (Art. 7)
- [x] Saldo total do dashboard exclui `CREDIT_CARD`; dívida sai por `cardDebt`
- [x] `TransferTest` e `TxnSignTest` com `@Req`
- [x] **Pagar fatura zera a dívida do cartão sem nenhum ramo condicional** — é o
      retorno prático do ADR-003, e tem teste próprio

### T-009 — Repositórios e DI
**Fase** F0 · **Depende de** T-005, T-007, T-008 · **REQ** —

Repositórios expondo `Flow`, módulos Hilt, `Application`.

**Só leitura.** Cada escrita nasce com a tela que a chama — T-012 cria conta,
T-013 cria transação, T-016 mexe em categoria. É a tela que decide quais colunas
de apresentação (`colorArgb`, `iconKey`, `sortOrder`) sobem para o modelo de
domínio; escrever os `upsert` agora seria adivinhar essa assinatura três vezes e
acertar por acaso.

**Pronto quando**
- [x] Nenhum repositório tem interface (Art. 10). Não existe segunda
      implementação, e para trocar por fake em teste o Hilt substitui o módulo
      inteiro — a interface custaria um arquivo por repositório, para sempre
- [x] Nenhum arquivo em `domain/` importa `android.*` ou `androidx.*` —
      `ForbiddenImport` do detekt, restrito a `**/domain/**`. Guarda exercida:
      um `import android.util.Log` em `Models.kt` reprova o build
- [x] `@HiltAndroidApp` na `FinanceApp`, e nada além disso lá: o banco é
      construído na primeira injeção, não no `onCreate`, para não atrasar o start
      de todo lançamento por trabalho que a primeira tela já dispara
- [x] Módulo Hilt mora em `data/db/`, não num pacote `di/`. Um pacote só de
      módulos vira índice remoto do resto do app, para manter em sincronia sem
      ganhar nada. Repositórios nem aparecem nele — têm `@Inject constructor`
- [x] `RepositoryTest` leva o resultado do repositório até `balanceOf`: se o
      mapeamento perdesse `cleared` ou `counterAccountId`, o saldo daria outro
      número. A conversão `LocalDate` ↔ `epochDay` é de borda e tem teste próprio

### T-010 — Sistema visual Slush ⇉
**Fase** F0 · **Depende de** T-001 · **REQ** REQ-UI-007, REQ-A11Y-002, REQ-A11Y-005, REQ-DS-001, REQ-DS-002, REQ-DS-003, REQ-DS-004, REQ-DS-005, REQ-DS-006, REQ-DS-007, REQ-DS-008, REQ-DS-009, REQ-DS-010

Tokens, tipografia, formas e componentes base conforme [design.md](design.md).

**Pronto quando**
- [x] Antonio e Inter empacotadas em `res/font/` — nenhuma Downloadable Font.
      As duas são **variáveis**, um arquivo por família: `FontVariation.weight`
      tira W500 e W700 de Inter do mesmo arquivo, e dois estáticos custariam o
      dobro de APK pelo mesmo resultado. `ManifestTest` guarda o flanco
- [x] `SlushColors` com `paper`/`ink`; contorno inverte junto com o tema, sem
      condicional nas telas
- [x] `ContrastTest` recalcula da paleta e falha se um sticker virar cor de
      texto. A afirmação que ele fixa é a que justifica a regra: **nenhum**
      sticker passa em 4.5:1 nos dois temas — sobre branco só o Violet passa,
      sobre papel escuro é justamente o Violet que reprova
- [x] `TypographyTest` exige `lineHeight ≤ 0.85.em`, `includeFontPadding = false`
      e `Trim.Both` nos três estilos display
- [x] `Color(0x` fora de `core/ui/theme/`, elevação ≠ 0 e `Brush.*Gradient`
      proibidos — por **`TokenLintTest`**, não por detekt: `ForbiddenMethodCall`
      exige resolução de tipos, e não existe regra de "este padrão não pode
      aparecer fora deste pacote". Mesma escolha que a T-002 fez para o Art. 6.
      O teste inclui um caso que prova que a varredura achou arquivos — sem ele,
      um `walkTopDown` no diretório errado passaria calado.
      Guarda exercida: `Color(0xFFFF0000)` e `defaultElevation = 4.dp` reprovam
- [x] Sexto caso fecha o furo que a varredura não pega: as cores das categorias
      semeadas são ARGB `Int` em `Seed.kt` (porque `data/` não importa Compose),
      e o teste confere que elas são as mesmas seis do tema
- [x] `Card`, `Button`, `FAB` e `Surface` com elevação e `tonalElevation` zerados
      explicitamente, uma vez, em `core/ui/component/Slush.kt`
- [x] Componentes base nascem com alvo ≥ 48dp via `minimumInteractiveComponentSize()`
- [x] `MoneyText` centraliza formatação, sinal e `tnum` — nenhuma tela formata
      dinheiro à mão
- [ ] Duas renderizações WebP da fita 3D, ≤ 120KB cada, em `nodpi` — **não
      feito.** É arte, não código: são renders 3D da fita, e não há como
      produzi-los aqui sem inventar outra coisa no lugar. Bloqueia a T-012
      (onboarding é o único pôster completo) e o estado vazio da T-014

**Dívida deixada**

- `ponytail:` em `Type.kt` — Inter variável completa pesa 876KB, não os ~350KB
  que o design.md §3 estimou, e subsetar para latin + latin-ext derrubaria para
  ~120KB. Exige `fontTools`, que não instala nesta máquina (o `pip` do Python do
  msys2 não sobe). Fazer no CI ou noutra máquina.
- `CategorySticker` (design.md §6.2) fica para a T-013: ele precisa de
  `colorArgb` no modelo de domínio de categoria, e é a T-013 que define qual
  campo de apresentação sobe para o domínio.

### T-011 — Navegação
**Fase** F0 · **Depende de** T-010 · **REQ** REQ-UI-001

Bottom bar de 4 destinos, rotas type-safe.

**Pronto quando**
- [x] Quatro destinos — Início, Transações, Orçamento, Mais — como `@Serializable
      data object`. Rota como texto só quebra em tempo de execução, e num app de
      quatro abas o custo de descobrir isso já é maior que o de anotá-las
- [x] Barra é pílula flutuante contornada, não a `NavigationBar` do Material:
      aquela traz superfície tonal e nenhum contorno, o oposto da gramática de
      Slush (design.md §6.1)
- [x] Seleção sinalizada por **preenchimento**, não só por cor (REQ-A11Y-003), e
      exposta ao leitor de tela por `semantics { selected = ... }`
- [x] `popUpTo(Inicio) { saveState }` + `launchSingleTop` + `restoreState`: sem
      eles, ir e voltar entre abas empilha destinos e o botão voltar vira labirinto
- [x] `MainActivity` fica só com tema e grafo — tela dentro da `Activity` é tela
      que nenhuma rota alcança
- [x] `navigation-compose` fixado na **2.9.8**: a 2.10.0 é a terceira dependência
      a exigir `compileSdk 37`, junto com Compose BOM e sqlcipher

As quatro telas são um único placeholder compartilhado, não quatro arquivos de
stub. Cada uma vira tela de verdade na sua task (T-017, T-014, T-029, T-015)
trocando uma linha do `NavHost`.

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

**Pronto quando**
- [x] `ManifestTest` com `@Req`, lendo o mesclado pelo `PackageManager`
- [x] Guarda exercida de verdade: declarar `INTERNET` no manifesto faz o teste
      falhar. Sem esse ensaio, um teste verde não prova nada
- [x] Segundo caso ancora o primeiro — um manifesto que não chegou ao teste
      também "não declara INTERNET", e passaria por vacuidade. O teste exige ver
      a `MainActivity` na lista
- [x] Proíbe `INTERNET` nominalmente, não "nenhuma permissão": a T-018 traz
      `USE_BIOMETRIC` legitimamente, e um teste que reprova toda permissão vira
      obstáculo em vez de guarda

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
- [x] Anotação `@Req` escrita em `core/testing/Req.kt`, `@Retention(SOURCE)`
- [x] Workflow em `.github/workflows/ci.yml`: rastreabilidade roda **antes** do
      build e sem Gradle — se a spec quebrou, não vale compilar
- [x] Roda em segundos, sem dependência externa (Art. 4)
- [ ] Workflow exercido de verdade — só na primeira execução em um remoto
- [ ] Ao fechar a F0, trocar `trace.py` por `trace.py --phase F0` no workflow.
      Hoje `--phase F0` falha com 35 erros, que é o resultado correto

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
- [x] `InvoiceMonthTest` cobre as 6 linhas de REQ-CARD-003 e as 4 de REQ-CARD-004
- [x] Virada de ano testada explicitamente (dez → jan)
- [x] Invariante extra: **vencimento nunca vem antes do fechamento**, verificado
      nas 784 combinações de `closingDay` × `dueDay`
- [x] A fatura nunca retrocede nem pula um mês ao percorrer 2 anos de calendário

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
- [x] `SplitInstallmentsTest` verifica `soma == total` para **todo** `n` de 1 a 72,
      sobre 11 totais — 792 combinações
- [x] Sobra alocada na última parcela, nunca na primeira: quem confere o extrato
      compara a **primeira** parcela com o valor anunciado na compra
- [x] Cobre as 4 linhas da tabela de REQ-TXN-008
- [x] Funciona igual para valor negativo, que é como despesa é gravada
- [x] Datas ajustam fim de mês: compra em 31/01 gera parcela em 28/02 e **volta**
      para 31/03, em vez de ficar presa no dia 28

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
- [x] `RecurrenceExpansionTest` cobre as 4 linhas de clamp de REQ-REC-006, incluindo ano bissexto
- [x] `interval` testado para as 4 frequências
- [x] **O clamp não gruda**: regra do dia 31 gera 28/02 e volta para 31/03. Cada
      ocorrência sai da âncora original, nunca da ocorrência anterior — calcular
      a partir da anterior prenderia a regra no dia 28 para sempre
- [x] Sequência estritamente crescente nas 4 frequências × 3 intervalos: data
      repetida duplicaria lançamento, e data que retrocede quebraria a
      idempotência da T-031, que avança `lastGeneratedDate`

Campos de âncora (`dayOfMonth`, `weekday`, `monthOfYear`) são **opcionais** e,
quando nulos, saem de `startDate` — mesma convenção do iCalendar (`BYMONTHDAY`
derivando de `DTSTART`). Evita o estado inconsistente de `dayOfMonth = 10` com
`startDate` no dia 3, sem mudar o schema: as colunas já são nuláveis.

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
