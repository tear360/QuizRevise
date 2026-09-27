import SwiftUI
import UIKit

struct ContentView: View {
    @StateObject private var store = Store()
    @State private var showNewDeck = false
    @State private var showStats = false
    @State private var newDeckName = ""
    @State private var deckToRename: Deck?
    @State private var renameText = ""
    @State private var updateMessage: String?
    @State private var releasePage: URL?

    var body: some View {
        NavigationView {
            Group {
                if store.decks.isEmpty {
                    VStack(spacing: 12) {
                        Text("Aucun paquet pour l'instant.")
                        Text("Crée ton premier paquet pour commencer à réviser.")
                            .foregroundColor(.secondary)
                    }
                    .multilineTextAlignment(.center)
                    .padding()
                } else {
                    List {
                        ForEach(store.decks) { deck in
                            NavigationLink(destination: DeckView(deck: $store.decks[store.index(of: deck)], store: store)) {
                                HStack(spacing: 14) {
                                    RoundedRectangle(cornerRadius: 6)
                                        .fill(Color(hex: deck.colorHex))
                                        .frame(width: 44, height: 44)
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(deck.name).font(.headline)
                                        Text("\(deck.cards.count) cartes").font(.caption).foregroundColor(.secondary)
                                    }
                                }
                            }
                            .contextMenu {
                                Button("Renommer") {
                                    deckToRename = deck
                                    renameText = deck.name
                                }
                                Button("Supprimer", role: .destructive) { store.deleteDeck(deck) }
                            }
                        }
                        .onDelete { offsets in
                            store.decks.remove(atOffsets: offsets)
                        }
                    }
                }
            }
            .navigationTitle("Mes paquets")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Menu {
                        Button("Statistiques") { showStats = true }
                        Button("Rechercher les mises à jour") { checkUpdates() }
                        Button("À propos") {
                            let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
                            updateMessage = "QuizRévise \(v)\n\nAlternative libre à Quizlet : flashcards, QCM, stats.\nMises à jour : github.com/\(GitHubUpdater.repo)/releases"
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                }
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { newDeckName = ""; showNewDeck = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showNewDeck) {
                NavigationView {
                    Form {
                        TextField("Nom du paquet (ex. : Anglais)", text: $newDeckName)
                    }
                    .navigationTitle("Nouveau paquet")
                    .toolbar {
                        ToolbarItem(placement: .cancellationAction) { Button("Annuler") { showNewDeck = false } }
                        ToolbarItem(placement: .confirmationAction) {
                            Button("Créer") {
                                let name = newDeckName.trimmingCharacters(in: .whitespaces)
                                if !name.isEmpty { store.addDeck(named: name) }
                                showNewDeck = false
                            }
                        }
                    }
                }
            }
            .sheet(item: $deckToRename) { deck in
                NavigationView {
                    Form { TextField("Nouveau nom", text: $renameText) }
                        .navigationTitle("Renommer")
                        .toolbar {
                            ToolbarItem(placement: .cancellationAction) { Button("Annuler") { deckToRename = nil } }
                            ToolbarItem(placement: .confirmationAction) {
                                Button("Enregistrer") {
                                    let name = renameText.trimmingCharacters(in: .whitespaces)
                                    if !name.isEmpty { store.renameDeck(deck, to: name) }
                                    deckToRename = nil
                                }
                            }
                        }
                }
            }
            .sheet(isPresented: $showStats) { StatsSheet(store: store) }
            .alert("Mises à jour",
                   isPresented: Binding(get: { updateMessage != nil && releasePage == nil },
                                        set: { if !$0 { updateMessage = nil } })) {
                Button("OK", role: .cancel) { updateMessage = nil }
            } message: { Text(updateMessage ?? "") }
            .alert("Mise à jour disponible",
                   isPresented: Binding(get: { releasePage != nil },
                                        set: { if !$0 { releasePage = nil } })) {
                Button("Ouvrir la page") { if let u = releasePage { UIApplication.shared.open(u) } }
                Button("Plus tard", role: .cancel) { }
            } message: { Text(updateMessage ?? "") }
        }
        .onAppear { checkUpdates(silent: true) }
    }

    private func checkUpdates(silent: Bool = false) {
        GitHubUpdater.check { result in
            DispatchQueue.main.async {
                switch result {
                case .failure:
                    if !silent { updateMessage = "Impossible de vérifier les mises à jour (connexion ?)" }
                case .success(let info):
                    if GitHubUpdater.isNewer(info.version, than: GitHubUpdater.currentVersion) {
                        updateMessage = "La version \(info.version) est disponible (tu as la \(GitHubUpdater.currentVersion))."
                        releasePage = info.pageURL
                    } else if !silent {
                        updateMessage = "Tu as déjà la dernière version (\(GitHubUpdater.currentVersion))."
                    }
                }
            }
        }
    }
}

extension Store {
    func index(of deck: Deck) -> Int {
        decks.firstIndex(where: { $0.id == deck.id }) ?? 0
    }
}

extension Color {
    init(hex: String) {
        var v: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&v)
        self.init(
            red: Double((v >> 16) & 0xFF) / 255.0,
            green: Double((v >> 8) & 0xFF) / 255.0,
            blue: Double(v & 0xFF) / 255.0
        )
    }
}
