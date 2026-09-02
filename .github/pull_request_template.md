<!--
  Art. 2 da constitution.md. Todo PR que muda comportamento responde a pelo
  menos um requisito. Se você não achar o requisito, provavelmente ele ainda
  não existe, e aí o primeiro commit deste PR é o que o escreve na spec.
-->

## Requisitos

<!-- Um por linha, no formato REQ-AREA-000. O CI confere que existem. -->

- REQ-

## O que muda, e por quê

<!-- O diff já mostra o quê. Escreva o porquê, e o que se perde na escolha. -->

## Como verificar

<!--
  O teste que falhava antes e passa agora. Se não houver, explique por que
  este PR não muda comportamento (documentação, refatoração sem efeito).
-->

## Antes de marcar como pronto

- [ ] `python tools/trace.py` sai com código zero
- [ ] `./gradlew detekt test assembleDebug` passa
- [ ] O teste do passo 4 do ciclo existe, e falhava antes da correção
- [ ] Se houve escolha de arquitetura, virou ADR em `docs/decisoes.md`
- [ ] Se a spec divergiu da implementação, a spec foi corrigida neste mesmo PR

## Se este PR adiciona dependência

- [ ] A licença dela é compatível com a Apache 2.0 do projeto
- [ ] Ela não traz permissão nova para o manifesto mesclado, ou `ManifestTest` foi atualizado junto e a permissão está justificada
- [ ] Ela não fala com nenhum host além de `api.bcb.gov.br`
- [ ] Se o binário dela é redistribuído, o `NOTICE` foi atualizado
