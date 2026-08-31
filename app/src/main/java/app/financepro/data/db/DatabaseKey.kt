package app.financepro.data.db

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Senha do banco SQLCipher. REQ-SEC-002 · [ADR-010](../../../../../../../../docs/decisoes.md)
 *
 * Trinta e dois bytes de [SecureRandom], gerados no primeiro boot e guardados
 * **cifrados** em `filesDir/db.key`. Quem os cifra é uma chave AES-GCM que vive
 * dentro do Android Keystore e nunca sai de lá.
 *
 * Por que não guardar os 32 bytes direto no Keystore, como diz o ADR-010 ao pé
 * da letra: o provider `AndroidKeyStore` não devolve material de chave —
 * `SecretKey.getEncoded()` retorna `null` de propósito. Uma senha que precisa
 * ser lida de volta para abrir o banco, portanto, só pode ser **embrulhada** por
 * uma chave do Keystore, não guardada nele. O efeito prático é o que o ADR quer:
 * o arquivo copiado do aparelho não abre em outro lugar, porque a chave que o
 * decifra é inextraível e presa àquele hardware.
 *
 * Sem `setUserAuthenticationRequired`: o worker de recorrência (T-031) abre o
 * banco com a tela bloqueada. O bloqueio biométrico é da tela (REQ-SEC-003), não
 * do arquivo.
 */
class DatabaseKey(private val context: Context) {

    /**
     * A senha, gerando-a na primeira chamada.
     *
     * Falha em vez de gerar uma senha nova quando o arquivo existe mas não
     * decifra: senha nova significa banco ilegível, e regenerar em silêncio
     * transformaria um problema de chave na perda calada de todo o histórico
     * financeiro do usuário. Melhor estourar e deixar a restauração de backup
     * (T-035) ser uma decisão de quem é dono dos dados.
     */
    fun getOrCreate(): ByteArray {
        val arquivo = File(context.filesDir, KEY_FILE)
        if (arquivo.exists()) return decifrar(arquivo.readBytes())

        val senha = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        val temporario = File(context.filesDir, "$KEY_FILE.tmp")
        temporario.writeBytes(cifrar(senha))
        // Renomear é atômico no mesmo volume: sem isto, um desligamento no meio
        // da escrita deixaria um arquivo truncado, indistinguível de corrupção.
        check(temporario.renameTo(arquivo)) { "não consegui gravar $KEY_FILE" }
        return senha
    }

    private fun cifrar(senha: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMACAO)
        cipher.init(Cipher.ENCRYPT_MODE, chaveDoKeystore())
        // IV na frente, em claro: GCM exige que ele seja único, não secreto.
        return cipher.iv + cipher.doFinal(senha)
    }

    private fun decifrar(envelope: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMACAO)
        val spec = GCMParameterSpec(TAG_BITS, envelope, 0, IV_BYTES)
        cipher.init(Cipher.DECRYPT_MODE, chaveDoKeystore(), spec)
        return cipher.doFinal(envelope, IV_BYTES, envelope.size - IV_BYTES)
    }

    private fun chaveDoKeystore(): SecretKey {
        val keystore = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (keystore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BYTES * Byte.SIZE_BITS)
                    // Recusa reusar IV, que em GCM vaza o texto claro.
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val ALIAS = "finance_db_key"
        const val KEY_FILE = "db.key"
        const val TRANSFORMACAO = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
