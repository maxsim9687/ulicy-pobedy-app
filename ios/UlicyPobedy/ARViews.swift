import SwiftUI
import ARKit
import SceneKit
import CoreLocation

/* =====================================================================
   AR-таблички. ARKit с выравниванием по гравитации и компасу
   (x – на восток, z – на юг), поэтому табличку можно поставить
   в настоящем направлении по GPS-координатам.
   ===================================================================== */

struct PlaqueCardImage: View {
    let plaque: Plaque
    let hero: Hero
    var body: some View {
        VStack(spacing: 6) {
            Image(uiImage: Assets.image(hero.image)).resizable().scaledToFit().saturation(0).opacity(0.85).frame(height: 120)
            Text("В ЧЕСТЬ").font(.system(size: 12, design: .monospaced)).tracking(3)
            Text(hero.fullName.uppercased()).font(.system(size: 22, weight: .bold, design: .serif)).multilineTextAlignment(.center)
            Text(hero.years).font(.system(size: 18, design: .serif))
            Text(plaque.text).font(.system(size: 14)).multilineTextAlignment(.center).padding(.top, 4)
        }
        .foregroundColor(.gold)
        .padding(20)
        .frame(width: 420, height: 300)
        .background(LinearGradient(colors: [Color(red: 0.23, green: 0.2, blue: 0.19), Color(red: 0.12, green: 0.1, blue: 0.1), Color(red: 0.18, green: 0.16, blue: 0.15)], startPoint: .topLeading, endPoint: .bottomTrailing))
        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color.gold.opacity(0.8), lineWidth: 5))
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}

@MainActor
func renderPlaque(_ p: Plaque, _ h: Hero) -> UIImage {
    let r = ImageRenderer(content: PlaqueCardImage(plaque: p, hero: h))
    r.scale = 2
    return r.uiImage ?? UIImage()
}

final class PlaqueARModel: ObservableObject {
    @Published var tappedId: String?
}

struct PlaqueARContainer: UIViewRepresentable {
    let target: Plaque
    let demo: Bool
    let location: CLLocation?
    let model: PlaqueARModel

    func makeCoordinator() -> Coord { Coord(model: model) }

    @MainActor final class Coord: NSObject, ARSCNViewDelegate {
        let model: PlaqueARModel
        weak var view: ARSCNView?
        var nodes: [String: SCNNode] = [:]
        var target: Plaque?
        var demo = false
        var demoPlaced = false
        var location: CLLocation?
        var timer: Timer?
        init(model: PlaqueARModel) { self.model = model }

        func node(for p: Plaque) -> SCNNode {
            if let n = nodes[p.id] { return n }
            let hero = AppContent.shared.hero(p.hero)!
            let plane = SCNPlane(width: 1.4, height: 1.0)
            let m = SCNMaterial()
            m.diffuse.contents = renderPlaque(p, hero)
            m.isDoubleSided = true
            m.lightingModel = .constant
            plane.materials = [m]
            let n = SCNNode(geometry: plane)
            n.name = p.id
            let bb = SCNBillboardConstraint(); bb.freeAxes = .Y
            n.constraints = [bb]
            n.opacity = 0
            view?.scene.rootNode.addChildNode(n)
            nodes[p.id] = n
            return n
        }

        @objc func tap(_ g: UITapGestureRecognizer) {
            guard let v = view else { return }
            let hits = v.hitTest(g.location(in: v), options: nil)
            if let id = hits.compactMap({ $0.node.name }).first { model.tappedId = id }
        }

        /// раз в полсекунды пересчитываем положение таблички по GPS
        func update() {
            guard let v = view, let frame = v.session.currentFrame, let t = target else { return }
            let cam = frame.camera.transform.columns.3
            nodes.forEach { id, n in if id != t.id { n.opacity = 0 } }
            let n = node(for: t)
            if demo {
                if !demoPlaced {
                    let fwd = frame.camera.transform.columns.2 // камера смотрит по -Z
                    n.simdPosition = SIMD3(cam.x - fwd.x * 3, cam.y, cam.z - fwd.z * 3)
                    demoPlaced = true
                }
                n.opacity = 1
                return
            }
            demoPlaced = false
            guard let loc = location else { n.opacity = 0; return }
            let d = Geo.distance(loc.coordinate, t.coordinate)
            guard d <= 150 else { n.opacity = 0; return }
            let b = Geo.bearing(loc.coordinate, t.coordinate) * .pi / 180
            let dd = Float(min(d, 18))           // дальние таблички ставим на 18 м, но в верном направлении
            let pos = SIMD3<Float>(cam.x + dd * Float(sin(b)), cam.y, cam.z - dd * Float(cos(b)))
            SCNTransaction.begin(); SCNTransaction.animationDuration = 0.5
            n.simdPosition = pos
            n.opacity = 1
            let s: Float = d < 18 ? 1 : 1.3     // издалека чуть крупнее, чтобы было видно
            n.scale = SCNVector3(s, s, s)
            SCNTransaction.commit()
        }
    }

    func makeUIView(context: Context) -> ARSCNView {
        let v = ARSCNView()
        v.autoenablesDefaultLighting = true
        v.delegate = context.coordinator
        context.coordinator.view = v
        let cfg = ARWorldTrackingConfiguration()
        cfg.worldAlignment = .gravityAndHeading
        v.session.run(cfg)
        v.addGestureRecognizer(UITapGestureRecognizer(target: context.coordinator, action: #selector(Coord.tap(_:))))
        context.coordinator.timer = Timer.scheduledTimer(withTimeInterval: 0.5, repeats: true) { [weak c = context.coordinator] _ in Task { @MainActor in c?.update() } }
        return v
    }

    func updateUIView(_ v: ARSCNView, context: Context) {
        let c = context.coordinator
        if c.target?.id != target.id { c.demoPlaced = false }
        c.target = target
        if c.demo != demo { c.demoPlaced = false }
        c.demo = demo
        c.location = location
    }

    static func dismantleUIView(_ v: ARSCNView, coordinator: Coord) {
        coordinator.timer?.invalidate()
        v.session.pause()
    }
}

struct PlaqueARView: View {
    let initial: String?
    @EnvironmentObject var store: QuestStore
    @EnvironmentObject var loc: LocationService
    @StateObject private var model = PlaqueARModel()
    @State private var chosen: String?
    @State private var demo = false
    @State private var opened: Plaque?
    @State private var newBlock = false
    @State private var message: String?
    @Environment(\.openURL) var openURL
    let c = AppContent.shared

    var target: Plaque {
        if let id = chosen ?? initial, let p = c.plaques.first(where: { $0.id == id }) { return p }
        if let l = loc.location {
            if let p = c.plaques.filter({ !store.blocks.contains($0.id) }).min(by: { Geo.distance(l.coordinate, $0.coordinate) < Geo.distance(l.coordinate, $1.coordinate) }) { return p }
        }
        return c.plaques.first(where: { !store.blocks.contains($0.id) }) ?? c.plaques[0]
    }
    var distance: Double? { loc.location.map { Geo.distance($0.coordinate, target.coordinate) } }
    var near: Bool { demo || (distance.map { $0 <= max(40, (loc.location?.horizontalAccuracy ?? 999) + 15) } ?? false) }

    var status: String {
        if demo { return "Демо-режим: табличка перед вами. Нажмите на неё." }
        guard let d = distance else { return loc.authorized ? "Ищу GPS…" : "Разрешите геолокацию" }
        if d > 150 { return "До таблички \(Geo.human(d)) – подойдите ближе" }
        if !near { return "Табличка рядом – ищите её через камеру. \(Geo.human(d))" }
        return "Вы рядом! Найдите табличку и нажмите на неё"
    }
    var arrowAngle: Double {
        guard let l = loc.location, let h = loc.heading else { return 0 }
        return Geo.bearing(l.coordinate, target.coordinate) - h
    }

    var body: some View {
        let hero = c.hero(target.hero)!
        ZStack {
            PlaqueARContainer(target: target, demo: demo, location: loc.location, model: model).ignoresSafeArea()
            VStack(spacing: 10) {
                VStack(spacing: 4) {
                    Text(hero.street).bold()
                    Text(status).font(.system(size: 14)).multilineTextAlignment(.center).foregroundColor(near ? .gold : .paper.opacity(0.85))
                    if let a = loc.location?.horizontalAccuracy, !demo { Text("точность GPS ±\(Int(a)) м").font(.system(size: 11)).opacity(0.5) }
                }
                .foregroundColor(.paper).padding(12).background(Color.ink.opacity(0.8)).clipShape(RoundedRectangle(cornerRadius: 14))
                if !demo, loc.heading != nil, loc.location != nil {
                    Image(systemName: "location.north.fill").font(.system(size: 30)).foregroundColor(.gold)
                        .rotationEffect(.degrees(arrowAngle)).frame(width: 56, height: 56).background(Color.ink.opacity(0.7)).clipShape(Circle())
                        .overlay(Circle().stroke(Color.gold, lineWidth: 2))
                }
                if let m = message { Text(m).font(.system(size: 14)).foregroundColor(.paper).padding(10).background(Color.acc).clipShape(Capsule()) }
                Spacer()
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(c.plaques) { p in
                            let got = store.blocks.contains(p.id)
                            Button { chosen = p.id } label: {
                                Text((got ? "✓ " : "") + (c.hero(p.hero)?.street ?? "")).font(.system(size: 13)).foregroundColor(.paper)
                                    .padding(.horizontal, 12).padding(.vertical, 8).background(p.id == target.id ? Color.acc : Color.paper.opacity(0.3)).clipShape(Capsule())
                            }
                        }
                    }.padding(.horizontal, 12)
                }
                HStack(spacing: 10) {
                    Button { demo.toggle() } label: {
                        Text(demo ? "Выключить демо" : "Демо-режим").bold().padding(.horizontal, 16).padding(.vertical, 12)
                            .background(demo ? Color.gold : Color.acc).foregroundColor(demo ? .ink : .paper).clipShape(Capsule())
                    }
                    if let l = loc.location {
                        Button {
                            openURL(URL(string: "https://yandex.ru/maps/?rtext=\(l.coordinate.latitude),\(l.coordinate.longitude)~\(target.lat),\(target.lon)&rtt=pd")!)
                        } label: { Text("Маршрут").padding(.horizontal, 16).padding(.vertical, 12).overlay(Capsule().stroke(Color.paper)).foregroundColor(.paper) }
                    }
                    Text("Блоков: \(store.blocks.count)/3").foregroundColor(.gold)
                }.padding(.bottom, 16)
            }.padding(.top, 8)
        }
        .navigationTitle("AR-таблички").navigationBarTitleDisplayMode(.inline)
        .onAppear { loc.start() }
        .onChange(of: model.tappedId) { id in
            guard let id, let p = c.plaques.first(where: { $0.id == id }) else { return }
            model.tappedId = nil
            if near && p.id == target.id {
                newBlock = store.addBlock(p.id)
                opened = p
            } else {
                message = "Подойдите ближе, чтобы прочитать табличку"
                DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) { message = nil }
            }
        }
        .sheet(item: $opened) { p in PlaqueSheet(plaque: p, isNew: newBlock) }
    }
}

struct PlaqueSheet: View {
    let plaque: Plaque
    let isNew: Bool
    @EnvironmentObject var store: QuestStore
    @Environment(\.dismiss) var dismiss
    var body: some View {
        let h = AppContent.shared.hero(plaque.hero)!
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text(h.fullName).font(.system(size: 24, weight: .bold, design: .serif))
                Text("\(h.years) · \(plaque.`where`)").foregroundColor(.acc).font(.system(size: 14))
                Text(plaque.text)
                Text(plaque.hint).bold().padding(12).background(Color(red: 0.95, green: 0.886, blue: 0.77)).clipShape(RoundedRectangle(cornerRadius: 12))
                Text(isNew ? "🧩 Блок знака получен! +20 очков" : "Этот блок у вас уже есть").bold().foregroundColor(.okGreen)
                MedalView(blocks: store.blocks).frame(width: 160, height: 160).frame(maxWidth: .infinity)
                Button { dismiss() } label: { Text("Дальше").bold().frame(maxWidth: .infinity).padding().background(Color.acc).foregroundColor(.paper).clipShape(RoundedRectangle(cornerRadius: 14)) }
            }.padding(22)
        }
        .foregroundColor(.ink)
        .background(Color.paper.ignoresSafeArea())
        .presentationDetents([.medium, .large])
    }
}

/* =====================================================================
   «Герой рядом»: ARKit находит пол, по нажатию на экран ставит героя
   в полный рост. Можно ходить вокруг и сделать снимок.
   ===================================================================== */
final class HeroARModel: ObservableObject {
    @Published var placed = false
    @Published var planeFound = false
    weak var view: ARSCNView?
    var heroNode: SCNNode?
    var heroId = "gromov"
    var heightM: Float = 1.7

    func makeHeroNode() -> SCNNode {
        let h = AppContent.shared.hero(heroId)!
        let img = Assets.image(h.image)
        let aspect = img.size.height > 0 ? Float(img.size.width / img.size.height) : 0.6
        let plane = SCNPlane(width: CGFloat(heightM * aspect), height: CGFloat(heightM))
        let m = SCNMaterial(); m.diffuse.contents = img; m.isDoubleSided = true; m.lightingModel = .constant
        m.transparencyMode = .aOne
        plane.materials = [m]
        let body = SCNNode(geometry: plane)
        body.position = SCNVector3(0, heightM / 2, 0)
        let bb = SCNBillboardConstraint(); bb.freeAxes = .Y
        body.constraints = [bb]
        // мягкая тень
        let shadow = SCNNode(geometry: SCNPlane(width: CGFloat(heightM * 0.5), height: CGFloat(heightM * 0.25)))
        let sm = SCNMaterial(); sm.diffuse.contents = UIColor(white: 0, alpha: 0.35); sm.lightingModel = .constant
        shadow.geometry?.materials = [sm]
        shadow.eulerAngles.x = -.pi / 2
        shadow.position = SCNVector3(0, 0.002, 0)
        let root = SCNNode()
        root.addChildNode(shadow); root.addChildNode(body)
        root.opacity = 0
        root.runAction(.fadeIn(duration: 0.6))
        return root
    }

    func place(at t: simd_float4x4) {
        heroNode?.removeFromParentNode()
        let n = makeHeroNode()
        n.simdTransform = t
        view?.scene.rootNode.addChildNode(n)
        heroNode = n
        placed = true
    }
    func rebuild() {
        guard let old = heroNode else { return }
        let t = old.simdTransform
        place(at: t)
    }
}

struct HeroARContainer: UIViewRepresentable {
    let model: HeroARModel
    func makeCoordinator() -> Coord { Coord(model: model) }
    final class Coord: NSObject, ARSCNViewDelegate {
        let model: HeroARModel
        init(model: HeroARModel) { self.model = model }
        @objc func tap(_ g: UITapGestureRecognizer) {
            guard let v = model.view else { return }
            let pt = g.location(in: v)
            if let q = v.raycastQuery(from: pt, allowing: .estimatedPlane, alignment: .horizontal),
               let r = v.session.raycast(q).first {
                model.place(at: r.worldTransform)
            }
        }
        func renderer(_ renderer: SCNSceneRenderer, didAdd node: SCNNode, for anchor: ARAnchor) {
            if anchor is ARPlaneAnchor { DispatchQueue.main.async { self.model.planeFound = true } }
        }
    }
    func makeUIView(context: Context) -> ARSCNView {
        let v = ARSCNView()
        v.delegate = context.coordinator
        model.view = v
        let cfg = ARWorldTrackingConfiguration()
        cfg.planeDetection = [.horizontal]
        v.session.run(cfg)
        v.addGestureRecognizer(UITapGestureRecognizer(target: context.coordinator, action: #selector(Coord.tap(_:))))
        return v
    }
    func updateUIView(_ v: ARSCNView, context: Context) {}
    static func dismantleUIView(_ v: ARSCNView, coordinator: Coord) { v.session.pause() }
}

struct HeroARView: View {
    let initialHero: String
    @StateObject private var model = HeroARModel()
    @State private var heroId = "gromov"
    @State private var height: Float = 1.7
    @State private var saved = false
    let c = AppContent.shared

    var body: some View {
        ZStack {
            HeroARContainer(model: model).ignoresSafeArea()
            VStack {
                Text(model.placed ? "Обойдите героя и сделайте снимок" : (model.planeFound ? "Нажмите на землю – там встанет герой" : "Медленно водите телефоном, чтобы найти пол"))
                    .font(.system(size: 14)).foregroundColor(.paper).padding(10).background(Color.ink.opacity(0.8)).clipShape(Capsule()).padding(.top, 8)
                if saved { Text("Фото сохранено в галерею").font(.system(size: 14)).foregroundColor(.paper).padding(10).background(Color.okGreen).clipShape(Capsule()) }
                Spacer()
                VStack(spacing: 10) {
                    HStack(spacing: 8) {
                        ForEach(c.heroes) { h in
                            Button { heroId = h.id; model.heroId = h.id; model.rebuild() } label: {
                                Text(h.name).font(.system(size: 13)).foregroundColor(.paper).padding(.horizontal, 10).padding(.vertical, 8)
                                    .background(heroId == h.id ? Color.acc : Color.paper.opacity(0.3)).clipShape(Capsule())
                            }
                        }
                    }
                    HStack {
                        Text("Рост").foregroundColor(.paper).font(.system(size: 13))
                        Slider(value: $height, in: 0.5...2.2) { _ in model.heightM = height; model.rebuild() }.tint(.gold)
                        Text(String(format: "%.1f м", height)).foregroundColor(.paper).font(.system(size: 13, design: .monospaced))
                    }.padding(.horizontal, 20)
                    Button {
                        guard let v = model.view else { return }
                        let img = v.snapshot()
                        UIImageWriteToSavedPhotosAlbum(img, nil, nil, nil)
                        saved = true
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { saved = false }
                    } label: {
                        Circle().fill(Color.acc).frame(width: 72, height: 72).overlay(Circle().stroke(Color.paper, lineWidth: 5)).overlay(Image(systemName: "camera.fill").foregroundColor(.paper))
                    }.disabled(!model.placed).opacity(model.placed ? 1 : 0.5)
                }.padding(.vertical, 14).frame(maxWidth: .infinity).background(LinearGradient(colors: [.clear, Color.ink.opacity(0.9)], startPoint: .top, endPoint: .bottom))
            }
        }
        .navigationTitle("Герой рядом").navigationBarTitleDisplayMode(.inline)
        .onAppear { heroId = initialHero; model.heroId = initialHero }
    }
}
