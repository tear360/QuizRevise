import SwiftUI

struct StudyView: View {
    let cards: [Card]
    let onFinish: (Int, Int) -> Void
    @Environment(\.presentationMode) private var presentation

    enum Phase { case modeChoice, flash, quiz, done }
    @State private var phase: Phase = .modeChoice
    @State private var order: [Int] = []
    @State private var pos = 0
    @State private var correct = 0
    @State private var revealed = false
    @State private var quizOptions: [String] = []
    @State private var selectedOption: String?
    @State private var delayTask: DispatchWorkItem?
    @State private var flipAngle: Double = 0
    @State private var flipAnimating = false

    private var currentCard: Card { cards[order[pos]] }

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
                    Spacer()
                case .flash, .quiz:
                    ProgressView(value: Double(pos), total: Double(order.count))
                        .padding(.horizontal)
                    Text("\(min(pos + 1, order.count)) / \(order.count)")
                        .font(.caption).foregroundColor(.secondary)
                    if phase == .flash { flashCard } else { quizCard }
                case .done:
                    Spacer()
                    Text("Session terminée !").font(.title2).bold()
                    Text("Score : \(correct) / \(order.count) (\(correct * 100 / max(order.count, 1))%)")
                        .font(.title3).foregroundColor(.accentColor).bold()
                    Button("Recommencer") { start(currentMode) }
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
        .interactiveDismissDisabled(phase == .flash || phase == .quiz)
    }

    private var currentMode: String { phase == .quiz ? "quiz" : "flash" }

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

    private func optionColor(_ option: String) -> Color {
        guard let sel = selectedOption else { return .accentColor }
        if option == sel { return option == currentCard.answer ? .green : .red }
        if option == currentCard.answer { return .green }
        return .accentColor
    }

    private func start(_ mode: String) {
        order = Array(cards.indices).shuffled()
        pos = 0
        correct = 0
        revealed = false
        selectedOption = nil
        phase = mode == "flash" ? .flash : .quiz
        if mode == "quiz" { prepareQuiz() }
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
            if pos >= order.count {
                onFinish(order.count, correct)
                phase = .done
            } else {
                if phase == .quiz { prepareQuiz() }
            }
        }
        delayTask = task
        DispatchQueue.main.asyncAfter(deadline: .now() + (phase == .quiz ? 0.9 : 0.2), execute: task)
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
