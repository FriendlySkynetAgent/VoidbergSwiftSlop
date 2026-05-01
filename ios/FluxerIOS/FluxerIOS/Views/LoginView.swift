import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var appState: AppState
    @State private var token = ""
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        Form {
            SecureField("Token", text: $token)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()

            Button(isLoading ? "Connecting..." : "Connect") {
                Task { await login() }
            }
            .disabled(isLoading || token.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

            if let errorMessage {
                Text(errorMessage).foregroundStyle(.red)
            }
        }
        .navigationTitle("Fluxer Login")
    }

    private func login() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let api = FluxerAPI(token: token.trimmingCharacters(in: .whitespacesAndNewlines))
            let me = try await api.getMe()
            appState.saveSession(token: token, user: me)
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
