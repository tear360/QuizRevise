import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct ContentView: View {
    @StateObject private var store = Store()
    @State private var showNewDeck = false
    @State private var showStats = false
    @State private var showSettings = false
    @AppStorage("appTheme") private var appTheme = "system"
    @State private var deckToRename: Deck?
    @State private var deckToColor: Deck?
    @State private var renameText = ""
    @State private var updateMessage: String?
    @State private var releasePage: URL?
    @State private var showImporter = false
    @State private var shareItems: [Any] = []
    @State private var showShare = false

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
                                Button("Changer la couleur") { deckToColor = deck }
                                Button("Exporter (.qrevise)") {
                                    if let json = store.exportJson(deckID: deck.id),
                                       let url = Self.writeTemp(json, name: "\(deck.name).qrevise".replacingOccurrences(of: "/", with: "-")) {
                                        shareItems = [url]
                                        showShare = true
                                    }
                                }
                                Button("Supprimer", role: .destructive) { store.deleteDeck(deck) }
                            }
                        }
                        .onDelete { offsets in store.decks.remove(atOffsets: offsets) }
                    }
                }
            }
            .navigationTitle("Mes paquets")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { showSettings = true } label: { Image(systemName: "gearshape") }
                        .accessibilityLabel("Paramètres")
                }
                ToolbarItem(placement: .navigationBarLeading) {
                    Button { showNewDeck = true } label: { Image(systemName: "plus") }
                }
            }
            .sheet(isPresented: $showNewDeck) {
                NewDeckSheet(store: store)
            }
            .sheet(item: $deckToColor) { deck in
                DeckColorSheet(store: store, deck: deck)
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
            .sheet(isPresented: $showSettings) {
                SettingsSheet(
                    theme: $appTheme,
                    onImport: {
                        showSettings = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { showImporter = true }
                    },
                    onExport: {
                        showSettings = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                            if let json = store.exportJson(),
                               let url = Self.writeTemp(json, name: "mes-paquets.qrevise") {
                                shareItems = [url]
                                showShare = true
                            }
                        }
                    },
                    onStats: {
                        showSettings = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { showStats = true }
                    },
                    onCheckUpdates: {
                        showSettings = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { checkUpdates() }
                    },
                    onAbout: {
                        showSettings = false
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) { showAbout() }
                    },
                    onClose: { showSettings = false }
                )
            }
            .sheet(isPresented: $showShare) { ShareSheet(items: shareItems) }
            .fileImporter(isPresented: $showImporter, allowedContentTypes: [.json, .data]) { result in
                guard case .success(let url) = result else { return }
                let secured = url.startAccessingSecurityScopedResource()
                defer { if secured { url.stopAccessingSecurityScopedResource() } }
                if let text = try? String(contentsOf: url, encoding: .utf8) {
                    let added = store.importJson(text)
                    updateMessage = added > 0
                        ? (added > 1 ? "\(added) paquets importés ✅" : "Paquet importé ✅")
                        : "Aucun paquet importé (fichier invalide ?)"
                }
            }
            .alert("Mises à jour",
                   isPresented: Binding(get: { updateMessage != nil && releasePage == nil },
                                        set: { if !$0 { updateMessage = nil } })) {
                Button("OK", role: .cancel) { updateMessage = nil }
            } message: { Text(updateMessage ?? "") }
            .alert("Mise à jour disponible",
                   isPresented: Binding(get: { releasePage != nil },
                                        set: { if !$0 { releasePage = nil } })) {
                Button("Ouvrir la page") { if let url = releasePage { UIApplication.shared.open(url) } }
                Button("Plus tard", role: .cancel) { }
            } message: { Text(updateMessage ?? "") }
        }
        .preferredColorScheme(appTheme == "light" ? .light : appTheme == "dark" ? .dark : nil)
        .onAppear { checkUpdates(silent: true) }
    }

    private func showAbout() {
        let version = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        updateMessage = "QuizRévise \(version)\n\nAlternative libre à Quizlet : flashcards, QCM et statistiques.\nMises à jour : github.com/\(GitHubUpdater.repo)/releases"
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

// Palette des paquets — identique sur Android, PC et iPhone (voir Store.palette).
private let deckPalette = ["6750A4", "1B873B", "B3261E", "0B57D0", "E8590C", "7A1FA2",
                           "00897B", "C2185B", "F9A825", "5D4037", "3949AB", "7CB342"]

/// Rangée de pastilles de couleurs ; la pastille active est entourée.
private struct DeckColorSwatches: View {
    let selected: String
    let onPick: (String) -> Void

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(deckPalette, id: \.self) { hex in
                    Circle()
                        .fill(Color(hex: hex))
                        .frame(width: 34, height: 34)
                        .overlay(
                            Circle().stroke(Color.primary.opacity(selected == hex ? 0.8 : 0),
                                            lineWidth: selected == hex ? 3 : 0)
                        )
                        .onTapGesture { onPick(hex) }
                }
            }
            .padding(.vertical, 4)
        }
    }
}

/// Création d'un paquet : nom + couleur personnalisée.
private struct NewDeckSheet: View {
    @ObservedObject var store: Store
    @Environment(\.presentationMode) private var presentation
    @State private var name = ""
    @State private var color = "6750A4"

    var body: some View {
        NavigationView {
            Form {
                TextField("Nom du paquet (ex. : Anglais)", text: $name)
                Section("Couleur") {
                    DeckColorSwatches(selected: color) { color = $0 }
                }
            }
            .navigationTitle("Nouveau paquet")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { presentation.wrappedValue.dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Créer") {
                        let trimmed = name.trimmingCharacters(in: .whitespaces)
                        if !trimmed.isEmpty { store.addDeck(named: trimmed, colorHex: color) }
                        presentation.wrappedValue.dismiss()
                    }
                }
            }
        }
        .onAppear { color = Store.defaultColorHex(forCount: store.decks.count) }
    }
}

/// Changement de la couleur d'un paquet existant.
private struct DeckColorSheet: View {
    @ObservedObject var store: Store
    let deck: Deck
    @Environment(\.presentationMode) private var presentation
    @State private var color = "6750A4"

    var body: some View {
        NavigationView {
            Form {
                Section("Couleur") {
                    DeckColorSwatches(selected: color) { color = $0 }
                }
            }
            .navigationTitle("Couleur du paquet")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Annuler") { presentation.wrappedValue.dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Enregistrer") {
                        store.setDeckColor(deck, colorHex: color)
                        presentation.wrappedValue.dismiss()
                    }
                }
            }
        }
        .onAppear { color = deck.colorHex }
    }
}

private struct SettingsSheet: View {
    @Binding var theme: String
    let onImport: () -> Void
    let onExport: () -> Void
    let onStats: () -> Void
    let onCheckUpdates: () -> Void
    let onAbout: () -> Void
    let onClose: () -> Void

    var body: some View {
        NavigationView {
            Form {
                Section("Apparence") {
                    Picker("Thème", selection: $theme) {
                        Text("Suivre l’appareil").tag("system")
                        Text("Clair").tag("light")
                        Text("Sombre").tag("dark")
                    }
                    .pickerStyle(.inline)
                }
                Section("Données") {
                    Button("Importer un paquet (.qrevise)", action: onImport)
                    Button("Exporter tous mes paquets", action: onExport)
                    Button("Statistiques", action: onStats)
                }
                Section("Application") {
                    Button("Rechercher les mises à jour", action: onCheckUpdates)
                    Button("À propos", action: onAbout)
                }
            }
            .navigationTitle("Paramètres")
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fermer", action: onClose) } }
        }
    }
}

extension Store {
    func index(of deck: Deck) -> Int {
        decks.firstIndex(where: { $0.id == deck.id }) ?? 0
    }
}

extension ContentView {
    static func writeTemp(_ text: String, name: String) -> URL? {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(name)
        do { try text.write(to: url, atomically: true, encoding: .utf8); return url } catch { return nil }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) { }
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
