import Foundation

@MainActor
final class AppState: ObservableObject {
    @Published var token: String?
    @Published var username: String?
    @Published var userId: String?

    init() {
        token = UserDefaults.standard.string(forKey: "fluxer.token")
        username = UserDefaults.standard.string(forKey: "fluxer.username")
        userId = UserDefaults.standard.string(forKey: "fluxer.user_id")
    }

    func saveSession(token: String, user: FluxerUser) {
        self.token = token
        self.username = user.username
        self.userId = user.id
        UserDefaults.standard.set(token, forKey: "fluxer.token")
        UserDefaults.standard.set(user.username, forKey: "fluxer.username")
        UserDefaults.standard.set(user.id, forKey: "fluxer.user_id")
    }

    func logout() {
        token = nil
        username = nil
        userId = nil
        UserDefaults.standard.removeObject(forKey: "fluxer.token")
        UserDefaults.standard.removeObject(forKey: "fluxer.username")
        UserDefaults.standard.removeObject(forKey: "fluxer.user_id")
    }
}
