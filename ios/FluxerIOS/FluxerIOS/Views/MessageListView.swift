import SwiftUI

struct MessageListView: View {
    @EnvironmentObject private var appState: AppState
    let channel: Channel

    @State private var messages: [FluxerMessage] = []
    @State private var draft = ""
    @State private var replyTo: FluxerMessage?

    var body: some View {
        VStack {
            List(messages) { message in
                VStack(alignment: .leading, spacing: 4) {
                    Text(message.author?.username ?? "Unknown").font(.caption).foregroundStyle(.secondary)
                    Text(message.content.isEmpty ? "📎 attachment" : message.content)
                }
                .contentShape(Rectangle())
                .onLongPressGesture { replyTo = message }
            }

            if let replyTo {
                HStack {
                    Text("Replying to \(replyTo.author?.username ?? "?")")
                    Spacer()
                    Button("Cancel") { self.replyTo = nil }
                }.padding(.horizontal)
            }

            HStack {
                TextField("Message", text: $draft)
                Button("Send") { Task { await sendMessage() } }
                    .disabled(draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding()
        }
        .navigationTitle(channel.displayName)
        .task { await loadMessages() }
    }

    private func loadMessages() async {
        guard let token = appState.token else { return }
        do { messages = try await FluxerAPI(token: token).getMessages(channelId: channel.id) }
        catch { messages = [] }
    }

    private func sendMessage() async {
        guard let token = appState.token else { return }
        let content = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !content.isEmpty else { return }
        do {
            try await FluxerAPI(token: token).sendMessage(channelId: channel.id, content: content, replyToId: replyTo?.id)
            draft = ""
            replyTo = nil
            await loadMessages()
        } catch {}
    }
}
