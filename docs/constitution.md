# Constituição do projeto

Regras invioláveis. Diferente do resto da documentação, isto **não é
recomendação**. Código que viola qualquer artigo abaixo não entra, e a revisão
não precisa argumentar — basta citar o artigo.

Emendar exige alterar este arquivo num commit próprio, com justificativa. Não se
abre exceção pontual "só desta vez".

---

## Processo (SDD)

### Art. 1 — A spec vem antes do código

A ordem é `spec → design → tasks → teste → código`. Nenhuma das etapas é pulada,
inclusive para "mudança pequena".

Mudança de comportamento **começa** em [spec.md](spec.md). Se o requisito não
está lá, ele não existe, e implementá-lo é trabalho não pedido.

### Art. 2 — Todo código responde a um requisito

Todo commit que altera comportamento cita ao menos um `REQ-*` na mensagem.

```
feat(card): fatura agrupa por competência

REQ-CARD-003, REQ-CARD-004
```

Código sem requisito é uma de duas coisas: requisito que faltou escrever, ou
código que não devia existir. As duas se resolvem antes do merge, nunca depois.

Exceção: commits que só mexem em build, formatação ou documentação.

### Art. 3 — A spec é a verdade; divergência é bug da spec

Se a implementação diverge da spec, a spec ganha por padrão. Duas saídas legítimas:

1. Corrigir o código para obedecer a spec, **ou**
2. Corrigir a spec **no mesmo commit** que muda o código, com o porquê

O que nunca acontece: código e spec discordando entre commits. Documentação
desatualizada é pior que ausência de documentação, porque mente com autoridade.

### Art. 4 — Rastreabilidade é verificada por máquina

Todo `REQ-*` marcado `MUST` tem, ao final da sua fase:

- ao menos uma task em [tasks.md](tasks.md) que o cita, **e**
- ao menos um teste que o cita via `@Req("REQ-...")`

`python tools/trace.py` valida isso e sai com código ≠ 0 se algo ficou órfão.
Roda no CI. Rastreabilidade que depende de disciplina humana apodrece em duas
semanas; esta não depende.

### Art. 5 — Ambiguidade vira pergunta, não suposição

Requisito ambíguo é marcado `[NEEDS-CLARIFICATION: pergunta]` na spec e **bloqueia
a task** que depende dele. Ninguém adivinha regra de negócio de dinheiro.

Resolvida a dúvida, a decisão vai para [decisoes.md](decisoes.md) com o motivo —
para que ninguém a reabra em seis meses sem saber o que já foi pesado.

---

## Dinheiro

### Art. 6 — Dinheiro é `Long` em centavos

Proibido `Double`, `Float` e `BigDecimal` em qualquer caminho de dinheiro:
modelo, banco, cálculo, **parser de arquivo importado** e serialização.

`Double` chega no código por parser, não por descuido de cálculo. `"187.50".toDouble()`
é a violação típica. Conversão de texto para centavos é feita por manipulação de
texto, nunca por ponto flutuante.

Formatação para `String` acontece **só na borda da UI**, no Composable.

### Art. 7 — Dinheiro não some nem aparece

Toda operação que divide, distribui ou transfere valor tem um teste que verifica
que a **soma das partes é exatamente igual ao todo**. Parcelamento, rateio,
transferência, importação de lote.

Não é caso de teste opcional. É o invariante do produto.

---

## Arquitetura

### Art. 8 — `domain/` é Kotlin puro

Nenhum arquivo em `domain/` importa `android.*`, `androidx.*` ou anotação de Room.

É o que permite testar toda regra de dinheiro em JVM, em milissegundos, sem
emulador. No dia em que essa fronteira vazar, os testes ficam lentos, e testes
lentos deixam de ser rodados.

### Art. 9 — Regra de negócio não mora em SQL nem em ViewModel

Saldo, competência de fatura, divisão de parcela, progresso de orçamento e
expansão de recorrência são funções puras em `domain/usecase`.

`@Query` busca linha. ViewModel orquestra estado. Nenhum dos dois decide regra —
duas fontes de verdade para a mesma regra divergem, sempre.

### Art. 10 — Sem abstração especulativa

Proibido: interface com uma única implementação, factory de um produto,
`sealed class` de um caso, configuração para valor que nunca muda, camada
"para facilitar o futuro".

Interface entra quando existir a **segunda implementação real**. Substituir por
fake em teste não conta como motivo: o Hilt troca o módulo inteiro.

### Art. 11 — Atalho deliberado é marcado

Simplificação consciente que tem teto conhecido leva comentário nomeando o teto e
a saída:

```kotlin
// ponytail: carrega o mês inteiro em memória.
// Trocar por Paging 3 se o filtro "todos os períodos" passar de ~5k linhas.
```

Sem o comentário, o atalho vira dívida invisível — e alguém o "descobre" como bug
às 3 da manhã.

---

## Dados e segurança

### Art. 12 — Nunca se apaga dado do usuário

`fallbackToDestructiveMigration()` é **proibido**. Toda mudança de schema tem
`Migration` explícita e teste de migração.

Toda operação destrutiva (restaurar backup, apagar tudo, desfazer lote) confirma
antes, e diz exatamente quantos registros serão afetados.

### Art. 13 — Nada sai do aparelho sem ação explícita

Nenhum envio automático, nenhuma telemetria, nenhum crash reporter de terceiro,
nenhuma analytics. Export e backup só acontecem por toque do usuário, para destino
escolhido por ele.

A permissão `INTERNET` **não existe no manifesto** até a F4. Um app financeiro sem
permissão de rede é uma garantia que o usuário verifica no próprio Android, não uma
promessa numa política de privacidade.

### Art. 14 — Importação nunca grava sem confirmação

Transação vinda de OFX, CSV, notificação ou Open Finance entra como **sugestão**,
numa tela de revisão. Sem exceção, sem "modo confiança", sem lote pequeno demais
para revisar.

Um app que inventa lançamento sozinho perde a confiança na primeira vez que erra,
e não tem segunda chance.

### Art. 15 — Log não conta a vida financeira de ninguém

Nenhum valor monetário, descrição de transação, nome de estabelecimento ou saldo
em `Log`, nem em build de debug. Logcat é lido por qualquer app com permissão em
aparelho comprometido, e por qualquer pessoa em cima de um ombro.

---

## Qualidade

### Art. 16 — Regra de dinheiro nasce com teste

Todo use case em `domain/` tem teste JVM escrito **junto** com a implementação —
não numa task de "cobrir com testes" depois.

Isso vale para `domain/`. Não é mandato de TDD para o app inteiro: UI e navegação
não exigem teste no MVP.

### Art. 17 — Acessibilidade não é fase

`contentDescription` em ícone acionável, alvo de toque ≥ 48dp, contraste ≥ 4.5:1,
suporte a fonte 200%, e **cor nunca como sinal único**.

Entra em cada tela, quando a tela é escrita. "Passe de acessibilidade depois" é
um passe que nunca acontece.

### Art. 18 — O caminho de 5 segundos é protegido

Lançar uma despesa a partir da tela inicial custa no máximo **3 toques** e
nenhuma navegação de tela cheia.

Qualquer proposta que acrescente um campo obrigatório, um passo ou um diálogo
nesse fluxo precisa justificar o custo contra esta métrica. É a única coisa que
separa este app de uma planilha abandonada.
