import Foundation

struct Card: Identifiable, Codable, Equatable, Hashable {
    var id = UUID()
    var question: String
    var answer: String
}

struct Deck: Identifiable, Codable, Equatable, Hashable {
    var id = UUID()
    var name: String
    var colorHex: String
    var cards: [Card] = []
}

struct DayStat: Codable {
    var reviews: Int = 0
    var correct: Int = 0
    var best: Int = 0
}

/// Persistance locale (JSON dans Documents) — mêmes concepts que la version Android.
final class Store: ObservableObject {
    @Published var decks: [Deck] = [] { didSet { save() } }
    @Published var stats: [String: DayStat] = [:] { didSet { saveStats() } }

    private let decksURL: URL
    private let statsURL: URL
    private static let palette = ["6750A4", "1B873B", "B3261E", "0B57D0", "E8590C", "7A1FA2"]

    init() {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        decksURL = docs.appendingPathComponent("decks.json")
        statsURL = docs.appendingPathComponent("stats.json")
        decks = Self.load([Deck].self, from: decksURL) ?? []
        stats = Self.load([String: DayStat].self, from: statsURL) ?? [:]
    }

    private static func load<T: Decodable>(_ type: T.Type, from url: URL) -> T? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private func save() {
        if let data = try? JSONEncoder().encode(decks) {
            try? data.write(to: decksURL, options: .atomic)
        }
    }

    private func saveStats() {
        if let data = try? JSONEncoder().encode(stats) {
            try? data.write(to: statsURL, options: .atomic)
        }
    }

    // MARK: - Paquets

    func addDeck(named name: String) {
        let color = Self.palette[decks.count % Self.palette.count]
        decks.insert(Deck(name: name, colorHex: color), at: 0)
    }

    func renameDeck(_ deck: Deck, to name: String) {
        guard let i = decks.firstIndex(where: { $0.id == deck.id }) else { return }
        decks[i].name = name
    }

    func deleteDeck(_ deck: Deck) {
        decks.removeAll { $0.id == deck.id }
    }

    // MARK: - Cartes

    func addCard(to deck: Deck, question: String, answer: String) {
        guard let i = decks.firstIndex(where: { $0.id == deck.id }) else { return }
        decks[i].cards.append(Card(question: question, answer: answer))
    }

    func updateCard(_ card: Card, in deck: Deck, question: String, answer: String) {
        guard let i = decks.firstIndex(where: { $0.id == deck.id }),
              let j = decks[i].cards.firstIndex(where: { $0.id == card.id }) else { return }
        decks[i].cards[j].question = question
        decks[i].cards[j].answer = answer
    }

    func deleteCard(_ card: Card, in deck: Deck) {
        guard let i = decks.firstIndex(where: { $0.id == deck.id }) else { return }
        decks[i].cards.removeAll { $0.id == card.id }
    }

    // MARK: - Statistiques

    private static func dayKey(_ date: Date = Date()) -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: date)
    }

    func recordSession(total: Int, correct: Int) {
        let key = Self.dayKey()
        var s = stats[key] ?? DayStat()
        s.reviews += total
        s.correct += correct
        s.best = max(s.best, currentStreak())
        stats[key] = s
    }

    func todayStat() -> DayStat { stats[Self.dayKey()] ?? DayStat() }

    func currentStreak() -> Int {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        var streak = 0
        var day = Calendar.current.startOfDay(for: Date())
        if stats[f.string(from: day)]?.reviews == nil {
            day = Calendar.current.date(byAdding: .day, value: -1, to: day) ?? day
        }
        while stats[f.string(from: day)]?.reviews != nil {
            streak += 1
            day = Calendar.current.date(byAdding: .day, value: -1, to: day) ?? day
        }
        return streak
    }

    func bestStreak() -> Int { stats.values.map(\.best).max() ?? 0 }
}
