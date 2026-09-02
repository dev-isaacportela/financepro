# Política de segurança

## Como relatar

Use o **[relato privado de vulnerabilidade do GitHub](https://github.com/dev-isaacportela/mobile-finance/security/advisories/new)**.
Não abra issue pública para falha de segurança, e não mande detalhes por
pull request.

Respondo em até 7 dias. Se a falha for confirmada, a correção entra pelo mesmo
ciclo de sempre, com requisito na spec e teste que falha antes de passar, e o
crédito vai no advisory a menos que você prefira ficar anônimo.

## O que já é garantido, e por teste

Vale saber o que o projeto afirma, porque isso define o que é falha.

| Garantia | Onde vive | O que a quebra |
|---|---|---|
| Banco local cifrado com SQLCipher | REQ-SEC-001 | chave em claro, ou banco sem cifra |
| Chave no Keystore do aparelho | REQ-SEC-002 | chave derivada de constante, ou gravada em prefs |
| Sem backup do sistema | REQ-SEC-004, `allowBackup=false` | cópia do banco saindo pela nuvem do fabricante |
| Log sem dado financeiro | REQ-SEC-006 | valor, descrição ou conta em `Log` |
| Rede só para `api.bcb.gov.br` | REQ-SEC-007 | qualquer outro host em `src/main` |
| Conjunto exato de permissões | `ManifestTest` | permissão a mais, inclusive vinda de AAR transitivo |

As duas últimas são as mais fáceis de quebrar sem perceber, e por isso são as
que têm guarda automática. `ManifestTest` compara o manifesto **mesclado**
contra a lista exata, então uma dependência que traga `ACCESS_FINE_LOCATION`
reprova o build mesmo sem ninguém tocar no XML. E `tools/trace.py` reprova se
aparecer no código uma URL para host que não seja o do Banco Central.

## Dentro do escopo

Qualquer coisa que faça dado financeiro do usuário sair do aparelho, ou que
torne o banco legível sem a credencial do dono. Também conta contornar o
bloqueio biométrico de REQ-SEC-003, e qualquer caminho que grave a chave fora
do Keystore.

## Fora do escopo

Aparelho com root ou com o carregador de inicialização destravado. Nesse
cenário o Keystore não é uma fronteira, e o projeto não finge que é.

Ataque que exija acesso físico ao aparelho já desbloqueado.

A ausência de recursos que estão explicitamente fora por decisão registrada em
[decisoes.md](docs/decisoes.md), como Open Finance antes da F4.

## Versões

O projeto ainda não tem release publicada. Enquanto isso, o que recebe correção
é a branch `main`.
