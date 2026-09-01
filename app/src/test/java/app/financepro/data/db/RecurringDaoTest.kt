package app.financepro.data.db

import app.financepro.core.testing.Req
import app.financepro.data.repo.RecurringRepository
import app.financepro.domain.model.TxnType
import app.financepro.domain.usecase.Frequency
import app.financepro.domain.usecase.RecurrenceSpec
import app.financepro.domain.usecase.RecurringRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * REQ-REC-001 — a regra atravessa a fronteira sem perder campo.
 *
 * O schema guarda a recorrência em colunas soltas — `freq` como texto, `weekday`
 * como inteiro ISO, três datas como `epochDay` — e o domínio a vê como um
 * `RecurrenceSpec`. São duas traduções escritas em arquivos diferentes
 * (`Mappers.kt` e `Repositories.kt`), e nada além deste teste garante que elas
 * concordam: um `weekday` gravado como ordinal e lido como ISO deslocaria toda
 * conta semanal em um dia, sem erro nenhum.
 */
@Req("REQ-REC-001")
class RecurringDaoTest : DbTest() {

    private val hoje = LocalDate.of(2026, 9, 1)

    @Test
    fun `ida e volta preserva os campos da regra`() = runBlocking {
        val conta = db.accountDao().upsert(CONTA)
        val categoria = db.categoryDao().upsert(CATEGORIA)
        // Inativa de propósito: `salvar` gera junto, e uma regra que gera volta
        // com `lastGeneratedDate` preenchido — o que se quer comparar aqui é o
        // cadastro, não a geração (essa é do `GenerateRecurringTest`).
        val regra = RecurringRule(
            accountId = conta,
            categoryId = categoria,
            type = TxnType.EXPENSE,
            amountCents = -1_200_00,
            description = "Academia",
            spec = RecurrenceSpec(
                frequency = Frequency.WEEKLY,
                startDate = LocalDate.of(2026, 9, 2),
                interval = 3,
                endDate = LocalDate.of(2027, 1, 1),
                weekday = DayOfWeek.FRIDAY,
            ),
            autoPost = true,
            active = false,
        )

        val id = RecurringRepository(db.recurringDao()).salvar(regra, hoje)

        assertEquals(regra.copy(id = id), db.recurringDao().byId(id)?.toDomain())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `frequencia desconhecida estoura, em vez de virar mensal`() {
        // `Frequency.valueOf` falhar é a decisão: uma frequência que o app não
        // conhece virando MONTHLY em silêncio colocaria a conta no mês errado
        // sem nenhum sinal de que algo deu errado.
        runBlocking {
            val conta = db.accountDao().upsert(CONTA)
            db.recurringDao().upsert(
                RecurringRuleEntity(
                    accountId = conta,
                    type = TxnType.EXPENSE,
                    amountCents = -100,
                    description = "Lunar",
                    freq = "LUNAR",
                    startDate = dia(2026, 9, 1),
                ),
            ).let { db.recurringDao().byId(it)!!.toDomain() }
        }
    }
}
