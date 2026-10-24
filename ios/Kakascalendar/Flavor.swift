import Foundation

/// Build flavor of the Squad.
/// Public build: leave everything empty — users type a server address.
/// A private build bakes in its own server and invite code.
enum Flavor {
    static let serverURL = ""
    static let inviteCode = ""
    static let autoSquad = false

    static var fixedServer: String? { serverURL.isEmpty ? nil : serverURL }
}
