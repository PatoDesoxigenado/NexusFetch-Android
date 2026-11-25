package io.github.patodesoxigenado.nexusfetch.domain.error

/**
 * Strongly typed domain errors so failures never fall back to silent catches or generic strings.
 */
sealed class NexusError(
    open val userMessage: String,
    override val message: String = userMessage,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    data class NetworkError(
        override val userMessage: String = "Falha de conexão com a internet. Verifique sua rede e tente novamente.",
        override val cause: Throwable? = null,
    ) : NexusError(userMessage = userMessage, cause = cause)

    data class UnsupportedUrlError(
        override val userMessage: String = "Link não reconhecido ou plataforma não suportada.",
    ) : NexusError(userMessage = userMessage)

    data class ExtractionError(
        override val userMessage: String = "Não foi possível obter os dados dessa mídia.",
        override val cause: Throwable? = null,
    ) : NexusError(userMessage = userMessage, cause = cause)

    data class EncoderError(
        override val userMessage: String = "Falha ao processar o arquivo de mídia.",
        override val cause: Throwable? = null,
    ) : NexusError(userMessage = userMessage, cause = cause)

    data class StorageError(
        override val userMessage: String = "Não foi possível gravar o arquivo no armazenamento do aparelho.",
        override val cause: Throwable? = null,
    ) : NexusError(userMessage = userMessage, cause = cause)

    data class CancelledError(
        override val userMessage: String = "Operação cancelada pelo usuário.",
    ) : NexusError(userMessage = userMessage)

    data class ChecksumMismatchError(
        override val userMessage: String = "A verificação de integridade SHA-256 da atualização falhou.",
    ) : NexusError(userMessage = userMessage)

    data class ValidationError(
        override val userMessage: String,
    ) : NexusError(userMessage = userMessage)
}
