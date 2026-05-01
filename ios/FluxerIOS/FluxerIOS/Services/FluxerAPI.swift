import Foundation

enum FluxerAPIError: LocalizedError {
    case invalidURL
    case invalidResponse
    case httpError(Int, String)

    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Invalid URL"
        case .invalidResponse: return "Invalid response"
        case let .httpError(code, body): return "HTTP \(code): \(body)"
        }
    }
}

final class FluxerAPI {
    private let base = "https://api.fluxer.app/v1"
    private let token: String

    init(token: String) {
        self.token = token
    }

    private func makeRequest(path: String, method: String = "GET", body: Data? = nil) throws -> URLRequest {
        guard let url = URL(string: base + path) else { throw FluxerAPIError.invalidURL }
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue(token, forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = body
        return request
    }

    private func decode<T: Decodable>(_ type: T.Type, from path: String) async throws -> T {
        let request = try makeRequest(path: path)
        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw FluxerAPIError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else {
            throw FluxerAPIError.httpError(http.statusCode, String(data: data, encoding: .utf8) ?? "")
        }
        return try JSONDecoder().decode(T.self, from: data)
    }

    func getMe() async throws -> FluxerUser { try await decode(FluxerUser.self, from: "/users/@me") }
    func getMyGuilds() async throws -> [Guild] { try await decode([Guild].self, from: "/users/@me/guilds") }
    func getPrivateChannels() async throws -> [Channel] { try await decode([Channel].self, from: "/users/@me/channels") }
    func getChannels(guildId: String) async throws -> [Channel] { try await decode([Channel].self, from: "/guilds/\(guildId)/channels") }

    func getMessages(channelId: String) async throws -> [FluxerMessage] {
        Array(try await decode([FluxerMessage].self, from: "/channels/\(channelId)/messages?limit=50").reversed())
    }

    func sendMessage(channelId: String, content: String, replyToId: String? = nil) async throws {
        var payload: [String: Any] = ["content": content]
        if let replyToId, !replyToId.isEmpty {
            payload["message_reference"] = ["message_id": replyToId]
        }
        let data = try JSONSerialization.data(withJSONObject: payload)
        let request = try makeRequest(path: "/channels/\(channelId)/messages", method: "POST", body: data)
        let (_, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw FluxerAPIError.invalidResponse }
        guard (200..<300).contains(http.statusCode) else {
            throw FluxerAPIError.httpError(http.statusCode, "Unable to send message")
        }
    }
}
