package tss.t.core.exceptions

class TSExceptions(
    val errorCode: Int,
    val description: String,
    override val message: String?,
    override val cause: Throwable?
) : Exception(message, cause) {
}