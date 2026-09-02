# Sistema visual — dois modos aplicados ao Android

> **Documento de design.** Os requisitos normativos estão em
> [spec.md](spec.md) — `REQ-DS-*`. Aqui está o *como* e o *porquê*; lá está o
> *o quê*, com critérios verificáveis. Em caso de divergência, a spec ganha
> ([Art. 3](constitution.md#art-3--a-spec-é-a-verdade-divergência-é-bug-da-spec)).

**Preview navegável:** <https://claude.ai/code/artifact/28fcc531-eda1-48f5-8651-6d1b5e296f5f>
(fonte em [preview/revolut-android.html](preview/revolut-android.html) — telas em
380px, onde 1px ≈ 1dp, então os tamanhos aproximam os valores da spec).

O sistema anterior — Slush, adesivos inflados sobre papel branco, tudo contornado
em preto — foi trocado em [ADR-011](decisoes.md). O que se perdeu está registrado
lá; este documento descreve só o que está no código hoje.

Origem: um sistema de **duas bandas de tela cheia**. Preto absoluto para mostrar
conteúdo, branco para percorrer cadastro, e a troca entre eles acontece de uma
vez, sem transição. Ação é pílula, conteúdo é card de 20dp, e a profundidade
inteira do desenho é um único degrau de luminância acima de cada canvas.

## 1. A tradução, e onde ela para

O sistema de origem foi desenhado para uma landing page de 1440px com tipo de
136px. Este é um app de finanças em tela de 360dp, e a maior parte do tempo do
usuário é gasta numa **lista densa de números**.

Portar a banda de marketing para todas as telas destruiria o app. Portar nada
descartaria a identidade. A tradução é por intensidade:

| Tela | Intensidade | O que entra |
|---|---|---|
| Onboarding | **Pôster completo** | `DisplayXl` 64sp, canvas preto sangrado |
| Estados vazios | **Pôster** | `Display` 44sp, um preenchimento de acento |
| Dashboard | Média | saldo em `DisplaySm`, **um** bloco em Cobalto, cards de 20dp |
| Cartão / fatura | Média | fatura em `DisplaySm`, card de 20dp |
| Orçamento | Média | anel do teto, barras de acento, sem display type nas linhas |
| Lançamento rápido | Baixa | chips pill, grid de categorias como preenchimento |
| Lista de transações | **Mínima** | só tipografia UI e o degrau do card. Zero display type. |
| Importação, ajustes | **Mínima** | idem |

A regra: **quanto mais denso o dado, menos pôster**. Uma lista de 100 transações
com tipografia de 64sp não é ousada, é inutilizável — e violaria o Art. 18, que
protege o caminho de 5 segundos.

A segunda regra, e a que é mais fácil de perder: **um bloco em Cobalto por tela,
no máximo**. Cobalto é assinatura. Dois na mesma viewport e ele deixa de ser
carimbo para virar tema de cor, que é o oposto do que o sistema faz com ele.

## 2. Três conflitos com o que já está especificado

Não são detalhes de gosto. Cada um tem uma resolução registrada.

### 2.1 O sistema é escuro-primeiro; o app exige claro, escuro e sistema

[REQ-UI-007](spec.md#req-ui-007--tema) pede os três. O sistema de origem trata o
preto como canvas de narrativa e o branco como banda de catálogo, e não documenta
tokens escuros fora das faixas pretas.

**Resolução.** As duas bandas viram os dois temas, e cada uma ganha o degrau que
falta:

| | Canvas `paper` | Superfície `surface` | Tinta `ink` |
|---|---|---|---|
| Escuro | `#000000` | `#16181A` | `#FFFFFF` |
| Claro | `#FFFFFF` | `#F4F4F4` | `#191C1F` |

O modo escuro é o principal, e a ordem de declaração em `SlushColors.kt` registra
isso. As oito cores de acento **não mudam entre os temas**: a cor de uma
categoria é identidade, e identidade não muda quando anoitece.

Preto absoluto, e não quase-preto. `#0A0A0A` existe no sistema de origem para
cards embutidos; usá-lo como canvas achataria a única troca de banda que o
desenho tem.

### 2.2 A paleta reprova em contraste — e o fundo fácil esconde isso

Medido (ver §5): sobre **preto puro**, as oito cores de acento passam de 4.5:1.
Parece que qualquer uma serve como cor de texto.

Sobre `#16181A`, que é onde o conteúdo de fato mora, cinco reprovam: Azul 3.91,
Rosa 3.94, Verde 3.95, Marrom 3.90 e Vermelho 4.20. E nenhuma das oito passa nos
**dois** temas — Verde-azulado dá 5.85 no escuro e 2.77 no claro.

**Resolução.** Uma regra que vale nos dois temas e não depende de tabela:

> A paleta de acento é **sempre preenchimento, nunca cor de texto**. Texto é
> `ink` sobre `paper` ou sobre `surface`. Sem exceção.

Estendê-la ao conjunto inteiro elimina a classe de erro em vez de administrá-la
por superfície e por tema. O risco aqui é específico e vale nomear: um teste que
medisse contra `paper` passaria, e estaria concordando com o erro que a regra
existe para impedir. `ContrastTest` mede contra `surface`.

Cobalto é a única exceção permitida, e só num sentido: **branco sobre Cobalto**
(6.06:1) passa. Branco sobre Vermelho (4.24:1) **reprova**, e os dois parecem
igualmente seguros a olho.

Há uma segunda consequência, menor e mais fácil de esquecer: **preenchimento de
acento com menos de 24dp leva anel de `ink` de 1dp**. Sozinho, Verde-azulado dá
2.77:1 sobre a superfície clara e Laranja 2.53:1, abaixo dos 3:1 de elemento não
textual da WCAG. Vale para o ponto da linha de transação e para a amostra do
seletor de cores — os dois lugares onde a cor aparece pequena.

### 2.3 O app de finanças quer verde e vermelho; a medição não deixa

A intenção era usar verde para receita e vermelho para despesa, como no
protótipo. E [REQ-A11Y-003](spec.md#req-a11y-003--cor-não-é-sinal-único) proíbe
cor como sinal único de qualquer forma.

**Resolução.** Valor monetário é **sempre `ink`**. O significado vem do **sinal**
`+` / `−`, que [REQ-CORE-005](spec.md#req-core-005--formatação-pt-br) já exige,
mais o rótulo da categoria.

O argumento decisivo é numérico, não doutrinário: sobre o card, um par
verde/vermelho reprovaria justamente na metade vermelha — a que avisa. Cor em só
uma das polaridades é pior que cor em nenhuma.

Estados do orçamento usam preenchimento **com ícone**, nunca cor sozinha:

| Estado | Preenchimento | Ícone |
|---|---|---|
| Dentro do teto | sem preenchimento | — |
| ≥ 80% | Laranja `#ec7e00` | triângulo de atenção |
| ≥ 100% | Vermelho `#e23b4a` | círculo de estouro |

A palavra "estourou" fica em `ink`. Vermelho como texto sobre o card dá 4.20:1 e
reprovaria — a barra pode ser vermelha, o aviso escrito não.

**O que se perde:** a leitura periférica instantânea de verde/vermelho numa lista.
É uma troca real e deliberada. Se a leitura periférica se mostrar mais importante
no uso, reverter custa dois tokens e um ADR — mas custa também uma superfície
mais clara para o vermelho pousar.

## 3. Fontes

A display do sistema de origem é proprietária. O próprio sistema nomeia os
substitutos e a receita para usá-los.

| Papel | Origem | Aqui | Por quê |
|---|---|---|---|
| Display | Aeonik Pro 500 | **Inter** wght 500 | Substituto indicado. Entrelinha travada em 1.0 e −1% de entreletra reproduzem o aperto |
| UI | Inter 400/600 | **Inter** wght 400/600 | É a própria fonte do sistema, e é livre |

**Uma família só.** A troca de sistema visual removeu `antonio.ttf` de
`res/font/`: o display passou a sair da família que já estava no APK. Trocar de
família no display renderia menos que acertar entrelinha e entreletra, e custaria
um arquivo de fonte a mais.

**As fontes são empacotadas em `res/font/`, não baixadas.** Downloadable Fonts
exige rede e Google Play Services, e
[REQ-SEC-007](spec.md#req-sec-007--sem-permissão-de-rede) proíbe a permissão
`INTERNET` até a F4. Fonte remota seria a primeira coisa a furar essa garantia,
sem ninguém perceber.

Inter variável completa pesa ~876KB. Subsetada para latin + latin-ext fica em
~120KB. Vale o subset, e está marcado com `ponytail:` em `Type.kt`.

`tnum` (algarismos tabulares) não é enfeite aqui: sem ele, os valores numa lista
de transações não alinham na vertical, e uma coluna de dinheiro desalinhada é
mais difícil de conferir contra o extrato.

## 4. Tokens

`core/ui/theme/`. Fonte única — nenhuma cor literal fora destes arquivos
([REQ-DS-001](spec.md#req-ds-001--tokens-como-fonte-única)).

```kotlin
// Color.kt — os dois canvas, o degrau de cada um, e os acentos.
val CanvasDark      = Color(0xFF000000)   // preto absoluto, nao quase-preto
val CanvasLight     = Color(0xFFFFFFFF)
val SurfaceElevated = Color(0xFF16181A)   // o degrau unico no escuro
val SurfaceSoft     = Color(0xFFF4F4F4)   // o degrau unico no claro

val InkLight        = Color(0xFF191C1F)   // texto no claro; mais quente que preto
val MuteLight       = Color(0xFF505A63)
val MuteDark        = Color(0xFFB8B8B8)   // branco a 72% ja achatado sobre preto
val HairlineLight   = Color(0xFFE2E2E7)
val HairlineDark    = Color(0x1FFFFFFF)

val Cobalt          = Color(0xFF494FDF)   // carimbo da marca, escasso por regra

val Teal       = Color(0xFF00A87E)
val LightBlue  = Color(0xFF007BC2)
val LightGreen = Color(0xFF428619)
val Yellow     = Color(0xFFB09000)
val Warning    = Color(0xFFEC7E00)
val Pink       = Color(0xFFE61E49)
val Danger     = Color(0xFFE23B4A)
val Brown      = Color(0xFF936D62)
```

```kotlin
// SlushColors.kt — o degrau e um token, nao um literal espalhado.
@Immutable
data class SlushColors(
    val paper: Color,     // canvas do modo
    val surface: Color,   // o degrau unico acima do canvas
    val ink: Color,       // texto primario
    val inkMute: Color,   // texto secundario
    val hairline: Color,  // fio entre superficies de mesmo tom
    val onFill: Color,    // texto sobre preenchimento saturado — so sobre Cobalto
)

val DarkSlush = SlushColors(
    paper = CanvasDark, surface = SurfaceElevated,
    ink = CanvasLight, inkMute = MuteDark,
    hairline = HairlineDark, onFill = CanvasLight,
)

val LightSlush = SlushColors(
    paper = CanvasLight, surface = SurfaceSoft,
    ink = InkLight, inkMute = MuteLight,
    hairline = HairlineLight, onFill = CanvasLight,
)

val LocalSlush = staticCompositionLocalOf { DarkSlush }
```

`paper` e `surface` juntos são a linguagem de profundidade inteira. Um card não
tem sombra nem moldura: ele é a superfície mais clara — ou mais escura — que o
fundo. `hairline` existe só para o caso em que dois tons iguais se encostam, e
some assim que houver degrau para fazer o trabalho.

`MuteDark` é guardado **já achatado**, e não como `Color.White.copy(alpha = .72f)`.
Alfa por cima de superfície variável dá um número de contraste por fundo, e o
teste passaria medindo o caso fácil.

### 4.1 Formas

Quatro degraus e uma pílula, cada um com dono
([REQ-DS-003](spec.md#req-ds-003--raios-e-contornos)).

```kotlin
val SlushShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),    // tag inline, chip pequeno
    small      = RoundedCornerShape(12.dp),   // campo, tile
    medium     = RoundedCornerShape(20.dp),   // card
    large      = RoundedCornerShape(20.dp),   // card e folha
    extraLarge = RoundedCornerShape(28.dp),   // folha de fundo, chrome
)
val Pill = CircleShape                        // nav, botoes, chips, badges
```

**Ação é pílula, conteúdo é 20dp.** A diferença entre o botão e o card passa a ser
a forma, e não a cor — o que sobrevive ao daltonismo e à troca de canvas sem
nenhuma condicional. Nada de raio intermediário: um card de 16dp lê como
componente de outro app, e o revisor não tem régua para discutir 16 contra 20.

### 4.2 Tipografia

A escala de origem (136/80/48px) pressupõe 1440px de largura. Em 360dp, o que
produz o mesmo efeito é o tipo ocupar de 60% a 90% da largura útil:

```kotlin
private val CrushedLeading = PlatformTextStyle(includeFontPadding = false)
private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

private fun display(size: TextUnit, tracking: Float) = TextStyle(
    fontFamily = Inter, fontWeight = FontWeight.W500,
    fontSize = size, lineHeight = 1.0.em, letterSpacing = tracking.em,
    platformStyle = CrushedLeading, lineHeightStyle = TrimBoth,
)

val DisplayXl = display(64.sp, -0.020f)   // onboarding
val Display   = display(44.sp, -0.015f)   // estados vazios, banners de secao
val DisplaySm = display(34.sp, -0.010f)   // saldo, total da fatura
```

**`includeFontPadding = false` e `Trim.Both` não são opcionais.** Sem os dois, o
Compose adiciona a folga de métrica da fonte acima e abaixo de cada linha, e a
entrelinha de 1.0 simplesmente não aparece na tela. O bloco fica com o espaçamento
de um parágrafo comum, e o empilhamento apertado — a única razão de a regra
existir — se perde silenciosamente. É o erro que se comete uma vez e demora a
diagnosticar, porque o código parece certo. Sobreviveu à troca de sistema visual
intacto; só o número que ele protege mudou.

**A entreletra troca de sinal entre o display e o corpo**, e isso é o desenho, não
descuido. O aperto negativo separa tipo grande de tipo apenas ampliado; a abertura
positiva do corpo é o que dá aos rótulos a precisão mecânica que o sistema pede.

```kotlin
private val TNUM = "tnum"   // algarismos tabulares: colunas de dinheiro alinham

val HeadingSm  = TextStyle(Inter, W500, 28.sp, lineHeight = 1.19.em, letterSpacing = (-0.01).em)
val Subheading = TextStyle(Inter, W500, 22.sp, lineHeight = 1.33.em)
val BodyLg     = TextStyle(Inter, W400, 18.sp, lineHeight = 1.56.em, letterSpacing = 0.005.em)
val Body       = TextStyle(Inter, W400, 16.sp, lineHeight = 1.5.em,  letterSpacing = 0.015.em)
val Caption    = TextStyle(Inter, W400, 13.sp, lineHeight = 1.4.em)
val Label      = TextStyle(Inter, W600, 14.sp, lineHeight = 1.43.em)   // nav, botoes

// Todo valor monetario passa por aqui.
val MoneyLg    = DisplaySm.copy(fontFeatureSettings = TNUM)
val MoneyBody  = Body.copy(fontWeight = W600, letterSpacing = 0.em, fontFeatureSettings = TNUM)
```

`MoneyBody` zera a entreletra que herdaria de `Body`. Tracking positivo num número
com `tnum` afasta os algarismos e desfaz o alinhamento que o `tnum` acabou de
garantir — `TypographyTest` guarda essa linha.

### 4.3 Fonte ampliada até 200%

[REQ-A11Y-004](spec.md#req-a11y-004--fonte-ampliada) exige 200% sem truncar. Como
`lineHeight` está em `em`, ele escala junto e a proporção travada se mantém — o
bloco só fica mais alto.

Duas condições para isso funcionar:

- Nenhum contêiner de display type tem altura fixa. Ele cresce.
- `DisplayXl` a 200% dá 128sp. Cabe em 360dp com 4–5 caracteres por linha, o que
  ainda é a estética pretendida. Palavra que não couber quebra; não se aplica
  reticências em display type — **truncar um título escultural é pior que
  quebrá-lo**.

## 5. Contraste medido

Calculado com a fórmula de luminância relativa da WCAG 2.1. **A coluna que decide
é a do meio**: `surface` é onde o conteúdo mora.

| Cor | sobre `#000000` | sobre `#16181A` | sobre `#F4F4F4` | Uso permitido |
|---|---|---|---|---|
| Branco `#ffffff` | 21.00:1 | 17.80:1 | 1.10:1 | texto no **escuro** |
| Ink `#191c1f` | 1.23:1 | 1.04:1 | 15.56:1 | texto no **claro** |
| Mute escuro `#b8b8b8` | 10.59:1 | 8.97:1 | — | texto secundário escuro |
| Mute claro `#505a63` | — | — | 6.40:1 | texto secundário claro |
| Cobalto `#494fdf` | 3.47:1 | 2.94:1 | 5.51:1 | preenchimento; **texto branco sobre ele** |
| Laranja `#ec7e00` | 7.55:1 | 6.40:1 | 2.53:1 | **preenchimento apenas** |
| Verde-azul `#00a87e` | 6.90:1 | 5.85:1 | 2.77:1 | **preenchimento apenas** |
| Amarelo `#b09000` | 6.84:1 | 5.79:1 | 2.79:1 | **preenchimento apenas** |
| Vermelho `#e23b4a` | 4.96:1 | 4.20:1 | 3.85:1 | **preenchimento apenas** |
| Verde `#428619` | 4.66:1 | 3.95:1 | 4.10:1 | **preenchimento apenas** |
| Rosa `#e61e49` | 4.65:1 | 3.94:1 | 4.11:1 | **preenchimento apenas** |
| Azul `#007bc2` | 4.61:1 | 3.91:1 | 4.14:1 | **preenchimento apenas** |
| Marrom `#936d62` | 4.60:1 | 3.90:1 | 4.15:1 | **preenchimento apenas** |

Duas leituras que a tabela torna óbvias e que o olho não daria:

1. **A coluna de preto puro mente.** As oito passam de 4.5:1 ali, e nenhuma passa
   nos dois temas. Medir esse fundo autorizaria a regra que a próxima coluna
   proíbe.
2. **Sobre a superfície clara, três acentos caem abaixo de 3:1** — Laranja 2.53,
   Verde-azulado 2.77 e Amarelo 2.79. É por isso que preenchimento pequeno leva
   anel de `ink`.

Branco sobre preenchimento saturado: **Cobalto 6.06:1 passa**, Vermelho 4.24:1
**reprova**. Os dois parecem igualmente seguros a olho, e é exatamente o par que
`ContrastTest` registra.

Os números vivem em `ContrastTest`, que recalcula a partir dos tokens e falha se
alguém mudar um hex ou usar um acento como cor de texto. Uma tabela em Markdown
mente assim que o token muda; o teste não.

## 6. Componentes

Todos com **elevação zero**, superfície em `surface` e ação em pílula.

```kotlin
// Acao primaria: pilula de `ink` sobre o canvas. Branca no escuro, preta no claro.
// E o pixel mais forte da tela — Cobalto e carimbo de card, nao cor de botao.
@Composable
fun FilledCta(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = LocalSlush.current.ink,
            contentColor = LocalSlush.current.paper,
        ),
        elevation = null,                                   // sem sombra, nunca
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
    ) { Text(text, style = Label) }
}

// Acao secundaria e chip nao selecionado: pilula contornada sobre o canvas.
// O contorno aqui nao e a moldura que sumiu das superficies — e o que distingue
// a acao secundaria da primaria sem usar uma segunda cor.
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
        contentPadding = PaddingValues(horizontal = 27.dp, vertical = 13.dp),
    ) { Text(text, style = Label) }
}
```

**Sombra é o erro fácil aqui**, porque o Material 3 a coloca sozinho. `Card`,
`Button`, `FloatingActionButton` e `Surface` trazem elevação por padrão, e
`Surface` ainda aplica *tonal elevation*, que tinge o fundo mesmo sem sombra
visível — o que, num sistema cuja profundidade inteira é um degrau de luminância,
inventa um terceiro tom que ninguém pediu
([REQ-DS-004](spec.md#req-ds-004--sem-sombra-e-sem-gradiente)):

```kotlin
Card(
    shape = SlushShapes.medium,                             // 20dp, sem border
    colors = CardDefaults.cardColors(containerColor = LocalSlush.current.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
)

Surface(color = LocalSlush.current.surface, tonalElevation = 0.dp, shadowElevation = 0.dp)
```

### 6.1 Barra de navegação

A tradução para as quatro abas de
[REQ-UI-001](spec.md#req-ui-001--navegação-principal) é uma barra em pill
flutuante, em vez da `NavigationBar` padrão do Material, que traz superfície tonal
e um quinto tom.

Seleção é sinalizada por **preenchimento**, não só por cor
([REQ-A11Y-003](spec.md#req-a11y-003--cor-não-é-sinal-único)).

Alvo de toque de 48dp mantido ([REQ-A11Y-002](spec.md#req-a11y-002--alvo-de-toque)),
que é maior que o padding do desenho. **A acessibilidade vence o token**; o padding
visual fica, o alvo é ampliado por `Modifier.minimumInteractiveComponentSize()`.

### 6.2 Sticker de categoria

Onde a paleta de oito cores mais rende. Cada categoria já tem cor e ícone
próprios, então o grid do lançamento rápido é uma cartela:

```kotlin
Box(
    Modifier
        .size(64.dp)
        .clip(SlushShapes.medium)
        .background(Color(category.colorArgb))          // preenchimento apenas
        .border(if (selecionado) 3.dp else 0.dp, LocalSlush.current.ink,
                SlushShapes.medium),
)
```

Seleção é a **presença do anel**, não a cor — cor já é a identidade da categoria,
e usá-la também para estado a tornaria sinal único duplamente sobrecarregado.
Presença lê melhor que espessura, que era como o sistema anterior sinalizava.

O nome fica **fora** do preenchimento: a paleta é preenchimento e nunca cor de
texto, e sobre o card cinco dos oito acentos reprovam em 4.5:1.

A amostra do seletor de cores é o caso oposto e merece nota: lá a cor **é** o
conteúdo, então ela leva anel de 1dp mesmo não selecionada (§2.2) e leva também
`contentDescription` com o nome falado — oito quadrados anunciados como "Cor"
deixam quem usa leitor de tela escolhendo às cegas
([REQ-A11Y-001](spec.md#req-a11y-001--descrição-de-conteúdo)).

### 6.3 Linha de transação

A tela mais densa do app, e por isso a que menos tolera vocabulário de pôster.

**O indicador de categoria é um ponto de 10dp, não um sticker.** Uma coluna de dez
blocos coloridos de 34dp tornava a lista ilegível, e o argumento não é de gosto:
**a categoria já aparece por extenso na linha de baixo** ("Alimentação · Carteira").
O bloco não carregava nenhuma informação que a linha já não tivesse.

O ponto mantém o anel de 1dp. A redundância com o subtítulo isentaria pela letra
da WCAG, mas Verde-azulado dá 2.77:1 sobre a superfície clara e Laranja 2.53:1 —
e 1dp custa menos para desenhar do que a isenção custa para defender.

**Estrutura da linha**, dentro do card `surface`:

| Elemento | Estilo |
|---|---|
| Ponto de categoria | 10dp, anel 1dp `ink`, alinhado à **primeira** linha |
| Descrição | `BodyStrong` 16sp, peso 500, uma linha com reticências |
| Categoria · conta | `Caption` 13sp em `inkMute`, **sempre na linha de baixo** |
| Valor | `MoneyBody` 16sp, peso 600, `tnum`, alinhado à direita |

Descrição e subtítulo são blocos empilhados, nunca lado a lado. Em Compose isso é
uma `Column`; o erro equivalente no protótipo em HTML foi deixá-los como `span`
inline, e os dois colaram na mesma linha.

## 7. O bloco em Cobalto

Cobalto é a assinatura da marca, e o sistema de origem o gasta em um lugar por
página: o card em destaque. Aqui ele é o **saldo total** do dashboard —
preenchimento cheio, texto `onFill`, sem contorno, porque o preenchimento saturado
já separa do canvas.

**Um por tela, no máximo.** Está em
[REQ-DS-009](spec.md#req-ds-009--intensidade-proporcional-à-densidade) como
`NÃO DEVE`, e não como recomendação, porque é a regra que se perde primeiro: cada
tela nova tem um elemento que "também merecia destaque", e ao terceiro Cobalto o
carimbo virou paleta.

**O que não entra:** Cobalto como cor de botão, de link ou de texto. Ele reprova
como texto nos dois fundos escuros (3.47:1 sobre preto, 2.94:1 sobre o card), e no
slot `primary` do Material viraria a cor de todo botão do app — que é exatamente
por que `primary` recebe `ink`.

## 8. O que não foi portado, e por quê

Registrar isto evita que alguém "complete" o design mais tarde achando que faltou.

- **A faixa promocional acima da nav.** É componente de página de marketing. Num
  app instalado não há promoção a anunciar, e a faixa custaria 40dp permanentes no
  topo de uma tela de 360dp.
- **Fotografia de produto full-bleed.** O sistema de origem usa o próprio aparelho
  fotografado como seção inteira. Um app não fotografa a si mesmo.
- **A grade de quatro planos.** Não há assinatura, nível nem preço aqui — e é
  justamente essa a razão de o app existir sem rede.
- **Tipo de 136px.** Não existe equivalente em 360dp. `DisplayXl` a 64sp é a
  tradução do *efeito*, não do número.
- **Largura máxima de 1200px.** Regra de web. O equivalente no Android é não
  restringir o conteúdo com margens grandes: 16dp de margem lateral, e o display
  type sangra até a borda quando o desenho pedir.

## 9. Verificação

| O que | Como |
|---|---|
| Contraste dos tokens | `ContrastTest` recalcula da paleta, **contra `surface`**, e falha em regressão |
| Acento como cor de texto | `ContrastTest`: nenhum dos oito passa nos dois temas |
| Anel em preenchimento pequeno | `ContrastTest` registra que algum acento fica abaixo de 3:1 no claro — se todos passarem, o anel virou decoração |
| Cor literal fora do tema | `TokenLintTest`: `Color(0x` proibido fora de `core/ui/theme/` |
| Cor semeada divergindo da paleta | `TokenLintTest` compara `CATEGORIAS_PADRAO` com `Acentos` |
| Sombra e elevação | `TokenLintTest`: `shadowElevation`/`defaultElevation` diferente de zero |
| Gradiente | `TokenLintTest`: `Brush.linearGradient` e afins proibidos |
| Raio fora da escala | revisão; os raios só existem em `SlushShapes` |
| Entrelinha travada | `TypographyTest` verifica `lineHeight == 1.0.em` e `includeFontPadding == false` nos três display |
| Entreletra trocando de sinal | `TypographyTest`: negativa no display, positiva no corpo, zero no dinheiro |
| Fonte 200% | verificação manual em T-020, junto com o resto da acessibilidade |

O item da entrelinha é o que mais importa: `includeFontPadding` volta a `true` com
uma linha distraída, e o sintoma — "o título ficou espaçado demais" — não parece
um bug de código.
