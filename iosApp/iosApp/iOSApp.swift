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
                    // Custom URL Scheme (fuju://auth/callback) を Kotlin 側に通知する。
                    // TODO(フェーズ 4): AuthStateMachine.completeSocialCallback に state/code を橋渡し。
                    print("OAuth callback URL: \(url)")
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
}
