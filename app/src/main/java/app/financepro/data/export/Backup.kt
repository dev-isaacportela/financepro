package app.financepro.data.export

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Backup criptografado. REQ-BAK-002
 *
 * O arquivo é o JSON da [BaseExportada], comprimido e cifrado com AES-256-GCM,
 * sob uma chave derivada da senha do usuário por PBKDF2-HMAC-SHA256.
 *
 * ```
 * FPBK | versão (1) | sal (16) | IV (12) | texto cifrado + tag GCM
 * ```
 *
 * O cabeçalho vai em claro porque precisa: sem o sal e o IV não há como derivar
 * a chave nem decifrar, e nenhum dos dois é segredo — o que eles precisam ser é
 * **único por arquivo**, e é o que [SecureRandom] garante. Ele entra como AAD do
 * GCM, então trocar um byte do sal ou da versão faz a autenticação falhar em vez
 * de produzir lixo silencioso.
 *
 * **GCM, não CBC.** O modo autenticado é o que transforma "senha errada" em erro
 * detectado em vez de bytes aleatórios que o parser de JSON tentaria ler. É a
 * mesma escolha de `DatabaseKey`, pela mesma razão.
 *
 * **Comprimido antes de cifrar**, e não .zip como a arquitetura dizia: um `.zip`
 * de um arquivo só é um nome de entrada e um diretório central em volta do mesmo
 * gzip. `GZIPOutputStream` é da biblioteca padrão e dá a mesma redução — dez
 * anos de lançamentos cabem em algumas centenas de KB — sem nada para escolher
 * ou nomear. (Comprimir antes de cifrar vaza tamanho, nunca conteúdo, e aqui não
 * há segredo adivinhável misturado a texto escolhido pelo atacante: não é o
 * cenário do CRIME.)
 */

/** Versão do envelope, não do conteúdo. O `schema` do JSON é outro. */
const val VERSAO_BACKUP = 1

/**
 * Iterações do PBKDF2.
 *
 * O arquivo vai parar no Drive, no cartão SD, no e-mail para si mesmo — lugares
 * onde um ataque de dicionário roda offline, sem limite de tentativas. É por
 * isso que o número é alto e não "o que fica instantâneo": o custo é de dois a
 * cinco segundos **uma vez**, na criação e na restauração, e é a única coisa
 * entre uma senha fraca e o histórico financeiro inteiro.
 */
const val ITERACOES_PBKDF2 = 600_000

/** Senha errada, arquivo corrompido, ou trocado no meio do caminho. */
class BackupIlegivel(mensagem: String, causa: Throwable? = null) : Exception(mensagem, causa)

fun cifrarBackup(base: BaseExportada, senha: CharArray): ByteArray {
    val aleatorio = SecureRandom()
    val sal = ByteArray(SAL_BYTES).also(aleatorio::nextBytes)
    val iv = ByteArray(IV_BYTES).also(aleatorio::nextBytes)
    val cabecalho = MAGICO + byteArrayOf(VERSAO_BACKUP.toByte()) + sal + iv

    val cifra = Cipher.getInstance(TRANSFORMACAO).apply {
        init(Cipher.ENCRYPT_MODE, derivar(senha, sal), GCMParameterSpec(TAG_BITS, iv))
        updateAAD(cabecalho)
    }
    return cabecalho + cifra.doFinal(comprimir(paraJson(base)))
}

/**
 * Lê o arquivo, ou explica por que não deu. REQ-BAK-002
 *
 * Toda falha vira [BackupIlegivel] com uma frase: senha errada e arquivo
 * truncado chegam aqui como `AEADBadTagException` e como
 * `ArrayIndexOutOfBoundsException`, e nenhum dos dois nomes diz nada a quem
 * está tentando recuperar o próprio histórico.
 */
fun decifrarBackup(arquivo: ByteArray, senha: CharArray): BaseExportada {
    val (sal, iv) = cabecalhoDe(arquivo)

    return runCatching {
        val cifra = Cipher.getInstance(TRANSFORMACAO).apply {
            init(Cipher.DECRYPT_MODE, derivar(senha, sal), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(arquivo.copyOf(CABECALHO_BYTES))
        }
        val claro = cifra.doFinal(arquivo, CABECALHO_BYTES, arquivo.size - CABECALHO_BYTES)
        deJson(descomprimir(claro))
    }.getOrElse { throw BackupIlegivel(SENHA_OU_ARQUIVO, it) }
}

/**
 * Sal e IV do arquivo, depois de conferir que ele é um backup desta versão.
 *
 * As duas checagens vêm **antes** de derivar a chave: sem elas, apontar o
 * seletor para uma foto por engano custaria os mesmos segundos de PBKDF2 para
 * terminar em "senha incorreta" — que é a frase errada para o problema.
 */
private fun cabecalhoDe(arquivo: ByteArray): Pair<ByteArray, ByteArray> {
    if (arquivo.size < CABECALHO_BYTES || !arquivo.copyOf(MAGICO.size).contentEquals(MAGICO)) {
        throw BackupIlegivel(NAO_E_BACKUP)
    }
    val versao = arquivo[MAGICO.size].toInt()
    if (versao != VERSAO_BACKUP) {
        throw BackupIlegivel("Backup da versão $versao; este app lê a $VERSAO_BACKUP.")
    }
    return arquivo.copyOfRange(MAGICO.size + 1, MAGICO.size + 1 + SAL_BYTES) to
        arquivo.copyOfRange(MAGICO.size + 1 + SAL_BYTES, CABECALHO_BYTES)
}

/**
 * PBKDF2-HMAC-SHA256, 256 bits.
 *
 * A senha viaja como `CharArray` porque é o que o `PBEKeySpec` recebe, e o
 * `clearPassword` no `finally` zera a cópia que ele guarda. Não é higiene
 * completa: o campo de texto da tela entrega uma `String`, que é imutável e fica
 * no heap até o coletor passar. Zerar o que dá para zerar continua valendo mais
 * que não zerar nada, e o resto sairia caro — um `TextField` sobre `CharArray`
 * é reescrever o campo inteiro por um vazamento que o Android já tem em toda
 * tela de senha.
 */
private fun derivar(senha: CharArray, sal: ByteArray): SecretKeySpec {
    val spec = PBEKeySpec(senha, sal, ITERACOES_PBKDF2, CHAVE_BITS)
    return try {
        SecretKeySpec(SecretKeyFactory.getInstance(DERIVACAO).generateSecret(spec).encoded, "AES")
    } finally {
        spec.clearPassword()
    }
}

private fun comprimir(texto: String): ByteArray {
    val saida = ByteArrayOutputStream()
    GZIPOutputStream(saida).use { it.write(texto.toByteArray(Charsets.UTF_8)) }
    return saida.toByteArray()
}

private fun descomprimir(bytes: ByteArray): String =
    GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }

/** `FPBK`, para o app reconhecer o arquivo antes de gastar segundos derivando. */
private val MAGICO = "FPBK".toByteArray(Charsets.US_ASCII)

private const val SAL_BYTES = 16
private const val IV_BYTES = 12
private const val TAG_BITS = 128
private const val CHAVE_BITS = 256
private const val CABECALHO_BYTES = 4 + 1 + SAL_BYTES + IV_BYTES
private const val TRANSFORMACAO = "AES/GCM/NoPadding"
private const val DERIVACAO = "PBKDF2WithHmacSHA256"

private const val NAO_E_BACKUP = "Este arquivo não é um backup do app."
private const val SENHA_OU_ARQUIVO = "Senha incorreta, ou o arquivo está corrompido."
