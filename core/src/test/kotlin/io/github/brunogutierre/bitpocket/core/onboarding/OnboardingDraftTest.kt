package io.github.brunogutierre.bitpocket.core.onboarding

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class OnboardingDraftTest {
    @Test
    fun `replacing or clearing the mnemonic wipes the previous entropy`() {
        val draft = OnboardingDraft()
        val first = ByteArray(16) { 1 }

        draft.setMnemonic(first, listOf("a"))
        draft.setMnemonic(ByteArray(16) { 2 }, listOf("b"))

        assertArrayEquals(ByteArray(16), first)
        assertEquals(listOf("b"), draft.words)
        draft.clear()
        assertNull(draft.entropy)
        assertEquals(emptyList<String>(), draft.words)
    }
}
