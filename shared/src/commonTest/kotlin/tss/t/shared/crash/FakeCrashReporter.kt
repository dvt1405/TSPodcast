package tss.t.shared.crash

/** Records every call, for asserting what a component reported. */
class FakeCrashReporter : CrashReporter {
    val logs = mutableListOf<String>()
    val records = mutableListOf<Record>()
    val keys = mutableListOf<Pair<String, Any>>()
    val userIds = mutableListOf<String>()

    data class Record(val throwable: Throwable, val tag: String, val keys: Map<String, String>)

    override fun log(message: String) {
        logs += message
    }

    override fun record(throwable: Throwable, tag: String, keys: Map<String, String>) {
        records += Record(throwable, tag, keys)
    }

    override fun key(name: String, value: String) {
        keys += name to value
    }

    override fun key(name: String, value: Long) {
        keys += name to value
    }

    override fun key(name: String, value: Boolean) {
        keys += name to value
    }

    override fun userId(id: String) {
        userIds += id
    }
}
