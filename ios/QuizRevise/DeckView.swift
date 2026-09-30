import SwiftUI

struct DeckView: View {
    @Binding var deck: Deck
    @ObservedObject var store: Store
    @State private var showStudy = false
    @State private var showAdd = false
    @State private var cardToEdit: Card?

    var body: some View {
        Group {
            if deck.cards.isEmpty {
                VStack(spacing: 12) {
                    Text("Ce paquet est vide.")
                    Text("Ajoute ta première carte !").foregroundColor(.secondary)
                }
                .multilineTextAlignment(.center)
            } else {
                List {
                    ForEach(deck.cards) { card in
                        // Carte PLIÉE par défaut : un appui déplie (réponse + actions).
                        // Évite d'éditer/supprimer par accident en visant « Réviser ».
                        FoldableCard(
                            card: card,
                            onEdit: { cardToEdit = card },
                            onDelete: { store.deleteCard(card, in: deck) }
                        )
                    }
                    .onDelete { offsets in
                        for i in offsets { store.deleteCard(deck.cards[i], in: deck) }
                    }
                }
            }
        }
        .navigationTitle(deck.name)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button { showAdd = true } label: { Image(systemName: "plus") }
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    if deck.cards.count >= 2 { showStudy = true }
                } label: {
                    Text("Réviser")
                }
                .disabled(deck.cards.count < 2)
            }
        }
        .alert("Il faut au moins 2 cartes pour lancer une session.",
               isPresented: Binding(get: { deck.cards.count < 2 && !deck.cards.isEmpty && showStudy },
                                    set: { _ in })) { Button("OK", role: .cancel) { } }
        .sheet(isPresented: $showStudy) {
            StudyView(cards: deck.cards) { total, correct in
                store.recordSession(total: total, correct: correct)
            }
        }
        .sheet(isPresented: $showAdd) {
            CardForm(title: "Ajouter une carte") { q, a in
                store.addCard(to: deck, question: q, answer: a)
            }
        }
        .sheet(item: $cardToEdit) { card in
            CardForm(title: "Modifier la carte", question: card.question, answer: card.answer) { q, a in
                store.updateCard(card, in: deck, question: q, answer: a)
            }
        }
    }
}

/// Carte pliée par défaut : la réponse et les boutons n'apparaissent qu'après appui.
private struct FoldableCard: View {
    let card: Card
    let onEdit: () -> Void
    let onDelete: () -> Void
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(card.question).font(.headline)
                Spacer()
                Image(systemName: expanded ? "chevron.down" : "chevron.right")
                    .font(.caption).foregroundColor(.accentColor)
            }
            .contentShape(Rectangle())
            .onTapGesture { withAnimation { expanded.toggle() } }
            if expanded {
                Text(card.answer).font(.subheadline).foregroundColor(.secondary)
                HStack(spacing: 10) {
                    Button("Modifier", action: onEdit)
                        .buttonStyle(.bordered).controlSize(.small)
                    Button("Supprimer", role: .destructive, action: onDelete)
                        .buttonStyle(.bordered).controlSize(.small)
                }
            }
        }
        .padding(.vertical, 2)
    }
}

struct CardForm: View {
    let title: String
    var question: String = ""
    var answer: String = ""
    let onSave: (String, String) -> Void
    @Environment(\.presentationMode) private var presentation
    @State private var q: String = ""
    @State private var a: String = ""

    var body: some View {
        NavigationView {
            Form {
                TextField("Question / Recto", text: $q)
                TextField("Réponse / Verso", text: $a)
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { presentation.wrappedValue.dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Enregistrer") {
                        let qt = q.trimmingCharacters(in: .whitespaces)
                        let at = a.trimmingCharacters(in: .whitespaces)
                        if !qt.isEmpty && !at.isEmpty {
                            onSave(qt, at)
                            presentation.wrappedValue.dismiss()
                        }
                    }
                }
            }
        }
        .onAppear { q = question; a = answer }
    }
}
