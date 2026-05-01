import SwiftUI

struct GuildListView: View {
    @EnvironmentObject private var appState: AppState
    @State private var guilds: [Guild] = []
    @State private var query = ""

    var filtered: [Guild] {
        if query.isEmpty { return guilds }
        return guilds.filter { $0.name.localizedCaseInsensitiveContains(query) }
    }

    var body: some View {
        List {
            NavigationLink("Direct Messages") {
                ChannelListView(mode: .dms)
            }
            Section("Guilds") {
                ForEach(filtered) { guild in
                    NavigationLink(guild.name) {
                        ChannelListView(mode: .guild(guild))
                    }
                }
            }
        }
        .searchable(text: $query)
        .navigationTitle("Guilds")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Logout") { appState.logout() }
            }
        }
        .task { await loadGuilds() }
        .overlay(alignment: .center) {
            if guilds.isEmpty { Text("No guilds") }
        }
    }

    private func loadGuilds() async {
        guard let token = appState.token else { return }
        do {
            guilds = try await FluxerAPI(token: token).getMyGuilds()
        } catch {
            guilds = []
        }
    }
}
