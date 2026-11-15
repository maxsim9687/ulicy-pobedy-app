import SwiftUI
import WebKit
import CoreLocation

/// Арка: полукруг сверху и прямоугольник снизу.
struct ArchShape: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        let rad = r.width / 2
        p.move(to: CGPoint(x: r.minX, y: r.maxY))
        p.addLine(to: CGPoint(x: r.minX, y: r.minY + rad))
        p.addArc(center: CGPoint(x: r.midX, y: r.minY + rad), radius: rad, startAngle: .degrees(180), endAngle: .degrees(0), clockwise: false)
        p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
        p.closeSubpath()
        return p
    }
}

@main
struct UlicyPobedyApp: App {
    @StateObject private var store = QuestStore()
    @StateObject private var loc = LocationService.shared
    var body: some Scene {
        WindowGroup {
            NavigationStack { HomeView() }
                .environmentObject(store)
                .environmentObject(loc)
                .tint(.accL)
                .preferredColorScheme(.dark)
        }
    }
}

/* ======================= общие элементы ======================= */
struct Backdrop: View {
    var body: some View {
        RadialGradient(colors: [Color(red: 0.35, green: 0.11, blue: 0.11), .ink, .black], center: .topTrailing, startRadius: 20, endRadius: 900)
            .ignoresSafeArea()
    }
}

struct Kicker: View {
    let text: String
    var body: some View { Text(text.uppercased()).font(.system(size: 12, design: .monospaced)).tracking(2).foregroundColor(.accL) }
}

struct PaperCard<C: View>: View {
    @ViewBuilder let content: C
    var body: some View {
        VStack(alignment: .leading, spacing: 8) { content }
            .padding(18).frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.paper).foregroundColor(.ink).clipShape(RoundedRectangle(cornerRadius: 20))
    }
}

struct TagView: View {
    let text: String
    var color: Color = .acc
    var body: some View {
        Text(text.uppercased()).font(.system(size: 11, weight: .medium, design: .monospaced)).foregroundColor(.paper)
            .padding(.horizontal, 10).padding(.vertical, 4).background(color).clipShape(Capsule())
    }
}

struct AssetImage: View {
    let name: String
    var gray = false
    var body: some View {
        Image(uiImage: Assets.image(name)).resizable().scaledToFit().saturation(gray ? 0 : 1)
    }
}

struct AudioRow: View {
    let file: String
    let title: String
    var light = true
    @StateObject private var player = AudioPlayer()
    var body: some View {
        HStack(spacing: 12) {
            Button { player.toggle(file) } label: {
                Image(systemName: player.playing ? "pause.fill" : "play.fill").foregroundColor(.paper)
                    .frame(width: 48, height: 48).background(Color.acc).clipShape(Circle())
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(title).font(.system(size: 15, weight: .bold))
                Text("озвучка ElevenLabs").font(.caption).opacity(0.6)
                ProgressView(value: player.progress).tint(.acc)
            }
        }
        .foregroundColor(light ? .ink : .paper)
        .padding(12).background(light ? Color(red: 0.94, green: 0.88, blue: 0.86) : Color.acc.opacity(0.25))
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .onDisappear { player.stop() }
    }
}

/* ======================= Главная ======================= */
struct HomeView: View {
    @EnvironmentObject var store: QuestStore
    let c = AppContent.shared
    var body: some View {
        ZStack {
            Backdrop()
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Kicker(text: "Екатеринбург · проект 7 «П»")
                    Text("Улицы Победы").font(.system(size: 34, weight: .bold, design: .serif)).foregroundColor(.paper)
                    Text("Три улицы Ленинского и Академического районов, названные в честь участников Великой Отечественной войны. Квест, AR-таблички, аудиогид и викторина.")
                        .foregroundColor(.paper.opacity(0.85))
                    HStack(spacing: 10) {
                        ForEach(c.heroes) { h in
                            NavigationLink { HeroView(hero: h) } label: {
                                VStack(spacing: 2) {
                                    AssetImage(name: h.image, gray: true).frame(height: 120)
                                    Text(h.name.components(separatedBy: " ").last ?? "").font(.system(size: 14, weight: .bold)).foregroundColor(.paper)
                                    Text(h.years).font(.system(size: 10, design: .monospaced)).foregroundColor(.paper.opacity(0.6))
                                }.padding(.bottom, 8).frame(maxWidth: .infinity).background(Color.acc.opacity(0.35)).clipShape(RoundedRectangle(cornerRadius: 16))
                            }
                        }
                    }.padding(.vertical, 6)
                    NavigationLink { QuestView() } label: { Tile(icon: "🗺", title: "Квест «Три улицы»", sub: "Этап \(min(store.stage + 1, c.stages.count)) из \(c.stages.count) · очки: \(store.points)", main: true) }
                    NavigationLink { PlaqueARView(initial: nil) } label: { Tile(icon: "📍", title: "AR-таблички на стенах", sub: "Повесьте мемориальную доску на стену дома · блоков \(store.blocks.count) из 3") }
                    NavigationLink { QuizView() } label: { Tile(icon: "❓", title: "Викторина", sub: "10 вопросов · лучший результат \(store.quizBest)/10") }
                    NavigationLink { FinalView() } label: { Tile(icon: "🏅", title: "Знак «Хранитель памяти»", sub: "Собирается из блоков AR-табличек") }
                    NavigationLink { AboutView() } label: { Tile(icon: "ℹ️", title: "О проекте", sub: "Автор, источники, сайт") }
                    Text("Максим Шумилов · 7 «П» класс").font(.caption).foregroundColor(.paper.opacity(0.5)).frame(maxWidth: .infinity).padding(.top, 12)
                }.padding(20)
            }
        }
        .navigationBarHidden(true)
        .onAppear { LocationService.shared.start() }
    }
}

struct Tile: View {
    let icon, title, sub: String
    var main = false
    var body: some View {
        HStack(spacing: 14) {
            Text(icon).font(.system(size: 26))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.system(size: 17, weight: .bold)).foregroundColor(.paper)
                Text(sub).font(.system(size: 13)).foregroundColor(.paper.opacity(0.7)).multilineTextAlignment(.leading)
            }
            Spacer()
            Image(systemName: "chevron.right").foregroundColor(.paper)
        }
        .padding(16).background(main ? Color.acc : Color.paper.opacity(0.07))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.paper.opacity(main ? 0 : 0.16)))
        .clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

/* ======================= Герой ======================= */
struct HeroView: View {
    let hero: Hero
    var body: some View {
        ZStack {
            Backdrop()
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    ZStack(alignment: .bottom) {
                        ArchShape().fill(Color.acc).frame(width: 230, height: 230)
                        AssetImage(name: hero.image, gray: true).frame(height: 290)
                    }.frame(maxWidth: .infinity)
                    Kicker(text: hero.district)
                    Text(hero.fullName).font(.system(size: 28, weight: .bold, design: .serif)).foregroundColor(.paper)
                    Text("\(hero.years) · \(hero.role)").foregroundColor(.accL).font(.system(size: 14))
                    AudioRow(file: hero.audio, title: "Рассказ: \(hero.name)", light: false)
                    ForEach(hero.life, id: \.self) { item in
                        PaperCard {
                            Text((item.first ?? "").uppercased()).font(.system(size: 12, design: .monospaced)).tracking(2).foregroundColor(.acc)
                            Text(item.last ?? "").font(.system(size: 16))
                        }
                    }
                    Text("А вы знали?").font(.system(size: 22, weight: .bold, design: .serif)).foregroundColor(.paper).padding(.top, 6)
                    ForEach(hero.facts, id: \.self) { f in
                        HStack(alignment: .top) { Text("★").foregroundColor(.gold); Text(f).foregroundColor(.paper.opacity(0.9)) }
                    }
                    Image(uiImage: Assets.image(hero.lifeImage)).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 16)).padding(.top, 8)
                    Text(hero.lifeCaption).font(.caption).foregroundColor(.paper.opacity(0.6))
                    NavigationLink { PlaqueARView(initial: hero.id) } label: {
                        Text("📍 AR-табличка на стене").bold().frame(maxWidth: .infinity).padding().background(Color.acc).foregroundColor(.paper).clipShape(RoundedRectangle(cornerRadius: 14))
                    }.padding(.top, 8)
                }.padding(20)
            }
        }
        .navigationTitle(hero.street).navigationBarTitleDisplayMode(.inline)
    }
}

/* ======================= Карта Яндекс в WKWebView ======================= */
struct YandexMap: UIViewRepresentable {
    let stage: Int
    let done: Set<Int>
    let me: CLLocation?
    let centerOnMeTick: Int

    func makeCoordinator() -> Coord { Coord() }
    final class Coord: NSObject, WKNavigationDelegate {
        var loaded = false
        var lastTick = 0
        var pending: [String] = []
        weak var web: WKWebView?
        func run(_ js: String) { if loaded { web?.evaluateJavaScript(js) } else { pending.append(js) } }
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            loaded = true
            let q = AppContent.stagesJSON.replacingOccurrences(of: "\\", with: "\\\\").replacingOccurrences(of: "'", with: "\\'")
            webView.evaluateJavaScript("setup('\(q)')")
            pending.forEach { webView.evaluateJavaScript($0) }
            pending = []
        }
    }
    func makeUIView(context: Context) -> WKWebView {
        let w = WKWebView()
        w.navigationDelegate = context.coordinator
        w.isOpaque = false
        w.scrollView.isScrollEnabled = false
        context.coordinator.web = w
        w.loadHTMLString(Assets.mapHTML, baseURL: URL(string: "https://city.maxsim.space/app/"))
        return w
    }
    func updateUIView(_ w: WKWebView, context: Context) {
        let c = context.coordinator
        c.run("setStage(\(stage), '[\(done.map(String.init).joined(separator: ","))]')")
        if let m = me { c.run("setMe(\(m.coordinate.latitude), \(m.coordinate.longitude), \(max(m.horizontalAccuracy, 10)))") }
        if centerOnMeTick != c.lastTick { c.lastTick = centerOnMeTick; c.run("showMe()") }
    }
}
