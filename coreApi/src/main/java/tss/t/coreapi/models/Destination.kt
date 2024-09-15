package tss.t.coreapi.models

data class Destination(
    val address: String,
    val customKey: String? = null,
    val customValue: String? = null,
    val fee: Boolean? = null,
    val name: String,
    val split: Int,
    val type: String
)