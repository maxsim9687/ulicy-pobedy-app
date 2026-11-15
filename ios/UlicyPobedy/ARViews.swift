import SwiftUI
import ARKit
import SceneKit
import CoreLocation
import CoreImage

/* =====================================================================
   AR-табличка на стене. ARKit ищет вертикальные плоскости – стены домов,
   заборы, стену класса. По нажатию на стену туда «прикручивается»
   объёмная гранитная доска: бронзовая рама, плита с гравировкой,
   четыре латунных болта и мягкая тень на стене. Доска привязана к якорю
   ARKit и остаётся на месте, когда вы ходите вокруг.
   ===================================================================== */

enum PlaqueArt {
    static let gold = UIColor(red: 0.87, green: 0.72, blue: 0.42, alpha: 1)

    /// золотистый портрет: сепия поверх чёрно-белого, прозрачность сохраняется
    static func goldPortrait(_ img: UIImage) -> UIImage {
        guard let ci = CIImage(image: img) else { return img }
        let mono = ci.applyingFilter("CIColorControls", parameters: [kCIInputSaturationKey: 0, kCIInputContrastKey: 1.1])
        let sepia = mono.applyingFilter("CISepiaTone", parameters: [kCIInputIntensityKey: 1.0])
        let ctx = CIContext()
        guard let cg = ctx.createCGImage(sepia, from: ci.extent) else { return img }
        return UIImage(cgImage: cg)
    }

    /// лицевая сторона доски 1200×840: гранит с крапинками, золотые буквы с тенью гравировки
    static func face(_ p: Plaque, _ h: Hero) -> UIImage {
        let W: CGFloat = 1200, H: CGFloat = 840
        let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: W, height: H), format: fmt).image { r in
            let cg = r.cgContext
            let cs = CGColorSpaceCreateDeviceRGB()
            let grad = CGGradient(colorsSpace: cs, colors: [
                UIColor(red: 0.18, green: 0.157, blue: 0.149, alpha: 1).cgColor,
                UIColor(red: 0.086, green: 0.075, blue: 0.071, alpha: 1).cgColor,
                UIColor(red: 0.149, green: 0.129, blue: 0.122, alpha: 1).cgColor] as CFArray, locations: [0, 0.55, 1])!
            cg.drawLinearGradient(grad, start: .zero, end: CGPoint(x: W, y: H), options: [])
            var rng = SeededRandom(seed: UInt64(bitPattern: Int64(p.id.hashValue)) | 1)
            for _ in 0..<6000 {
                let l = CGFloat(70 + Int(rng.next() % 90)) / 255
                UIColor(white: l, alpha: CGFloat(12 + Int(rng.next() % 40)) / 255).setFill()
                let rad = 0.5 + CGFloat(rng.next() % 1000) / 1000 * 2.3
                let x = CGFloat(rng.next() % 1200), y = CGFloat(rng.next() % 840)
                cg.fillEllipse(in: CGRect(x: x - rad, y: y - rad, width: rad * 2, height: rad * 2))
            }
            gold.setStroke()
            cg.setLineWidth(5); cg.stroke(CGRect(x: 90, y: 34, width: W - 180, height: H - 68))
            cg.setLineWidth(2); cg.stroke(CGRect(x: 104, y: 48, width: W - 208, height: H - 96))

            let photo = goldPortrait(Assets.image(h.image))
            if photo.size.width > 0 {
                let k = min(340 / photo.size.width, 600 / photo.size.height)
                let pw = photo.size.width * k, ph = photo.size.height * k
                photo.draw(in: CGRect(x: 130 + (340 - pw) / 2, y: 120 + (600 - ph) / 2, width: pw, height: ph), blendMode: .normal, alpha: 0.92)
            }

            let tx: CGFloat = 500, tw: CGFloat = 590
            func block(_ text: String, _ y: CGFloat, _ font: UIFont, kern: CGFloat = 0, maxH: CGFloat = 400) -> CGFloat {
                let ps = NSMutableParagraphStyle(); ps.alignment = .center; ps.lineBreakMode = .byWordWrapping
                let sh = NSShadow(); sh.shadowColor = UIColor(white: 0, alpha: 0.7); sh.shadowOffset = CGSize(width: 2.5, height: 2.5); sh.shadowBlurRadius = 1
                let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: gold, .paragraphStyle: ps, .kern: kern, .shadow: sh]
                let s = NSAttributedString(string: text, attributes: attrs)
                let b = s.boundingRect(with: CGSize(width: tw, height: maxH), options: [.usesLineFragmentOrigin], context: nil)
                let hh = min(ceil(b.height), maxH)
                s.draw(with: CGRect(x: tx, y: y, width: tw, height: hh), options: [.usesLineFragmentOrigin, .truncatesLastVisibleLine], context: nil)
                return y + hh
            }
            func serif(_ s: CGFloat, bold: Bool = false) -> UIFont {
                let base = UIFont.systemFont(ofSize: s, weight: bold ? .bold : .regular)
                return UIFont(descriptor: base.fontDescriptor.withDesign(.serif) ?? base.fontDescriptor, size: s)
            }
            var y: CGFloat = 90
            y = block("В ЧЕСТЬ", y, .monospacedSystemFont(ofSize: 30, weight: .regular), kern: 9) + 14
            y = block(h.fullName.uppercased(), y, serif(54, bold: true), maxH: 200) + 8
            y = block(h.years, y, serif(40)) + 18
            gold.setFill(); cg.fill(CGRect(x: tx + tw / 2 - 120, y: y, width: 240, height: 3))
            y += 22
            y = block(h.street.uppercased(), y, serif(32, bold: true), kern: 2.5) + 16
            _ = block(p.text, y, serif(27), maxH: max(40, H - 70 - y))
        }
    }

    /// мягкая тень, которую доска отбрасывает на стену
    static func shadow() -> UIImage {
        let s = CGSize(width: 400, height: 300)
        return UIGraphicsImageRenderer(size: s).image { r in
            let cg = r.cgContext
            cg.setShadow(offset: .zero, blur: 40, color: UIColor(white: 0, alpha: 0.85).cgColor)
            UIColor(white: 0, alpha: 0.6).setFill()
            cg.fill(CGRect(x: 60, y: 55, width: 280, height: 190))
        }
    }

    static func pbr(_ color: UIColor, metal: CGFloat, rough: CGFloat) -> SCNMaterial {
        let m = SCNMaterial()
        m.lightingModel = .physicallyBased
        m.diffuse.contents = color
        m.metalness.contents = metal
        m.roughness.contents = rough
        return m
    }

    /// объёмная доска; лицевая сторона смотрит по +Z
    static func node(_ p: Plaque, _ h: Hero) -> SCNNode {
        let w: CGFloat = 0.60, hh: CGFloat = 0.42, d: CGFloat = 0.035
        let root = SCNNode(); root.name = "plaque"
        let granite = pbr(UIColor(red: 0.118, green: 0.102, blue: 0.098, alpha: 1), metal: 0, rough: 0.32)
        let bronze = pbr(UIColor(red: 0.69, green: 0.54, blue: 0.29, alpha: 1), metal: 1, rough: 0.35)
        let brass = pbr(UIColor(red: 0.85, green: 0.72, blue: 0.42, alpha: 1), metal: 1, rough: 0.22)

        let sh = SCNNode(geometry: SCNPlane(width: w + 0.16, height: hh + 0.16))
        let sm = SCNMaterial(); sm.diffuse.contents = shadow(); sm.lightingModel = .constant; sm.writesToDepthBuffer = false
        sh.geometry?.materials = [sm]; sh.position = SCNVector3(0.01, -0.015, 0.001); sh.renderingOrder = -1
        root.addChildNode(sh)

        let frame = SCNNode(geometry: SCNBox(width: w + 0.028, height: hh + 0.028, length: 0.01, chamferRadius: 0.003))
        frame.geometry?.materials = [bronze]; frame.position = SCNVector3(0, 0, 0.005)
        root.addChildNode(frame)

        let slab = SCNNode(geometry: SCNBox(width: w, height: hh, length: d, chamferRadius: 0.004))
        let faceM = pbr(.white, metal: 0, rough: 0.3)
        faceM.diffuse.contents = face(p, h)
        slab.geometry?.materials = [faceM, granite, granite, granite, granite, granite] // перед, право, зад, лево, верх, низ
        slab.position = SCNVector3(0, 0, Float(0.008 + d / 2))
        root.addChildNode(slab)

        let bx = Float(w / 2 - 0.032), by = Float(hh / 2 - 0.032)
        for (x, y) in [(bx, by), (-bx, by), (bx, -by), (-bx, -by)] {
            let b = SCNNode(geometry: SCNCylinder(radius: 0.0115, height: 0.009))
            b.geometry?.materials = [brass]
            b.eulerAngles.x = .pi / 2
            b.position = SCNVector3(x, y, Float(0.008 + d + 0.0045))
            root.addChildNode(b)
            let slot = SCNNode(geometry: SCNBox(width: 0.016, height: 0.0025, length: 0.002, chamferRadius: 0))
            slot.geometry?.materials = [pbr(UIColor(white: 0.15, alpha: 1), metal: 1, rough: 0.6)]
            slot.position = SCNVector3(x, y, Float(0.008 + d + 0.0092)); slot.eulerAngles.z = .pi / 4
            root.addChildNode(slot)
        }
        root.scale = SCNVector3(0.01, 0.01, 0.01)
        root.runAction(.scale(to: 1, duration: 0.35))
        return root
    }
}

struct SeededRandom {
    var state: UInt64
    init(seed: UInt64) { state = seed }
    mutating func next() -> UInt64 { state ^= state << 13; state ^= state >> 7; state ^= state << 17; return state }
}

final class PlaqueARModel: ObservableObject {
    @Published var tapped = false
    @Published var placedId: String?
    @Published var wallSeen = false
    @Published var tracking = false
    @Published var message: String?
    weak var view: ARSCNView?
    var pending: SCNNode?
    var anchor: ARAnchor?

    @MainActor func clear() {
        if let a = anchor { view?.session.remove(anchor: a) }
        anchor = nil; pending = nil; placedId = nil
    }

    func say(_ s: String) {
        DispatchQueue.main.async {
            self.message = s
            DispatchQueue.main.asyncAfter(deadline: .now() + 2.6) { if self.message == s { self.message = nil } }
        }
    }

    @MainActor func snapshot(street: String) -> Bool {
        guard let v = view else { return false }
        let img = v.snapshot()
        let out = UIGraphicsImageRenderer(size: img.size).image { _ in
            img.draw(at: .zero)
            let s = img.size.width / 1080
            UIColor(red: 0.11, green: 0.06, blue: 0.055, alpha: 0.67).setFill()
            UIRectFill(CGRect(x: 0, y: img.size.height - 100 * s, width: img.size.width, height: 100 * s))
            let f = UIFont(descriptor: UIFont.systemFont(ofSize: 36 * s).fontDescriptor.withDesign(.serif) ?? UIFont.systemFont(ofSize: 36 * s).fontDescriptor, size: 36 * s)
            NSAttributedString(string: "\(street) · Улицы Победы", attributes: [.font: f, .foregroundColor: UIColor.white])
                .draw(at: CGPoint(x: 36 * s, y: img.size.height - 76 * s))
        }
        UIImageWriteToSavedPhotosAlbum(out, nil, nil, nil)
        return true
    }
}

struct WallARContainer: UIViewRepresentable {
    let target: Plaque
    let model: PlaqueARModel

    func makeCoordinator() -> Coord { Coord(model: model) }

    final class Coord: NSObject, ARSCNViewDelegate, ARSessionDelegate {
        let model: PlaqueARModel
        var target: Plaque?
        init(model: PlaqueARModel) { self.model = model }

        @MainActor @objc func tap(_ g: UITapGestureRecognizer) {
            guard let v = model.view, let t = target else { return }
            let pt = g.location(in: v)
            // нажали на саму доску?
            let hits = v.hitTest(pt, options: [.searchMode: SCNHitTestSearchMode.all.rawValue])
            if hits.contains(where: { h in var n: SCNNode? = h.node; while let c = n { if c.name == "plaque" { return true }; n = c.parent }; return false }) {
                model.tapped = true; return
            }
            guard let frame = v.session.currentFrame, case .normal = frame.camera.trackingState else {
                model.say("Подождите, камера ещё осматривается"); return
            }
            var result: ARRaycastResult?
            if let q = v.raycastQuery(from: pt, allowing: .existingPlaneGeometry, alignment: .vertical) { result = v.session.raycast(q).first }
            if result == nil, let q = v.raycastQuery(from: pt, allowing: .estimatedPlane, alignment: .vertical) { result = v.session.raycast(q).first }
            guard let r = result else {
                if let q = v.raycastQuery(from: pt, allowing: .estimatedPlane, alignment: .horizontal), !v.session.raycast(q).isEmpty {
                    model.say("Это пол или стол – наведите на стену")
                } else { model.say("Стена не найдена – медленно поведите телефоном вдоль стены") }
                return
            }
            let pos = SIMD3<Float>(r.worldTransform.columns.3.x, r.worldTransform.columns.3.y, r.worldTransform.columns.3.z)
            var n: SIMD3<Float>
            if let pa = r.anchor as? ARPlaneAnchor { let c = pa.transform.columns.1; n = SIMD3(c.x, c.y, c.z) }
            else { let c = r.worldTransform.columns.1; n = SIMD3(c.x, c.y, c.z) }
            let cam = frame.camera.transform.columns.3
            let toCam = SIMD3<Float>(cam.x - pos.x, 0, cam.z - pos.z)
            if abs(n.y) > 0.55 { n = toCam }            // нормаль неясна – повернём доску к себе
            n.y = 0
            if simd_length(n) < 1e-3 { return }
            n = simd_normalize(n)
            if simd_dot(n, toCam) < 0 { n = -n }        // лицом к нам
            let yaw = atan2(n.x, n.z)
            var m = matrix_identity_float4x4
            m.columns.0 = SIMD4(cos(yaw), 0, -sin(yaw), 0)
            m.columns.2 = SIMD4(sin(yaw), 0, cos(yaw), 0)
            m.columns.3 = SIMD4(pos.x, pos.y, pos.z, 1)

            model.clear()
            let hero = AppContent.shared.hero(t.hero)!
            model.pending = PlaqueArt.node(t, hero)
            let a = ARAnchor(name: "plaque", transform: m)
            model.anchor = a
            v.session.add(anchor: a)
            model.placedId = t.id
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        }

        func renderer(_ renderer: SCNSceneRenderer, nodeFor anchor: ARAnchor) -> SCNNode? {
            guard anchor.name == "plaque" else { return nil }
            let holder = SCNNode()
            if let p = model.pending { holder.addChildNode(p) }
            return holder
        }

        func renderer(_ renderer: SCNSceneRenderer, didAdd node: SCNNode, for anchor: ARAnchor) {
            if let pa = anchor as? ARPlaneAnchor, pa.alignment == .vertical {
                DispatchQueue.main.async { if !self.model.wallSeen { self.model.wallSeen = true } }
            }
        }

        func session(_ session: ARSession, cameraDidChangeTrackingState camera: ARCamera) {
            let ok: Bool
            if case .normal = camera.trackingState { ok = true } else { ok = false }
            DispatchQueue.main.async { if self.model.tracking != ok { self.model.tracking = ok } }
        }
    }

    func makeUIView(context: Context) -> ARSCNView {
        let v = ARSCNView()
        v.delegate = context.coordinator
        v.session.delegate = context.coordinator
        v.automaticallyUpdatesLighting = true
        v.antialiasingMode = .multisampling4X
        model.view = v
        let cfg = ARWorldTrackingConfiguration()
        cfg.planeDetection = [.vertical, .horizontal]
        cfg.environmentTexturing = .automatic
        cfg.isLightEstimationEnabled = true
        v.session.run(cfg)
        // подсказка Apple: «поведите телефоном, чтобы найти стену»
        let coach = ARCoachingOverlayView()
        coach.session = v.session
        coach.goal = .verticalPlane
        coach.activatesAutomatically = true
        coach.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        v.addSubview(coach)
        v.addGestureRecognizer(UITapGestureRecognizer(target: context.coordinator, action: #selector(Coord.tap(_:))))
        return v
    }

    func updateUIView(_ v: ARSCNView, context: Context) {
        context.coordinator.target = target
        if let id = model.placedId, id != target.id { Task { @MainActor in model.clear() } }
    }

    static func dismantleUIView(_ v: ARSCNView, coordinator: Coord) { v.session.pause() }
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
    @State private var saved = false
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
        if !model.tracking { return "Медленно поводите телефоном – камера осматривается" }
        if model.placedId == nil { return model.wallSeen ? "Стена найдена! Нажмите на неё – там появится доска" : "Наведите камеру на стену дома и ведите вдоль неё" }
        if !near {
            if let d = distance { return "Доска висит. Блок можно получить у самой улицы: \(Geo.human(d))" }
            return "Доска висит. Блок – у самой улицы или в демо-режиме"
        }
        return "Нажмите на доску, чтобы прочитать и получить блок"
    }
    var arrowAngle: Double {
        guard let l = loc.location, let h = loc.heading else { return 0 }
        return Geo.bearing(l.coordinate, target.coordinate) - h
    }

    var body: some View {
        let hero = c.hero(target.hero)!
        ZStack {
            WallARContainer(target: target, model: model).ignoresSafeArea()
            VStack(spacing: 10) {
                VStack(spacing: 4) {
                    Text(hero.street).bold()
                    Text(status).font(.system(size: 14)).multilineTextAlignment(.center).foregroundColor(model.placedId != nil && near ? .gold : .paper.opacity(0.9))
                    if let a = loc.location?.horizontalAccuracy, !demo {
                        Text("GPS ±\(Int(a)) м" + (distance.map { " · до улицы \(Geo.human($0))" } ?? "")).font(.system(size: 11)).opacity(0.55)
                    }
                }
                .foregroundColor(.paper).padding(12).background(Color.ink.opacity(0.8)).clipShape(RoundedRectangle(cornerRadius: 14))
                .padding(.horizontal, 16)
                if !demo, !near, loc.heading != nil, loc.location != nil {
                    Image(systemName: "location.north.fill").font(.system(size: 30)).foregroundColor(.gold)
                        .rotationEffect(.degrees(arrowAngle)).frame(width: 56, height: 56).background(Color.ink.opacity(0.7)).clipShape(Circle())
                        .overlay(Circle().stroke(Color.gold, lineWidth: 2))
                }
                if let m = model.message { Text(m).font(.system(size: 14)).multilineTextAlignment(.center).foregroundColor(.paper).padding(10).background(Color.acc).clipShape(Capsule()).padding(.horizontal, 16) }
                if saved { Text("Фото сохранено в галерею").font(.system(size: 14)).foregroundColor(.paper).padding(10).background(Color.okGreen).clipShape(Capsule()) }
                Spacer()
                VStack(spacing: 10) {
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
                            Text(demo ? "Демо: вкл" : "Демо").bold().padding(.horizontal, 16).padding(.vertical, 12)
                                .background(demo ? Color.gold : Color.acc).foregroundColor(demo ? .ink : .paper).clipShape(Capsule())
                        }
                        if let l = loc.location, !near {
                            Button {
                                openURL(URL(string: "https://yandex.ru/maps/?rtext=\(l.coordinate.latitude),\(l.coordinate.longitude)~\(target.lat),\(target.lon)&rtt=pd")!)
                            } label: { Text("Маршрут").padding(.horizontal, 16).padding(.vertical, 12).overlay(Capsule().stroke(Color.paper)).foregroundColor(.paper) }
                        }
                        Spacer()
                        Button {
                            if model.snapshot(street: hero.street) {
                                saved = true
                                DispatchQueue.main.asyncAfter(deadline: .now() + 2) { saved = false }
                            }
                        } label: {
                            Circle().fill(Color.acc).frame(width: 62, height: 62).overlay(Circle().stroke(Color.paper, lineWidth: 4)).overlay(Image(systemName: "camera.fill").foregroundColor(.paper))
                        }.disabled(model.placedId == nil).opacity(model.placedId == nil ? 0.45 : 1)
                    }.padding(.horizontal, 16)
                    Text("Блоков знака: \(store.blocks.count) из 3 · чтобы перевесить доску, нажмите на другое место стены")
                        .font(.system(size: 12)).foregroundColor(.paper.opacity(0.7)).padding(.horizontal, 16)
                }
                .padding(.vertical, 12).frame(maxWidth: .infinity)
                .background(LinearGradient(colors: [.clear, Color.ink.opacity(0.92)], startPoint: .top, endPoint: .bottom))
            }.padding(.top, 8)
        }
        .navigationTitle("AR-таблички").navigationBarTitleDisplayMode(.inline)
        .onAppear { loc.start() }
        .onChange(of: model.tapped) { t in
            guard t else { return }
            model.tapped = false
            if near {
                let cur = target; chosen = cur.id
                newBlock = store.addBlock(cur.id)
                opened = target
            } else {
                model.say("Чтобы получить блок, подойдите к улице (или включите демо-режим)")
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
