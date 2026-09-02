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
- [x] Plataforma subida de uma vez para `compileSdk 37`, AGP 9.3.2 e Gradle
      9.5.0, depois de a **quarta** dependência seguida exigir isso. `targetSdk`
      fica em 36: é decisão separada, e liga comportamento de runtime.
      O `distributionSha256Sum` do wrapper foi trocado pelo checksum **publicado
      pelo Gradle**, conferido contra o que baixou — não pelo que a máquina
      calculou, que não provaria nada
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
- [x] Arquivo do banco em disco **não** começa com `SQLite format 3` — é a marca
      que o `sqlite3` procura, e o que ele exige para abrir. Verificado no
      aparelho, lendo o arquivo real
- [x] Chave não aparece em `SharedPreferences`, DataStore, código ou log. O
      diretório `shared_prefs` nem chega a existir, e `db.key` tem exatamente
      60 bytes — IV(12) + senha(32) + tag GCM(16). Se alguém trocasse o embrulho
      por uma gravação crua, teria 32
- [x] Fechar e reabrir enxerga os mesmos dados: prova que a senha sobrevive ao
      ciclo completo do Keystore — gerar, embrulhar, gravar, ler, desembrulhar
- [ ] **Reinstalação** com dados preservados ainda abre o banco — distinto de
      reabrir: testa se o alias do Keystore sobrevive a um `install -r`. Precisa
      que o app crie o banco no uso normal, o que só acontece a partir da T-013

Os três primeiros eram `Teste: manual` na spec. Viraram `DatabaseCipherTest`, em
`androidTest`, que roda pelo mesmo `buildDatabase` da produção — um teste que
montasse o banco de outro jeito provaria a criptografia de um banco que ninguém
abre desse jeito. Não vão para o CI: não há emulador no pipeline, e o valor é
justamente rodar em hardware com Keystore de verdade.

```
./gradlew connectedDebugAndroidTest      # 3 testes, SM-S942B (Android 16)
```

O `databases/` não existia até este teste: **nenhuma tela injeta o banco ainda**,
então o caminho do SQLCipher nunca tinha sido executado. Descoberto ao instalar
no aparelho, não em revisão de código.

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
- [x] `Paleta` com `paper`/`ink`; contorno inverte junto com o tema, sem
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
      explicitamente, uma vez, em `core/ui/component/Componentes.kt`
- [x] Componentes base nascem com alvo ≥ 48dp via `minimumInteractiveComponentSize()`
- [x] `MoneyText` centraliza formatação, sinal e `tnum` — nenhuma tela formata
      dinheiro à mão
- [~] Duas renderizações WebP da fita 3D, ≤ 120KB cada, em `nodpi` —
      **diferida por decisão do dono do produto (2026-08-31)**, e registrada
      como tal em spec.md (REQ-DS-009) e design.md §7. É arte, não código.
      A T-012 segue sem ela: o pôster de onboarding fica com tipografia,
      stickers e banda de cor, que é o que já entrega o efeito em 360dp

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
- [x] Barra respeita `WindowInsets.navigationBars`. Com `enableEdgeToEdge` o
      Scaffold entrega o slot colado na borda física, e quem usa três botões em
      vez de gestos veria a barra atrás deles. **Só apareceu rodando no
      aparelho** — no emulador de gestos o erro é de poucos dp e passa batido
- [x] Verificado na S26 (SM-S942B, Android 16): navegação entre abas funciona, e
      o contorno `ink` inverte junto com o papel nos dois temas sem nenhuma
      condicional nas telas — que era a aposta da T-010
- [x] `navigation-compose` fixado na **2.9.8**: a 2.10.0 é a terceira dependência
      a exigir `compileSdk 37`, junto com Compose BOM e sqlcipher

As quatro telas eram um único placeholder compartilhado, não quatro arquivos de
stub. Cada uma vira tela de verdade na sua task (T-017, T-014, T-029, T-015)
trocando uma linha do `NavHost` — e foi exatamente uma linha nas quatro vezes.
Só o Orçamento (T-029) ainda usa o placeholder.

### T-012 — Onboarding
**Fase** F0 · **Depende de** T-009, T-011 · **REQ** REQ-UI-005

**Pronto quando**
- [x] Uma única tela, uma pergunta: "Quanto você tem hoje?". Verificado no
      aparelho — instalar, informar o saldo e tocar Começar, e o app já está
      utilizável
- [x] Cria `CASH` e `CHECKING` com o saldo informado em `initialBalanceCents`
- [x] "Já passou pelo onboarding?" é **pergunta ao banco**, não flag em
      DataStore: sem conta nenhuma, não há onde lançar, então "sem contas" já é
      a definição de primeiro uso. Uma flag seria mais um estado para
      dessincronizar — apagar os dados e não a flag prenderia o usuário numa
      tela inicial que não funciona
- [x] É o único pôster completo do app. Sem a fita (diferida), o pôster é
      `DisplayXl` a 88sp, que em 360dp ocupa quase a largura toda

### T-013 — Lançamento rápido
**Fase** F0 · **Depende de** T-009, T-011 · **REQ** REQ-UI-002, REQ-UI-003, REQ-CAT-006

O fluxo mais importante do app (Art. 18).

**Pronto quando**
- [x] Despesa registrada em 3 toques a partir da tela inicial — FAB, valor,
      categoria, salvar —, verificado na S26: saldo de R$ 500,00 vira R$ 490,00
      depois de uma despesa de R$ 10,00, sem nenhuma navegação de tela cheia
- [x] Bottom sheet, nunca tela cheia, com o teclado numérico já em foco. Digita-se
      **centavos**, da direita para a esquerda, como numa maquininha: sem vírgula
      para posicionar, e sem o erro clássico de digitar "18" e gravar 18 centavos
- [x] Campos condicionais de REQ-UI-003 aparecem e somem corretamente. A regra
      vive em `QuickEntryState` como propriedade derivada, não espalhada em `if`
      pela folha — é o que a torna verificável em JVM por `QuickEntryStateTest`
- [x] Grid ordenado por `useCount`, incrementado ao salvar, com teste próprio
- [x] O sinal é aplicado na borda: quem digita 18,50 numa despesa grava −1850
      (REQ-TXN-002). Pedir o sinal a quem está com o cartão na mão seria mudar a
      convenção do banco de lugar
- [x] Parcelamento usa `splitInstallments` da T-026, já testada em 792
      combinações, e grava as N linhas numa escrita só — meia compra parcelada é
      dinheiro inventado no extrato. A UI de editar/excluir por escopo é da T-027

**Dois bugs que só o aparelho mostrou**

- **A folha não abria uma segunda vez.** Ela vive fora do `NavHost` de propósito
  (é sobreposição, não destino), então o `hiltViewModel()` dela é o da Activity e
  sobrevive ao fechamento com `salvo = true` — e na reabertura o efeito que fecha
  ao salvar disparava na primeira composição. O sintoma era o botão de lançar
  simplesmente parar de responder. `concluido()` consome o sinal e devolve o
  formulário limpo; `QuickEntryViewModelTest` guarda a regressão.
- **Texto branco sobre papel branco no onboarding.** O `windowBackground` do tema
  de plataforma estava fixo em branco enquanto o tema escuro estava ativo, e a
  tela não pintava fundo próprio. O pior é que o layout estava correto: o
  `uiautomator dump` mostrava tudo no lugar enquanto a tela parecia vazia.
  Corrigido nos dois lados — `values-night/themes.xml` e a tela pintando
  `Tema.paper`, porque nenhuma tela deve depender do fundo da janela.

### T-014 — Lista de transações
**Fase** F0 · **Depende de** T-013 · **REQ** REQ-TXN-010, REQ-TXN-011, REQ-TXN-012

**Pronto quando**
- [x] Agrupada por dia, decrescente, com o líquido do dia no cabeçalho. O
      cabeçalho soma **as linhas visíveis**, e não só as `cleared` como
      `balanceOf` — um total que não bate com o que está logo abaixo lê como
      bug. Em F0 a diferença é teórica: nada cria transação prevista antes da
      T-031, e o `ponytail:` diz onde separar as duas quando ela deixar de ser
- [x] Swipe-excluir com desfazer de 5s, sem diálogo. Cinco segundos **exatos**
      exigem `Indefinite` dentro de `withTimeoutOrNull`: `SnackbarDuration.Short`
      são ~4s e `Long` ~10s, e nenhum dos dois é o que a spec pede
- [x] Filtros de mês, conta, categoria e tipo, e busca por texto ou valor
- [x] Deslizar não é o único caminho: a linha tem `CustomAccessibilityAction`
      de excluir. Com o TalkBack ligado o gesto de arrastar não chega ao
      componente, então sem ela **excluir seria inalcançável** por leitor de
      tela (Art. 17). São quatro linhas
- [x] Comentário `ponytail:` documentando a ausência de Paging ([ADR-009](decisoes.md#adr-009--sem-paging-3-no-mvp)),
      com o teto de ~5k linhas nomeado onde ele de fato é atingido: o filtro
      "Tudo" e o extrato de conta, que carregam o histórico inteiro
- [x] Extrato por conta com saldo corrente linha a linha — o item que a T-015
      empurrou para cá. É a **mesma** lista com a conta filtrada, não uma
      segunda tela: duplicá-la daria duas para reconciliar depois, que era
      exatamente o motivo do adiamento. Verificado na S26 — a primeira linha do
      extrato da Carteira traz −R$ 18,50, o mesmo número que a tela de Contas
      mostra para ela

**Três decisões que carregam o resto**

- **O desfazer guarda a entidade, não o modelo de domínio.** `TxnEntity` tem
  `notes`, `recurringRuleId`, `importBatchId`, `dedupeKey` e `createdAt`, que
  `Txn` não carrega. Repor a linha a partir do domínio apagaria as cinco em
  silêncio — e uma `dedupeKey` perdida faria a importação da F2 recriar a
  transação como se fosse nova. A entidade fica no repositório: subi-la para o
  domínio quebraria o Art. 8, devolvê-la ao ViewModel quebraria a §3 da
  arquitetura. O teste compara o **objeto inteiro**, e não campo a campo, para
  continuar pegando o erro quando alguém acrescentar a décima segunda coluna.
- **`efeitoEm` saiu de dentro de `balanceOf`.** O total do dia e o saldo
  corrente precisavam da mesma fórmula de dois termos do ADR-003; reescrevê-la
  nos dois daria três fontes de verdade para a conta mais sensível do app.
  `balanceOf` passou a somá-la, e os testes que já existiam guardaram a
  refatoração. O caso que justifica o par: na lista geral, R$ 1.000 mudando de
  bolso não pode aparecer como prejuízo de R$ 1.000 no cabeçalho do dia.
- **Filtro e busca são regra, não `@Query`.** Em SQL ficam só o mês e a conta,
  que já estavam lá e já tinham teste. O que decide não é gosto: o `LIKE` do
  SQLite só ignora caixa em ASCII, e a busca por valor exigiria uma segunda
  conversão texto → centavos. REQ-TXN-012 teve o `Teste:` corrigido na spec no
  mesmo commit (Art. 3).

**Um bug que só o aparelho mostrou**

"Agosto de 2026" saía cortado em **"Agosto de"**: o título dividia a linha com
três pílulas e não cabe em 360dp. O `uiautomator dump` não denuncia — ele traz o
texto inteiro, porque o corte é de desenho, não de conteúdo. Foi preciso olhar a
captura. Título em linha própria, que é também o que sobrevive à fonte a 200%
(REQ-A11Y-004), onde a versão de uma linha só ficaria pior.

**Fora de escopo, e por quê**

**Editar transação não entra aqui.** A DoD desta task não pede, mas REQ-TXN-001
exige "criar, editar, excluir e listar" — e nenhuma task da F0 cobria a edição.
A T-027 é dona do escopo de parcela, não da edição simples. Inventar uma folha
de edição aqui seria trabalho não pedido (Art. 1); o buraco virou a **T-050**,
onde tem dono e DoD.

**Dívida deixada**

- `ponytail:` em `TxnList.kt` — a busca não dobra acento, então "alimentacao"
  não acha "Alimentação". A dobra é da `normalize` da T-036, e escrever uma
  segunda aqui criaria as duas implementações que aquela task existe para
  impedir.
- `ponytail:` em `TransactionsState` — `monthStartDay` fixo em 1. `monthRange`
  já aceita o parâmetro (REQ-CORE-003); falta a tela de ajustes de onde lê-lo.

### T-015 — Contas ⇉
**Fase** F0 · **Depende de** T-009, T-011 · **REQ** REQ-ACC-005

CRUD, lista com saldo, extrato com saldo corrente acumulado linha a linha.

**Pronto quando**
- [x] Lista com o saldo de cada conta, vindo de `balanceOf` — o caso de uso puro
      da T-008 — e não de um `SUM` por conta em `@Query`. Verificado no aparelho
- [x] Criar e editar: nome, tipo, cor e saldo de abertura (REQ-ACC-001)
- [x] Campos de cartão só aparecem para `CREDIT_CARD` (REQ-ACC-002), conferido
      na S26: escolher "Cartão" faz surgirem limite, fechamento e vencimento
- [x] Dia de fechamento e vencimento saem de uma lista de 1 a 28, não de campo
      livre. Recusar depois o que a interface ofereceu é pior que não oferecer
- [x] **Arquivar, nunca excluir** (REQ-ACC-005): excluir levaria as transações
      junto por `CASCADE`, e o relatório do ano passado mudaria sozinho
- [x] Criar um cartão aqui faz o campo de parcelas aparecer na folha de
      lançamento — a ponta que a T-013 não tinha como exercitar
- [x] Extrato por conta com saldo corrente acumulado linha a linha — entregue
      pela T-014, que é quem define a linha de transação e seus filtros.
      Duplicar a lista aqui daria duas para reconciliar depois, então ele é a
      **mesma** lista com a conta filtrada: a coluna de saldo aparece quando há
      exatamente uma conta escolhida

**Um achado de uso**

`MoneyField` pedia foco sempre. Correto na folha de lançamento, onde ele é o
único e o Art. 18 exige o teclado aberto; errado neste formulário, que tem
**dois** — saldo de abertura e limite do cartão — e o último composto roubava o
cursor. Virou `autoFocus`, desligado por padrão.

### T-016 — Categorias ⇉
**Fase** F0 · **Depende de** T-009, T-011 · **REQ** REQ-CAT-005

**Pronto quando**
- [x] Exclusão de categoria com transações é bloqueada pelo banco, e a UI oferece
      recategorização em lote
- [x] A tela **pergunta antes de tentar**: conta as transações presas e já mostra
      para onde movê-las. Deixar o `RESTRICT` estourar e só então perguntar faria
      o usuário passar por um erro para chegar à opção que resolve
- [x] A mensagem é a da spec, com o número: "Mova as N transações antes". Um
      "não foi possível excluir" não diz o que fazer
- [x] O destino oferecido nunca mistura receita com despesa (REQ-CAT-003):
      mover despesa para categoria de receita produziria transação inválida
      assim que alguém a editasse
- [x] A movimentação é **uma** escrita, não N: mover uma a uma deixaria metade
      num limbo se o app morresse no meio, e a categoria antiga continuaria
      inexcluível por um resto
- [x] `CategoriesViewModelTest` cobre os quatro casos

### T-017 — Dashboard
**Fase** F0 · **Depende de** T-014, T-015 · **REQ** REQ-UI-004, REQ-UI-006

Existe hoje um `HomeScreen` **mínimo**, criado pela T-013 só para o fluxo de três
toques ter de onde partir: saldo total e o botão de lançar. Os blocos em ordem de
REQ-UI-004 são desta task. O saldo já vem de `totalBalance`, o caso de uso puro
da T-008 — não de um `SUM` em `@Query`.

Quatro dos seis blocos entram: saldo, cartões, comparativo e últimas transações.
**Orçamento e próximas contas não entram nem vazios** — a linha original desta
task dizia "blocos de cartão e orçamento entram vazios", e ela é anterior à
T-015: cartão hoje tem dado na F0, porque `AccountFormSheet` já cria conta
`CREDIT_CARD` com dia de fechamento e vencimento, e `cardDebt` já calcula a
dívida desde a T-008 sem nunca ter tido um chamador. Orçamento não tem nem DAO
(T-028), e nada cria `cleared = 0` antes da T-031 — os dois ficariam mudos para
sempre, que é o oposto de REQ-UI-006. A regra está registrada em REQ-UI-004
(Art. 3).

`comparativo do período` não estava definido em lugar nenhum da spec — Art. 5.
Resolvido com o dono do produto em 2026-08-31: receitas × despesas do período
**e** a variação do líquido contra o anterior. Registrado em REQ-UI-004.

**Pronto quando**
- [x] `comparativoDe` é caso de uso puro em `domain/usecase`, não conta no
      ViewModel nem `SUM` em `@Query` (Art. 9). O saldo continua vindo de
      `totalBalance` pela mesma razão
- [x] `PeriodSummaryTest` prova o invariante do Art. 7 — particionar por sinal
      não altera a soma —, que transferência vale zero dos dois lados e que
      previsto fica fora (REQ-TXN-006)
- [x] A partição é por **sinal do efeito**, não por `Txn.type`: `INCOME`
      negativo é estorno, e classificar por tipo infla os dois lados do bloco
- [x] A linha de transação virou `core/ui/component/TxnRow.kt` em vez de ser
      copiada — o dashboard é o segundo chamador, mesma régua que promoveu
      `Chips`. Duas cópias divergiriam, e a errada seria a que ninguém abre
- [x] Todo bloco vazio traz a ação que o preenche (REQ-UI-006): sem cartão,
      "Adicionar cartão"; sem lançamento nenhum, "Lançar"
- [x] Variação do mês chega **por palavra** além do sinal, e o valor fica em
      tinta neutra — cor não é sinal único (REQ-A11Y-003, REQ-DS-007)
- [x] FAB fora da rolagem: os três toques do Art. 18 não podem custar um quarto
      para trazer o botão de volta à vista
- [ ] Conferido no aparelho: 3 toques até salvar, fonte a 200% sem truncar,
      TalkBack percorrendo os quatro blocos

### T-018 — Segurança do app ⇉
**Fase** F0 · **Depende de** T-011 · **REQ** REQ-SEC-003, REQ-SEC-004, REQ-SEC-005, REQ-SEC-006

Biometria opcional, `allowBackup="false"`, `FLAG_SECURE`, expurgo de logs.

REQ-SEC-004 já estava fechado desde a T-001: `allowBackup="false"` e
`data_extraction_rules.xml` com `<cloud-backup />` e `<device-transfer />`
vazios já estavam no manifesto.

**Pronto quando**
- [x] ~~Regra de detekt~~ **guarda no `trace.py`** proíbe `Log` em `src/main`.
      Detekt não dá: o `ForbiddenImport` já está escopado em `'**/domain/**'`
      para a pureza do Art. 8, e detekt não aceita duas instâncias da mesma
      regra com escopos diferentes; `ForbiddenMethodCall` exigiria resolução de
      tipos, que `detekt.yml` já documenta como frágil aqui. A guarda foi para
      onde moram as dos Arts. 6 e 12, pelo mesmo motivo delas
- [x] Bane `Log` inteiro, e não "Log com valor monetário": detectar
      interpolação monetária exige semântica que uma varredura não tem, e um
      saldo entra tanto por `Log.d("saldo=$s")` quanto por uma variável de nome
      inocente três linhas acima. Nenhum arquivo de `main` usava `Log` — a
      guarda nasceu de graça
- [x] Guarda exercida de verdade: um `android.util.Log.d` temporário em
      `HomeScreen` faz o `trace.py` reprovar com 1 erro, e removê-lo devolve o
      verde. Sem esse ensaio, um gate verde não prova nada (mesma régua da
      T-019)
- [x] `FLAG_SECURE` **coletado**, não lido uma vez: quem liga o bloqueio espera
      que valha agora, e ler só no `onCreate` deixaria a miniatura dos recentes
      exibindo o saldo até o próximo start do processo
- [x] O bloqueio é um ramo do `when` de `FinanceNav`, antes do onboarding — não
      uma sobreposição. Como sobreposição, o dashboard estaria montado atrás e
      um instante de transição já o mostraria (REQ-SEC-003 diz "antes de exibir")
- [x] `MainActivity` virou `FragmentActivity`: o `BiometricPrompt` monta um
      fragmento para sobreviver a mudança de configuração no meio da autenticação
- [x] `USE_BIOMETRIC` declarada nominalmente, e o `ManifestTest` da T-019
      continua verde — ele proíbe `INTERNET` pelo nome justamente para esta caber
- [ ] Conferido no aparelho: prompt antes de qualquer valor, recentes em branco,
      e o **fallback de credencial** nas duas pontas do `minSdk` — API 26–29 usa
      `setDeviceCredentialAllowed`, API 30+ usa `setAllowedAuthenticators`

**Aberto de propósito.** O app tranca uma vez por processo, não a cada volta do
segundo plano. O relock em `onStop` precisa distinguir "o usuário saiu" de "a
tela de credencial do sistema subiu" — que é outra Activity, e sem essa
distinção o app entra em laço pedindo senha. Registrado como `ponytail:` em
`FinanceNav`, e fechável quando houver aparelho para exercer os dois caminhos.

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

Auditoria, mas não só: REQ-A11Y-006 é **código**, e a passada encontrou mais três
defeitos reais no que já estava entregue.

**Pronto quando**
- [x] TalkBack lê valores por extenso — `spokenBRL` em `core/money`, ligado na
      `contentDescription` do `MoneyText`. Uma correção, e todas as ~30 chamadas
      de valor do app passam a falar: é para isso que o KDoc do componente cobra
      que todo dinheiro passe por lá
- [x] Soletração escrita à mão porque não há de onde tirá-la: o
      `RuleBasedNumberFormat` do ICU faz exatamente isto e o Android **não o
      expõe** — `android.icu.text` publica `NumberFormat` sem o estilo
      `SPELLOUT`. A alternativa era arrastar o `com.ibm.icu:icu4j` inteiro por
      sessenta linhas. Tentado e descartado com o compilador na mão, não por
      suposição
- [x] `MoneySpokenTest` em JVM pura, 9 casos, incluindo os dois que só
      apareceram rodando: `R$ 0,01` dizia "zero reais e um centavo", e zero pede
      singular ("zero real"). REQ-A11Y-006 deixou de ser `manual` na spec no
      mesmo commit (Art. 3)
- [x] **Alvo de toque das abas** — 14dp de padding com `Label` de 13sp dava
      ~46dp, dois a menos que REQ-A11Y-002. O design.md §6.1 já mandava ampliar
      por `minimumInteractiveComponentSize()`; o código nunca o aplicou. É a
      classe de defeito que só uma passada dedicada encontra, porque a tela
      parece certa
- [x] **Seletor de cores** anunciava "Cor" seis vezes, e a seleção era só
      espessura de contorno — invisível para o leitor. Agora cada cor diz o nome
      (`StickerNames`) e expõe `selected` (REQ-A11Y-001, REQ-A11Y-003).
      `TokenLintTest` prova que nenhuma cor do tema fica sem nome, senão a
      próxima entraria só numa das duas declarações
- [x] **Pontos de cor** em Contas e Categorias ganharam contorno: sem ele um
      ponto Sunburst sobre papel branco dá 1.40:1 e some. A razão já estava
      escrita em design.md §6.3 para a linha de transação e não tinha sido
      aplicada nas outras duas telas
- [x] FAB pede `rotulo` obrigatório. O conteúdo dele é um `+`, e "sinal de
      adição" não diz o que o botão faz. Parâmetro e não `Modifier` opcional,
      para o próximo chamador não conseguir esquecer
- [x] Conferido no aparelho (Galaxy S25, Android 16, 450dpi). A leitura por
      extenso saiu da árvore de acessibilidade real, não de suposição:
      `R$ 481,50` → "quatrocentos e oitenta e um reais e cinquenta centavos",
      `−R$ 30,00` → "menos trinta reais", `R$ 0,00` → "zero real", `−R$ 18,50` →
      "menos dezoito reais e cinquenta centavos", FAB → "Novo lançamento"
- [x] Alvos de toque medidos em dp a partir das `bounds` do `uiautomator`, não
      lidos do código: abas 82×48, FAB 56×56, `◀`/`▶` 58×48, "Filtros" 62×48,
      linhas do Mais 352×48, stickers 64×83. Nenhum abaixo de 48
- [x] Três toques do Art. 18 exercidos: o FAB abre a folha com o campo de valor
      **já em foco**, e categorias e "Salvar" ficam visíveis sem rolar
- [x] **A fonte a 200% derrubou três telas**, e nenhuma tinha aparecido em
      revisão de código:

      1. **Cabeçalho do dia** em Transações — o total do dia quebrava em quatro
         linhas: `−R` / `$` / `18,` / `50`. É REQ-A11Y-004 literal, "sem truncar
         valores". Num `Row`, os filhos **sem** `weight` são medidos primeiro e
         ficam com a largura que quiserem; a data engolia a linha inteira e
         sobrava uma coluna de um caractere para o dinheiro. `weight` foi para a
         data, que é quem pode ceder
      2. **Lista de contas e de categorias** — o nome descia letra por letra
         ("( D i n h e i r o"), pela mesma razão elevada ao cubo: valor e botão,
         os dois sem peso, não cabiam ao lado do nome. Viraram `FlowRow`, e o
         que não couber cai para a linha de baixo. Na escala normal o desenho é
         idêntico ao de antes — conferido nas duas escalas
      3. **Abas** saíam como "Trans" e "Orça", cortadas sem reticência, o que lê
         como outra palavra em vez de texto truncado. Agora duas linhas com
         reticência: "Transações" cabe inteira, "Orçamento" fica visivelmente
         cortado. Quatro abas em 360dp com a fonte dobrada não cabem por
         extenso, e é o que a própria `NavigationBar` do Material faz — o leitor
         de tela continua recebendo o rótulo inteiro
- [x] Nenhum **valor** truncado em nenhuma tela, nas duas escalas. O que trunca
      é descrição, nome de conta e de categoria, que é o desenho de design.md
      §6.3 — e o nome completo chega ao leitor pela `contentDescription`
- [ ] TalkBack ligado de verdade, ouvindo a navegação. A árvore de
      acessibilidade prova o que o leitor **recebe**; falta ouvir a ordem em que
      ele percorre a tela, que é a parte que nenhum dump mostra

**O que a passada no aparelho ensinou.** As três quebras de 200% estavam em
código já entregue e revisado, e nenhuma delas é visível lendo o arquivo: é
preciso medir. A regra que sai daqui, e que vale para toda tela nova: num `Row`
com valor monetário, o **rótulo** leva o `weight`, nunca o valor. Se houver um
terceiro filho — botão, ícone, o que for —, o `Row` não serve, é `FlowRow`.

`maxLines = 1` fica onde está — descrição de transação, nome de conta e de
categoria. É o desenho de design.md §6.3, e o que REQ-A11Y-004 proíbe truncar é
**valor**, que não tem `maxLines` em lugar nenhum. O nome completo chega ao
leitor pela `contentDescription` do sticker.

### T-021 — Rastreabilidade no CI ⇉
**Fase** F0 · **Depende de** T-001 · **REQ** —

`tools/trace.py` no pipeline, e anotação `@Req` disponível para os testes.

**Pronto quando**
- [x] Anotação `@Req` escrita em `core/testing/Req.kt`, `@Retention(SOURCE)`
- [x] Workflow em `.github/workflows/ci.yml`: rastreabilidade roda **antes** do
      build e sem Gradle — se a spec quebrou, não vale compilar
- [x] Roda em segundos, sem dependência externa (Art. 4)
- [x] `@Req` é `vararg`, e a varredura lia **só o primeiro id** de cada
      anotação. A rastreabilidade mentia na direção perigosa: reprovava
      requisito que estava coberto, e fazia o gate parecer mais longe do que
      estava. Corrigido — 19 requisitos rastreados viraram 40, sem uma linha de
      teste nova
- [x] Workflow exercido de verdade — verde na primeira execução, em
      `dev-isaacportela/mobile-finance` (privado)
- [ ] Ao fechar a F0, trocar `trace.py` por `trace.py --phase F0` no workflow.
      Hoje `--phase F0` falha com **1** erro, e ele é legítimo: `REQ-DATA-001`
      espera um `MigrationTest` que só faz sentido quando existir um schema v2.
      O segundo erro era `REQ-CAT-005`, e a T-016 o fechou — esta linha ficou
      desatualizada entre os dois commits, que é o que o Art. 3 não quer

### T-050 — Editar transação
**Fase** F0 · **Depende de** T-013, T-014 · **REQ** REQ-TXN-001, REQ-TXN-003, REQ-TXN-005

REQ-TXN-001 exige "criar, editar, excluir e listar". A F0 entregou três: a T-013
cria, a T-014 lista e exclui. **Editar não tinha dono** — a T-014 registrou o
buraco em vez de tapá-lo por conta própria (Art. 1), e esta task é o dono que
faltava. A F0 não fecha sem ela: é um `MUST` pela metade.

Número alto para uma task de F0 de propósito. Os IDs são identificadores, não
ordem, e renumerar a F0 inteira quebraria a referência de todo commit já feito.

Reabre a `QuickEntrySheet` com a transação carregada, em vez de uma segunda
folha: os campos são os mesmos, e duas folhas divergiriam no primeiro campo
novo. `validateTxn` e `sanitize` da T-007 já valem para os dois caminhos.

Fora do escopo: escolher entre "esta parcela" e "todas as parcelas" é da T-027,
que é dona do escopo de parcela. Aqui, transação parcelada abre para leitura com
o aviso de que a edição de parcela chega na F1 — melhor que editar uma parcela e
deixar as outras onze inconsistentes.

**Pronto quando**
- [x] Toque na linha da lista abre a folha preenchida, e salvar **atualiza** a
      transação em vez de criar outra — com teste, porque um `insert` no lugar
      de um `update` duplica dinheiro na tela e é invisível na revisão. Vale
      também no dashboard: a mesma `LinhaDeTransacao`, o mesmo toque
- [x] Editar não perde o que o domínio não carrega: `notes`, `dedupeKey`,
      `importBatchId`, `recurringRuleId` e `createdAt` sobrevivem ao ciclo. É o
      mesmo cuidado que o desfazer da T-014 já tomou guardando a entidade, e
      pela mesma razão — uma `dedupeKey` perdida faz a importação da F2 recriar
      a transação como nova.

      A guarda ficou em `TxnRepository.salvar`, e não em quem edita: com `id`
      diferente de zero ele lê a linha antes de sobrescrevê-la. É por ali que
      passa todo chamador, inclusive os que ainda não existem (T-025, T-041)
- [x] `updatedAt` muda, `createdAt` não
- [x] Transação com `installmentGroupId` abre somente leitura, com o motivo na
      tela (T-027 destrava)

Dois achados que a DoD não previa e a implementação obrigou a resolver:

- **A data.** A folha não tem campo de data e sempre gravou em `LocalDate.now()`.
  Reusá-la sem mudança faria "corrigir a descrição" mudar o dia — e o mês em que
  o lançamento entra no relatório. `salvar` preserva a data do original. Campo de
  data continua fora de escopo: entra quando houver requisito que o peça
- **A folha reaberta.** O `ViewModel` é o da Activity e sobrevive à folha (o
  mesmo motivo do `concluido` da T-013). Uma edição dispensada sem salvar
  deixaria `original` ligado, e o próximo "+" abriria preenchido — gravando por
  cima da transação editada em vez de criar outra. A limpeza ficou na
  **abertura** com `txnId` nulo, e não ao dispensar, porque a carga é assíncrona
  e dispensar antes dela terminar sujaria o estado depois da limpeza

Conferido no emulador (Medium_Phone_API_36.1, API 36), e não só nos testes:
editar da lista e do dashboard deixa **uma** linha com o valor novo; com o
relógio do aparelho em 5 de setembro, editar um lançamento de 1º de setembro o
manteve em 1º; a parcela abre com "Parcela 1 de 3", o motivo por extenso e um
"Fechar" no lugar do "Salvar"; a folha de edição e o bloco da parcela sobrevivem
à fonte a 200% sem cortar texto (REQ-A11Y-004); e dispensar uma edição sem salvar
devolve o "+" limpo.

Não conferido: a **frase** que o TalkBack fala ao focar a linha. O
`onClickLabel` e a ação personalizada "Excluir" não aparecem no
`uiautomator dump` nem no `dumpsys accessibility`, e ler a fala exigiria
instrumentação que a F0 não tem. O que dá para afirmar é que com o TalkBack
ligado a linha continua ativável e abre a folha.

---

# F1 — Cartão, orçamento, recorrência

### T-022 — Configuração de cartão
**Fase** F1 · **Depende de** T-015 · **REQ** REQ-CARD-001, REQ-CARD-002

A T-015 deixou a validação de conta escrita dentro do `AccountsViewModel`, com o
comentário dizendo que a faixa de dia era desta task e que duplicar metade da
regra no domínio criaria a segunda fonte de verdade a reconciliar. Esta é a
reconciliação: `validateAccount` vira função pura em `domain/usecase`, a tela
chama, e a régua passa a valer também para o dia que chegar por fora da tela —
importação (F2) e restauração de backup (T-035).

**Pronto quando**
- [x] `ValidateAccountTest` recusa dia 0, 29, 31 e negativo, nos **dois** campos,
      com a mensagem que REQ-CARD-002 soletra. As bordas 1 e 28 passam: um
      `until` no lugar do `..` recusaria o dia 28, que é o mais comum em fatura
- [x] Campos de cartão só aparecem para `CREDIT_CARD`, e os chips de dia saem de
      `DIAS_DE_FATURA` em vez de uma lista própria da tela — a interface não
      pode oferecer o dia que a regra recusa depois
- [x] REQ-CARD-001 já tinha o teste de persistência das quatro colunas no
      `AccountDaoTest` desde a T-004; faltava o `@Req`, e a rastreabilidade não
      via o que o código cobria

`paymentAccountId` continua **sem tela**. A coluna existe e é gravada, que é o
que REQ-CARD-001 pede, mas escolher a conta que quita a fatura só tem sentido
onde a fatura é paga: a T-025. Um campo aqui, sem consumidor, seria configuração
que ninguém sabe para que serve — o mesmo motivo pelo qual esta task existiu em
vez de a T-015 adivinhar a regra de cartão.

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

Composição, status derivado e limite disponível. Tudo em `CardInvoice.kt`, ao
lado da competência e do vencimento que a T-023 deixou: é um conceito só, e
separá-lo em arquivos obrigaria a abrir dois para entender uma fatura.

**Pronto quando**
- [x] Fatura exclui `TRANSFER` — teste prova que pagamento não entra na fatura.
      É o item que mais custa quando falta: pagamento é uma transferência **para**
      o cartão, e contá-lo como item faria o pagamento aumentar a conta que quita
- [x] `InvoiceStatusTest` cobre as 3 condições de status, com a borda de
      `hoje == fechamento` do lado de `Aberta` — no dia 10 ainda se compra para
      aquela fatura
- [x] Nada de status persistido em coluna. `Invoice` nasce de `invoiceFor` a cada
      leitura e morre com a tela; nem o total, nem o status, nem a lista de itens
      existem em `TxnEntity` ou `AccountEntity`

Uma decisão que a DoD não previa. REQ-CARD-007 diz "pagamentos **desde** o
fechamento", e "desde" sem limite superior faria toda fatura antiga virar `Paga`
com o tempo: o pagamento de março abateria janeiro, fevereiro e março ao mesmo
tempo. Um pagamento agora pertence à última fatura que fechou antes dele —
`paymentInvoiceMonthFor`, o espelho de `invoiceMonthFor`, com teste dos dois
lados da janela.

Limite disponível é `limite + saldo`, e não `limite − total do mês`: a dívida é
tudo que está lançado, inclusive as parcelas de dezembro compradas hoje. Como o
saldo do cartão sai de `balanceOf` sem exceção (REQ-CARD-009), o pagamento
devolve limite pelo mesmo termo do ADR-003 que já move o saldo — sem uma linha
de código de cartão.

### T-025 — Tela de cartão
**Fase** F1 · **Depende de** T-024 · **REQ** REQ-CARD-006

Seletor de mês, itens por categoria, botão pagar fatura. Chega-se a ela pelo
bloco "Cartões" do dashboard, um botão por cartão — é o único destino com
argumento, e mesmo ele leva o id num `data class` serializável em vez de string
interpolada.

**Pronto quando**
- [x] Pagar fatura gera uma `TRANSFER` de `paymentAccountId` para o cartão, na
      data de vencimento. A transferência sai de `cardPaymentFor`, no domínio:
      quem paga o quê, em que direção e em que data é regra (Art. 9)
- [x] Valor editável (pagamento parcial), conferido no aparelho pagando R$ 100
      de R$ 300 e depois o resto
- [x] `InvoicePaymentTest`: após pagamento integral a dívida zera, medida por
      `balanceOf` e por `availableLimitFor` — as duas funções que um caso
      especial de cartão teria de contaminar para o teste ainda passar
- [x] `paymentAccountId` ganhou tela, como a T-022 prometeu: chips no formulário
      de conta, só para cartão, sem outros cartões nem a própria conta em edição

Duas correções que só o aparelho mostrou.

- **A folha pré-preenchia com o total.** REQ-CARD-006 dizia "o total da fatura",
  e ao pé da letra: quem pagasse R$ 100 de R$ 300 e voltasse para quitar
  encontraria R$ 300 no campo, e dois toques pagariam R$ 400 numa fatura de
  R$ 300. Passou a oferecer o que **falta**, e a spec foi corrigida no mesmo
  commit (Art. 3). Sem pagamento nenhum as duas leituras dão o mesmo número — a
  diferença só aparece onde a primeira erra.
- **A linha da fatura mentia.** Reusar `LinhaDeTransacao` imprimia "Sem
  categoria" **dentro** do grupo "Compras"; passar a categoria consertava a
  mentira e imprimia a mesma palavra três vezes na mesma linha. O componente não
  serve aqui: a linha de fatura quer data e parcela, que é o que o extrato do
  banco mostra. Ficou uma linha própria, de doze linhas.

Uma decisão de leitura: a fatura ainda **Aberta** pode já estar quitada, e o
botão "Pagar fatura" some pelo que falta, não pelo status — pelo status, ele
abriria a folha em R$ 0,00.

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

Destrava a T-050: era ela quem abria parcela somente leitura, por não haver
quem perguntasse o escopo.

**Pronto quando**
- [x] Campo de parcelas só para `CREDIT_CARD`, e some ao **editar** — parcelar é
      da criação. A lista de chips passou de sete "comuns" para a faixa inteira,
      e sai do mesmo `INSTALLMENT_RANGE` que `splitInstallments` valida: a tela
      não pode oferecer um número que a divisão recusa
- [x] Editar/excluir parcela pergunta escopo, e `InstallmentScopeTest` prova que
      só o escopo escolhido muda. O teste vai até o **banco**: a regra de escopo
      é fácil de acertar isolada e fácil de perder entre o ViewModel que escolhe
      os alvos e o repositório que grava, e "escolhi só esta e mudaram as três"
      só aparece depois da escrita

O que **não** atravessa na propagação é o que é de cada parcela: `id`, `date` e a
posição no grupo. Espalhar a data colapsaria as doze no mesmo dia — o oposto do
espaçamento de REQ-TXN-007, e um jeito silencioso de destruir uma compra
parcelada inteira. Tem teste próprio.

O desfazer virou lista. Excluir com escopo "todas" apaga doze linhas de uma vez,
e o desfazer que repunha uma só seria pior que nenhum — o `ponytail:` da T-014
previa a pilha para a T-042, e foi esta task que precisou primeiro.

Dois achados fora do texto da task.

- **O deslize travava a linha.** A caixa confirmava a dispensa e guardava
  "dispensada" no `remember` da linha; um "Desfazer" tocado um segundo depois,
  antes de o Room emitir a lista sem ela, devolvia a transação para uma linha que
  continuava desenhada como o fundo de "Excluir", travada assim até outra
  recomposição. Agora o gesto **nunca** confirma: ele pede a exclusão, e quem
  tira a linha da tela é o banco. Serve também à parcela, que pergunta antes e
  pode nem excluir. Encontrado no aparelho.
- **`TooManyFunctions` subiu de 11 para 14** em `config/detekt/detekt.yml`. O
  padrão mede largura, não complexidade: um ViewModel de formulário é uma fileira
  de setters de uma linha e um DAO é uma fileira de queries, e os dois passam de
  11 sem nenhuma função difícil de ler. Quebrá-los em duas classes para caber no
  número faria o código pior para agradar a régua errada. `LongMethod` e
  `CyclomaticComplexMethod` ficam nos valores padrão, e são eles que pegam função
  complicada de verdade.

### T-028 — Orçamento: dados e cálculo ⇉
**Fase** F1 · **Depende de** T-003, T-004 · **REQ** REQ-BUD-001, REQ-BUD-002, REQ-BUD-004

As entidades existem desde a T-004; o que faltava era o DAO, o repositório e a
regra. Nenhuma tela — a T-029 é dona dela.

**Pronto quando**
- [x] `BudgetProgressTest` prova que despesa em subcategoria conta no teto da
      mãe. Sem isso o teto vira decoração: bastaria lançar tudo numa filha para
      nunca estourar
- [x] Transferência nunca entra no consumo. O filtro é por `type`, e não pela
      ausência de categoria: uma linha vinda de importação com categoria
      preenchida ainda seria transferência, e mover dinheiro entre bolsos não é
      gasto
- [x] Período respeita `monthStartDay`. `budgetProgress` recebe a `MonthRange`
      pronta em vez de mês + dia de virada, e tira o mês de referência do início
      dela — um segundo parâmetro seria a chance de a tela passar um mês que não
      é o do período que ela mesma montou

`BudgetDaoTest` cobre REQ-BUD-001 de escrita, e não só de índice: `@Upsert`
sozinho não garante "no máximo um por par", porque em violação do índice único
ele cai para um `UPDATE` pela chave primária que numa linha nova não casa com
nada — a mesma armadilha já documentada em `TxnDao.insert`. Quem grava lê antes
e reusa o id.

Duas decisões que a DoD não previa.

- **Teto zero ou negativo é recusado na escrita.** "Não gaste nada nesta
  categoria" não é um teto, é a ausência dele, e é `remover` que diz isso. A
  regra também mantém `percent` livre de divisão por zero, sem inventar um
  percentual para um caso que não deveria existir.
- **`diasRestantes` fica preso à faixa do período.** A tela navega meses: olhando
  agosto em dezembro, "restantes" seria negativo e a divisão de REQ-BUD-004
  quebraria; olhando em janeiro, passaria do tamanho do próprio período.

### T-029 — Tela de orçamento
**Fase** F1 · **Depende de** T-028 · **REQ** REQ-BUD-003, REQ-BUD-005

Tira o último placeholder do `NavHost`: a aba Orçamento era a única que ainda
mostrava só o próprio nome.

**Pronto quando**
- [x] Âmbar em 80%, vermelho em 100%, **com ícone** além da cor — e com a
      **palavra** junto do ícone. Um triângulo sozinho é cor com outra forma, e
      quem usa leitor de tela ouviria "triângulo apontando para cima": a linha
      diz "⚠ Atenção" e "▲ Estourou R$ 3,00", e a linha inteira vira uma frase só
      para o leitor (REQ-A11Y-001, REQ-A11Y-003)
- [x] Copiar tetos do mês anterior em uma ação, e o botão só aparece quando há o
      que copiar

A barra enche **só a partir dos 80%**; dentro do teto é contorno vazio, como
design.md pede. Uma barra que enche desde o primeiro real gasto transforma
"gastei um pouco" em alerta visual, e alerta que fica sempre ligado deixa de ser
alerta.

Um defeito que só a tela mostrou: REQ-BUD-003 pede barra com gasto, **limite** e
percentual, e a primeira versão exibia gasto e percentual — o limite existia só
na descrição falada. "82%" sem o número obriga a fazer a conta de cabeça para
saber de quanto. A linha passou a dizer "R$ 33,00 de R$ 40,00".

Copiar **não sobrescreve** o que o mês de destino já tem: "copiar todos" que
apaga um teto ajustado à mão é uma ação destrutiva escondida atrás de um botão de
conveniência. Quem já decidiu que este mês tem outro teto disse mais do que o mês
passado.

Valor zerado na folha é tratado como remoção, e não recusado: o repositório já
diz que teto zero não é teto, e mandar procurar outro botão para dizer a mesma
coisa transformaria a regra numa pegadinha.

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

Gatilho na abertura do app. O worker diário ficou de fora — ver abaixo.

**Pronto quando**
- [x] `GenerateRecurringTest` roda a geração 3× no mesmo dia e prova conjunto idêntico ao da 1ª.
      Compara as **linhas**, não a contagem: uma geração que apagasse e recriasse
      tudo daria o mesmo número e quebraria toda referência a `txn.id`
- [x] Nada gerado além de hoje + 60 dias, em regra sem `endDate` — 61 linhas
      numa diária, contando os dois extremos
- [x] Alterar a regra não toca em ocorrência `cleared = 1`. Nem no passado
      **não** efetivado: o requisito diz "futuras com `cleared = 0`", e apagar
      uma conta vencida e não paga seria o app decidir que ela nunca existiu
- [x] `autoPost` decide o `cleared` da linha nascida (REQ-REC-005)
- [x] `RecurringDaoTest` fecha o teste que REQ-REC-001 nomeia: a regra vai e
      volta sem perder campo. São duas traduções em arquivos diferentes —
      `Mappers.kt` e `Repositories.kt` — e um `weekday` gravado como ordinal e
      lido como ISO deslocaria toda conta semanal em um dia, sem erro nenhum

**O worker diário não entrou**, e o [ADR-006](decisoes.md#adr-006--recorrência-materializa-sob-demanda-com-horizonte-de-60-dias)
foi corrigido junto com a spec, no mesmo commit (Art. 3). Nada no app lê o
resultado da geração com ele fechado: não há lembrete de vencimento, não há
widget, e a rede está barrada até a F4. O worker gravaria linhas que ninguém vê
antes da próxima abertura — que é quando o gatilho que entrou já roda. Fica um
`ponytail:` na `MainActivity` dizendo o que o destrava.

Um defeito que só o teste pegou: **`@Upsert` devolve `-1` no caminho de
`UPDATE`**. A primeira versão usava esse retorno como id da regra na hora de
limpar as ocorrências futuras, e limpava as da regra `-1` — que não existe. A
alteração parecia ter funcionado, com as ocorrências antigas intactas e o valor
novo em lugar nenhum. É a terceira aparição da mesma armadilha (`TxnDao.insert`,
`BudgetRepository.definir`), e por isso a gravação da regra virou uma
`@Transaction` no DAO, onde o id vem da entidade e não do retorno.

A geração inteira de uma regra é **uma** escrita: as linhas novas e o
`lastGeneratedDate` que as registra entram juntos. Separados, um processo morto
no meio deixaria linhas gravadas com a marca antiga — e elas seriam geradas de
novo na abertura seguinte, que é o defeito que REQ-REC-003 existe para fechar.
Uma transação por regra, não uma para todas: uma regra defeituosa não pode
desfazer a geração das outras.

### T-032 — Tela de recorrências
**Fase** F1 · **Depende de** T-031 · **REQ** REQ-REC-001, REQ-REC-002, REQ-REC-005, REQ-REC-008

Lista de regras, próxima ocorrência, e bloco "Próximas contas" no dashboard com
ação de efetivar.

**Pronto quando**
- [x] Cadastro com os oito campos que REQ-REC-001 nomeia, mais o `interval` de
      REQ-REC-002 e o `autoPost` de REQ-REC-005. Sem a tela, a T-031 gerava a
      partir de uma tabela que nada preenchia: o gerador estava pronto e a
      recorrência era inalcançável
- [x] A lista diz **quando cai de novo**, e é outra pergunta que a geração:
      `pendingOccurrences` pula tudo até `lastGeneratedDate` — a ocorrência já
      materializada continua sendo a próxima até acontecer. `nextOccurrence`
      responde a essa, e regra pausada ou encerrada não anuncia data nenhuma
- [x] Bloco "Próximas contas" no dashboard, na posição que REQ-UI-004 manda, com
      "Efetivar" por linha. Aparece **só quando há alguma**: com a fonte de dados
      existindo, um bloco vazio todo dia para quem não tem conta a vencer é o
      ruído que REQ-UI-006 recusa
- [x] Efetivar mexe **só** no `cleared`. Passa pelo `salvar` de sempre, que é o
      que preserva `dedupeKey`, `recurringRuleId` e o `createdAt` original
- [x] `ProximasContasTest` trava a janela nos dois extremos, o filtro de
      efetivadas e a ordem

A validação da regra é a **da transação**: `validateTxn` aplicado à ocorrência
que ela produziria no dia de início. Uma regra que gera doze lançamentos
inválidos é uma regra inválida, e um segundo validador aqui divergiria do
primeiro na primeira mudança de mensagem.

O calendário é o `DatePicker` do Material, não um seletor próprio: ele já vem no
`material3` que o app usa, já herda papel e tinta do `colorScheme`, e já traz
teclado, leitor de tela e localização. A conversão é `epochDay × 86.400.000` nos
dois sentidos — o seletor devolve meia-noite em **UTC**, e converter por
`Instant` local deslocaria a data em um dia para o Brasil inteiro.

Excluir a regra **não** leva as ocorrências junto (`recurringRuleId` é
`SET_NULL`), e o rótulo do botão diz isso. Quem quer só parar de gerar tem
"Pausar" antes, sem tocar em nada.

Conferido no emulador: a regra "Assinaturas" mensal entrou pela folha, a lista
mostrou "Todo mês · próxima 1 de setembro de 2026", o bloco apareceu no
dashboard entre "Este mês" e "Últimas transações" com `contentDescription`
"Efetivar Assinaturas", e efetivar levou o saldo de R$ 1.167,00 para R$ 666,92 —
a prevista não contava no saldo (REQ-TXN-006) e passou a contar. Três reinícios
do app depois, o saldo não se moveu: a idempotência da T-031 vale no aparelho,
não só no teste.

**O calendário fala a língua do aparelho, não a do app.** No emulador em inglês
ele saiu "Select date / September 2026", enquanto o resto da tela estava em
português — as datas do app são formatadas com `Locale.forLanguageTag("pt-BR")`
explícito, e o componente do Material segue o sistema. Num aparelho brasileiro,
que é o público, ele aparece em português. Não é defeito desta task e sim a
ausência de estratégia de idioma no app inteiro: não há `values-pt-BR`, nem
`localeConfig`, e todo texto é português cravado no código. Fica registrado
aqui; forçar o idioma é decisão de app, não de tela.

**Uma pergunta para o dono do produto, não resolvida por suposição** (Art. 5):
REQ-REC-008 diz "dos próximos 7 dias", e a janela foi implementada literalmente —
começa **em hoje**. Conta vencida e não paga fica de fora do bloco, embora
continue na lista de transações pelo filtro de previstas. Se o esperado for
"vencidas primeiro, depois a semana", é uma linha em `proximasContas` e uma
frase em REQ-REC-008 — mas é mudança de requisito, e começa na spec.

### T-033 — Relatórios ⇉
**Fase** F1 · **Depende de** T-017 · **REQ** REQ-RPT-001, REQ-RPT-002, REQ-RPT-003, REQ-RPT-004

**Pronto quando**
- [x] Transferências excluídas de todos os três relatórios — e não por um
      `filter` repetido em cada um: o corte é `efeitoGlobal(txn) < 0`, e
      transferência vale **zero** ali por construção (ADR-003). Filtrar por
      `type != TRANSFER` daria o mesmo hoje e erraria no dia em que aparecesse
      outro tipo com contrapartida
- [x] Toque na fatia navega para a lista já filtrada por categoria **e** mês
- [x] `ReportTest` prova, além disso, que a evolução concorda com o comparativo
      do dashboard: dois números para "quanto entrou em março" divergiriam, e o
      errado seria o que ninguém confere

**Nenhuma dependência de gráficos.** A pizza é um `drawArc` por fatia e a
evolução são dois `Path` — juntos, menos de sessenta linhas de `Canvas`. A Vico,
que o catálogo já nomeava para esta task, saiu de `libs.versions.toml`: ela traz
tema próprio para brigar com o da casa (REQ-DS-004 proíbe superfície tonal) e
**não desenha pizza** — seria dependência nova
para metade do trabalho. Pino de versão sem uso apodrece; por isso foi removido
no mesmo commit, e não deixado "para o caso de".

As fatias usam as cores **das categorias**, que já existem desde o grid do
lançamento rápido. Inventar uma paleta de gráfico daria à mesma categoria duas
cores no mesmo app. Nada depende só delas: a legenda traz nome, valor e
percentual escritos (REQ-A11Y-003).

A fatia é tocável **e** a linha da legenda também. A fatia porque é o que
REQ-RPT-004 diz; a legenda porque uma cunha fina não tem 48dp em canto nenhum
(REQ-A11Y-002) e o leitor de tela não alcança um pedaço de `Canvas` — sem ela o
detalhamento seria inacessível justamente para quem mais precisa dele.

**Um defeito que só o aparelho mostrou.** A primeira versão navegava para a
lista com um `navigate` seco, e `Transacoes` **é uma aba**: o destino ficava
empilhado dentro do "Mais", e tocar em "Mais" depois disso restaurava a lista de
transações — a barra apontando um lugar e a tela mostrando outro. A navegação da
fatia passou a usar as mesmas regras da barra inferior (`popUpTo(Inicio)` com
`saveState`), e **sem** `restoreState`: o que se quer é a lista com este filtro,
não a que ficou salva de antes.

Outro que só a tela mostrou: os percentuais da legenda somavam 99%. Truncar
93,8% dá 93, e o erro é sistemático — toda fatia sai para baixo. Passou a
arredondar, em inteiro, sem `Double` no caminho (Art. 6).

Conferido no emulador: pizza, legenda com nome, valor e percentual, doze meses e
as maiores despesas. O toque funciona nos dois alvos — na cunha da pizza e na
linha da legenda —, e os dois abrem a lista já em "Setembro de 2026" com
"Filtros •" ligado e só a categoria escolhida.

A rota `Transacoes` deixou de ser `data object` e passou a levar dois
argumentos **neutros**, não nulos: a rota type-safe do Navigation resolve `Long?`
para um `NavType` que não existe, e o erro só apareceria ao navegar. Zero é "sem
filtro" e vazio é "mês corrente"; a aba passa `Transacoes()` e não sabe que eles
existem. O filtro entra no estado **inicial** do ViewModel, não num efeito depois
da primeira emissão — aplicá-lo depois mostraria o mês inteiro de despesas por um
quadro, que é exatamente o que o usuário não pediu para ver.

### T-034 — Exportação ⇉
**Fase** F1 · **Depende de** T-009 · **REQ** REQ-BAK-001

CSV de transações e JSON da base, via `ACTION_CREATE_DOCUMENT`.

**Pronto quando**
- [x] Round-trip: `paraJson` seguido de `deJson` devolve a base **igual campo a
      campo**, inclusive `notes`, `dedupeKey`, `importBatchId` e `createdAt`.
      A volta que **escreve no banco** é da T-035; o que esta task garante, e
      que é o pré-requisito dela, é que o arquivo não perde coluna no caminho
- [x] CSV abre no Excel em pt-BR sem quebrar acento nem decimal: BOM, `;` como
      separador, vírgula decimal sem ponto de milhar, CRLF
- [x] `ExportTest` cobre também a descrição com `;` e com aspas, que sem escape
      viram colunas a mais

**Serializa entidade, não modelo de domínio.** `notes`, `dedupeKey`,
`importBatchId`, `createdAt` e `lastGeneratedDate` não sobem para o domínio
(Art. 8), e um backup que os perde não é backup — a restauração recriaria
transações que a importação já conhecia, e o dedupe da F2 as trataria como
novas. Por isso o código mora em `data/export/` e não em `domain/`: o formato do
arquivo **é** o formato das tabelas, de propósito.

Cinco tabelas, não sete: `import_batch` e `payee_rule` existem no schema mas nada
as escreve antes da F2, e nem DAO elas têm. O `schema` gravado no JSON é o que
permitirá à restauração recusar um arquivo que ela não sabe ler, em vez de
escrever lixo por cima do histórico.

Três detalhes do CSV, e cada um sozinho estraga o arquivo: sem **BOM** o Excel lê
como ANSI e "Alimentação" vira "AlimentaÃ§Ã£o"; com `,` como separador ele joga a
linha inteira numa célula; e `formatBRL` produz `−R$ 1.234,56`, com o menos
tipográfico (U+2212) e ponto de milhar — bonito na tela, não numérico em planilha
nenhuma. O valor do CSV sai de aritmética inteira, e `/data/export/` entrou em
`MONEY_PATHS` do `trace.py` no mesmo commit: um `Double` entre o centavo e o
arquivo corromperia o backup em silêncio, e o backup é a cópia que sobra quando o
aparelho se perde.

Conferido no emulador, com o seletor do sistema de verdade: o CSV saiu com
`EF BB BF` nos três primeiros bytes, `Data;Descrição;Categoria;…` no cabeçalho,
`-33,00` no valor e `01/10/2026;…;Não` na prevista de outubro; o JSON saiu com
`"schema": 1`, os enums por nome e os nulos preservados. A tela disse "Pronto: 5
transações no arquivo" e "Pronto: 21 registros no arquivo".

`ACTION_CREATE_DOCUMENT` e não uma pasta do app: quem escolhe o destino é o
seletor do sistema, então o app não pede permissão de armazenamento, não inventa
caminho e não deixa cópia do histórico financeiro onde o usuário não sabe. A
falha vira **frase na tela**, não exceção: escrita em `Uri` de outro app falha
por motivos que não são bug nosso — cartão removido, nuvem sem espaço, permissão
revogada entre a escolha e a gravação — e quem exporta precisa saber que **não**
foi salvo.

### T-035 — Backup e restauração
**Fase** F1 · **Depende de** T-034 · **REQ** REQ-BAK-002, REQ-BAK-003, REQ-BAK-004

**Pronto quando**
- [x] AES-256-GCM com chave de PBKDF2-HMAC-SHA256, 600 mil iterações, sal e IV
      sorteados por arquivo; o aviso de que senha perdida é backup perdido fica
      **junto do campo**, não escondido num diálogo depois
- [x] Restaurar informa quantos registros vêm **e** quantos vão embora, antes de
      qualquer escrita
- [x] Apagar tudo exige digitar `APAGAR` e aponta as duas ações que salvam antes
      — exportar e fazer backup — que estão na mesma tela, acima
- [x] `BackupTest` cobre senha errada, byte trocado no corpo, byte trocado no
      **cabeçalho**, arquivo que não é backup, dois backups da mesma base saindo
      diferentes, e a descrição não aparecendo em claro no arquivo

**GCM, não CBC.** É o modo autenticado que transforma "senha errada" em erro
detectado, em vez de bytes aleatórios que o parser de JSON tentaria ler e
reportaria como erro de sintaxe. O cabeçalho vai em claro porque precisa — sem
sal e IV não há como derivar nem decifrar — e entra como AAD, então adulterá-lo
falha a autenticação em vez de virar "senha incorreta", que seria a frase errada
para um arquivo corrompido.

**600 mil iterações não é exagero, é o ponto.** O arquivo vai para o Drive, para
o cartão, para o e-mail que a pessoa manda para si mesma: lugares onde o ataque
de dicionário roda offline, sem limite de tentativas. O custo é de dois a cinco
segundos, uma vez, e é a única coisa entre uma senha fraca e dez anos de
histórico. A tela avisa que a espera é de propósito.

A senha é pedida **duas vezes** porque o próprio requisito diz que perdê-la torna
o backup irrecuperável: um erro de digitação num campo só produziria um arquivo
que nem o dono abre, e o defeito só apareceria no dia em que ele fosse
necessário. E há um piso de 8 caracteres — com dicionário offline, quatro
caracteres caem em minutos mesmo com PBKDF2.

**Restaurar são três passos, e isso é o requisito.** Escolher o arquivo, digitar
a senha e ler; só então aparecem os números e o botão que substitui. Um
"restaurar" de um toque teria de escolher entre perguntar sem saber o que vem, ou
apagar para depois descobrir que a senha estava errada. `ler` não toca no banco.

**Apagar tudo repõe as categorias.** A semente roda em `onCreate`, e o arquivo do
banco continua existindo depois de um `DELETE`: sem repô-las, o usuário voltaria
ao onboarding com o grid de lançamento vazio e sem como fazer a primeira despesa
em três toques (Art. 18). Fica na mesma transação do apagar. Ele **não** repõe
`payee_rule` — as regras caem por `CASCADE` junto das categorias e não voltam,
o que difere de uma instalação limpa. Invisível hoje, porque nada as lê antes da
F2; o `ponytail:` no `BackupDao` diz que é uma linha quando a T-040 lhes der um
leitor.

O `.zip` que a arquitetura pedia virou `.fpbk` com gzip por dentro (Art. 3, doc
corrigido no mesmo commit): um zip de um arquivo só é um nome de entrada e um
diretório central em volta do mesmo gzip, e `GZIPOutputStream` é da biblioteca
padrão.

**O ciclo inteiro conferido no emulador**, com o seletor do sistema de verdade:
backup de 21 registros em 1.118 bytes, começando por `FPBK` e sem uma palavra do
conteúdo em claro; senha errada devolvendo "Senha incorreta, ou o arquivo está
corrompido"; `apagar` em minúsculas não liberando o botão e `APAGAR` liberando;
apagar tudo caindo direto no onboarding; e a restauração dizendo "O arquivo traz
21 registros. Os 12 que estão no app agora serão substituídos" antes de devolver
o saldo de R$ 666,92 exatamente como estava.

Um susto que valeu a checagem: depois do ciclo, uma conta que a exportação de
antes chamava de "Nubankgv" apareceu como "Nubank". O arquivo `.fpbk` foi
decifrado fora do app, e continha "Nubank" — a diferença já estava no banco
**antes** do backup, de digitação perdida na própria verificação manual. O
caminho backup → restauração devolveu byte a byte o que recebeu. De brinde, o
arquivo gerado no Android abriu numa JVM comum: `PBKDF2WithHmacSHA256` dá a mesma
chave nos dois lados, então o backup não fica preso ao aparelho que o criou.

**Uma pergunta para o dono do produto, não resolvida por suposição** (Art. 5):
restaurar só é alcançável **depois** do onboarding, porque a tela vive dentro das
abas e "sem conta nenhuma" é o que dispara o onboarding (REQ-UI-005). No aparelho
novo — que é o caso principal de um backup — o caminho é criar duas contas para
depois substituí-las. Funciona, e é esquisito. A saída seria um "Restaurar um
backup" na tela de onboarding, o que mexe em REQ-UI-005 ("uma única tela
perguntando o saldo atual"): é mudança de requisito, e começa na spec.

---

# F2 — Importação de arquivo

Design em [ingestao.md](ingestao.md).

### T-036 — Normalização e chave de dedupe ⇉
**Fase** F2 · **Depende de** T-002 · **REQ** REQ-IMP-008, REQ-ACT-004

Uma única função `normalize`, usada por dedupe e por auto-categorização.

**Pronto quando**
- [x] `NormalizeTest` cobre as 4 linhas da tabela de REQ-IMP-008
- [x] Uma só implementação no código — duas fariam dedupe e aprendizado discordarem
- [x] Acento sai por decomposição NFD, não por tabela de pares: a tabela
      esqueceria o Ç na primeira revisão, e "AÇOUGUE" com e sem cedilha viraria
      dois estabelecimentos
- [x] Número **curto** fica. `\d{4,}` some porque é NSU, autorização e final de
      cartão; três dígitos ou menos são nome de loja, e removê-los faria
      `POSTO 24H` e `POSTO 12H` virarem a mesma chave
- [x] Pontuação vira **espaço**, não some: `MERCADO-SUL` colado daria
      `MERCADOSUL`, que não casa com `MERCADO SUL` do mesmo lugar noutro extrato

O pacote é `data/ingest/` e não `data/import/` como a arquitetura escrevia
(corrigida no mesmo commit): `import` é palavra reservada em Kotlin, e um pacote
com esse nome precisaria de crase em todo arquivo que o referencia. O
`MONEY_PATHS` do `trace.py` acompanhou — é por ali que valor de extrato entra no
app, e a guarda do Art. 6 tem de cobrir o caminho de entrada como já cobre o de
saída.

### T-037 — Parser OFX ⇉
**Fase** F2 · **Depende de** T-002 · **REQ** REQ-IMP-002, REQ-IMP-003

**Pronto quando**
- [~] `OfxParserTest` com arquivos de três formatos, como fixtures — **mas
      sintéticos**, não downloads reais. Ver a ressalva abaixo
- [x] OFX 1.x SGML e 2.x XML pelo mesmo caminho de código
- [x] Fixture com `CHARSET:1252` e acento produz "ALIMENTAÇÃO" correto
- [x] Arquivo multi-conta vira um extrato por conta, e quem importa escolhe
- [x] `FITID` duplicado dentro do próprio arquivo não quebra a importação

**A diferença entre SGML e XML some** quando se lê o arquivo como "uma tag, e o
texto até a próxima tag": `<TRNAMT>-187.50` e `<TRNAMT>-187.50</TRNAMT>` dão o
mesmo par. Um parser de XML de verdade recusaria o 1.x, que é o que a maioria
dos bancos brasileiros ainda exporta — e é o motivo de o scanner ser próprio, e
não `XmlPullParser`.

**A ressalva das fixtures, dita na cara.** As três são montadas à mão sobre os
formatos que de fato divergem: 1.x SGML em CP1252, 2.x XML em UTF-8, e 1.x com
vírgula decimal e duas contas no mesmo arquivo. Elas cobrem o que se conhece de
antemão — acento em CP1252, tag de folha sem fechamento, `TRNTYPE` mentindo
sobre o sinal, `FITID` repetido, multi-conta. O que elas **não** cobrem é a
surpresa de um exportador específico, que é justamente o que um download real
traria. A T-041, que liga a importação à tela, passa arquivos de verdade antes
de isto chegar ao usuário.

O parser é tolerante de propósito: `<STMTTRN>` novo fecha o anterior mesmo sem
`</STMTTRN>`, e linha sem valor ou sem data é pulada em vez de derrubar o
arquivo. Exportador que erra o fechamento é comum, e recusar o extrato inteiro
por causa de uma linha é pior do que ler as outras trezentas.

Charset desconhecido cai em **ISO-8859-1**, não em UTF-8: byte alto isolado é
inválido em UTF-8 e viraria `?`, enquanto em ISO-8859-1 vira uma letra
acentuada possivelmente errada — legível, e que o usuário corrige na revisão.


### T-038 — Parser CSV ⇉
**Fase** F2 · **Depende de** T-002 · **REQ** REQ-IMP-006

**Pronto quando**
- [x] `CsvSniffTest` acerta separador, formato de data e decimal em três amostras
      — sintéticas, com a mesma ressalva da T-037
- [x] Sem biblioteca de CSV adicionada (Art. 10 / ladder)
- [x] Aspas resolvidas de verdade: separador dentro do campo, aspa dobrada e
      quebra de linha dentro das aspas
- [x] O BOM que a própria exportação escreve (REQ-BAK-001) não vira parte da
      primeira célula — reimportar o CSV do app é o teste mais provável de quem
      está experimentando

**O caso que justifica o farejador** é o extrato brasileiro típico: separador `;`
com decimal `,`. Contando vírgulas, o arquivo pareceria ter duas colunas e
`-187,50` viraria `-187` e `50` — valor errado em toda linha. A pontuação é
`colunas × linhas que concordam`: só colunas escolheria a vírgula, só
concordância escolheria qualquer caractere ausente, e o produto acerta.

`temCabecalho` não está em REQ-IMP-006 e entrou assim mesmo: sem ele
`Data;Descrição;Valor` viraria uma transação de valor nulo, e a primeira coisa
que o usuário veria da importação seria uma linha errada.

O decimal é detectado porque o requisito pede, **não** porque o parser precise:
`parseCents` decide sozinho pelo último separador (REQ-IMP-004). Quem usa o
palpite é a tela de mapeamento, que exibe o que entendeu para o usuário
confirmar.

### T-039 — Motor de deduplicação
**Fase** F2 · **Depende de** T-036, T-037 · **REQ** REQ-IMP-007, REQ-IMP-008, REQ-IMP-009, REQ-IMP-012

Três níveis: `FITID`, hash exato, janela difusa ±3 dias.

**Pronto quando**
- [x] `DedupeTest`: reimportar o mesmo arquivo não cria nenhuma transação nova
- [x] Nível 3 **nunca** descarta sozinho — marca e devolve o par para decisão
- [x] Índice único rejeita duplicata mesmo se a checagem em código falhar. O
      teste já existia no `TxnDaoTest` desde a T-004; faltava o `@Req`, e a
      rastreabilidade não via o que o código cobria — mesma correção que a
      REQ-CARD-001 precisou na T-022
- [x] A mesma chave repetida **dentro do próprio arquivo** cai na segunda vez.
      É o buraco que a T-037 deixou aberto de propósito: sem esta linha, a
      segunda cópia passaria como novidade e só esbarraria no índice único, já
      na gravação, com o lote pela metade

**O nível 3 não filtra por "descrição diferente"**, como REQ-IMP-009 escreve, e a
diferença é deliberada: descrição **igual** com a data deslocada em um dia é a
duplicata mais óbvia que existe, e ela não bate no nível 1 porque a data entra no
hash. Filtrar por descrição diferente deixaria justamente esse caso passar como
novidade. A janela implementada é um superconjunto do que o requisito pede e erra
para o lado de **perguntar**, que é o lado que o próprio requisito escolhe ao
proibir o descarte automático.

Entre várias linhas na janela, o motor aponta a **mais próxima em data**: é a que
o usuário reconhece como sendo a mesma compra quando as duas aparecem lado a lado.

O motor é puro e recebe listas — `Candidata` e `JaGravada`, com só o que ele
compara. `JaGravada` existe porque `dedupeKey` não sobe para o modelo de domínio
(Art. 8), e um motor que recebesse `TxnEntity` só se testaria com banco.

### T-040 — Auto-categorização
**Fase** F2 · **Depende de** T-036 · **REQ** REQ-ACT-001, REQ-ACT-002

**Pronto quando**
- [x] `PayeeRuleTest`: salvar transações do mesmo estabelecimento faz a
      importação seguinte já vir categorizada
- [x] Sugestão usa a **mesma** `normalize` do dedupe (REQ-ACT-004): NSU
      diferente na mesma padaria dá a mesma sugestão
- [x] Transferência não ensina — ela não tem categoria (REQ-TXN-004) — e
      descrição que normaliza para vazio também não: a chave `""` casaria com
      toda linha sem descrição de todo extrato futuro
- [x] Doze parcelas ensinam **uma** vez: são uma compra só
- [x] Categoria sugerida é sempre editável na revisão (fechado na T-041)

**O defeito que o teste pegou antes de existir tela.** A primeira versão
procurava a regra por igualdade da chave normalizada, que é o que REQ-ACT-001
descreve para o **aprendizado**. Só que as ~40 regras semeadas de REQ-ACT-003 são
palavras-chave — `IFOOD`, `UBER`, `NETFLIX` — e o extrato traz `iFood *Pedido
12345`, que normaliza para `IFOOD PEDIDO`. Por igualdade, **nenhuma** das
quarenta casaria com coisa alguma, e a primeira importação chegaria vazia: o
oposto exato do que a semente existe para fazer.

A busca passou a ser por **palavra contida**, com a chave mais longa ganhando.
Os espaços em volta dos dois lados são o que impede casar no meio de palavra —
sem eles a chave `UBER` acharia `SUBERBIA`. E a chave mais longa ser a
vencedora resolve o empate do jeito certo: entre a semente `UBER` e a regra
aprendida `UBER TRIP AEROPORTO`, a segunda foi o usuário quem ensinou.

**A última correção manda.** `normalizedKey` é único, então uma chave tem uma
categoria. Quando o usuário corrige uma sugestão, o que ele disse foi "não é
isso, é aquilo" — guardar a maioria histórica faria a correção precisar de três
repetições para valer, e ele desistiria antes.

O aprendizado mora em `TxnRepository.salvar`, e não em quem chama, pela mesma
razão da leitura-antes-de-escrever que já vive lá: é por ali que passa todo
chamador, inclusive os que ainda não existem. Corrigir a categoria de uma
transação importada é o momento em que o app mais tem a aprender, e é um caminho
que não passa por tela nova.

### T-041 — Fluxo de importação
**Fase** F2 · **Depende de** T-038, T-039, T-040 · **REQ** REQ-IMP-001, REQ-IMP-005, REQ-IMP-010

Picker SAF, mapeamento de colunas, tela de revisão.

**Pronto quando**
- [x] Nenhuma gravação sem confirmação explícita (Art. 14). O `confirmar` do
      ViewModel é o **único** ponto de escrita do fluxo, e o botão que o chama
      fica desabilitado enquanto houver linha incluída sem categoria
- [x] Possíveis duplicatas exibidas lado a lado — a linha que chegou e a que já
      existe, com data e valor, na mesma carta
- [x] Mapeamento de CSV salvo e reaproveitado, chaveado pela **assinatura da
      primeira linha** e não pelo nome do arquivo: o extrato de setembro se
      chama diferente do de agosto, e o cabeçalho do banco é o mesmo sempre
- [x] `READ_EXTERNAL_STORAGE` não existe no manifesto, e agora com teste: o
      `ManifestTest` passou a vigiar as três permissões de armazenamento pela
      mesma razão que já vigiava a `INTERNET` — elas entram por dependência
      transitiva, não por alguém digitá-las
- [x] Categoria sugerida é editável na revisão, fechando a linha que a T-040
      tinha deixado aberta (REQ-ACT-002)

**Conferido no emulador, o ciclo inteiro.** OFX em CP1252 escolhido pelo seletor
do sistema chegou à revisão com "PADARIA ALIMENTAÇÃO" acentuado — REQ-IMP-003
verificado ponta a ponta, do byte ao pixel. Três linhas categorizadas, três
gravadas. **Reimportar o mesmo arquivo** trouxe "3 já estavam na conta e foram
descartadas" com o botão desabilitado, que é REQ-IMP-007 no aparelho. Um arquivo
com o mesmo valor num dia vizinho e descrição diferente apareceu como "⚠ Parecida
com SUPERMERCADO XYZ LTDA de 15/08, −R$ 187,50", incluída por padrão e
desmarcável — REQ-IMP-009. E o CSV passou pelo mapeamento com a prévia das três
primeiras linhas e o palpite já escolhido.

**Dois defeitos que só o aparelho mostrou.** O nome do arquivo no lote saía como
`msf:39`: `lastPathSegment` de um `Uri` do SAF é o **id do documento**, e o nome
de verdade só vem de `DISPLAY_NAME`, perguntando ao provedor — o que a tela de
lotes da T-042 exibiria para o usuário reconhecer a importação. E os textos de
resumo diziam "1 parecidas" e "Faltam 1 categorias": descuido de plural que faz
desconfiar do resto da tela, justamente naquela em que é preciso confiar para
confirmar trezentas linhas.

**A duplicata exata some da lista e vira um número.** A spec manda descartá-la
sozinha (REQ-IMP-007, REQ-IMP-008); trezentas linhas riscadas que ninguém pode
mexer só atrapalhariam quem precisa conferir as vinte que sobraram.

**A possível duplicata entra marcada, não desmarcada.** REQ-IMP-009 proíbe
descartar por heurística porque isso perde transação legítima — e um padrão
desligado seria o app decidindo por omissão o que ele não pode decidir por ação.

O confirmar exige categoria em toda linha incluída, porque REQ-TXN-005 exige, e o
rótulo do botão diz quantas faltam: desabilitado e mudo, ele mandaria o usuário
procurar o problema em trezentas linhas.

### T-042 — Lotes e desfazer
**Fase** F2 · **Depende de** T-041 · **REQ** REQ-IMP-011

**Pronto quando**
- [x] `ImportBatchTest`: desfazer remove exatamente as transações do lote, e não
      encosta no que estava fora dele
- [x] Transação editada manualmente após a importação **não** é removida pelo
      desfazer — e perde o vínculo com o lote em vez de sumir junto com ele
- [x] O lote sai da lista depois de desfeito, e desfazer um não mexe no outro

**Como o app sabe que a linha foi editada, sem coluna nova.** A importação grava
`createdAt` e `updatedAt` com o **mesmo** instante, e toda edição posterior passa
por `TxnRepository.salvar`, que preserva o primeiro e avança o segundo. Então
`updatedAt <= createdAt` é exatamente "ninguém tocou nisto desde que chegou". Uma
coluna `editadaManualmente` diria a mesma coisa com um estado a mais para
dessincronizar — e alguém esqueceria de marcá-la no primeiro caminho de escrita
novo.

**O que sobrou perde o vínculo, não a vida.** `txn.importBatchId` é `SET_NULL`:
apagar o lote transforma a linha que o usuário corrigiu numa transação comum.
Apagar o trabalho dele para desfazer o trabalho do app seria a troca errada.

A lista aparece nos **dois** momentos em que serve: antes de importar, para
conferir o que já entrou, e logo depois de importar, que é quando se descobre que
o arquivo era o errado. Uma tela própria escondida no menu seria um caminho a
mais justamente para quem está com pressa de desfazer.

O recado diz as duas metades — quantas saíram e quantas ficaram por terem sido
editadas. Dizer só "removidas" faria o usuário procurar no extrato as que
sobraram, achando que o desfazer falhou.

Conferido no emulador: lote de 3 linhas listado com nome e horário, saldo em
R$ 5.433,52; depois do desfazer, "3 removidas", lista vazia e saldo de volta em
R$ 666,92 — exatamente o que havia antes da importação.

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
