/// In which order a play of the news hub writes its shows to Room (M6 §3.4: "mỗi tập ghi Room
/// với podcast từ feed của nó"). The list mixes several shows, and `PlaybackHistory.savePlaying`
/// writes one podcast and the current id per call, so the list is saved one show at a time:
/// every other show first, in the order it first appears, the show of the started episode last
/// (its call sets `extra:current_item_id`). Pure, so it is tested on macOS too.
enum NewsHistoryOrder {
    /// One `savePlaying` call: the show and the positions of its episodes in the list.
    struct Group: Equatable, Sendable {
        let feedId: Int64
        let indices: [Int]
    }

    /// Groups of `feedIds` (one per list item); the group holding `startIndex` comes last, and
    /// its started episode is the one to pass as `episode`. Empty when `startIndex` is out of range.
    static func groups(feedIds: [Int64], startIndex: Int) -> [Group] {
        guard feedIds.indices.contains(startIndex) else { return [] }
        var order: [Int64] = []
        var positions: [Int64: [Int]] = [:]
        for (index, feedId) in feedIds.enumerated() {
            if positions[feedId] == nil {
                order.append(feedId)
            }
            positions[feedId, default: []].append(index)
        }
        let startFeed = feedIds[startIndex]
        let others = order.filter { $0 != startFeed }
        return (others + [startFeed]).map { Group(feedId: $0, indices: positions[$0] ?? []) }
    }
}
