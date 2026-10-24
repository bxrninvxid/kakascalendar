import SwiftUI

struct AccentPalette {
    let label: String
    let primary: Color
    let secondary: Color
    let tertiary: Color
    var colors: [Color] { [primary, secondary, tertiary] }
}

let accentPresets: [AccentPalette] = [
    AccentPalette(label: "Caramel", primary: Color(hex: 0x9A5B34), secondary: Color(hex: 0xE39B5B), tertiary: Color(hex: 0xF2C66D)),
    AccentPalette(label: "Mint", primary: Color(hex: 0x23806A), secondary: Color(hex: 0x5FC9A0), tertiary: Color(hex: 0xB6DE6A)),
    AccentPalette(label: "Grape", primary: Color(hex: 0x6A4CC2), secondary: Color(hex: 0xB08AF2), tertiary: Color(hex: 0xF08AC6)),
    AccentPalette(label: "Ocean", primary: Color(hex: 0x2465B0), secondary: Color(hex: 0x55BFE6), tertiary: Color(hex: 0x86DEC4)),
    AccentPalette(label: "Cherry", primary: Color(hex: 0xC0364A), secondary: Color(hex: 0xFF8A65), tertiary: Color(hex: 0xFFC46B)),
    AccentPalette(label: "Homelander", primary: Color(hex: 0x1D3B8F), secondary: Color(hex: 0xD62839), tertiary: Color(hex: 0xF2C14E))
]
let customAccentIndex = accentPresets.count

func customPalette(_ hue: Double) -> AccentPalette {
    let h = hue.truncatingRemainder(dividingBy: 360) / 360
    func wrap(_ x: Double) -> Double { x.truncatingRemainder(dividingBy: 1) }
    return AccentPalette(
        label: "Custom",
        primary: Color(hue: h, saturation: 0.70, brightness: 0.62),
        secondary: Color(hue: wrap(h + 28.0 / 360), saturation: 0.58, brightness: 0.88),
        tertiary: Color(hue: wrap(h + 56.0 / 360), saturation: 0.50, brightness: 0.96)
    )
}

func palette(_ s: AppSettings) -> AccentPalette {
    s.accent == customAccentIndex ? customPalette(s.customHue) : accentPresets[min(max(s.accent, 0), accentPresets.count - 1)]
}

enum AppFont: Int, CaseIterable, Identifiable {
    case standard, rounded, condensed, serif, mono
    var id: Int { rawValue }
    var label: String { ["Default", "Rounded", "Condensed", "Classic", "Mono"][rawValue] }
    var design: Font.Design {
        switch self {
        case .standard, .condensed: .default
        case .rounded: .rounded
        case .serif: .serif
        case .mono: .monospaced
        }
    }
    var width: Font.Width { self == .condensed ? .condensed : .standard }
}

/// Root-level styling: accent tint, font design, color scheme.
struct AppStyle: ViewModifier {
    let settings: AppSettings
    func body(content: Content) -> some View {
        let font = AppFont(rawValue: settings.font) ?? .standard
        content
            .tint(palette(settings).primary)
            .fontDesign(font.design)
            .fontWidth(font.width)
            .preferredColorScheme(settings.themeMode == 1 ? .light : settings.themeMode == 2 ? .dark : nil)
    }
}

/// Warm mesh gradient tinted with the accent, so Liquid Glass has something to refract.
struct AppBackground: View {
    @Environment(AppSettings.self) private var settings
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let p = palette(settings)
        let base: Color = scheme == .dark ? Color(hex: 0x15110F) : Color(hex: 0xFFF8F2)
        let a = scheme == .dark ? 0.45 : 0.35
        TimelineView(.animation(minimumInterval: 1 / 20, paused: !settings.animatedBg)) { ctx in
            let t = settings.animatedBg ? Float(sin(ctx.date.timeIntervalSinceReferenceDate / 6) * 0.15) : 0
            MeshGradient(
                width: 3, height: 3,
                points: [[0, 0], [0.5, 0], [1, 0],
                         [0, 0.5], [0.55 + t, 0.45 - t], [1, 0.5],
                         [0, 1], [0.5, 1], [1, 1]],
                colors: [
                    p.primary.opacity(a), base, p.secondary.opacity(a * 0.8),
                    base, p.tertiary.opacity(a * 0.5), base,
                    p.secondary.opacity(a * 0.6), base, p.primary.opacity(a * 0.7)
                ]
            )
            .background(base)
        }
        .ignoresSafeArea()
    }
}

/// Glass / solid / outline card surface, picked in Settings.
struct CardSurface: ViewModifier {
    @Environment(AppSettings.self) private var settings
    var tint: Color? = nil
    var radius: CGFloat = 24

    func body(content: Content) -> some View {
        switch settings.cardStyle {
        case 1:
            content.background((tint?.opacity(0.25) ?? Color(.secondarySystemBackground)), in: .rect(cornerRadius: radius))
        case 2:
            content
                .background(tint?.opacity(0.15) ?? .clear, in: .rect(cornerRadius: radius))
                .overlay(RoundedRectangle(cornerRadius: radius).strokeBorder(.secondary.opacity(0.5), lineWidth: 1.5))
        default:
            if let tint {
                content.glassEffect(.regular.tint(tint.opacity(0.35)), in: .rect(cornerRadius: radius))
            } else {
                content.glassEffect(.regular, in: .rect(cornerRadius: radius))
            }
        }
    }
}

extension View {
    func card(tint: Color? = nil, radius: CGFloat = 24) -> some View { modifier(CardSurface(tint: tint, radius: radius)) }
}

func tierColor(_ t: Tier) -> Color {
    switch t {
    case .bronze: Color(hex: 0xCD8B5A)
    case .silver: Color(hex: 0x9EA7B3)
    case .gold: Color(hex: 0xE8B931)
    case .legend: Color(hex: 0xB06BFF)
    }
}
