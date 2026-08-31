# Sistema visual — Slush aplicado ao Android

> **Documento de design.** Os requisitos normativos estão em
> [spec.md](spec.md) — `REQ-DS-*`. Aqui está o *como* e o *porquê*; lá está o
> *o quê*, com critérios verificáveis. Em caso de divergência, a spec ganha
> ([Art. 3](constitution.md#art-3--a-spec-é-a-verdade-divergência-é-bug-da-spec)).

**Preview navegável:** <https://claude.ai/code/artifact/276f61c4-cb0f-4531-a84c-130ab07a4b7c>
(fonte em [preview/slush-android.html](preview/slush-android.html) — telas em 360px, onde
1px = 1dp, então os tamanhos são os valores reais da spec).

Origem: style reference **Slush** — universo de stickers infláveis sobre papel
pastel. Tipografia display gigante e esmagada, fitas 3D em azul elétrico, paleta
de seis cores saturadas usada como conjunto de adesivos, tudo contornado em preto
com cantos muito arredondados.

## 1. A tradução, e onde ela para

Slush foi desenhado para uma landing page de 1440px com tipo de 640px. Este é um
app de finanças em tela de 360dp, e a maior parte do tempo do usuário é gasta
numa **lista densa de números**.

Portar Slush literalmente para todas as telas destruiria o app. Portar nada
descartaria a identidade. A tradução é por intensidade:

| Tela | Intensidade | O que entra |
|---|---|---|
| Onboarding | **Pôster completo** | `display-xl`, fita 3D, 3 stickers, banda Sky Wash |
| Estados vazios | **Pôster** | `display`, 1 sticker, sem fita |
| Dashboard | Média | bandas de cor, cards com contorno, saldo em `display-sm` |
| Cartão / fatura | Média | card 40dp, sticker do cartão, fatura em `display-sm` |
| Orçamento | Média | barras com contorno preto, stickers de estado |
| Lançamento rápido | Baixa | chips pill contornados, **grid de categorias como stickers** |
| Lista de transações | **Mínima** | só contorno e tipografia UI. Zero display type. |
| Importação, ajustes | **Mínima** | idem |

A regra: **quanto mais denso o dado, menos pôster**. Uma lista de 100 transações
com tipografia de 88sp não é ousada, é inutilizável — e violaria o Art. 18, que
protege o caminho de 5 segundos.

O grid de categorias do lançamento rápido é onde a paleta de seis cores mais
rende: cada categoria já tem cor e ícone próprios
([REQ-CAT-001](spec.md#req-cat-001--cadastro-de-categoria)), então o grid vira
literalmente uma cartela de adesivos, sem inventar nada.

## 2. Três conflitos com o que já está especificado

Não são detalhes de gosto. Cada um tem uma resolução registrada.

### 2.1 Slush é light-only; o app exige tema escuro

[REQ-UI-007](spec.md#req-ui-007--tema) pede claro, escuro e sistema. Slush declara
`Theme: light` e seus fundos pastel (`#dceeff`, `#cccccc`, `#e9ccff`) não
sobrevivem no escuro.

**Resolução.** A lógica de sticker é preservada invertendo o *papel*, não as
cores dos adesivos:

- Papel `#ffffff` → `#111111`; contorno Carbon → Paper White
- As **seis cores de sticker permanecem idênticas** nos dois temas
- As três bandas pastel ganham equivalentes escuros dessaturados

Um adesivo colorido contornado funciona sobre papel claro ou escuro. É a mesma
gramática.

### 2.2 A paleta reprova em contraste — em direções opostas por tema

Medido (ver §5): sobre branco, só **Voltage Violet** (6.02:1) passa em 4.5:1.
Electric Blue dá 2.65:1, Mint Pop 1.75:1, Sunburst **1.40:1**.

Sobre `#111111` a situação **se inverte**: Sunburst 13.50:1, Mint Pop 10.79:1,
Electric Blue 7.13:1 — e Voltage Violet cai para 3.14:1.

**Resolução.** Uma regra que vale nos dois temas e não depende de tabela:

> A paleta de stickers é **sempre preenchimento, nunca cor de texto**. Texto é
> Carbon sobre superfície clara ou Paper White sobre superfície escura. Sem
> exceção.

Slush já dizia isso do Sunburst ("never used for text backgrounds"); a medição
mostra que a regra vale para cinco das seis cores. Estendê-la ao conjunto inteiro
elimina a classe de erro em vez de administrá-la.

Voltage Violet é a única exceção permitida, e só num sentido: **branco sobre
Violet** (6.02:1) é o padrão do card QR de Slush e passa. Branco sobre Ember
(3.47:1) **reprova** — esse padrão só existe em Violet ou Carbon.

### 2.3 Slush proíbe verde semântico; um app de finanças quer verde e vermelho

Slush: *"green is not a success state color, it is a sticker accent"*. E
[REQ-A11Y-003](spec.md#req-a11y-003--cor-não-é-sinal-único) proíbe cor como sinal
único de qualquer forma.

**Resolução.** Valor monetário é **sempre Carbon** (ou Paper White no escuro). O
significado vem do **sinal** `+` / `−`, que
[REQ-CORE-005](spec.md#req-core-005--formatação-pt-br) já exige, mais o rótulo da
categoria.

Estados do orçamento usam sticker **com ícone**, nunca cor sozinha:

| Estado | Sticker | Ícone |
|---|---|---|
| Dentro do teto | sem preenchimento, só contorno Carbon | — |
| ≥ 80% | Sunburst `#ffd731` | triângulo de atenção |
| ≥ 100% | Ember `#fb4903` | círculo de estouro |

**O que se perde:** a leitura periférica instantânea de verde/vermelho numa lista.
É uma troca real, e deliberada — a favor de Slush e da acessibilidade ao mesmo
tempo. Se a leitura periférica se mostrar mais importante no uso, reverter custa
dois tokens e um ADR.

## 3. Fontes

Lateral e Aeonik Pro são licenciadas. Os substitutos, ambos **SIL OFL** e
empacotáveis:

| Papel | Slush | Aqui | Por quê |
|---|---|---|---|
| Display | Lateral 800 | **Antonio** (variável, wght 700) | Grotesca condensada pesada; é o substituto livre usual de Druk/Lateral. Variável, ~50KB |
| UI | Aeonik Pro 500/700 | **Inter** (variável) | Já listada como substituta. Tem `tnum` e `ss01` |

**As fontes são empacotadas em `res/font/`, não baixadas.** Downloadable Fonts
exige rede e Google Play Services, e
[REQ-SEC-007](spec.md#req-sec-007--sem-permissão-de-rede) proíbe a permissão
`INTERNET` até a F4. Fonte remota seria a primeira coisa a furar essa garantia,
sem ninguém perceber.

Inter variável completa pesa ~350KB. Subsetada para latin + latin-ext fica em
~120KB. Vale o subset.

`tnum` (algarismos tabulares) não é enfeite aqui: sem ele, os valores numa lista
de transações não alinham na vertical, e uma coluna de dinheiro desalinhada é
mais difícil de conferir contra o extrato.

## 4. Tokens

`core/ui/theme/`. Fonte única — nenhuma cor literal fora destes arquivos
([REQ-DS-001](spec.md#req-ds-001--tokens-como-fonte-única)).

```kotlin
// Color.kt — a paleta de stickers e igual nos dois temas.
val Carbon         = Color(0xFF000000)
val PaperWhite     = Color(0xFFFFFFFF)
val SkyWash        = Color(0xFFDCEEFF)
val ConcreteGray   = Color(0xFFCCCCCC)
val SoftMist       = Color(0xFFE9E9E9)

val ElectricBlue   = Color(0xFF4DA2FF)   // fita 3D, assinatura da marca
val MintPop        = Color(0xFF55DB9C)
val Lavender       = Color(0xFFE9CCFF)
val Ember          = Color(0xFFFB4903)
val Sunburst       = Color(0xFFFFD731)
val VoltageViolet  = Color(0xFF5C4ADE)

// Papel escuro: as bandas pastel nao sobrevivem, os stickers sim.
val CarbonPaper    = Color(0xFF111111)   // papel
val SkyWashDark    = Color(0xFF0D1A26)
val ConcreteDark   = Color(0xFF1C1C1C)
val LavenderDark   = Color(0xFF1E1729)
```

```kotlin
// Theme.kt — o contorno preto e um token, nao um literal espalhado.
@Immutable
data class SlushColors(
    val paper: Color,        // fundo da superficie
    val ink: Color,          // texto e contorno
    val bandSky: Color,
    val bandNeutral: Color,
    val bandLavender: Color,
    val onFill: Color,       // texto sobre preenchimento saturado
)

val LightSlush = SlushColors(
    paper = PaperWhite, ink = Carbon,
    bandSky = SkyWash, bandNeutral = ConcreteGray, bandLavender = Lavender,
    onFill = PaperWhite,
)

val DarkSlush = SlushColors(
    paper = CarbonPaper, ink = PaperWhite,
    bandSky = SkyWashDark, bandNeutral = ConcreteDark, bandLavender = LavenderDark,
    onFill = PaperWhite,
)

val LocalSlush = staticCompositionLocalOf { LightSlush }
```

`ink` é ao mesmo tempo a cor do texto e a do contorno. É o que faz o contorno
inverter junto com o tema sem nenhuma condicional espalhada pelas telas.

### 4.1 Formas

Web em px, Android em dp. Os números são mantidos; `1600px` vira `CircleShape`,
que é o que aquele raio absurdo significava.

```kotlin
val SlushShapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),   // sticker pequeno, icone de carteira
    small      = RoundedCornerShape(20.dp),   // cards
    medium     = RoundedCornerShape(20.dp),
    large      = RoundedCornerShape(30.dp),   // corpo, folhas
    extraLarge = RoundedCornerShape(40.dp),   // cards elevados, bottom sheet
)
val Pill = CircleShape                        // nav, botoes, chips, tags
```

Nada abaixo de 16dp. É regra, não preferência
([REQ-DS-003](spec.md#req-ds-003--raios-e-contornos)).

### 4.2 Tipografia

A escala de Slush (640/281/200/160/110/70px) pressupõe 1440px de largura. Em
360dp, o que produz o mesmo efeito escultural é o tipo ocupar de 60% a 90% da
largura útil — o que dá:

```kotlin
private val CrushedLeading = PlatformTextStyle(includeFontPadding = false)
private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private fun display(size: TextUnit, leading: Float) = TextStyle(
    fontFamily = Antonio, fontWeight = FontWeight.W700,
    fontSize = size, lineHeight = leading.em,
    platformStyle = CrushedLeading, lineHeightStyle = TrimBoth,
)

val DisplayXl = display(88.sp, 0.78f)   // onboarding
val Display   = display(64.sp, 0.80f)   // estados vazios, banners de secao
val DisplaySm = display(44.sp, 0.82f)   // saldo, total da fatura
```

**`includeFontPadding = false` e `Trim.Both` não são opcionais.** Sem os dois, o
Compose adiciona a folga de métrica da fonte acima e abaixo de cada linha, e o
`lineHeight` de 0.78 simplesmente não aparece na tela. O bloco fica com o espaçamento
de um parágrafo comum, e o efeito escultural — a única razão de a regra existir —
se perde silenciosamente. É o erro que se comete uma vez e demora a diagnosticar,
porque o código parece certo.

```kotlin
private val Money = "tnum"   // algarismos tabulares: colunas de dinheiro alinham

val HeadingSm  = TextStyle(Inter, W700, 30.sp, lineHeight = 1.1.em)
val Subheading = TextStyle(Inter, W700, 24.sp, lineHeight = 1.2.em)
val BodyLg     = TextStyle(Inter, W500, 15.sp, lineHeight = 1.39.em, letterSpacing = (-0.01).em)
val Body       = TextStyle(Inter, W500, 14.sp, letterSpacing = (-0.01).em)
val Caption    = TextStyle(Inter, W500, 12.sp, lineHeight = 1.56.em, letterSpacing = (-0.01).em)

// Nav, botoes e rotulos maiusculos: a abertura e o que da ar aos controles pill.
val Label      = TextStyle(Inter, W700, 13.sp, letterSpacing = 0.032.em)

// Todo valor monetario passa por aqui.
val MoneyLg    = DisplaySm.copy(fontFeatureSettings = Money)
val MoneyBody  = Body.copy(fontWeight = W700, fontFeatureSettings = Money)
```

### 4.3 Fonte ampliada até 200%

[REQ-A11Y-004](spec.md#req-a11y-004--fonte-ampliada) exige 200% sem truncar. Como
`lineHeight` está em `em`, ele escala junto e a proporção esmagada se mantém — o
bloco só fica mais alto.

Duas condições para isso funcionar:

- Nenhum contêiner de display type tem altura fixa. Ele cresce.
- `DisplayXl` a 200% dá 176sp. Cabe em 360dp com 3–4 caracteres por linha, o que
  ainda é a estética pretendida. Palavra que não couber quebra; não se aplica
  reticências em display type — **truncar um título escultural é pior que
  quebrá-lo**.

## 5. Contraste medido

Calculado com a fórmula de luminância relativa da WCAG 2.1.

| Cor | sobre `#ffffff` | sobre `#111111` | Uso permitido |
|---|---|---|---|
| Carbon `#000000` | 21.00:1 | 1.11:1 | texto/contorno **claro** |
| Paper White `#ffffff` | 1.00:1 | 18.88:1 | texto/contorno **escuro** |
| Voltage Violet `#5c4ade` | **6.02:1** | 3.14:1 | preenchimento; texto branco sobre ele |
| Ember `#fb4903` | 3.47:1 | 5.45:1 | **preenchimento apenas** |
| Electric Blue `#4da2ff` | 2.65:1 | 7.13:1 | **preenchimento apenas** |
| Mint Pop `#55db9c` | 1.75:1 | 10.79:1 | **preenchimento apenas** |
| Sunburst `#ffd731` | 1.40:1 | 13.50:1 | **preenchimento apenas** |
| Lavender `#e9ccff` | 1.44:1 | 13.08:1 | **superfície apenas** |

Carbon sobre as bandas pastel, todas confortáveis: Sky Wash 17.72:1,
Lavender 14.55:1, Concrete Gray 13.08:1.

Branco sobre preenchimento saturado — o padrão do card QR:
Voltage Violet **6.02:1 passa**, Carbon 21:1 passa, Ember **3.47:1 reprova**.

Os números vivem em `ContrastTest`, que recalcula a partir dos tokens e falha se
alguém mudar um hex ou usar uma cor de sticker como cor de texto. Uma tabela em
Markdown mente assim que o token muda; o teste não.

## 6. Componentes

Traduções diretas dos componentes de Slush. Todos com contorno `ink` de 1dp e
**elevação zero**.

```kotlin
// Botao primario: preenchimento Carbon, texto Paper White. Nunca azul.
@Composable
fun FilledCta(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(40.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LocalSlush.current.ink,
            contentColor = LocalSlush.current.paper,
        ),
        elevation = null,                                   // sem sombra, nunca
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) { Text(text, style = Label) }
}

// Botao secundario e chips de nav: pill, contorno, preenchimento de papel.
@Composable
fun GhostButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = Pill,
        border = BorderStroke(1.dp, LocalSlush.current.ink),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = LocalSlush.current.paper,
            contentColor = LocalSlush.current.ink,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) { Text(text, style = Label) }
}
```

**Sombra é o erro fácil aqui**, porque o Material 3 a coloca sozinho. `Card`,
`Button`, `FloatingActionButton` e `Surface` trazem elevação por padrão, e
`Surface` ainda aplica *tonal elevation*, que tinge o fundo mesmo sem sombra
visível. Todos os quatro precisam de `0.dp` explícito
([REQ-DS-004](spec.md#req-ds-004--sem-sombra-e-sem-gradiente)):

```kotlin
Card(
    shape = SlushShapes.small,
    border = BorderStroke(1.dp, LocalSlush.current.ink),
    colors = CardDefaults.cardColors(containerColor = LocalSlush.current.paper),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
)

Surface(tonalElevation = 0.dp, shadowElevation = 0.dp) { /* ... */ }
```

### 6.1 Barra de navegação

Slush usa nav em pill. A tradução para as quatro abas de
[REQ-UI-001](spec.md#req-ui-001--navegação-principal) é uma barra flutuante em
pill, contornada, sobre a banda de cor da seção — em vez da `NavigationBar` padrão
do Material, que traz superfície tonal e nenhum contorno.

Alvo de toque de 48dp mantido ([REQ-A11Y-002](spec.md#req-a11y-002--alvo-de-toque)),
que é maior que o padding de 15/12dp de Slush. **A acessibilidade vence o token**;
o padding visual fica, o alvo é ampliado por `Modifier.minimumInteractiveComponentSize()`.

### 6.2 Sticker de categoria

Onde a paleta de seis cores mais rende. Cada categoria já tem cor e ícone
próprios, então o grid do lançamento rápido é literalmente uma cartela de
adesivos:

```kotlin
@Composable
fun CategorySticker(category: Category, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(category.colorArgb))          // preenchimento apenas
            .border(if (selected) 3.dp else 1.dp, LocalSlush.current.ink,
                    RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = category.name },
    ) { /* icone Carbon, nome abaixo em Caption */ }
}
```

Seleção é sinalizada pela **espessura do contorno**, não por cor — cor já é a
identidade da categoria, e usá-la também para estado a tornaria sinal único
duplamente sobrecarregado.

### 6.3 Linha de transação

A tela mais densa do app, e por isso a que menos tolera vocabulário de pôster.

**O indicador de categoria é um ponto de 10dp, não um sticker.** A primeira versão
usava o mesmo sticker de 34dp do grid de lançamento, e uma coluna de dez blocos
coloridos contornados tornava a lista ilegível.

O argumento não é de gosto: **a categoria já aparece por extenso na linha de
baixo** ("Alimentação · Carteira"). O bloco não carregava nenhuma informação que a
linha já não tivesse — era peso visual puro, e era a lógica de pôster vazando para
dentro da tela de dados que [REQ-DS-009](spec.md#req-ds-009--intensidade-proporcional-à-densidade)
manda manter mínima.

O ponto mantém o contorno de 1dp. Sem ele, um ponto Sunburst sobre papel branco dá
1.40:1 e desaparece — leria como falha de renderização, não como escolha.

**Estrutura da linha**, de cima para baixo dentro do bloco central:

| Elemento | Estilo |
|---|---|
| Ponto de categoria | 10dp, contorno 1dp `ink`, alinhado à **primeira** linha |
| Descrição | `Body` 14sp, peso 700, uma linha com reticências |
| Categoria · conta | `Caption` 12sp, opacidade 62%, **sempre na linha de baixo** |
| Valor | `MoneyBody` 15sp, peso 700, `tnum`, alinhado à direita |

Descrição e subtítulo são blocos empilhados, nunca lado a lado. Em Compose isso é
uma `Column`; o erro equivalente no protótipo em HTML foi deixá-los como `span`
inline, e os dois colaram na mesma linha.

## 7. A fita 3D e os stickers

A fita azul é descrita como a assinatura da marca, presente em toda seção. Num
app isso é peso de APK: renders 3D full-bleed, por densidade.

**O que entra:** duas renderizações WebP com perda, ~80–120KB cada, em `nodpi`,
escaladas pelo Compose. Uma para o onboarding, uma para estados vazios.

**O que não entra:** fita em toda tela. Numa lista rolável ela seria redesenhada a
cada frame por trás de conteúdo em movimento, custando banda de memória por nada
visível. Os stickers 2D são vetores (`ImageVector`), sem custo relevante.

## 8. O que não foi portado, e por quê

Registrar isto evita que alguém "complete" o design mais tarde achando que faltou.

- **Marquee.** Faixa preta com texto rolando no topo. Num app de finanças não há
  anúncio a rolar, e movimento contínuo permanente atrapalha a leitura de números.
  Se um dia houver aviso global, ele é um banner estático.
- **Fita 3D em toda seção.** Reduzida a duas telas (§7).
- **Tipo de 640px.** Não existe equivalente em 360dp. `DisplayXl` a 88sp é a
  tradução do *efeito*, não do número.
- **Largura mínima de 1280px.** Regra de web. O equivalente no Android é não
  restringir o conteúdo com margens grandes: 16dp de margem lateral, e o display
  type sangra até a borda quando o desenho pedir.

## 9. Verificação

| O que | Como |
|---|---|
| Contraste dos tokens | `ContrastTest` recalcula da paleta e falha em regressão |
| Cor literal fora do tema | detekt: `Color(0x` proibido fora de `core/ui/theme/` |
| Sombra e elevação | detekt: `shadowElevation`/`defaultElevation` diferente de zero |
| Gradiente | detekt: `Brush.linearGradient` e afins proibidos |
| Raio abaixo de 16dp | revisão; os raios só existem em `SlushShapes` |
| Leading esmagado | `TypographyTest` verifica `lineHeight <= 0.85.em` e `includeFontPadding == false` nos três estilos display |
| Fonte 200% | verificação manual em T-020, junto com o resto da acessibilidade |

O último item de código é o que mais importa: `includeFontPadding` volta a `true`
com uma linha distraída, e o sintoma — "o título ficou espaçado demais" — não
parece um bug de código.
