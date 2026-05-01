import Foundation

struct FluxerUser: Codable {
    let id: String
    let username: String
}

struct Guild: Codable, Identifiable {
    let id: String
    let name: String
    let icon: String?
}

struct ChannelRecipient: Codable, Identifiable {
    let id: String
    let username: String
}

struct Channel: Codable, Identifiable {
    let id: String
    let type: Int
    let name: String?
    let topic: String?
    let position: Int?
    let recipients: [ChannelRecipient]?
    let lastMessageId: String?

    enum CodingKeys: String, CodingKey {
        case id, type, name, topic, position, recipients
        case lastMessageId = "last_message_id"
    }

    var displayName: String {
        if type == 1 { return recipients?.first?.username ?? id }
        if type == 3 {
            if let name, !name.trimmingCharacters(in: .whitespaces).isEmpty { return name }
            return recipients?.map(\.username).joined(separator: ", ") ?? id
        }
        return name ?? id
    }
}

struct FluxerMessageAuthor: Codable {
    let id: String?
    let username: String?
}

struct FluxerMessage: Codable, Identifiable {
    let id: String
    let channelId: String?
    let content: String
    let author: FluxerMessageAuthor?
    let timestamp: String?

    enum CodingKeys: String, CodingKey {
        case id, content, author, timestamp
        case channelId = "channel_id"
    }
}
