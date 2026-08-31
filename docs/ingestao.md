# Ingestão automática de transações

> **Documento de design.** Os requisitos normativos estão em
> [spec.md](spec.md) — `REQ-IMP-*`, `REQ-ACT-*`, `REQ-NOT-*` e `REQ-OF-*`. Aqui
> está o *como* e o *porquê*; lá está o *o quê*, com os critérios de aceite. Em
> caso de divergência, a spec ganha ([Art. 3](constitution.md#art-3--a-spec-é-a-verdade-divergência-é-bug-da-spec)).

Digitar transação à mão é o que mata o hábito. Mas "importação automática" no
Brasil não é uma feature — são três features com custos e bloqueios muito
diferentes. Este documento separa as três para que a mais barata entregue valor
sem esperar a mais cara. Decisão registrada em
[ADR-007](decisoes.md#adr-007--ingestão-em-três-camadas-open-finance-isolado-na-f4).

| Camada | Fase | Cobertura | Permissões | Backend | Bloqueio externo |
|---|---|---|---|---|---|
| **1. OFX / CSV** | F2 | Todo banco brasileiro | Nenhuma | Não | Nenhum |
| **2. Notificação** | F3 | Bancos digitais, em tempo real | `BIND_NOTIFICATION_LISTENER_SERVICE` | Não | Justificativa na Play |
| **3. Open Finance** | F4 | Tudo, automático | `INTERNET` | **Sim** | CNPJ + contrato + BCB |

**Mapa para os requisitos**

| Seção | Requisitos | Tasks |
|---|---|---|
| §1 OFX e CSV | `REQ-IMP-001`…`REQ-IMP-006` | T-037, T-038, T-041 |
| §3 Deduplicação | `REQ-IMP-007`…`REQ-IMP-009`, `REQ-IMP-011`, `REQ-IMP-012` | T-036, T-039, T-042 |
| §4 Auto-categorização | `REQ-ACT-001`…`REQ-ACT-004` | T-040 |
| §2 Notificações | `REQ-NOT-001`…`REQ-NOT-006` | T-043…T-046 |
| §5 Open Finance | `REQ-OF-001`…`REQ-OF-004` | T-047…T-049 |

**Regra que vale para as três: nada é gravado sem confirmação do usuário**
([REQ-IMP-010](spec.md#req-imp-010--revisão-obrigatória),
[Art. 14](constitution.md#art-14--importação-nunca-grava-sem-confirmação)). Toda
transação vinda de fora entra como sugestão numa tela de revisão. Um app que
inventa lançamento sozinho perde a confiança na primeira vez que erra, e não tem
segunda chance.

---

## 1. Camada 1 — OFX e CSV

A que entrega mais valor por linha de código. Todo banco brasileiro exporta OFX
(é o formato que o Money/Quicken consagrou e que o Febraban adotou). Zero
permissões, zero rede, zero aprovação de ninguém.

### 1.1 Fluxo

```
Importar → escolher conta de destino → escolher arquivo (SAF)
  → parse → dedupe → auto-categorização
  → TELA DE REVISÃO (o usuário edita, descarta, categoriza)
  → confirmar → grava tudo num ImportBatch
```

Seleção de arquivo por `ACTION_OPEN_DOCUMENT` (Storage Access Framework). Sem
`READ_EXTERNAL_STORAGE`, sem varrer a pasta Downloads.

```kotlin
val mimeTypes = arrayOf(
    "application/x-ofx", "application/ofx",
    "text/csv", "text/comma-separated-values",
    "text/plain",          // OFX exportado com extensão errada é comum
    "application/octet-stream"
)
```

### 1.2 OFX

Duas versões em circulação, e os dois casos precisam funcionar:

- **OFX 1.x** — SGML. Tags sem fechamento, header de chave-valor antes do corpo.
  É o que a maioria dos bancos brasileiros ainda exporta.
- **OFX 2.x** — XML bem-formado.

Ambos carregam o mesmo bloco:

```
<STMTTRN>
  <TRNTYPE>DEBIT
  <DTPOSTED>20260815120000[-3:GMT]
  <TRNAMT>-187.50
  <FITID>2026081500123456
  <MEMO>SUPERMERCADO XYZ LTDA
</STMTTRN>
```

Mapeamento:

| OFX | Campo | Observação |
|---|---|---|
| `TRNAMT` | `amountCents` | Parse como texto → centavos. **Nunca via `Double`.** |
| `DTPOSTED` | `date` | Pegar só `yyyyMMdd`. Ignorar hora e offset. |
| `FITID` | `dedupeKey` | Identificador único do banco. É o dedupe perfeito. |
| `MEMO` / `NAME` | `description` | `NAME` quando existir, senão `MEMO`. |
| `TRNTYPE` | `type` | Só valida o sinal de `TRNAMT`; o sinal manda. |

**Parser próprio, ~150 linhas.** Não existe biblioteca Kotlin/Java de OFX
mantida que valha a dependência, e o subconjunto necessário (`STMTTRN` dentro de
`BANKMSGSRSV1` ou `CREDITCARDMSGSRSV1`) é pequeno. Tratar OFX 1.x como XML com um
parser tolerante resolve os dois formatos com o mesmo código.

Armadilhas reais que vão aparecer, e que são caso de teste:

- Encoding `ISO-8859-1` (o header declara `CHARSET:1252`) — acento vira lixo se
  ler como UTF-8. **Ler o header antes de decidir o charset.**
- `TRNAMT` com vírgula decimal em alguns exportadores brasileiros
- `FITID` duplicado dentro do mesmo arquivo (acontece)
- Arquivo com mais de uma conta (`STMTRS` repetido) — importar só a que casa com
  a conta escolhida, ou perguntar

### 1.3 CSV

Sem biblioteca. `split` com tratamento de aspas resolve, e CSV de banco é simples.

O problema real do CSV não é parsear, é **mapear colunas** — cada banco usa uma
ordem. Solução: tela de mapeamento onde o usuário aponta qual coluna é data,
valor e descrição, com preview das 3 primeiras linhas. O mapeamento é salvo por
nome de arquivo/banco e reaproveitado na próxima importação.

Detectar automaticamente: separador (`,` ou `;`), formato de data
(`dd/MM/yyyy` vs `yyyy-MM-dd`), e decimal (`,` vs `.`). Acerta na maioria e o
usuário corrige o resto na tela de mapeamento.

CSV **não tem** `FITID`. Cai no dedupe por hash da §3.

---

## 2. Camada 2 — Notificações bancárias

Captura em tempo real: a compra no cartão aparece no app segundos depois de
acontecer, sem o usuário fazer nada.

### 2.1 Por que notificação e não SMS

`READ_SMS` é **permissão restrita** na Play Store. Só é concedida a apps que são
o handler padrão de SMS do aparelho, ou mediante formulário de declaração que
raramente é aprovado para app de finanças. Tentar esse caminho é apostar a
publicação do app numa aprovação improvável.

`BIND_NOTIFICATION_LISTENER_SERVICE` exige justificativa na Play Console e um
consentimento explícito do usuário nas configurações do Android — mas é um
caminho viável e usado por apps de finanças em produção. Além disso, notificação
cobre bancos digitais que nem mandam SMS.

### 2.2 Como funciona

`NotificationListenerService` recebe toda notificação do sistema. A primeira coisa
que o serviço faz é descartar o que não interessa:

```kotlin
override fun onNotificationPosted(sbn: StatusBarNotification) {
    // Allowlist explícita, definida pelo usuário nos ajustes.
    // Notificação de app fora dela é descartada sem ser lida.
    val parser = parsers[sbn.packageName] ?: return
    if (sbn.packageName !in settings.enabledBankApps) return
    ...
}
```

Isso não é detalhe de implementação, é a garantia de privacidade: o serviço tem
acesso técnico a todas as notificações (incluindo WhatsApp), então o filtro por
allowlist tem que ser a **primeira linha**, antes de qualquer leitura de texto.
E o texto capturado nunca é persistido — só o valor e a descrição extraídos.

Parser por banco, um regex sobre título + texto:

```kotlin
// Nubank: "Compra de R$ 45,90 aprovada em PADARIA CENTRAL"
// Inter:  "Você fez uma compra de R$ 45,90 em PADARIA CENTRAL"
private val AMOUNT = Regex("""R\$\s?([\d.]+,\d{2})""")
```

Resultado vira uma transação **pendente**, e o app dispara a própria notificação:
"Confirmar R$ 45,90 · PADARIA CENTRAL?" com ações rápidas de categoria. Um toque
e está lançado.

### 2.3 Limites, ditos na cara

Isso não é infraestrutura confiável, e a spec assume isso:

- **Os bancos mudam o texto sem avisar.** O parser quebra e ninguém é notificado.
  Mitigação: se um app da allowlist ficar 30 dias sem nenhum match, avisar o
  usuário que a captura pode ter parado.
- **Não há categoria na notificação.** Só valor e estabelecimento. A categoria vem
  da auto-categorização da §4 ou do usuário.
- **Estorno e cancelamento não chegam.** Só compra. Só a Camada 1 ou 3 fecha a conta.
- **Se o regex não bate, ignora em silêncio.** Nunca notificar "não consegui ler" —
  isso vira spam e o usuário desliga o serviço.

Por isso a Camada 2 **complementa** a Camada 1, não a substitui. O usuário
concilia por OFX de tempos em tempos e o dedupe cuida da sobreposição.

---

## 3. Deduplicação

O núcleo de qualquer ingestão. Sem isso, importar duas vezes duplica a vida
financeira do usuário. Três níveis, do mais confiável ao mais frouxo:

**Nível 1 — `FITID` (só OFX).** Identificador único emitido pelo banco. Match
exato = mesma transação, ponto final. Descarta automaticamente, sem perguntar.

```
dedupeKey = "ofx:$fitid"
```

**Nível 2 — hash determinístico.** Para CSV e notificação, que não têm ID:

```kotlin
fun dedupeKey(accountId: Long, date: LocalDate, cents: Long, desc: String): String =
    "h:" + sha256("$accountId|${date.toEpochDay()}|$cents|${normalize(desc)}")

/** Descrições de banco vêm sujas e instáveis. Normalizar antes de comparar. */
fun normalize(s: String): String = s
    .uppercase()
    .let { Normalizer.normalize(it, Normalizer.Form.NFD) }
    .replace(Regex("\\p{M}"), "")          // remove acentos
    .replace(Regex("\\d{4,}"), "")         // NSU, autorização, nº do cartão
    .replace(Regex("[^A-Z0-9 ]"), " ")
    .replace(Regex("\\s+"), " ")
    .trim()
```

Match exato = duplicata. Descarta automaticamente.

**Nível 3 — janela difusa.** Mesma conta, mesmo valor exato, data dentro de ±3
dias, descrição diferente. Acontece porque a data da compra e a data de
lançamento no extrato divergem.

Aqui o app **não decide**. Marca como *possível duplicata*, mostra as duas lado a
lado na tela de revisão e o usuário escolhe. Descartar automaticamente com base
em heurística é como se perde uma transação legítima — e duas compras de R$ 20 na
mesma padaria no mesmo dia são um caso normal, não um erro.

**Rede de segurança:** o índice único parcial `idx_txn_dedupe` (arquitetura.md
§4.1) faz o banco recusar a duplicata mesmo se as três checagens falharem.

### 3.1 Desfazer importação

Todo lote gravado tem `importBatchId`. A tela de importações lista os lotes com
data, origem e quantidade, e permite **desfazer o lote inteiro** — deleta as
transações daquele `importBatchId` que ainda não foram editadas manualmente.

É a válvula de escape. Sem ela, uma importação errada de 400 linhas só se resolve
apagando o app.

---

## 4. Auto-categorização

Sem ML, sem serviço externo. Memória de escolhas do usuário, na tabela `payee_rule`.

1. Ao salvar uma transação com categoria, grava/incrementa
   `payee_rule(normalize(description) → categoryId)`.
2. Na importação, procura a chave normalizada. Achou → pré-seleciona a categoria.
3. Não achou → deixa sem categoria, destacada na tela de revisão.

Acerto ruim nas 10 primeiras importações e muito bom depois, porque as despesas
de uma pessoa se repetem. Um classificador de verdade não bate isso o suficiente
para justificar o custo.

Categorias padrão semeadas já cobrem os estabelecimentos mais óbvios por
palavra-chave (`UBER` → Transporte, `IFOOD` → Alimentação, `NETFLIX` →
Assinaturas) — uma lista de ~40 pares no seed inicial, para o app não começar burro.

---

## 5. Camada 3 — Open Finance

**Esta é a única parte do projeto com bloqueio externo real.** Não é questão de
esforço de desenvolvimento.

### 5.1 A restrição

Open Finance Brasil é regulado pelo Banco Central. Para consumir dados de conta
de um cliente, é preciso ser **instituição participante autorizada pelo BCB** —
ou contratar um **agregador** que já seja: Pluggy, Belvo ou Klavi.

Um app pessoal, publicado por pessoa física, não obtém acesso direto. O caminho
viável é o agregador, e ele exige:

- **CNPJ** e contrato comercial assinado
- **Custo por conexão/mês** (não é gratuito em produção)
- **Um backend seu.** As credenciais do agregador não podem ficar no APK — quem
  extrair o app teria acesso aos dados de todos os usuários. Isso é inegociável.

### 5.2 O que isso implica

```
App  →  seu backend  →  agregador  →  banco
```

Adotar a Camada 3 significa passar a operar um servidor, com tudo que vem junto:
autenticação de usuário, custo mensal, LGPD como operador de dados, disponibilidade
e uma superfície de ataque que hoje não existe.

Ou seja: **a Camada 3 quebra a premissa "offline, sem cadastro, sem servidor"**
que define o produto nas fases F0–F3.

### 5.3 Decisão

Open Finance fica na **F4, como opt-in explícito**, e o app continua 100%
funcional sem ela. Quem não conectar nunca vê a diferença — e o app segue sem
permissão de `INTERNET` no manifesto até que essa fase exista de fato.

Antes de investir em F4, medir: se as Camadas 1 e 2 já reduzirem o lançamento
manual a um nível aceitável, a F4 pode simplesmente não valer o preço.

Se e quando for feita, o consentimento do Open Finance tem prazo máximo de 12
meses por regra do BCB — o app precisa avisar a renovação com antecedência, senão
a sincronização morre em silêncio e o usuário só descobre quando os dados já estão
desatualizados.

### 5.4 Ordem de trabalho

F2 e F3 primeiro, sempre. Elas compartilham com a F4 o dedupe (§3), a
auto-categorização (§4) e a tela de revisão — ou seja, quando a F4 chegar, a
maior parte da ingestão já está construída e testada. Só o transporte é novo.
