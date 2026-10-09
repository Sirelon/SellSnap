package com.sirelon.sellsnap.content

import com.sirelon.sellsnap.model.Pillar

/** Short post code: account prefix, pillar letter, zero-padded sequence, e.g. `pld0042`. */
object PostCode {
    /** Apple caps the campaign token `ct` at 30 alphanumeric characters. */
    const val MAX_LENGTH = 30

    private val VALID = Regex("[a-z0-9]{1,$MAX_LENGTH}")

    fun format(prefix: String, pillar: Pillar, seq: Int): String {
        require(seq >= 0) { "seq must not be negative" }
        val code = prefix + pillar.letter + seq.toString().padStart(4, '0')
        require(isValid(code)) { "Post code is not 1-$MAX_LENGTH lowercase alphanumeric characters" }
        return code
    }

    /** Cheap shape check to run before any read on `/go/{code}`. */
    fun isValid(code: String): Boolean = VALID.matches(code)
}
