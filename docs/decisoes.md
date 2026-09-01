# Decisões de arquitetura (ADR)

Registro do que foi decidido, por quê, e o que se perdeu ao decidir. Existe para
que ninguém reabra em seis meses uma questão já pesada — e para que quem reabrir
com informação nova saiba exatamente contra o que está argumentando.

Status: `Aceita` · `Substituída por ADR-N` · `Revisar em F<n>`

---

## ADR-001 — Kotlin + Jetpack Compose

**Status** Aceita · **Afeta** todo o app

**Contexto.** Foi levantada a possibilidade de usar Java.

**Decisão.** Kotlin com Compose.

**Razão.** O compilador do Compose é um plugin do compilador Kotlin. Não existe
`@Composable` em Java — escolher Java descarta a camada de UI inteira e volta
para XML + Fragments + `ViewBinding`, além de trocar `Flow` por RxJava ou
`LiveData`.

O custo aparece concentrado numa tela específica: o bottom sheet de lançamento
rápido ([REQ-UI-002](spec.md#req-ui-002--lançamento-em-três-toques)), onde campos aparecem e somem conforme
o tipo selecionado. São ~60 linhas de Compose contra um `Fragment` com
`View.GONE`/`VISIBLE` manual e sincronização de estado à mão.

**Alternativas rejeitadas.**

- *Java + XML* — viável, mas mais código em todas as camadas de UI, e Kotlin é o
  padrão oficial do Android desde 2019. Só compensaria com equipe Java existente
  ou código legado a integrar, o que não é o caso.
- *Híbrido (domínio em Java, UI em Kotlin)* — duas linguagens e dois modelos de
  async no mesmo projeto. Pior que escolher um lado.

**Consequência.** `minSdk 26` e JVM target 17. Toda contratação futura precisa de Kotlin.

---

## ADR-002 — Dinheiro é `Long` em centavos

**Status** Aceita · **Afeta** todas as camadas · **Constituição** Art. 6, Art. 7

**Contexto.** Representação de valor monetário.

**Decisão.** `Long` de centavos em modelo, banco, cálculo e parser. Formatação
para texto só no Composable.

**Razão.** `0.1 + 0.2 != 0.3` em ponto flutuante binário. Num app de finanças
isso vira saldo que não fecha com o extrato do banco, que é a falha que destrói a
confiança no produto.

`BigDecimal` também resolveria a precisão, mas custa alocação por operação, não
tem suporte nativo em Room (exige converter), e ainda permite escala errada. `Long`
é primitivo, cabe no SQLite sem conversão, e o valor máximo cobre ~92 quatrilhões
de reais.

**Ponto de vazamento real.** `Double` não entra por cálculo, entra por **parser**:
`"187.50".toDouble()` na importação de OFX/CSV. Por isso a conversão texto → centavos
é feita por manipulação de texto, e isso é caso de teste
([REQ-IMP-004](spec.md#req-imp-004--conversão-sem-ponto-flutuante)).

**Consequência.** `amountCents = 18750` em todo lugar. Ninguém lê `18750` como
R$ 187,50 sem pensar — é o preço, e é barato perto do alternativo.

---

## ADR-003 — Transferência é uma linha, não duas

**Status** Aceita · **Afeta** `txn`, cálculo de saldo, pagamento de fatura

**Contexto.** Transferência entre contas do usuário afeta duas contas. O padrão
contábil clássico (partida dobrada) grava dois lançamentos espelhados.

**Decisão.** Um registro, com `accountId` (origem), `counterAccountId` (destino) e
`amountCents` negativo. `amountCents` é sempre o efeito líquido em `accountId`.

```
saldo(X) = initialBalanceCents(X)
         + SUM(amountCents) WHERE accountId = X
         - SUM(amountCents) WHERE counterAccountId = X
```

**Razão.** Duas linhas exigem mantê-las em sincronia em **toda** operação: editar
valor, editar data, excluir, desfazer lote de importação, migração. Cada uma
dessas é uma chance de deixar meia transferência órfã — e meia transferência é
dinheiro inventado ou dinheiro sumido no saldo do usuário.

Uma linha custa um `SUM` a mais numa query, escrita uma vez.

**Ganho não previsto, mas decisivo.** Pagamento de fatura de cartão vira uma
`TRANSFER` da conta corrente para o cartão. Entra como `counterAccountId = cartão`,
ou seja `+valor` no saldo do cartão, abatendo a dívida. **Zero código especial**
para o que costuma ser a parte mais confusa de um app de finanças ([ADR-004](#adr-004--fatura-de-cartão-é-derivada)).

**Alternativas rejeitadas.**

- *Duas linhas espelhadas* — pelo custo de sincronia acima.
- *Partida dobrada completa (ledger com débito/crédito)* — correto de contabilidade,
  e complexidade que um app pessoal não usa. Reabrir só se entrar conciliação
  contábil de verdade.

**Consequência.** Toda query de saldo tem dois termos. Está encapsulada em
`AccountBalance`; ninguém escreve à mão.

---

## ADR-004 — Fatura de cartão é derivada

**Status** Aceita · **Afeta** `account`, tela de cartão

**Contexto.** Cartão de crédito tem ciclo de fechamento e vencimento. A fatura é o
conceito central da tela de cartão.

**Decisão.** Não existe tabela `invoice`. A fatura é uma **função** sobre as
transações do cartão:

```kotlin
fun invoiceMonthFor(purchaseDate: LocalDate, closingDay: Int): YearMonth =
    if (purchaseDate.dayOfMonth <= closingDay) YearMonth.from(purchaseDate)
    else YearMonth.from(purchaseDate).plusMonths(1)
```

`closingDay` e `dueDay` são restritos a **1–28** na validação de entrada.

**Razão.** Fatura materializada precisa ser criada, fechada, reaberta quando o
usuário edita a data de uma compra antiga, e recalculada em toda importação.
Estado derivado armazenado é estado que sai de sincronia.

O limite 1–28 elimina a classe inteira de bugs de "dia 31 em fevereiro" **sem
uma linha de tratamento**. Bancos brasileiros não usam dia 29–31 para fechamento,
então não se perde caso real. Isso é diferente da recorrência ([ADR-006](#adr-006--recorrência-materializa-sob-demanda-com-horizonte-de-60-dias)),
onde o usuário legitimamente tem conta que vence dia 30 e o clamp é obrigatório.

**Regra de escopo.** Fatura = transações com `accountId = cartão` **e
`type != TRANSFER`**. Pagamentos não pertencem a fatura nenhuma: abatem o saldo
do cartão, não compõem a conta do mês.

**Status da fatura também é derivado** — `Aberta` / `Fechada` / `Paga` sai de
comparar a data de hoje e a soma dos pagamentos com o total. Nada armazenado.

**Consequência.** O agrupamento por competência acontece em Kotlin, não em SQL —
SQLite não tem aritmética de data decente, e replicar a regra em `@Query` criaria
a segunda fonte de verdade que o Art. 9 proíbe. A query traz uma janela ampla de
datas e o domínio filtra.

---

## ADR-005 — Parcelamento materializa N linhas na criação

**Status** Aceita · **Afeta** `txn`, fatura, orçamento, relatórios

**Contexto.** Compra parcelada em 6x precisa aparecer em 6 faturas futuras.

**Decisão.** Gravar 6 transações na criação, ligadas por `installmentGroupId`
(UUID), com `installmentIndex` 1..6 e `installmentTotal` 6.

**Razão.** A alternativa — uma linha expandida sob demanda — parece mais enxuta
até se contar os lugares que precisam da expansão: fatura de daqui a 4 meses,
orçamento do mês que vem, projeção de fluxo, relatório de 12 meses, filtro por
categoria. Cinco pontos de expansão é mais código, e mais chance de divergirem,
do que 6 `INSERT`s numa transação de banco.

**Arredondamento — a parte que importa.** `R$ 600,00 / 7` não fecha. A sobra vai
toda na **última** parcela:

```
parcela[i] = total / n                        para i = 1..n-1
parcela[n] = total - (n-1) * (total / n)
```

A soma das parcelas é sempre exatamente igual ao total, para todo `n` de 1 a 72.
Isso é teste, não esperança (Art. 7, [REQ-TXN-008](spec.md#req-txn-008--soma-das-parcelas-é-exata)).

**Consequência.** Editar ou excluir uma parcela precisa perguntar o escopo:
*só esta* | *esta e as futuras* | *todas*. É a UI que a decisão cobra de volta.

---

## ADR-006 — Recorrência materializa sob demanda, com horizonte de 60 dias

**Status** Aceita, com o worker adiado (ver abaixo) · **Afeta** `recurring_rule`

**Contexto.** Lançamentos fixos (salário, aluguel, assinatura) se repetem sem data
final.

**Decisão.** A regra fica em `recurring_rule`. Um `WorkManager` diário, e a
abertura do app, geram as ocorrências pendentes até **hoje + 60 dias**.
`lastGeneratedDate` marca até onde já foi materializado.

**Razão.** Gerar o futuro inteiro é impossível (regra sem fim) e gerar 10 anos
enche o banco de lixo que o usuário nunca vê. 60 dias cobre a tela de "próximas
contas" e a projeção do mês seguinte, que é o horizonte real de quem controla
finanças pessoais.

**Idempotência é requisito, não detalhe.** O gerador roda na abertura do app e no
worker — rodar duas vezes no mesmo dia não pode duplicar nada
([REQ-REC-003](spec.md#req-rec-003--geração-idempotente)). Sem isso, quem abre o app três vezes num dia
ganha três aluguéis.

**Clamp de dia do mês.** Regra mensal com `dayOfMonth = 31` cai no último dia do
mês curto (28, 29 ou 30). Aqui o clamp é necessário — diferente do cartão
([ADR-004](#adr-004--fatura-de-cartão-é-derivada)) — porque contas que vencem dia
30 ou 31 são comuns e o usuário perderia o lançamento.

**Consequência.** Alterar a regra não reescreve ocorrências já efetivadas.
Reescreve as futuras não efetivadas. O histórico é imutável.

**Revisão na T-031: o worker não entrou.** A abertura do app é o único gatilho.
Materializar em segundo plano só valeria se alguma coisa lesse o resultado com o
app fechado, e nada lê: não há lembrete de vencimento, não há widget, e a
permissão de rede está barrada até a F4 ([ADR-010](#adr-010--sqlcipher-e-sem-permissão-de-rede-até-a-f4)).
O worker gravaria linhas que ninguém vê antes da próxima abertura — que é
exatamente quando o gatilho de abertura já roda.

O preço de adiar é conhecido e é zero hoje: o `WorkManager` traria três
dependências, uma `WorkerFactory` do Hilt, a remoção do inicializador padrão no
manifesto e três permissões transitivas para o `ManifestTest` vigiar. O que ele
mudaria de observável é nada. No dia em que existir consumidor de fundo — e o
primeiro candidato é o lembrete de vencimento — ele entra como uma classe:
`RecurringRepository.gerarPendentes(hoje)` já é o corpo inteiro do worker.

---

## ADR-007 — Ingestão em três camadas; Open Finance isolado na F4

**Status** Aceita · **Afeta** roadmap, manifesto, modelo de negócio ·
**Detalhe** [ingestao.md](ingestao.md)

**Contexto.** O pedido original foi "importação automática (Open Finance / SMS / OFX)".

**Decisão.** Separar em três camadas independentes e entregá-las em ordem de custo
crescente:

| Camada | Fase | Permissão | Backend | Bloqueio |
|---|---|---|---|---|
| OFX / CSV | F2 | nenhuma | não | nenhum |
| Notificação bancária | F3 | `BIND_NOTIFICATION_LISTENER_SERVICE` | não | justificativa na Play |
| Open Finance | F4 | `INTERNET` | **sim** | CNPJ + contrato + BCB |

**Razão — SMS está fora.** `READ_SMS` é permissão restrita na Play Store, concedida
só a apps que são o handler padrão de SMS ou por formulário raramente aprovado
para finanças. Apostar a publicação do app nessa aprovação não é aceitável.
`NotificationListenerService` cobre o mesmo caso de uso, é viável na Play, e ainda
alcança bancos digitais que não mandam SMS.

**Razão — Open Finance não é questão de esforço.** Consumir dados de conta exige
ser instituição autorizada pelo BCB, ou contratar agregador (Pluggy, Belvo, Klavi).
O agregador exige CNPJ, contrato pago, e **um backend próprio** — as credenciais
não podem ficar no APK, senão quem extrair o app acessa os dados de todos os
usuários.

Isso significa operar servidor, autenticação, custo mensal, LGPD como operador e
uma superfície de ataque que hoje não existe. **A F4 quebra a premissa
"offline, sem cadastro, sem servidor"** que define o produto nas fases F0–F3.

**Consequência.** A F4 é opt-in e o app é 100% funcional sem ela. Antes de
investir, medir: se F2 e F3 reduzirem o lançamento manual o suficiente, a F4 pode
não valer o preço. F2 e F3 já constroem o dedupe, a auto-categorização e a tela
de revisão que a F4 reusaria — quando ela chegar, só o transporte é novo.

**Revisar em** F3, com dados de uso reais.

---

## ADR-008 — Módulo Gradle único

**Status** Aceita · **Revisar em** F2

**Decisão.** Um módulo `:app`. Separação por pacote.

**Razão.** Multi-módulo resolve tempo de build em projeto grande e reuso entre
apps. Este app não tem nenhum dos dois problemas hoje, e paga adiantado em
configuração de Gradle, DI entre módulos e navegação.

**Gatilho de reversão.** Build incremental passar de ~1 minuto. O primeiro corte
natural é `:core:database`.

---

## ADR-009 — Sem Paging 3 no MVP

**Status** Aceita · **Constituição** Art. 11

**Decisão.** A lista de transações carrega o mês inteiro em memória.

**Razão.** A visão padrão é filtrada por mês: ~100 linhas. Paging 3 traz
`PagingSource`, `RemoteMediator`, estados de load e testes próprios para resolver
um problema que este app não tem.

**Gatilho de reversão.** O filtro "todos os períodos" ficar perceptivelmente lento
— na prática, acima de ~5.000 linhas. Marcado no código com comentário `ponytail:`.

---

## ADR-010 — SQLCipher, e sem permissão de rede até a F4

**Status** Aceita · **Constituição** Art. 13

**Decisão.** Banco criptografado com SQLCipher, chave de 32 bytes no Android
Keystore. `allowBackup="false"`. Nenhuma permissão `INTERNET` no manifesto nas
fases F0–F3.

**Razão.** O sandbox do Android já protege contra outro app ler o banco — mas não
contra aparelho com root, extração física, nem backup em nuvem do sistema. Para
dado financeiro, ~20 linhas de configuração é preço baixo demais para recusar.

A ausência de `INTERNET` é a parte mais valiosa: é uma garantia que o usuário
**verifica sozinho** nas informações do app, não uma promessa numa política de
privacidade que ninguém lê.

**Consequência.** Nada de crash reporter, analytics ou remote config. Diagnóstico
de bug depende do usuário exportar e enviar — o que é exatamente o comportamento
que o Art. 13 quer.
