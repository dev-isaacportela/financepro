# Contribuindo

Obrigado pelo interesse. Este projeto tem uma regra que vale mais que todas as
outras juntas, e vale ler antes de escrever qualquer linha.

**Código sem requisito não entra.** Mudança de comportamento começa na spec, não
no editor, inclusive quando é pequena
([Art. 1](docs/constitution.md#art-1--a-spec-vem-antes-do-código)).

Se isso soa burocrático para um app de finanças pessoais, a razão está em
[constitution.md](docs/constitution.md). São dezoito artigos, leem-se em dez
minutos, e explicam por que a rastreabilidade aqui é verificada por máquina em
vez de depender de disciplina humana.

## O ciclo

```
1. spec.md      requisito em EARS, com ID e critério de aceite verificável
2. decisoes.md  se houver escolha de arquitetura, vira ADR antes do código
3. tasks.md     decompõe em task com dependências e definição de pronto
4. teste        escrito com @Req("REQ-..."), falhando
5. código       o mínimo que faz o teste passar
6. trace.py     rastreabilidade verificada antes do merge
```

O passo 4 não é opcional e não é formalidade. Um teste que nunca falhou não
prova nada.

## Antes de abrir um PR

Abra uma issue primeiro. Não é cerimônia, é economia. Boa parte do que parece
faltar no app está fora de escopo por decisão registrada em
[decisoes.md](docs/decisoes.md), e a issue evita você escrever código que a
revisão vai recusar por um motivo que já estava escrito.

Rode as mesmas três verificações que o CI roda.

```bash
python tools/trace.py
```

```bash
./gradlew detekt test assembleDebug
```

O `trace.py` sai com código diferente de zero se algum `MUST` ficou sem task, se
alguma task cita requisito inexistente, se um `@Req` no código aponta para
requisito que não existe, ou se as dependências entre tasks formam ciclo.

## Convenção de commit

```
feat(card): fatura agrupa por competência

REQ-CARD-003, REQ-CARD-004
```

Todo commit que muda comportamento cita ao menos um `REQ-*`
([Art. 2](docs/constitution.md#art-2--todo-código-responde-a-um-requisito)).
O corpo explica o porquê, não o quê. O diff já mostra o quê.

## O que a revisão vai cobrar

Estas quatro coisas reprovam com frequência, e todas são baratas de acertar
antes de abrir o PR.

**Cor literal fora do tema.** Nenhum `Color(0x` vive fora de
`core/ui/theme/`. `TokenLintTest` varre e reprova.

**Sombra e gradiente.** O Material 3 traz elevação por padrão em `Card`,
`Button`, `FloatingActionButton` e `Surface`. A profundidade inteira do sistema
é um degrau de luminância, e nada mais. Use os componentes de
`core/ui/component/Componentes.kt`, que já nascem zerados.

**Dinheiro em ponto flutuante.** Valor é `Long` em centavos
([ADR-002](docs/decisoes.md#adr-002--dinheiro-é-long-em-centavos)). `Double`
entra por parser de importação, nunca por cálculo.

**Rede para qualquer host novo.** O app conhece um só, `api.bcb.gov.br`, e o
build cai se aparecer outro em `src/main`
([REQ-SEC-007](docs/spec.md#req-sec-007--rede-só-para-o-índice-público)).

## Licença da sua contribuição

Ao abrir um PR você concorda em licenciar a contribuição sob a
[Apache License 2.0](LICENSE), a mesma do projeto. Não há CLA para assinar.
