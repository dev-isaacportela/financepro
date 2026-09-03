# Changelog

Formato de [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/), versão
por [SemVer](https://semver.org/lang/pt-BR/). A tag `vX.Y.Z` precisa bater com o
`versionName` de `app/build.gradle.kts` — o workflow de release reprova se não
bater.

## [Não lançado]

## [0.2.0] - 2026-09-03

### Adicionado

- **Descrição e observação no lançamento.** A folha de lançamento rápido nunca
  teve campo de texto: `REQ-TXN-001` pedia os dois desde a F0, e nenhum chegou à
  tela. Toda transação era gravada sem descrição, e a lista emprestava o nome da
  categoria — uma categoria inteira aparecia como linhas de mesmo título. Os dois
  campos são opcionais e nenhum recebe foco: o caminho de três toques continua
  três toques.

  A observação (`notes`) já existia na tabela e já saía no backup; o que faltava
  era ela subir ao modelo de domínio para a folha poder gravá-la. Editar uma
  parcela **não** propaga a observação para as irmãs — valor e categoria são da
  compra, a observação é do que aconteceu naquela parcela.

### Corrigido

- **Transições entre telas engasgavam.** Todo estado de tela expunha valor
  derivado como `get()`, recalculado a cada leitura durante a composição, sobre o
  histórico inteiro. Numa passada do dashboard: as próximas contas três vezes, o
  comparativo do mês duas, e a lista toda **ordenada** para mostrar cinco linhas.
  Isso caía no primeiro quadro da tela que entrava, com as duas telas compostas
  ao mesmo tempo. Agora cada derivação é calculada uma vez por emissão do banco
  (`REQ-PERF-001`, com teste que falha se alguém desfizer).

- **Mapeamento de transações saía da thread principal.** O Room já rodava o SQL
  fora dela; era a conversão para o domínio que voltava para a thread que
  desenha, uma alocação por linha do histórico a cada emissão.

- **Valores monetários eram reformatados a cada quadro.** A leitura por extenso
  para o leitor de tela era montada mesmo com ele desligado, duas vezes por linha
  de lista.

### Alterado

- O movimento entre telas passou de 320ms para 240ms. O deslize é de largura
  inteira e sem fade, e o tempo que sobrava era palco para qualquer hesitação
  aparecer. Não é o conserto do engasgo — esse está acima —, é o palco ficando do
  tamanho da peça.

## [0.1.0] - 2026-09-02

Primeira versão pública. As fases F0, F1 e F2 do
[roadmap](README.md#roadmap) entram inteiras; F3 e F4 ficam de fora.

### Adicionado

**Contas e lançamentos (F0)**

- Contas de carteira, corrente, poupança, cartão e investimento, com saldo
  calculado a partir do lançamento — não guardado.
- Transferência como **uma** linha, não duas: o pagamento de fatura sai de graça
  daí, sem código especial de cartão.
- Dez categorias semeadas na instalação, com hierarquia de um nível.
- Lançamento rápido em três toques, parcelamento em N linhas com a sobra de
  centavos na última, e validação que devolve todos os erros de uma vez.
- Dashboard com saldo total, dívida do cartão, resultado do mês e comparação com
  o mês anterior.

**Cartão, orçamento e análise (F1)**

- Fatura derivada do fechamento, nunca uma tabela: `closingDay` restrito a 1–28
  mata a classe de bug do dia 31.
- Tetos por categoria, com estado de atenção e de estouro, e ritmo por dia.
- Recorrências com geração de ocorrências e efetivação em lote.
- Relatórios: pizza de despesas por categoria e a série dos últimos 12 meses.
- Exportação em CSV, base completa em JSON, e **backup cifrado** com AES-256-GCM
  sob chave derivada por PBKDF2 (600 mil iterações).
- Conta de investimento com rendimento mês a mês, taxa fixa ou percentual do CDI,
  com o CDI lido da série pública do Banco Central.

**Importação (F2)**

- OFX e CSV, com farejamento de separador, decimal e formato de data, e tela de
  mapeamento que aceita correção manual.
- Deduplicação por chave estável, auto-categorização por regras de estabelecimento,
  e desfazer por lote.
- Nada é gravado antes da tela de revisão.

**Segurança e privacidade**

- Banco cifrado com SQLCipher, chave de 32 bytes embrulhada no Android Keystore.
- Bloqueio biométrico opcional, com fallback para a credencial do aparelho.
- Sem backend, sem login, sem analytics. Uma única permissão de rede, para um
  único host — `api.bcb.gov.br`, num GET sem parâmetro e sem corpo.
- `allowBackup="false"`: o histórico não sai do aparelho por conta do sistema.

**Acessibilidade**

- Alvos de toque ≥ 48dp, contraste verificado por teste que recalcula da paleta,
  seleção sinalizada por preenchimento e não só por cor, e valores monetários
  lidos por extenso.

### Notas

- `minSdk 26`, `targetSdk 36`. `java.time` nativo, sem desugaring.
- O APK publicado é assinado; confira o `.sha256` que acompanha a release.
- Migrações de schema são explícitas e versionadas em `app/schemas/`.
  `fallbackToDestructiveMigration` não existe no projeto e não vai existir.

[Não lançado]: https://github.com/dev-isaacportela/financepro/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/dev-isaacportela/financepro/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/dev-isaacportela/financepro/releases/tag/v0.1.0
