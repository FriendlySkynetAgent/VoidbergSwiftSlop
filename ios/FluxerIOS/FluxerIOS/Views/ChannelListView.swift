import SwiftUI

enum ChannelMode {
    case guild(Guild)
    case dms
}

struct ChannelListView: View {
    @EnvironmentObject private var appState: AppState
    let mode: ChannelMode
    @State private var channels: [Channel] = []
    @State private var query = ""

    private var title: String {
        switch mode {
        case let .guild(g): return g.name
        case .dms: return "Direct Messages"
        }
    }

    private var filtered: [Channel] {
        let base = channels.filter { c in
            switch mode {
            case .guild: return c.type == 0 || c.type == 5
            case .dms: return c.type == 1 || c.type == 3
            }
        }
        if query.isEmpty { return base }
        return base.filter { $0.displayName.localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        List(filtered) { channel in
            NavigationLink(channel.displayName) {
                MessageListView(channel: channel)
            }
        }
        .searchable(text: $query)
        .navigationTitle(title)
        .task { await loadChannels() }
    }

    private func loadChannels() async {
        guard let token = appState.token else { return }
        do {
            let api = FluxerAPI(token: token)
            switch mode {
            case let .guild(guild):
                channels = try await api.getChannels(guildId: guild.id).sorted { ($0.position ?? 0) < ($1.position ?? 0) }
            case .dms:
                channels = try await api.getPrivateChannels().sorted { ($0.lastMessageId ?? "") > ($1.lastMessageId ?? "") }
            }
        } catch {
            channels = []
        }
    }
}
