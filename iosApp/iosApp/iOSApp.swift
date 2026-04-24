import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    @StateObject private var session = IOSAppSession()

    var body: some Scene {
        WindowGroup {
            ComposeHost(session: session)
                .ignoresSafeArea()
                .onOpenURL { url in
                    session.handleOAuthCallback(url: url)
                }
        }
    }
}

/// Compose UIViewController をラップする SwiftUI コンテナ。
struct ComposeHost: UIViewControllerRepresentable {
    @ObservedObject var session: IOSAppSession

    func makeUIViewController(context: Context) -> UIViewController {
        return MainViewControllerKt.MainViewController(container: session.container)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) { /* noop */ }
}

/// Kotlin 側の AppContainer をアプリ寿命で 1 つ保持する SwiftUI observable。
@MainActor
final class IOSAppSession: ObservableObject {
    let container: AppContainer

    init() {
        // xcconfig 経由で差し替え可能にする。現状は dev 値をハードコード。
        self.container = AppContainer(
            authCoreBaseUrl: "http://localhost:8081",
            fujuApiBaseUrl: "http://localhost:8080",
            verboseLogging: true
        )
    }

    deinit {
        // Ktor HttpClient / AuthStateMachine の coroutine scope を確実に閉じる。
        container.close()
    }

    /// Custom URL Scheme `fuju://auth/callback/{provider}?state=...&code=...` を
    /// Kotlin 側の OAuthCallbackParser で解釈し、AuthStateMachine に通知する。
    func handleOAuthCallback(url: URL) {
        guard let callback = OAuthCallbackParser.shared.parse(url: url.absoluteString) else {
            return
        }
        let sm = container.authStateMachine
        Task {
            do {
                _ = try await sm.completeSocialCallback(
                    provider: callback.provider,
                    state: callback.state,
                    code: callback.code
                )
            } catch {
                // AuthException は Swift から見ると NSError。ログに留める。
                NSLog("OAuth callback failed: \(error)")
            }
        }
    }
}
