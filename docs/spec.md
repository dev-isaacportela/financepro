# Especificação de requisitos — mobile-finance

Fonte de verdade do comportamento do app. Nenhuma feature existe sem um `REQ-*`
aqui ([constitution.md](constitution.md) Art. 1 e 2).

## Como ler

Requisitos usam **EARS** (*Easy Approach to Requirements Syntax*). Cinco formas,
e nenhuma outra:

| Forma | Padrão |
|---|---|
| Ubíqua | `O SISTEMA DEVE <comportamento>` |
| Evento | `QUANDO <gatilho>, O SISTEMA DEVE <resposta>` |
| Estado | `ENQUANTO <estado>, O SISTEMA DEVE <comportamento>` |
| Opcional | `ONDE <recurso ativo>, O SISTEMA DEVE <comportamento>` |
| Indesejada | `SE <condição>, ENTÃO O SISTEMA DEVE <resposta>` |

Cada requisito traz uma linha de metadados:

```
`F1` · `MUST` · Teste: `InvoiceMonthTest`
```

- **Fase** — `F0`..`F4`, conforme o roadmap do [README](../README.md)
- **Prioridade** — `MUST` (não entrega a fase sem) ou `SHOULD` (adia sem sangrar)
- **Teste** — a classe de teste que o cobre, ou `manual` para o que só verificação
  humana alcança. Todo `MUST` com classe nomeada precisa de `@Req("REQ-...")`
  no teste, e `tools/trace.py` valida.

Ambiguidade não resolvida aparece como `[NEEDS-CLARIFICATION: ...]` e **bloqueia**
a task correspondente (Art. 5).

## Índice

| Prefixo | Domínio | Requisitos |
|---|---|---|
| [`REQ-CORE`](#core--regras-transversais) | Dinheiro, mês, moeda | 5 |
| [`REQ-ACC`](#acc--contas) | Contas e saldo | 7 |
| [`REQ-CAT`](#cat--categorias) | Categorias | 6 |
| [`REQ-TXN`](#txn--transações) | Transações e parcelamento | 13 |
| [`REQ-CARD`](#card--cartão-de-crédito) | Cartão e fatura | 9 |
| [`REQ-BUD`](#bud--orçamento) | Orçamento mensal | 5 |
| [`REQ-REC`](#rec--recorrências) | Lançamentos recorrentes | 8 |
| [`REQ-IMP`](#imp--importação-de-arquivo) | OFX e CSV | 12 |
| [`REQ-ACT`](#act--auto-categorização) | Auto-categorização | 4 |
| [`REQ-NOT`](#not--captura-por-notificação) | Notificação bancária | 6 |
| [`REQ-OF`](#of--open-finance) | Open Finance | 4 |
| [`REQ-RPT`](#rpt--relatórios) | Relatórios | 4 |
| [`REQ-UI`](#ui--interface) | Navegação e telas | 7 |
| [`REQ-DS`](#ds--sistema-visual) | Sistema visual | 10 |
| [`REQ-A11Y`](#a11y--acessibilidade) | Acessibilidade | 6 |
| [`REQ-SEC`](#sec--segurança) | Segurança | 7 |
| [`REQ-BAK`](#bak--backup-e-exportação) | Backup e export | 4 |
| [`REQ-DATA`](#data--integridade-de-dados) | Integridade | 3 |

---

# CORE — regras transversais

### REQ-CORE-001 — Dinheiro em centavos

`F0` · `MUST` · Teste: `MoneyTest`

O SISTEMA DEVE representar todo valor monetário como `Long` de centavos em
modelo, persistência, cálculo e desserialização, e converter para texto apenas na
camada de apresentação.

SE um valor monetário for representado como `Double`, `Float` ou `BigDecimal` em
qualquer camada, ENTÃO a revisão DEVE rejeitar a mudança.

Ver [ADR-002](decisoes.md#adr-002--dinheiro-é-long-em-centavos).

### REQ-CORE-002 — Valor não nulo

`F0` · `MUST` · Teste: `ValidateTxnTest`

SE `amountCents == 0`, ENTÃO O SISTEMA DEVE recusar a transação com a mensagem
"Informe um valor".

### REQ-CORE-003 — Mês configurável

`F0` · `MUST` · Teste: `MonthRangeTest`

O SISTEMA DEVE derivar todo período mensal (dashboard, orçamento, relatórios) de
um único `monthStartDay` configurável, com padrão `1`.

| Dado `monthStartDay` | Mês de referência | Período |
|---|---|---|
| 1 | 2026-08 | 2026-08-01 a 2026-08-31 |
| 5 | 2026-08 | 2026-08-05 a 2026-09-04 |
| 31 | 2026-02 | 2026-02-28 a 2026-03-30 |

O terceiro caso é o que exige clamp: `monthStartDay` maior que o último dia do
mês cai no último dia.

### REQ-CORE-004 — Moeda única

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE operar exclusivamente em BRL. Conversão de câmbio e multi-moeda
estão fora de escopo em todas as fases.

### REQ-CORE-005 — Formatação pt-BR

`F0` · `MUST` · Teste: `MoneyFormatTest`

O SISTEMA DEVE formatar valores como `R$ 1.234,56`, com separador de milhar `.` e
decimal `,`, e DEVE exibir despesa com sinal `−` explícito.

---

# ACC — contas

### REQ-ACC-001 — Cadastro de conta

`F0` · `MUST` · Teste: `AccountDaoTest`

O SISTEMA DEVE permitir criar, editar e listar contas com nome, tipo, cor, ícone e
saldo de abertura.

### REQ-ACC-002 — Tipos de conta

`F0` · `MUST` · Teste: `AccountDaoTest`

O SISTEMA DEVE suportar os tipos `CHECKING`, `SAVINGS`, `CASH`, `CREDIT_CARD` e
`INVESTMENT`.

ONDE o tipo for `CREDIT_CARD`, O SISTEMA DEVE exigir `creditLimitCents`,
`closingDay` e `dueDay`, e DEVE ocultar esses campos para os demais tipos.

### REQ-ACC-003 — Saldo de abertura

`F0` · `MUST` · Teste: `AccountBalanceTest`

O SISTEMA DEVE armazenar `initialBalanceCents` representando o saldo existente
antes do primeiro lançamento no app, e DEVE incluí-lo no saldo calculado.

### REQ-ACC-004 — Cálculo de saldo

`F0` · `MUST` · Teste: `AccountBalanceTest`

O SISTEMA DEVE calcular o saldo de uma conta como:

```
saldo(X) = initialBalanceCents(X)
         + SUM(amountCents) WHERE accountId = X        AND cleared = 1
         - SUM(amountCents) WHERE counterAccountId = X AND cleared = 1
```

Casos de aceite:

| Cenário | Esperado |
|---|---|
| Conta abre com R$ 100, despesa de R$ 30 | saldo R$ 70 |
| Transferência de R$ 50 de A para B | A cai 50, B sobe 50 |
| Transferência de R$ 50 de A para B | **soma(A+B) inalterada** |
| Transação com `cleared = 0` | não altera o saldo |

A terceira linha é o invariante do Art. 7: transferência não cria nem destrói
dinheiro. Ver [ADR-003](decisoes.md#adr-003--transferência-é-uma-linha-não-duas).

### REQ-ACC-005 — Arquivar conta

`F0` · `SHOULD` · Teste: `AccountDaoTest`

QUANDO o usuário arquiva uma conta, O SISTEMA DEVE removê-la das listas de
seleção e do saldo total, e DEVE preservar todas as suas transações no histórico
e nos relatórios.

### REQ-ACC-006 — Conta arquivada é somente leitura

`F0` · `MUST` · Teste: `ValidateTxnTest`

SE a conta de destino de um novo lançamento estiver arquivada, ENTÃO O SISTEMA
DEVE recusar com "Conta arquivada".

### REQ-ACC-007 — Saldo total exclui cartões

`F0` · `MUST` · Teste: `AccountBalanceTest`

O SISTEMA DEVE calcular o "saldo total" do dashboard somando apenas contas não
arquivadas de tipo diferente de `CREDIT_CARD`, e DEVE exibir a dívida de cartão
como número separado.

Misturar os dois faz o usuário acreditar que tem mais dinheiro do que tem. É o
erro clássico da categoria, e é requisito não cometê-lo.

---

# CAT — categorias

### REQ-CAT-001 — Cadastro de categoria

`F0` · `MUST` · Teste: `CategoryDaoTest`

O SISTEMA DEVE permitir criar, editar, listar e arquivar categorias com nome,
`kind`, ícone e cor.

### REQ-CAT-002 — Hierarquia de um nível

`F0` · `MUST` · Teste: `CategoryDaoTest`

O SISTEMA DEVE suportar subcategorias com exatamente um nível de profundidade.

SE o usuário tentar criar subcategoria de uma subcategoria, ENTÃO O SISTEMA DEVE
recusar.

### REQ-CAT-003 — Natureza da categoria

`F0` · `MUST` · Teste: `ValidateTxnTest`

O SISTEMA DEVE classificar cada categoria como `INCOME` ou `EXPENSE`.

SE o `kind` da categoria não corresponder ao tipo da transação, ENTÃO O SISTEMA
DEVE recusar com "Categoria de receita em uma despesa".

### REQ-CAT-004 — Categorias semeadas

`F0` · `MUST` · Teste: `SeedTest`

QUANDO o banco é criado, O SISTEMA DEVE semear as categorias: Alimentação,
Transporte, Moradia, Saúde, Lazer, Educação, Compras, Assinaturas, Salário e
Outros.

### REQ-CAT-005 — Exclusão protegida

`F0` · `MUST` · Teste: `CategoryDaoTest`

SE uma categoria possuir transações, ENTÃO O SISTEMA DEVE recusar a exclusão e
DEVE exibir "Mova as N transações antes", oferecendo recategorização em lote.

A proteção é imposta no banco por `ON DELETE RESTRICT`, não apenas em código.

### REQ-CAT-006 — Ordenação por frequência

`F0` · `SHOULD` · Teste: `CategoryDaoTest`

O SISTEMA DEVE ordenar o grid de categorias do lançamento rápido por `useCount`
decrescente, e DEVE incrementar `useCount` a cada transação salva.

---

# TXN — transações

### REQ-TXN-001 — Cadastro de transação

`F0` · `MUST` · Teste: `TxnDaoTest`

O SISTEMA DEVE permitir criar, editar, excluir e listar transações com conta,
tipo, valor, data, categoria, descrição e observação.

### REQ-TXN-002 — Tipos e convenção de sinal

`F0` · `MUST` · Teste: `TxnSignTest`

O SISTEMA DEVE tratar `amountCents` como o efeito líquido na conta `accountId`:

| Tipo | Sinal | Exemplo |
|---|---|---|
| `INCOME` | positivo | `+450000` |
| `EXPENSE` | negativo | `-18750` |
| `TRANSFER` | negativo | `-100000` (saída da origem) |

### REQ-TXN-003 — Transferência em registro único

`F0` · `MUST` · Teste: `TransferTest`

O SISTEMA DEVE gravar transferência como **um** registro com `accountId` de origem
e `counterAccountId` de destino.

SE `counterAccountId` for nulo ou igual a `accountId`, ENTÃO O SISTEMA DEVE
recusar com "Escolha uma conta de destino diferente".

### REQ-TXN-004 — Transferência não tem categoria

`F0` · `MUST` · Teste: `ValidateTxnTest`

QUANDO o tipo é `TRANSFER`, O SISTEMA DEVE definir `categoryId` como nulo e DEVE
ocultar o campo de categoria na UI.

Transferência não é receita nem despesa, e contá-la como tal duplicaria o valor
nos relatórios.

### REQ-TXN-005 — Categoria obrigatória

`F0` · `MUST` · Teste: `ValidateTxnTest`

SE o tipo for `INCOME` ou `EXPENSE` e `categoryId` for nulo, ENTÃO O SISTEMA DEVE
recusar com "Escolha uma categoria".

### REQ-TXN-006 — Efetivada e prevista

`F0` · `MUST` · Teste: `AccountBalanceTest`

O SISTEMA DEVE distinguir transação efetivada (`cleared = 1`) de prevista
(`cleared = 0`), DEVE excluir previstas do saldo, e DEVE exibi-las separadamente
como "Próximas contas".

### REQ-TXN-007 — Compra parcelada

`F1` · `MUST` · Teste: `SplitInstallmentsTest`

QUANDO o usuário lança uma compra em `n` parcelas, O SISTEMA DEVE criar `n`
transações com o mesmo `installmentGroupId`, `installmentIndex` de 1 a `n`, e
datas espaçadas de um mês.

O campo de parcelas DEVE aparecer apenas quando a conta selecionada for
`CREDIT_CARD`. O SISTEMA DEVE aceitar `n` entre 1 e 72.

### REQ-TXN-008 — Soma das parcelas é exata

`F1` · `MUST` · Teste: `SplitInstallmentsTest`

O SISTEMA DEVE distribuir o valor de forma que a soma das `n` parcelas seja
**exatamente** igual ao total, alocando a diferença de arredondamento na última
parcela.

| Total | n | Parcelas | Soma |
|---|---|---|---|
| 60000 | 7 | 6× 8571 + 1× 8574 | 60000 |
| 10 | 3 | 2× 3 + 1× 4 | 10 |
| 100000 | 1 | 1× 100000 | 100000 |
| 1 | 2 | 1× 0 + 1× 1 | 1 |

O teste DEVE cobrir todo `n` de 1 a 72 sobre um conjunto de totais, verificando a
igualdade da soma. Invariante do Art. 7.

### REQ-TXN-009 — Escopo de edição de parcela

`F1` · `MUST` · Teste: `InstallmentScopeTest`

QUANDO o usuário edita ou exclui uma transação com `installmentGroupId`,
O SISTEMA DEVE perguntar o escopo: **só esta**, **esta e as futuras**, ou
**todas**, e DEVE aplicar a mudança apenas ao escopo escolhido.

### REQ-TXN-010 — Exclusão com desfazer

`F0` · `SHOULD` · Teste: `manual`

QUANDO o usuário exclui uma transação por swipe, O SISTEMA DEVE aplicar a exclusão
imediatamente e DEVE oferecer desfazer por 5 segundos em Snackbar, sem diálogo de
confirmação.

### REQ-TXN-011 — Lista agrupada por dia

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE agrupar a lista de transações por dia, em ordem decrescente, com
cabeçalho exibindo a data e o saldo líquido do dia.

### REQ-TXN-012 — Filtros e busca

`F0` · `SHOULD` · Teste: `TxnListTest`

O SISTEMA DEVE permitir filtrar transações por mês, conta, categoria e tipo, e
buscar por trecho da descrição ou por valor exato.

O teste era `TxnDaoTest` e mudou na T-014 (Art. 3). A janela de mês e o filtro
de conta continuam sendo do banco, e `TxnDaoTest` os cobre; a combinação dos
quatro filtros com a busca é regra, e mora em `domain/usecase` (Art. 9). O que
decide não é gosto: o `LIKE` do SQLite só ignora caixa em ASCII, e a busca por
valor exigiria uma **segunda** conversão de texto para centavos — que é o que o
Art. 6 proíbe e o que a T-036 existe para impedir.

### REQ-TXN-013 — Limite de data

`F0` · `MUST` · Teste: `ValidateTxnTest`

SE a data da transação for posterior a hoje + 5 anos, ENTÃO O SISTEMA DEVE
recusar com "Data muito distante".

---

# CARD — cartão de crédito

### REQ-CARD-001 — Configuração do cartão

`F1` · `MUST` · Teste: `AccountDaoTest`

O SISTEMA DEVE armazenar, para conta `CREDIT_CARD`: `creditLimitCents`,
`closingDay`, `dueDay` e `paymentAccountId` (conta que quita a fatura por padrão).

### REQ-CARD-002 — Dias válidos

`F1` · `MUST` · Teste: `ValidateAccountTest`

SE `closingDay` ou `dueDay` estiver fora do intervalo 1–28, ENTÃO O SISTEMA DEVE
recusar com "Use um dia entre 1 e 28".

A restrição elimina a classe de bugs de "dia 31 em fevereiro" sem código de
tratamento, e não perde caso real: bancos não fecham fatura em dia 29–31.
Ver [ADR-004](decisoes.md#adr-004--fatura-de-cartão-é-derivada).

### REQ-CARD-003 — Competência da compra

`F1` · `MUST` · Teste: `InvoiceMonthTest`

QUANDO uma compra é lançada em conta `CREDIT_CARD`, O SISTEMA DEVE atribuí-la à
fatura do mês corrente se `dia(compra) <= closingDay`, e à fatura do mês seguinte
caso contrário.

| `closingDay` | Data da compra | Fatura |
|---|---|---|
| 10 | 2026-03-09 | 2026-03 |
| 10 | 2026-03-10 | 2026-03 |
| 10 | 2026-03-11 | 2026-04 |
| 10 | 2026-12-15 | **2027-01** |
| 1 | 2026-03-01 | 2026-03 |
| 28 | 2026-02-28 | 2026-02 |

A virada de ano é o caso que quebra implementação ingênua com aritmética de mês.

### REQ-CARD-004 — Vencimento da fatura

`F1` · `MUST` · Teste: `InvoiceMonthTest`

O SISTEMA DEVE calcular o vencimento da fatura que fecha em `invoiceMonth` como
`dueDay` do próprio `invoiceMonth` se `dueDay > closingDay`, e do mês seguinte
caso contrário.

| `closingDay` | `dueDay` | Fatura | Vencimento |
|---|---|---|---|
| 10 | 20 | 2026-03 | 2026-03-20 |
| 20 | 10 | 2026-03 | 2026-04-10 |
| 10 | 10 | 2026-03 | 2026-04-10 |
| 25 | 5 | 2026-12 | 2027-01-05 |

### REQ-CARD-005 — Escopo da fatura

`F1` · `MUST` · Teste: `InvoiceTest`

O SISTEMA DEVE compor a fatura de um mês com as transações onde
`accountId = cartão` **e** `type != TRANSFER` **e** a competência
([REQ-CARD-003](#req-card-003--competência-da-compra)) for o mês em questão.

Pagamentos de fatura não pertencem a fatura alguma: abatem o saldo do cartão, não
compõem a conta do mês.

### REQ-CARD-006 — Pagamento da fatura

`F1` · `MUST` · Teste: `InvoicePaymentTest`

QUANDO o usuário paga uma fatura, O SISTEMA DEVE criar uma `TRANSFER` de
`paymentAccountId` para o cartão, pré-preenchida com **o que falta** da fatura
(total menos o já pago, nunca negativo) e data no vencimento, com o valor
**editável** para permitir pagamento parcial.

A linha dizia "o total da fatura" até a T-025 exercer o caminho no aparelho: com
o total, quem pagou R$ 100 de R$ 300 e voltasse para pagar o resto encontraria
R$ 300 no campo, e dois toques pagariam R$ 400 numa fatura de R$ 300. Numa fatura
sem pagamento algum as duas leituras dão o mesmo número — a diferença só aparece
onde a primeira erra.

Aceite: após pagamento integral, o saldo do cartão referente àquela fatura fica
zerado, sem nenhum tratamento especial de cartão no cálculo de saldo.

### REQ-CARD-007 — Status derivado

`F1` · `MUST` · Teste: `InvoiceStatusTest`

O SISTEMA DEVE derivar o status da fatura, sem armazená-lo:

| Status | Condição |
|---|---|
| `Aberta` | hoje ≤ data de fechamento |
| `Fechada` | passou o fechamento e pagamentos < total |
| `Paga` | pagamentos desde o fechamento ≥ total |

### REQ-CARD-008 — Limite disponível

`F1` · `SHOULD` · Teste: `InvoiceTest`

O SISTEMA DEVE exibir o limite disponível como `creditLimitCents` menos a dívida
total do cartão, incluindo parcelas futuras já lançadas.

### REQ-CARD-009 — Saldo do cartão

`F1` · `MUST` · Teste: `AccountBalanceTest`

O SISTEMA DEVE calcular o saldo de conta `CREDIT_CARD` pela mesma fórmula de
[REQ-ACC-004](#req-acc-004--cálculo-de-saldo), sem exceção, resultando em valor
negativo quando há dívida.

---

# BUD — orçamento

### REQ-BUD-001 — Teto por categoria e mês

`F1` · `MUST` · Teste: `BudgetDaoTest`

O SISTEMA DEVE permitir definir um `limitCents` por par (categoria, mês), com no
máximo um teto por par.

### REQ-BUD-002 — Consumo inclui subcategorias

`F1` · `MUST` · Teste: `BudgetProgressTest`

O SISTEMA DEVE somar no consumo de uma categoria as despesas da própria categoria
**e** de todas as suas subcategorias, no período definido por
[REQ-CORE-003](#req-core-003--mês-configurável).

Transferências nunca entram no consumo de orçamento.

### REQ-BUD-003 — Progresso e alerta

`F1` · `MUST` · Teste: `BudgetProgressTest`

O SISTEMA DEVE exibir barra de progresso com gasto, limite e percentual.

| Estado | Preenchimento | Ícone |
|---|---|---|
| dentro do teto | nenhum, só contorno | — |
| ≥ 80% | Sunburst `#ffd731` | atenção |
| ≥ 100% | Ember `#fb4903` | estouro |

O ícone é obrigatório: cor nunca é sinal único
([REQ-A11Y-003](#req-a11y-003--cor-não-é-sinal-único),
[REQ-DS-007](#req-ds-007--valor-monetário-em-tinta-neutra)).

### REQ-BUD-004 — Sobra diária

`F1` · `SHOULD` · Teste: `BudgetProgressTest`

O SISTEMA DEVE exibir quanto resta por dia até o fim do período: `(limite − gasto)
/ dias restantes`.

SE o orçamento estiver estourado, ENTÃO O SISTEMA DEVE exibir o valor excedido em
vez de sobra diária negativa.

### REQ-BUD-005 — Copiar mês anterior

`F1` · `SHOULD` · Teste: `BudgetDaoTest`

O SISTEMA DEVE oferecer copiar todos os tetos do mês anterior para o mês atual em
uma ação.

---

# REC — recorrências

### REQ-REC-001 — Cadastro de regra

`F1` · `MUST` · Teste: `RecurringDaoTest`

O SISTEMA DEVE permitir criar regras de lançamento recorrente com conta,
categoria, tipo, valor, descrição, frequência, data de início e data de fim
opcional.

### REQ-REC-002 — Frequências

`F1` · `MUST` · Teste: `RecurrenceExpansionTest`

O SISTEMA DEVE suportar `DAILY`, `WEEKLY`, `MONTHLY` e `YEARLY`, com `interval`
para "a cada N".

### REQ-REC-003 — Geração idempotente

`F1` · `MUST` · Teste: `GenerateRecurringTest`

QUANDO a geração de ocorrências executa, O SISTEMA DEVE criar apenas as
ocorrências ainda não materializadas, avançando `lastGeneratedDate`.

SE a geração executar duas ou mais vezes no mesmo dia, ENTÃO O SISTEMA DEVE
produzir exatamente o mesmo conjunto de transações da primeira execução.

O gerador roda **na abertura do app**. Sem idempotência, quem abre o app três
vezes ganha três aluguéis.

O worker diário previsto no [ADR-006](decisoes.md#adr-006--recorrência-materializa-sob-demanda-com-horizonte-de-60-dias)
está adiado, e o requisito diz o que o app faz (Art. 3).

### REQ-REC-004 — Horizonte de materialização

`F1` · `MUST` · Teste: `GenerateRecurringTest`

O SISTEMA DEVE materializar ocorrências apenas até hoje + 60 dias, e NÃO DEVE
gerar além desse horizonte mesmo para regras sem data de fim.

### REQ-REC-005 — Lançamento automático

`F1` · `MUST` · Teste: `GenerateRecurringTest`

ONDE `autoPost = true`, O SISTEMA DEVE gerar a ocorrência com `cleared = 1`.

ONDE `autoPost = false`, O SISTEMA DEVE gerar com `cleared = 0` e exibi-la em
"Próximas contas" com ação de confirmação.

### REQ-REC-006 — Clamp do dia do mês

`F1` · `MUST` · Teste: `RecurrenceExpansionTest`

SE `dayOfMonth` for maior que o último dia do mês alvo, ENTÃO O SISTEMA DEVE
lançar no último dia daquele mês.

| `dayOfMonth` | Mês | Data gerada |
|---|---|---|
| 31 | 2026-02 | 2026-02-28 |
| 31 | 2028-02 | 2028-02-29 |
| 31 | 2026-04 | 2026-04-30 |
| 30 | 2026-02 | 2026-02-28 |

Diferente do cartão ([REQ-CARD-002](#req-card-002--dias-válidos)), aqui o clamp é
obrigatório: contas que vencem dia 30 ou 31 são comuns.

### REQ-REC-007 — Histórico imutável

`F1` · `MUST` · Teste: `GenerateRecurringTest`

QUANDO o usuário altera uma regra, O SISTEMA DEVE atualizar apenas ocorrências
futuras com `cleared = 0`, e NÃO DEVE alterar ocorrências já efetivadas.

### REQ-REC-008 — Próximas contas

`F1` · `MUST` · Teste: `manual`

O SISTEMA DEVE exibir no dashboard as transações com `cleared = 0` dos próximos 7
dias, ordenadas por data, com ação de efetivar.

---

# IMP — importação de arquivo

Design detalhado em [ingestao.md](ingestao.md).

### REQ-IMP-001 — Seleção de arquivo

`F2` · `MUST` · Teste: `manual`

O SISTEMA DEVE selecionar arquivos via `ACTION_OPEN_DOCUMENT` (SAF), e NÃO DEVE
solicitar `READ_EXTERNAL_STORAGE` nem varrer diretórios.

### REQ-IMP-002 — Parse de OFX

`F2` · `MUST` · Teste: `OfxParserTest`

O SISTEMA DEVE interpretar OFX 1.x (SGML) e OFX 2.x (XML), extraindo de cada
`STMTTRN`: `TRNAMT`, `DTPOSTED`, `FITID`, e `NAME` ou `MEMO` como descrição.

O SISTEMA DEVE derivar o tipo pelo sinal de `TRNAMT`, ignorando `TRNTYPE`.

### REQ-IMP-003 — Charset declarado

`F2` · `MUST` · Teste: `OfxParserTest`

O SISTEMA DEVE ler o cabeçalho do OFX para determinar o charset antes de decodificar
o corpo, suportando ao menos `UTF-8` e `ISO-8859-1`/`CP1252`.

Aceite: um OFX com `CHARSET:1252` contendo "ALIMENTAÇÃO" resulta em descrição com
o acento correto.

### REQ-IMP-004 — Conversão sem ponto flutuante

`F2` · `MUST` · Teste: `MoneyParseTest`

O SISTEMA DEVE converter o texto do valor para centavos por manipulação de texto,
e NÃO DEVE usar `toDouble()`, `toFloat()` ou aritmética de ponto flutuante em
nenhum ponto do caminho.

| Entrada | Centavos |
|---|---|
| `-187.50` | `-18750` |
| `-187,50` | `-18750` |
| `1.234,56` | `123456` |
| `1,234.56` | `123456` |
| `100` | `10000` |
| `0.07` | `7` |

Os dois formatos de milhar convivem porque exportadores brasileiros divergem.
Regra: o **último** separador é o decimal quando seguido de 1–2 dígitos.

### REQ-IMP-005 — Mapeamento de colunas CSV

`F2` · `MUST` · Teste: `manual`

O SISTEMA DEVE permitir mapear quais colunas do CSV correspondem a data, valor e
descrição, exibindo pré-visualização das primeiras linhas, e DEVE salvar o
mapeamento para reuso.

### REQ-IMP-006 — Detecção de formato CSV

`F2` · `SHOULD` · Teste: `CsvSniffTest`

O SISTEMA DEVE detectar automaticamente separador (`,` ou `;`), formato de data
(`dd/MM/yyyy` ou `yyyy-MM-dd`) e separador decimal, e DEVE permitir correção
manual em [REQ-IMP-005](#req-imp-005--mapeamento-de-colunas-csv).

### REQ-IMP-007 — Deduplicação por FITID

`F2` · `MUST` · Teste: `DedupeTest`

QUANDO o arquivo é OFX, O SISTEMA DEVE usar `FITID` como chave de deduplicação e
DEVE descartar automaticamente transações cujo `FITID` já exista na conta.

### REQ-IMP-008 — Deduplicação por hash

`F2` · `MUST` · Teste: `DedupeTest`

QUANDO não há `FITID`, O SISTEMA DEVE calcular
`sha256(accountId|epochDay|amountCents|normalize(descrição))` e DEVE descartar
automaticamente as colisões exatas.

`normalize` DEVE remover acentos, converter para maiúsculas, remover sequências de
4+ dígitos (NSU, autorização) e colapsar espaços.

| Descrição A | Descrição B | Mesma chave? |
|---|---|---|
| `SUPERMERCADO XYZ` | `Supermercado Xyz` | sim |
| `PADARIA 00123456` | `PADARIA 00987654` | sim |
| `UBER   TRIP` | `UBER TRIP` | sim |
| `MERCADO A` | `MERCADO B` | não |

### REQ-IMP-009 — Possível duplicata

`F2` · `MUST` · Teste: `DedupeTest`

SE existir transação na mesma conta, com valor idêntico, data dentro de ±3 dias e
descrição diferente, ENTÃO O SISTEMA DEVE marcá-la como **possível duplicata**,
exibir as duas lado a lado, e DEVE deixar a decisão para o usuário.

O SISTEMA NÃO DEVE descartar automaticamente neste caso. Duas compras de R$ 20 na
mesma padaria no mesmo dia são normais, e descartar por heurística perde
transação legítima.

### REQ-IMP-010 — Revisão obrigatória

`F2` · `MUST` · Teste: `manual`

O SISTEMA DEVE exibir todas as transações importadas em tela de revisão editável
antes de gravar, e NÃO DEVE gravar nenhuma transação sem confirmação explícita do
usuário (Art. 14).

### REQ-IMP-011 — Lote e desfazer

`F2` · `MUST` · Teste: `ImportBatchTest`

O SISTEMA DEVE registrar cada importação como `import_batch` e DEVE permitir
desfazer o lote inteiro, removendo as transações daquele lote que ainda não foram
editadas manualmente.

### REQ-IMP-012 — Rede de segurança no banco

`F2` · `MUST` · Teste: `TxnDaoTest`

O SISTEMA DEVE manter índice único parcial em `(accountId, dedupeKey)` que rejeite
inserção duplicada mesmo se a checagem em código falhar.

---

# ACT — auto-categorização

### REQ-ACT-001 — Aprendizado por estabelecimento

`F2` · `MUST` · Teste: `PayeeRuleTest`

QUANDO o usuário salva uma transação com categoria, O SISTEMA DEVE gravar ou
incrementar a regra `normalize(descrição) → categoryId` em `payee_rule`.

### REQ-ACT-002 — Aplicação na importação

`F2` · `MUST` · Teste: `PayeeRuleTest`

QUANDO uma transação é importada, O SISTEMA DEVE pré-selecionar a categoria da
regra correspondente, se existir, e DEVE destacá-la como sem categoria caso
contrário.

A categoria pré-selecionada é sempre editável na tela de revisão.

### REQ-ACT-003 — Semente de palavras-chave

`F2` · `SHOULD` · Teste: `SeedTest`

O SISTEMA DEVE semear ~40 regras iniciais de estabelecimentos comuns (`UBER` →
Transporte, `IFOOD` → Alimentação, `NETFLIX` → Assinaturas), para que a primeira
importação não chegue vazia.

### REQ-ACT-004 — Normalização compartilhada

`F2` · `MUST` · Teste: `NormalizeTest`

O SISTEMA DEVE usar a mesma função `normalize` em
[REQ-IMP-008](#req-imp-008--deduplicação-por-hash) e
[REQ-ACT-001](#req-act-001--aprendizado-por-estabelecimento).

Duas normalizações divergentes fariam o dedupe e o aprendizado discordarem sobre
o que é o mesmo estabelecimento.

---

# NOT — captura por notificação

### REQ-NOT-001 — Allowlist antes da leitura

`F3` · `MUST` · Teste: `NotificationFilterTest`

QUANDO uma notificação é recebida, O SISTEMA DEVE verificar se o `packageName`
está na allowlist definida pelo usuário **antes** de ler qualquer conteúdo, e
DEVE descartar silenciosamente caso não esteja.

O serviço tem acesso técnico a todas as notificações do aparelho, incluindo
mensageiros. O filtro ser a primeira instrução é a garantia de privacidade, não
uma otimização.

### REQ-NOT-002 — Parser por banco

`F3` · `MUST` · Teste: `NotificationParserTest`

O SISTEMA DEVE extrair valor e estabelecimento do texto da notificação usando
parser específico por `packageName`.

### REQ-NOT-003 — Confirmação por notificação própria

`F3` · `MUST` · Teste: `manual`

QUANDO uma notificação bancária é interpretada com sucesso, O SISTEMA DEVE criar
uma transação `cleared = 0` e DEVE emitir notificação própria com ação de
confirmação e escolha rápida de categoria.

### REQ-NOT-004 — Falha silenciosa

`F3` · `MUST` · Teste: `NotificationParserTest`

SE o parser não reconhecer o texto, ENTÃO O SISTEMA DEVE descartar sem notificar o
usuário e sem registrar o conteúdo.

Notificar cada falha vira spam, e usuário com spam desliga o serviço.

### REQ-NOT-005 — Detecção de parser morto

`F3` · `SHOULD` · Teste: `manual`

SE um app da allowlist passar 30 dias sem nenhuma interpretação bem-sucedida,
ENTÃO O SISTEMA DEVE avisar o usuário de que a captura pode ter parado.

Bancos mudam o texto sem aviso. Sem esse alerta, o usuário só descobre a quebra
ao conferir o extrato meses depois.

### REQ-NOT-006 — Conteúdo não persistido

`F3` · `MUST` · Teste: `NotificationParserTest`

O SISTEMA NÃO DEVE persistir o texto bruto da notificação. Apenas o valor e a
descrição extraídos são gravados.

---

# OF — Open Finance

Contexto e restrições regulatórias em
[ADR-007](decisoes.md#adr-007--ingestão-em-três-camadas-open-finance-isolado-na-f4).

### REQ-OF-001 — Opt-in explícito

`F4` · `MUST` · Teste: `manual`

O SISTEMA DEVE tratar a conexão Open Finance como recurso opcional, desativado por
padrão, ativado apenas por ação explícita do usuário.

### REQ-OF-002 — Credenciais fora do app

`F4` · `MUST` · Teste: `manual`

O SISTEMA NÃO DEVE conter credenciais de agregador no APK. Toda comunicação passa
por backend próprio.

Credencial de agregador no cliente expõe os dados de **todos** os usuários a quem
extrair o APK.

### REQ-OF-003 — Renovação de consentimento

`F4` · `MUST` · Teste: `manual`

O SISTEMA DEVE avisar o usuário antes do vencimento do consentimento, que tem
prazo máximo de 12 meses por regra do BCB.

Sem o aviso, a sincronização morre em silêncio e o usuário só percebe quando os
dados já estão defasados.

### REQ-OF-004 — Degradação total

`F4` · `MUST` · Teste: `manual`

ENQUANTO o Open Finance estiver desativado ou indisponível, O SISTEMA DEVE
permanecer 100% funcional em todas as demais features.

---

# RPT — relatórios

### REQ-RPT-001 — Despesas por categoria

`F1` · `MUST` · Teste: `ReportTest`

O SISTEMA DEVE exibir gráfico de pizza das despesas por categoria no período,
excluindo transferências.

### REQ-RPT-002 — Evolução em 12 meses

`F1` · `SHOULD` · Teste: `ReportTest`

O SISTEMA DEVE exibir gráfico de linha com receitas e despesas dos últimos 12
períodos.

### REQ-RPT-003 — Maiores despesas

`F1` · `SHOULD` · Teste: `ReportTest`

O SISTEMA DEVE listar as 10 maiores despesas do período.

### REQ-RPT-004 — Detalhamento

`F1` · `SHOULD` · Teste: `manual`

QUANDO o usuário toca numa fatia do gráfico, O SISTEMA DEVE navegar para a lista
de transações já filtrada por aquela categoria e período.

---

# UI — interface

### REQ-UI-001 — Navegação principal

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE apresentar barra inferior com exatamente quatro destinos: Início,
Transações, Orçamento e Mais.

### REQ-UI-002 — Lançamento em três toques

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE permitir registrar uma despesa a partir da tela inicial em no
máximo **três toques** — valor, categoria, salvar — sem navegação de tela cheia,
usando bottom sheet com teclado numérico já em foco.

Métrica de referência: ≤ 5 segundos. Protegido pelo Art. 18.

### REQ-UI-003 — Campos condicionais

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE exibir o campo de parcelas somente para conta `CREDIT_CARD`, o
campo de conta destino somente para tipo `TRANSFER`, e DEVE ocultar categoria
para `TRANSFER`.

### REQ-UI-004 — Dashboard

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE exibir na tela inicial, nesta ordem: saldo total, cartões,
comparativo do período, orçamentos mais próximos do estouro, próximas contas e
últimas transações.

O comparativo do período traz receitas e despesas do período corrente e a
variação do líquido contra o período anterior. Transferência entre contas
próprias não entra em nenhum dos dois — mesma regra de
[REQ-RPT-001](#req-rpt-001--despesas-por-categoria), e pelo mesmo motivo:
dinheiro mudando de bolso não é gasto.

Um bloco cuja fonte de dados ainda não existe **não é exibido vazio**. Orçamento
depende de [REQ-BUD-001](#req-bud-001--teto-por-categoria-e-mês) e próximas
contas de [REQ-REC-008](#req-rec-008--próximas-contas), ambos `F1`: antes deles
não há nem dado a mostrar nem ação que os preencha, e um bloco permanentemente
mudo é exatamente o que [REQ-UI-006](#req-ui-006--estados-vazios-acionáveis)
proíbe. A ordem acima é a ordem relativa dos blocos presentes; cada bloco entra
com a task que lhe dá dados.

### REQ-UI-005 — Onboarding

`F0` · `MUST` · Teste: `manual`

QUANDO o app é aberto pela primeira vez, O SISTEMA DEVE apresentar uma única tela
perguntando o saldo atual, criando uma conta `CASH` e uma `CHECKING`.

### REQ-UI-006 — Estados vazios acionáveis

`F0` · `SHOULD` · Teste: `manual`

O SISTEMA DEVE incluir, em toda tela sem dados, a ação que a preenche.

### REQ-UI-007 — Tema

`F0` · `SHOULD` · Teste: `manual`

O SISTEMA DEVE suportar tema claro, escuro e seguir o sistema.

O SISTEMA NÃO DEVE usar cores dinâmicas (Material You). A paleta é fixa e de
marca: cor dinâmica derivada do papel de parede sobrescreveria os tokens de
[REQ-DS-001](#req-ds-001--tokens-como-fonte-única) e destruiria as garantias de
contraste de [REQ-DS-006](#req-ds-006--paleta-de-acento-é-preenchimento), que
dependem de hexadecimais conhecidos.

O mapeamento entre os dois temas está em
[REQ-DS-008](#req-ds-008--tema-escuro-preserva-a-lógica-de-acento).

---

# DS — sistema visual

Tradução do style reference para Android — dois modos de tela cheia, preto e
branco, com um degrau de luminância no lugar do contorno. Design, rationale e os
conflitos resolvidos estão em [design.md](design.md); a troca do sistema anterior
e o que ela custou estão em [ADR-011](decisoes.md).

### REQ-DS-001 — Tokens como fonte única

`F0` · `MUST` · Teste: `TokenLintTest`

O SISTEMA DEVE definir toda cor, forma e estilo de texto em `core/ui/theme/`.

SE uma cor literal aparecer fora desse pacote, ENTÃO a verificação estática DEVE
falhar.

### REQ-DS-002 — Superfícies por degrau de luminância

`F0` · `MUST` · Teste: `ContrastTest`

O SISTEMA DEVE desenhar card, folha e superfície de conteúdo na cor `surface`,
que é um degrau de luminância acima do canvas `paper`, e NÃO DEVE contorná-los.

O SISTEMA DEVE reservar o fio de 1dp em `hairline` para o caso em que duas
superfícies do mesmo tom se encostam.

A escada tem **dois passos e para**: `paper` e `surface`, por modo. Um terceiro
tom seria elevação tonal com outro nome, que
[REQ-DS-004](#req-ds-004--sem-sombra-e-sem-gradiente) proíbe. Contornar o card
além do degrau diria a mesma coisa duas vezes.

**O anel de `ink` continua existindo em um lugar**: ao redor de preenchimento
colorido pequeno — o ponto de categoria e a amostra do seletor de cores —, onde
a cor não separa sozinha do fundo. A medição está em
[REQ-DS-006](#req-ds-006--paleta-de-acento-é-preenchimento).

### REQ-DS-003 — Raios e contornos

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE usar `CircleShape` em nav, botões, chips e badges, e a escala de
8dp, 12dp, 20dp e 28dp em todo o resto.

| Raio | Onde |
|---|---|
| 8dp | tag inline, chip pequeno |
| 12dp | campo de texto, tile |
| 20dp | card e folha |
| 28dp | folha de fundo, chrome de dispositivo |

SE algum raio ficar fora dessa escala, ENTÃO a revisão DEVE rejeitar a mudança.

**Ação é pílula, conteúdo é 20dp.** Não é preferência: a diferença entre o botão
e o card passa a ser a forma, e não a cor — o que sobrevive ao daltonismo e à
troca de canvas sem nenhuma condicional
([REQ-A11Y-003](#req-a11y-003--cor-não-é-sinal-único)).

### REQ-DS-004 — Sem sombra e sem gradiente

`F0` · `MUST` · Teste: `TokenLintTest`

O SISTEMA NÃO DEVE aplicar sombra, elevação tonal ou gradiente em nenhuma
superfície. Profundidade é comunicada por bandas de cor e contorno.

Material 3 aplica elevação por padrão em `Card`, `Button`, `FloatingActionButton`
e `Surface` — os quatro exigem `0.dp` explícito, e `Surface` também
`tonalElevation = 0.dp`.

### REQ-DS-005 — Tipografia display travada

`F0` · `MUST` · Teste: `TypographyTest`

O SISTEMA DEVE renderizar os estilos display com `lineHeight` de `1.0.em`,
`includeFontPadding = false` e `LineHeightStyle.Trim.Both`.

| Estilo | Tamanho | `lineHeight` | Entreletra |
|---|---|---|---|
| `DisplayXl` | 64sp | 1.0em | −0.020em |
| `Display` | 44sp | 1.0em | −0.015em |
| `DisplaySm` | 34sp | 1.0em | −0.010em |

O SISTEMA DEVE usar entreletra **negativa** no display e **positiva** no corpo.
O aperto é o que separa tipo grande de tipo apenas ampliado; a abertura do corpo
é o que dá aos rótulos a precisão mecânica que o sistema pede.

Sem `includeFontPadding = false` e `Trim.Both`, o Compose adiciona a folga de
métrica da fonte e a entrelinha travada não aparece na tela — o efeito se perde
silenciosamente, com o código parecendo correto.

O SISTEMA NÃO DEVE truncar display type com reticências. Texto que não couber
quebra em mais linhas, e o contêiner cresce.

### REQ-DS-006 — Paleta de acento é preenchimento

`F0` · `MUST` · Teste: `ContrastTest`

O SISTEMA DEVE usar as nove cores de acento — Verde-azulado, Azul, Verde,
Amarelo, Laranja, Rosa, Vermelho, Marrom e Violeta — exclusivamente como
preenchimento de ponto, ícone, barra ou superfície.

O SISTEMA NÃO DEVE usar nenhuma delas como cor de texto, cor de link ou cor de
ação.

**A medição é contra `surface`, não contra `paper`.** Sobre preto puro oito das
nove passam de 4.5:1, e é exatamente aí que a regra se perderia por descuido —
parece que dá para usar quase qualquer uma como texto. Sobre o card, que é onde o
conteúdo de fato mora, **seis** reprovam: Violeta 2.94, Marrom 3.90, Azul 3.91,
Rosa 3.94, Verde 3.95 e Vermelho 4.20. E nenhuma das nove passa nos **dois**
temas: Verde-azulado dá 5.85 no escuro e 2.77 no claro. A regra única elimina a
classe de erro em vez de administrar uma tabela por superfície e por tema.

SE um preenchimento de acento tiver menos de 24dp, ENTÃO O SISTEMA DEVE
contorná-lo com 1dp de `ink`, **nos dois temas**. Cada tema derruba acentos
diferentes abaixo dos 3:1 de elemento não textual da WCAG: no claro, Laranja
2.53:1, Verde-azulado 2.77:1 e Amarelo 2.79:1; no escuro, Violeta 2.94:1. Um anel
condicional ao tema deixaria metade dos casos descoberta. Vale para o ponto da
linha de transação e para a amostra do seletor de cores.

O SISTEMA NÃO DEVE usar preenchimento saturado com texto por cima. O único par
legível seria **branco sobre Violeta** (6.06:1) — branco sobre Vermelho reprova
com 4.24:1, e os dois parecem igualmente seguros a olho. Nenhuma tela precisa
desse padrão, e mantê-lo disponível é manter a porta pela qual a versão ilegível
entra.

### REQ-DS-007 — Valor monetário em tinta neutra

`F0` · `MUST` · Teste: `ContrastTest`

O SISTEMA DEVE exibir todo valor monetário na cor `ink`, com algarismos tabulares
(`tnum`), distinguindo receita de despesa pelo sinal `+`/`−` de
[REQ-CORE-005](#req-core-005--formatação-pt-br) e pelo rótulo da categoria.

O SISTEMA PODE tingir de verde e vermelho o valor de **transação**, como reforço
do sinal, DESDE QUE o par escolhido passe em 4.5:1 sobre o canvas **e** sobre o
card, nos dois temas.

O SISTEMA NÃO DEVE tingir saldo, teto ou total: ali o sinal separa "sobrou" de
"faltou", que é outra coisa, e a tela ficaria com cor em toda parte.

A condição de contraste é o requisito, não a cor. As duas primeiras tentativas
usaram a paleta de acento e reprovaram justamente na metade que avisa — Vermelho
4.20:1 sobre o card escuro, Verde-azulado 2.77:1 sobre o claro. O par que passou
é **por tema**, ao contrário dos acentos, porque um único não existe: verde claro
reprova sobre branco e verde escuro some sobre preto.

| | sobre canvas | sobre card |
|---|---|---|
| Escuro `#34e3a8` / `#f87171` | 12.70 / 7.59 | 10.76 / 6.44 |
| Claro `#067647` / `#be123c` | 5.69 / 6.29 | 5.17 / 5.71 |

A cor é **reforço, nunca o sinal**: o `+`/`−` e o rótulo da categoria continuam
sozinhos suficientes, e
[REQ-A11Y-003](#req-a11y-003--cor-não-é-sinal-único) segue valendo — desligar as
cores não tira informação nenhuma da tela.

Estados de orçamento de [REQ-BUD-003](#req-bud-003--progresso-e-alerta) usam
preenchimento **com ícone**: sem preenchimento dentro do teto, Laranja com ícone
de atenção em ≥ 80%, Vermelho com ícone de estouro em ≥ 100%. A palavra
"estourou" fica em `ink` — Vermelho como texto sobre o card dá 4.20:1 e
reprovaria.

`tnum` não é enfeite: sem ele os valores não alinham na vertical, e uma coluna de
dinheiro desalinhada é mais difícil de conferir contra o extrato do banco.

### REQ-DS-008 — Tema escuro preserva a lógica de acento

`F0` · `MUST` · Teste: `ContrastTest`

QUANDO o tema escuro está ativo, O SISTEMA DEVE usar preto absoluto (`#000000`)
como canvas e `#16181A` como superfície, DEVE manter as oito cores de acento
**idênticas**, e DEVE trocar apenas canvas, superfície, tinta e fio.

O modo escuro é o principal, e não uma variação do claro: o app passa a maior
parte do tempo mostrando números, e o modo claro é a banda de catálogo — cadastro,
ajustes, formulários.

Preto absoluto, e não quase-preto: `#0A0A0A` existe no sistema de origem para
cards embutidos, e usá-lo como canvas achataria a única troca de banda que o
desenho tem. A cor de uma categoria é identidade, e identidade não muda quando
anoitece.

### REQ-DS-009 — Intensidade proporcional à densidade

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE reduzir a expressão visual conforme a densidade de dados da tela.

| Tela | Display type |
|---|---|
| Onboarding | `DisplayXl` (64sp) |
| Estados vazios | `Display` (44sp) |
| Dashboard, cartão, orçamento | `DisplaySm` (34sp) |
| Lista de transações, importação, ajustes | **nenhum** |

O SISTEMA DEVE dar ênfase ao número herói por **tamanho e vazio ao redor**, e NÃO
DEVE assentá-lo sobre preenchimento colorido. A profundidade do sistema é o
degrau de luminância; um retângulo saturado atrás do saldo é a resposta que o
sistema anterior dava, e sobreviveu à troca por recoloração em vez de decisão.

O SISTEMA NÃO DEVE usar display type em listas roláveis de dados. Uma lista de 100
transações em 64sp violaria o caminho de 5 segundos do Art. 18.

### REQ-DS-010 — Fontes empacotadas

`F0` · `MUST` · Teste: `ManifestTest`

O SISTEMA DEVE empacotar as fontes em `res/font/` e NÃO DEVE usar Downloadable
Fonts.

Downloadable Fonts exige rede e Google Play Services, o que furaria
[REQ-SEC-007](#req-sec-007--sem-permissão-de-rede) pela porta dos fundos.

---

# A11Y — acessibilidade

Nenhum destes é adiável (Art. 17).

### REQ-A11Y-001 — Descrição de conteúdo

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE fornecer `contentDescription` significativa em todo ícone acionável.

### REQ-A11Y-002 — Alvo de toque

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE garantir alvo de toque de no mínimo 48dp em todo elemento
interativo, incluindo chips de categoria.

### REQ-A11Y-003 — Cor não é sinal único

`F0` · `MUST` · Teste: `manual`

O SISTEMA NÃO DEVE usar cor como única portadora de informação. Orçamento
estourado tem ícone além do vermelho; receita e despesa têm sinal `+`/`−` além da
cor.

### REQ-A11Y-004 — Fonte ampliada

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE permanecer utilizável com escala de fonte até 200%, sem truncar
valores nem sobrepor elementos.

Implica não usar altura fixa em item de lista.

### REQ-A11Y-005 — Contraste

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE manter contraste mínimo de 4.5:1 para texto nos temas claro e
escuro.

### REQ-A11Y-006 — Leitura de valores

`F0` · `MUST` · Teste: `MoneySpokenTest`

O SISTEMA DEVE expor valores monetários ao leitor de tela por extenso — "menos
dezoito reais e cinquenta centavos" — e não como dígitos crus.

---

# SEC — segurança

### REQ-SEC-001 — Banco criptografado

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE armazenar o banco criptografado com SQLCipher.

### REQ-SEC-002 — Chave no Keystore

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE gerar chave de 32 bytes de fonte criptograficamente segura no
primeiro boot e DEVE guardá-la no Android Keystore, nunca em `SharedPreferences`
nem no código.

### REQ-SEC-003 — Bloqueio biométrico

`F0` · `SHOULD` · Teste: `manual`

ONDE o bloqueio estiver ativo, O SISTEMA DEVE exigir `BiometricPrompt` com
fallback para credencial do aparelho antes de exibir qualquer dado financeiro.

### REQ-SEC-004 — Sem backup do sistema

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE declarar `android:allowBackup="false"` e regras de extração vazias,
impedindo cópia do banco para backup em nuvem do sistema.

### REQ-SEC-005 — Proteção de tela

`F0` · `SHOULD` · Teste: `manual`

ENQUANTO o bloqueio biométrico estiver ativo, O SISTEMA DEVE aplicar `FLAG_SECURE`,
impedindo captura de tela e ocultando o conteúdo na lista de recentes.

### REQ-SEC-006 — Log sem dados financeiros

`F0` · `MUST` · Teste: `manual`

O SISTEMA NÃO DEVE registrar valor monetário, descrição de transação, nome de
estabelecimento ou saldo em log, incluindo builds de debug.

### REQ-SEC-007 — Sem permissão de rede

`F0` · `MUST` · Teste: `ManifestTest`

O SISTEMA NÃO DEVE declarar a permissão `INTERNET` no manifesto nas fases F0 a F3.

Verificável por teste que lê o manifesto mesclado. É a garantia que o usuário
confere sozinho nas informações do app.

---

# BAK — backup e exportação

### REQ-BAK-001 — Exportação

`F1` · `MUST` · Teste: `ExportTest`

O SISTEMA DEVE exportar transações em CSV e a base completa em JSON, via
`ACTION_CREATE_DOCUMENT`, para destino escolhido pelo usuário.

### REQ-BAK-002 — Backup criptografado

`F1` · `MUST` · Teste: `BackupTest`

O SISTEMA DEVE gerar backup em arquivo único criptografado com AES-256, com chave
derivada por PBKDF2 de senha informada pelo usuário.

SE a senha for perdida, ENTÃO o backup é irrecuperável, e O SISTEMA DEVE avisar
disso no momento da criação.

### REQ-BAK-003 — Restauração confirmada

`F1` · `MUST` · Teste: `BackupTest`

QUANDO o usuário restaura um backup, O SISTEMA DEVE informar quantos registros
serão substituídos e DEVE exigir confirmação explícita antes de sobrescrever.

### REQ-BAK-004 — Apagar tudo

`F1` · `MUST` · Teste: `manual`

O SISTEMA DEVE oferecer apagar todos os dados, com confirmação por digitação e
sugestão de exportar antes.

---

# DATA — integridade de dados

### REQ-DATA-001 — Migrations explícitas

`F0` · `MUST` · Teste: `MigrationTest`

O SISTEMA DEVE definir `Migration` explícita para toda mudança de schema e NÃO
DEVE usar `fallbackToDestructiveMigration()` (Art. 12).

Todo salto de versão DEVE ter teste com `MigrationTestHelper`.

### REQ-DATA-002 — Chaves estrangeiras ativas

`F0` · `MUST` · Teste: `TxnDaoTest`

O SISTEMA DEVE executar `PRAGMA foreign_keys = ON` na abertura do banco.

Sem isso, `ON DELETE CASCADE` e `ON DELETE RESTRICT` são ignorados pelo SQLite, e
as proteções de [REQ-CAT-005](#req-cat-005--exclusão-protegida) não existem.

### REQ-DATA-003 — Schema versionado

`F0` · `MUST` · Teste: `manual`

O SISTEMA DEVE exportar o schema do Room para `app/schemas/` e versioná-lo no
repositório.

---

## Pendências

Nenhuma no momento. Ambiguidades encontradas durante a implementação entram aqui
como `[NEEDS-CLARIFICATION: ...]` e bloqueiam a task correspondente (Art. 5).
