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

    // MARK: - Transfert .qrevise (compatible Windows/Linux/Android)

    private static func hex(_ colorHex: String) -> String { "#" + colorHex }

    private static func color(fromHex hex: String?) -> String {
        let h = (hex ?? "").trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "#", with: "")
        return Self.palette.contains(h) ? h : (h.count == 6 ? h : "6750A4")
    }

    private static func stamp() -> String {
        let f = ISO8601DateFormatter()
        return f.string(from: Date())
    }

    /// Exporte tous les paquets (ou un seul) au format .qrevise. Renvoie le JSON, ou nil.
    func exportJson(deckID: UUID? = nil) -> String? {
        let selected = deckID.map { id in decks.filter { $0.id == id } } ?? decks
        guard !selected.isEmpty else { return nil }
        var arr: [[String: Any]] = []
        for d in selected {
            arr.append([
                "name": d.name,
                "color": hex(d.colorHex),
                "cards": d.cards.map { ["q": $0.question, "a": $0.answer] }
            ])
        }
        let root: [String: Any] = [
            "format": "quizrevise",
            "version": 1,
            "exported": Self.stamp(),
            "decks": arr
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys]) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    /// Importe des paquets depuis un JSON .qrevise. Renvoie le nombre de paquets ajoutés.
    @discardableResult
    func importJson(_ text: String) -> Int {
        guard let data = text.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              root["format"] as? String == "quizrevise",
              let arr = root["decks"] as? [[String: Any]] else { return 0 }
        var added = 0
        for d in arr {
            guard let name = d["name"] as? String, !name.trimmingCharacters(in: .whitespaces).isEmpty else { continue }
            let color = Self.color(fromHex: d["color"] as? String)
            let deck = Deck(name: name, colorHex: color)
            if let cards = d["cards"] as? [[String: Any]] {
                for c in cards {
                    if let q = c["q"] as? String, let a = c["a"] as? String,
                       !q.trimmingCharacters(in: .whitespaces).isEmpty,
                       !a.trimmingCharacters(in: .whitespaces).isEmpty {
                        deck.cards.append(Card(question: q, answer: a))
                    }
                }
            }
            decks.insert(deck, at: 0)
            added += 1
        }
        return added
    }
}
