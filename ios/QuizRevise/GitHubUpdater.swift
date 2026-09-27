import Foundation

struct UpdateInfo {
    let version: String
    let pageURL: URL
}

/// Vérification des mises à jour via GitHub Releases.
/// Apple n'autorisant pas l'installation d'app en dehors de l'App Store,
/// l'app ouvre la page de téléchargement (sideload via AltStore/Sideloadly…).
enum GitHubUpdater {
    static let repo = "tear360/QuizRevise"

    static var currentVersion: String {
        Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "0.0.0"
    }

    static func check(completion: @escaping (Result<UpdateInfo, Error>) -> Void) {
        let url = URL(string: "https://api.github.com/repos/\(repo)/releases/latest")!
        var req = URLRequest(url: url)
        req.timeoutInterval = 10
        req.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        URLSession.shared.dataTask(with: req) { data, _, error in
            if let error = error {
                completion(.failure(error))
                return
            }
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let tag = json["tag_name"] as? String else {
                completion(.failure(URLError(.badServerResponse)))
                return
            }
            let page = (json["html_url"] as? String) ?? "https://github.com/\(repo)/releases/latest"
            completion(.success(UpdateInfo(
                version: tag.hasPrefix("v") ? String(tag.dropFirst()) : tag,
                pageURL: URL(string: page)!
            )))
        }.resume()
    }

    static func isNewer(_ candidate: String, than current: String) -> Bool {
        let a = candidate.split(separator: ".").map { Int($0.trimmingCharacters(in: .whitespaces)) ?? 0 }
        let b = current.split(separator: ".").map { Int($0.trimmingCharacters(in: .whitespaces)) ?? 0 }
        for i in 0..<max(a.count, b.count) {
            let x = i < a.count ? a[i] : 0
            let y = i < b.count ? b[i] : 0
            if x != y { return x > y }
        }
        return false
    }
}
