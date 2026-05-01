import SwiftUI

struct RootView: View {
    @EnvironmentObject private var appState: AppState

    var body: some View {
        NavigationStack {
            if appState.token == nil {
                LoginView()
            } else {
                GuildListView()
            }
        }
    }
}
