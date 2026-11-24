package com.sirelon.sellsnap.features.seller.auth.data

import com.sirelon.sellsnap.features.seller.auth.domain.OlxTokens
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The OLX tokens as written to disk, in the legacy token store and inside [OlxAccountRecord].
 *
 * Installed apps hold JSON in exactly this shape, so a key renamed here logs every seller out
 * after an update. The keys are fixed by the `@SerialName` values; `OlxStoredTokensFormatTest`
 * pins them. [OlxTokens] stays free of serialization so its fields can change safely.
 */
@Serializable
internal class OlxTokensRecord(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long,
    @SerialName("token_type") val tokenType: String,
    @SerialName("scope") val scope: String,
    @SerialName("issued_at_epoch_seconds") val issuedAtEpochSeconds: Long,
) {
    fun toDomain(): OlxTokens = OlxTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresInSeconds = expiresInSeconds,
        tokenType = tokenType,
        scope = scope,
        issuedAtEpochSeconds = issuedAtEpochSeconds,
    )
}

internal fun OlxTokens.toRecord(): OlxTokensRecord = OlxTokensRecord(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresInSeconds = expiresInSeconds,
    tokenType = tokenType,
    scope = scope,
    issuedAtEpochSeconds = issuedAtEpochSeconds,
)

/** Lets [OlxAccountRecord] hold a plain [OlxTokens] while the disk format stays [OlxTokensRecord]. */
internal object OlxTokensRecordSerializer : KSerializer<OlxTokens> {
    private val delegate = OlxTokensRecord.serializer()

    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: OlxTokens) =
        encoder.encodeSerializableValue(delegate, value.toRecord())

    override fun deserialize(decoder: Decoder): OlxTokens =
        decoder.decodeSerializableValue(delegate).toDomain()
}
