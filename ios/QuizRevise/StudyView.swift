import SwiftUI

struct StudyView: View {
    let cards: [Card]
    let onFinish: (Int, Int) -> Void
    @Environment(\.presentationMode) private var presentation
    @Environment(\.scenePhase) private var scenePhase

    enum Phase { case modeChoice, flash, quiz, write, test, done }
    @State private var phase: Phase = .modeChoice
    @State private var order: [Int] = []
    @State private var pos = 0
    @State private var correct = 0
    @State private var revealed = false
    @State private var quizOptions: [String] = []
    @State private var selectedOption: String?
    @State private var writtenText = ""
    @State private var writeChecked = false
    @State private var delayTask: DispatchWorkItem?
    @State private var flipAngle: Double = 0
    @State private var flipAnimating = false
    // État du mode Test : 10 questions mixtes, pas de retour arrière, note sur 20.
    @State private var testIsQcm = false
    @State private var testAbandoned = false
    // Dernier mode lancé (phase == .done ne connaît plus le mode).
    @State private var lastMode = "flash"

    private var currentCard: Card { cards[order[pos]] }

    /// Un test type professeur compte 10 questions (chaque bonne réponse vaut 2 points sur 20).
    private static let testLength = 10

    var body: some View {
        NavigationView {
            VStack(spacing: 16) {
                switch phase {
                case .modeChoice:
                    Spacer()
                    Button("🃏 Flashcards") { start("flash") }
                        .buttonStyle(.borderedProminent).controlSize(.large)
                    Button("❓ QCM") { start("quiz") }
                        .buttonStyle(.bordered).controlSize(.large)
                    Button("✏️ Réécrire le mot") { start("write") }
                        .buttonStyle(.bordered).controlSize(.large)
                    Button("📝 Test (noté sur 20)") { start("test") }
                        .buttonStyle(.bordered).controlSize(.large)
                    Spacer()
                case .flash, .quiz, .write, .test:
                    ProgressView(value: Double(pos), total: Double(order.count))
                        .padding(.horizontal)
                    Text("\(min(pos + 1, order.count)) / \(order.count)")
                        .font(.caption).foregroundColor(.secondary)
                    if phase == .flash { flashCard } else if phase == .quiz { quizCard } else if phase == .write { writeCard } else { testCard }
                case .done:
                    Spacer()
                    if lastMode == "test" && testAbandoned {
                        Text("Test interrompu").font(.title2).bold()
                        Text("Tu as quitté l'app pendant l'épreuve — la note n'est pas comptée.")
                            .font(.body).foregroundColor(.secondary)
                            .multilineTextAlignment(.center).padding(.horizontal)
                    } else if lastMode == "test" {
                        Text("Test terminé !").font(.title2).bold()
                        Text("Note : \(correct * 20 / max(order.count, 1)) / 20")
                            .font(.system(size: 34, weight: .bold)).foregroundColor(.accentColor)
                        Text(mentionText).font(.title3)
                        Text("Score : \(correct) / \(order.count) (\(correct * 100 / max(order.count, 1))%)")
                            .font(.subheadline).foregroundColor(.secondary)
                    } else {
                        Text("Session terminée !").font(.title2).bold()
                        Text("Score : \(correct) / \(order.count) (\(correct * 100 / max(order.count, 1))%)")
                            .font(.title3).foregroundColor(.accentColor).bold()
                    }
                    Button("Recommencer") { start(lastMode) }
                        .buttonStyle(.borderedProminent)
                    Button("Fermer") { presentation.wrappedValue.dismiss() }
                    Spacer()
                }
            }
            .navigationTitle("Révision")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fermer") { presentation.wrappedValue.dismiss() }
                }
            }
        }
        .interactiveDismissDisabled(phase == .flash || phase == .quiz || phase == .write || phase == .test)
        .onChange(of: scenePhase) { newPhase in
            // Anti-triche : quitter l'app pendant l'épreuve (autre app, accueil…)
            // interrompt le test ; la note ne sera pas comptée.
            if newPhase != .active && phase == .test && pos < order.count { testAbandoned = true }
        }
    }

    private var currentMode: String {
        switch phase {
        case .quiz: return "quiz"
        case .write: return "write"
        case .test: return "test"
        default: return "flash"
        }
    }

    private var flashCard: some View {
        VStack(spacing: 24) {
            Text(revealed ? "RÉPONSE" : "QUESTION")
                .font(.caption).bold().foregroundColor(.accentColor)
            Text(revealed ? currentCard.answer : currentCard.question)
                .font(.title2).bold().multilineTextAlignment(.center)
            if !revealed {
                Text("Touche la carte pour la retourner")
                    .font(.caption).foregroundColor(.secondary)
            } else {
                HStack(spacing: 16) {
                    Button("Pas su") { answer(false) }
                        .buttonStyle(.bordered).tint(.red)
                    Button("Je savais") { answer(true) }
                        .buttonStyle(.borderedProminent).tint(.green)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(
            RoundedRectangle(cornerRadius: 24)
                .fill(Color(.systemBackground))
                .shadow(radius: 4)
        )
        .padding()
        .rotation3DEffect(.degrees(flipAngle), axis: (x: 0, y: 1, z: 0), perspective: 0.8)
        .onTapGesture { flip() }
    }

    /// Retournement illimité : rotation 3D, le contenu bascule à 90°.
    private func flip() {
        guard !flipAnimating else { return }
        flipAnimating = true
        withAnimation(.easeIn(duration: 0.18)) { flipAngle = 90 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
            revealed.toggle()
            withAnimation(.easeOut(duration: 0.18)) { flipAngle = 0 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) { flipAnimating = false }
        }
    }

    private var quizCard: some View {
        VStack(spacing: 20) {
            Text(currentCard.question)
                .font(.title2).bold().multilineTextAlignment(.center)
                .padding(.horizontal)
            ForEach(quizOptions, id: \.self) { option in
                Button(option) {
                    if selectedOption == nil { selectedOption = option; answer(option == currentCard.answer) }
                }
                .buttonStyle(.bordered)
                .tint(optionColor(option))
                .disabled(selectedOption != nil)
            }
            Spacer()
        }
        .padding(.top, 12)
    }

    private var writeCard: some View {
        VStack(spacing: 20) {
            Text(currentCard.question)
                .font(.title2).bold().multilineTextAlignment(.center)
                .padding(.horizontal)
            TextField("Ta réponse", text: $writtenText)
                .textFieldStyle(.roundedBorder)
                .onSubmit { checkWritten() }
                .padding(.horizontal)
                .disabled(writeChecked)
            if writeChecked {
                Text(writtenText.compareFolded == currentCard.answer.compareFolded ? "Correct !" : "Raté… → \(currentCard.answer)")
                    .font(.headline)
                    .foregroundColor(writtenText.compareFolded == currentCard.answer.compareFolded ? .green : .red)
            }
            if !writeChecked {
                Button("Valider") { checkWritten() }
                    .buttonStyle(.borderedProminent).controlSize(.large)
                    .disabled(writtenText.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            Spacer()
        }
        .padding(.top, 12)
    }

    private func checkWritten() {
        guard phase == .write else { return }
        guard !writeChecked, !writtenText.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        writeChecked = true
        answer(writtenText.compareFolded == currentCard.answer.compareFolded)
    }

    /// Épreuve type professeur : QCM ou réécriture tiré au sort, sans retour en arrière.
    private var testCard: some View {
        VStack(spacing: 20) {
            Text(currentCard.question)
                .font(.title2).bold().multilineTextAlignment(.center)
                .padding(.horizontal)
            if testIsQcm {
                ForEach(quizOptions, id: \.self) { option in
                    Button(option) {
                        if selectedOption == nil { selectedOption = option; answer(option == currentCard.answer) }
                    }
                    .buttonStyle(.bordered)
                    .tint(optionColor(option))
                    .disabled(selectedOption != nil)
                }
            } else {
                TextField("Ta réponse", text: $writtenText)
                    .textFieldStyle(.roundedBorder)
                    .onSubmit { checkTestWritten() }
                    .padding(.horizontal)
                    .disabled(writeChecked)
                if !writeChecked {
                    Button("Valider") { checkTestWritten() }
                        .buttonStyle(.borderedProminent).controlSize(.large)
                        .disabled(writtenText.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            Spacer()
        }
        .padding(.top, 12)
    }

    private var mentionText: String {
        let note = correct * 20 / max(order.count, 1)
        if note >= 16 { return "Très bien ! 🌟" }
        if note >= 12 { return "Bien 👍" }
        if note >= 10 { return "Assez bien" }
        return "Insuffisant — réessaie pour progresser 💪"
    }

    private func prepareTestQuestion() {
        let enough = cards.filter {
            $0.answer != currentCard.answer && !$0.answer.trimmingCharacters(in: .whitespaces).isEmpty
        }.count >= 3
        testIsQcm = Bool.random() && enough
        if testIsQcm {
            let distractors = cards.map(\.answer)
                .filter { $0 != currentCard.answer && !$0.answer.trimmingCharacters(in: .whitespaces).isEmpty }
                .shuffled().prefix(3)
            quizOptions = (Array(distractors) + [currentCard.answer]).shuffled()
        }
        selectedOption = nil
        writtenText = ""
        writeChecked = false
    }

    private func checkTestWritten() {
        guard !writeChecked, !writtenText.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        writeChecked = true
        answer(writtenText.compareFolded == currentCard.answer.compareFolded)
    }

    private func optionColor(_ option: String) -> Color {
        guard let sel = selectedOption else { return .accentColor }
        if option == sel { return option == currentCard.answer ? .green : .red }
        if option == currentCard.answer { return .green }
        return .accentColor
    }

    private func start(_ mode: String) {
        lastMode = mode
        order = Array(cards.indices).shuffled()
        if mode == "test" && order.count > Self.testLength { order = Array(order.prefix(Self.testLength)) }
        pos = 0
        correct = 0
        revealed = false
        selectedOption = nil
        writtenText = ""
        writeChecked = false
        testAbandoned = false
        phase = mode == "flash" ? .flash : (mode == "quiz" ? .quiz : (mode == "test" ? .test : .write))
        if mode == "quiz" { prepareQuiz() }
        if mode == "test" { prepareTestQuestion() }
    }

    private func prepareQuiz() {
        let distractors = cards.map(\.answer).filter { $0 != currentCard.answer }.shuffled().prefix(3)
        quizOptions = (Array(distractors) + [currentCard.answer]).shuffled()
        selectedOption = nil
    }

    private func answer(_ ok: Bool) {
        if ok { correct += 1 }
        let task = DispatchWorkItem {
            pos += 1
            revealed = false
            flipAngle = 0
            selectedOption = nil
            writtenText = ""
            writeChecked = false
            if pos >= order.count {
                if !(phase == .test && testAbandoned) { onFinish(order.count, correct) }
                phase = .done
            } else {
                if phase == .quiz { prepareQuiz() }
                if phase == .test { prepareTestQuestion() }
            }
        }
        delayTask = task
        let delay: Double
        switch phase {
        case .quiz: delay = 0.9
        case .write: delay = 1.2
        case .test: delay = testIsQcm ? 0.9 : 1.2
        default: delay = 0.2
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: task)
    }
}

/// Comparaison tolérante (mode Réécrire) : casse, accents, ponctuation et espaces ignorés.
private extension String {
    var compareFolded: String {
        let folded = folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "fr_FR"))
        let cleaned = folded.map { ch -> Character in
            (ch.isLetter || ch.isNumber) ? ch : " "
        }
        return String(cleaned).split(separator: " ", omittingEmptySubsequences: true)
            .joined(separator: " ").lowercased()
    }
}

struct StatsSheet: View {
    @ObservedObject var store: Store
    @Environment(\.presentationMode) private var presentation

    var body: some View {
        NavigationView {
            Form {
                Section("Aujourd'hui") {
                    Text("Cartes revues : \(store.todayStat().reviews)")
                    Text("Précision : \(store.todayStat().reviews > 0 ? store.todayStat().correct * 100 / store.todayStat().reviews : 0)%")
                }
                Section("Séries") {
                    Text("Série actuelle : \(store.currentStreak()) jours")
                    Text("Meilleure série : \(store.bestStreak()) jours")
                }
            }
            .navigationTitle("Statistiques")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fermer") { presentation.wrappedValue.dismiss() }
                }
            }
        }
    }
}
