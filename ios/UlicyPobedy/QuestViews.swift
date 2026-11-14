import SwiftUI
import CoreLocation

/* ======================= Квест ======================= */
struct QuestView: View {
    @EnvironmentObject var store: QuestStore
    @EnvironmentObject var loc: LocationService
    @State private var tick = 0
    let c = AppContent.shared

    var body: some View {
        ZStack {
            Backdrop()
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 5) {
                        ForEach(0..<c.stages.count, id: \.self) { k in
                            Capsule().fill(k < store.stage ? Color.accL : (k == store.stage ? Color.paper : Color.paper.opacity(0.15))).frame(height: 6)
                        }
                    }
                    YandexMap(stage: store.stage, done: store.done, me: loc.location, centerOnMeTick: tick)
                        .frame(height: 280).clipShape(RoundedRectangle(cornerRadius: 18))
                    HStack {
                        Button { tick += 1 } label: { Label("Где я?", systemImage: "location.fill").padding(.horizontal, 14).padding(.vertical, 10).overlay(Capsule().stroke(Color.paper.opacity(0.4))) }
                            .foregroundColor(.paper)
                        Text(statusText).foregroundColor(.paper.opacity(0.8)).font(.system(size: 14))
                    }
                    if store.stage >= c.stages.count { FinishBlock() } else { StageCard(i: store.stage) }
                    if store.stage > 0 {
                        Button("Начать квест заново") { store.reset() }.foregroundColor(.paper.opacity(0.5)).padding(.top, 20)
                    }
                }.padding(16)
            }
        }
        .navigationTitle("Квест · очки \(store.points)").navigationBarTitleDisplayMode(.inline)
        .onAppear { loc.start() }
    }

    var statusText: String {
        guard let l = loc.location else { return loc.authorized ? "Ищу вас…" : "Разрешите геолокацию" }
        guard store.stage < c.stages.count else { return "" }
        let s = c.stages[store.stage]
        let d = Geo.distance(l.coordinate, .init(latitude: s.lat, longitude: s.lon))
        return d < 200 ? "✓ Вы на месте" : "До точки: \(Geo.human(d))"
    }
}

struct StageCard: View {
    let i: Int
    @EnvironmentObject var store: QuestStore
    @EnvironmentObject var loc: LocationService
    @State private var wrong: Set<Int> = []
    @State private var openHistory = false
    @Environment(\.openURL) var openURL
    let c = AppContent.shared

    var body: some View {
        let s = c.stages[i]
        let hero = c.hero(s.hero)
        let solved = store.done.contains(i)
        VStack(alignment: .leading, spacing: 12) {
            PaperCard {
                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 6) {
                        TagView(text: s.kind == "transit" ? "Переезд" : s.kind == "food" ? "Привал" : s.kind == "final" ? "Финал" : "Точка \(i + 1)")
                        Text(s.title).font(.system(size: 22, weight: .bold, design: .serif))
                        Text(s.`where`).font(.system(size: 14)).foregroundColor(.muted)
                    }
                    Spacer()
                    if let h = hero { AssetImage(name: h.image, gray: true).frame(width: 90, height: 90) }
                }
                Text("✉ " + s.intro).italic().font(.system(size: 15)).opacity(0.85)
                if let h = hero { AudioRow(file: h.audio, title: "Аудиогид: \(h.name)") }
                VStack(alignment: .leading, spacing: 4) {
                    Text(s.kind == "food" ? "🍽 Можно перекусить" : "Что делать").bold()
                    ForEach(Array(s.how.enumerated()), id: \.offset) { k, t in Text("\(k + 1). \(t)").font(.system(size: 15)) }
                }.padding(14).frame(maxWidth: .infinity, alignment: .leading)
                    .background(s.kind == "food" ? Color(red: 0.95, green: 0.886, blue: 0.77) : Color.ink.opacity(0.06)).clipShape(RoundedRectangle(cornerRadius: 14))

                // действия
                VStack(alignment: .leading, spacing: 8) {
                    Button {
                        if let l = loc.location {
                            openURL(URL(string: "https://yandex.ru/maps/?rtext=\(l.coordinate.latitude),\(l.coordinate.longitude)~\(s.lat),\(s.lon)&rtt=\(s.kind == "transit" ? "mt" : "pd")")!)
                        } else { openURL(URL(string: "https://yandex.ru/maps/?pt=\(s.lon),\(s.lat)&z=16&l=map")!) }
                    } label: { pill("Маршрут в Яндекс Картах", filled: false) }
                    if s.kind == "food" {
                        Button { openURL(URL(string: "https://yandex.ru/maps/?text=%D0%BA%D0%B0%D1%84%D0%B5&ll=\(s.lon),\(s.lat)&z=16")!) } label: { pill("Кафе рядом", filled: false) }
                    }
                    if let h = hero {
                        let got = store.blocks.contains(h.id)
                        NavigationLink { PlaqueARView(initial: h.id) } label: { pill(got ? "✓ AR-табличка найдена" : "📍 Найти AR-табличку", filled: true, color: got ? .okGreen : .ink) }
                    }
                    if s.kind != "final" {
                        let near = loc.location.map { Geo.distance($0.coordinate, .init(latitude: s.lat, longitude: s.lon)) < max(200, $0.horizontalAccuracy + 50) } ?? false
                        let checked = store.checkins.contains(s.id)
                        Button { store.checkin(s.id) } label: { pill(checked ? "✓ Отмечено +15" : "Отметиться на месте", filled: true, color: .acc) }
                            .disabled(!near || checked).opacity(near || checked ? 1 : 0.45)
                    }
                }

                if let h = hero {
                    Button { withAnimation { openHistory.toggle() } } label: {
                        Text(openHistory ? "▾ История подробнее" : "▸ История подробнее").bold().foregroundColor(.acc)
                    }
                    if openHistory {
                        ForEach(h.life, id: \.self) { item in
                            Text(item.first ?? "").bold().foregroundColor(.acc)
                            Text(item.last ?? "").font(.system(size: 15))
                        }
                    }
                }

                Text("Вопрос – получите букву").font(.system(size: 17, weight: .bold)).padding(.top, 6)
                Text(s.q)
                ForEach(Array(s.opts.enumerated()), id: \.offset) { k, o in
                    let isOk = solved && k == s.a
                    let isBad = wrong.contains(k)
                    Button {
                        guard !solved else { return }
                        if k == s.a { store.solve(i, firstTry: wrong.isEmpty) } else { wrong.insert(k) }
                    } label: {
                        Text(o).fontWeight(.semibold).frame(maxWidth: .infinity, alignment: .leading).padding(14)
                            .background(isOk ? Color(red: 0.86, green: 0.94, blue: 0.886) : isBad ? Color(red: 0.965, green: 0.863, blue: 0.85) : Color.ink.opacity(0.03))
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(isOk ? Color.okGreen : isBad ? Color.acc : Color.ink.opacity(0.2)))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }.foregroundColor(.ink)
                }
                if !wrong.isEmpty && !solved { Text("Не совсем. Подсказка есть на AR-табличке и в аудиогиде.").foregroundColor(.acc).font(.system(size: 14)) }
                if solved {
                    Text("Верно! Ваша буква – «\(s.letter)».").bold().foregroundColor(.okGreen)
                    Button { store.next(total: c.stages.count) } label: { pill(i == c.stages.count - 1 ? "К финалу →" : "Следующий этап →", filled: true, color: .acc) }
                }
            }
            LettersRow()
        }
        .id(i)
    }

    func pill(_ t: String, filled: Bool, color: Color = .acc) -> some View {
        Text(t).fontWeight(.semibold).padding(.horizontal, 14).padding(.vertical, 11)
            .frame(maxWidth: .infinity)
            .foregroundColor(filled ? .paper : .ink)
            .background(filled ? color : Color.clear)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ink.opacity(filled ? 0 : 0.3)))
            .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

struct LettersRow: View {
    @EnvironmentObject var store: QuestStore
    let c = AppContent.shared
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Собранные буквы").bold().foregroundColor(.paper)
            HStack(spacing: 6) {
                ForEach(Array(c.stages.enumerated()), id: \.offset) { k, s in
                    let got = store.done.contains(k)
                    Text(got ? s.letter : "?").font(.system(size: 22, weight: .bold, design: .serif)).foregroundColor(.paper)
                        .frame(width: 42, height: 50).background(got ? Color.acc : Color.paper.opacity(0.08)).clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
        }.padding(16).frame(maxWidth: .infinity, alignment: .leading).background(Color.paper.opacity(0.07)).clipShape(RoundedRectangle(cornerRadius: 18))
    }
}

struct FinishBlock: View {
    @EnvironmentObject var store: QuestStore
    @State private var word = ""
    @State private var err = false
    let c = AppContent.shared
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Kicker(text: store.wordSolved ? "Квест пройден" : "Финал")
            if !store.wordSolved {
                Text("Сложите слово из букв").font(.system(size: 22, weight: .bold, design: .serif)).foregroundColor(.paper)
                LettersRow()
                TextField("Секретное слово", text: $word).textInputAutocapitalization(.characters).padding(12)
                    .background(Color.paper.opacity(0.1)).clipShape(RoundedRectangle(cornerRadius: 12)).foregroundColor(.paper)
                if err { Text("Пока не то слово. Буквы уже стоят по порядку.").foregroundColor(.accL).font(.system(size: 14)) }
                Button {
                    if word.trimmingCharacters(in: .whitespaces).uppercased().replacingOccurrences(of: "Ё", with: "Е") == c.word { store.solveWord() } else { err = true }
                } label: { Text("Проверить").bold().frame(maxWidth: .infinity).padding().background(Color.acc).foregroundColor(.paper).clipShape(RoundedRectangle(cornerRadius: 14)) }
            } else {
                Text("Слово «\(c.word)» разгадано!").font(.system(size: 22, weight: .bold, design: .serif)).foregroundColor(.paper)
                Text("Очки: \(store.points). Блоков знака: \(store.blocks.count) из 3.").foregroundColor(.paper.opacity(0.85))
                NavigationLink { FinalView() } label: { Text("🏅 Знак и грамота").bold().frame(maxWidth: .infinity).padding().background(Color.acc).foregroundColor(.paper).clipShape(RoundedRectangle(cornerRadius: 14)) }
            }
        }
    }
}

/* ======================= Знак «Хранитель памяти» ======================= */
struct MedalView: View {
    let blocks: Set<String>
    let ids = ["ryabinin", "gromov", "grizodubova"]
    let colors: [Color] = [.acc, Color(red: 0.72, green: 0.28, blue: 0.23), Color(red: 0.48, green: 0.12, blue: 0.125)]
    var body: some View {
        GeometryReader { g in
            let r = min(g.size.width, g.size.height) / 2 - 6
            let c = CGPoint(x: g.size.width / 2, y: g.size.height / 2)
            ZStack {
                ForEach(0..<3, id: \.self) { k in
                    Path { p in
                        p.move(to: c)
                        p.addArc(center: c, radius: r, startAngle: .degrees(-90 + Double(k) * 120), endAngle: .degrees(30 + Double(k) * 120), clockwise: false)
                        p.closeSubpath()
                    }.fill(blocks.contains(ids[k]) ? colors[k] : Color(red: 0.23, green: 0.16, blue: 0.16))
                    .overlay(Path { p in
                        p.move(to: c)
                        p.addArc(center: c, radius: r, startAngle: .degrees(-90 + Double(k) * 120), endAngle: .degrees(30 + Double(k) * 120), clockwise: false)
                        p.closeSubpath()
                    }.stroke(Color.paper, lineWidth: 3))
                }
                Circle().stroke(Color.gold, lineWidth: 8).frame(width: r * 2, height: r * 2)
                Circle().fill(blocks.count >= 3 ? Color.gold : Color(red: 0.16, green: 0.1, blue: 0.09)).frame(width: r * 0.66, height: r * 0.66)
                    .overlay(Circle().stroke(Color.paper, lineWidth: 3))
                Text("★").font(.system(size: r * 0.4)).foregroundColor(blocks.count >= 3 ? .acc : .muted)
            }
        }
    }
}

struct FinalView: View {
    @EnvironmentObject var store: QuestStore
    let c = AppContent.shared
    var body: some View {
        ZStack {
            Backdrop()
            ScrollView {
                VStack(spacing: 14) {
                    MedalView(blocks: store.blocks).frame(width: 240, height: 240).padding(.top, 10)
                    Text(store.blocks.count >= 3 ? "Знак «Хранитель памяти»" : "Собрано блоков: \(store.blocks.count) из 3")
                        .font(.system(size: 22, weight: .bold, design: .serif)).foregroundColor(.paper)
                    Text(store.blocks.count >= 3 ? "Вы нашли все три AR-таблички!" : "Найдите AR-таблички на улицах Рябинина, Громова и Гризодубовой.")
                        .foregroundColor(.paper.opacity(0.85)).multilineTextAlignment(.center)
                    HStack(spacing: 8) {
                        badge("🎯", "Знаток", store.done.count >= c.stages.count)
                        badge("🧭", "Путешественник", store.checkins.count >= 3)
                        badge("📍", "Искатель табличек", store.blocks.count >= 3)
                        badge("🔐", "Разгадал слово", store.wordSolved)
                    }
                    if store.blocks.count >= 3 || store.wordSolved {
                        TextField("Ваше имя для грамоты", text: $store.name).padding(12)
                            .background(Color.paper.opacity(0.1)).clipShape(RoundedRectangle(cornerRadius: 12)).foregroundColor(.paper)
                        VStack(spacing: 6) {
                            Text("ГРАМОТА").font(.system(size: 13, design: .monospaced)).tracking(3).foregroundColor(.acc)
                            Text("Квест «Три улицы» пройден!").font(.system(size: 22, weight: .bold, design: .serif)).multilineTextAlignment(.center)
                            Text(store.name.isEmpty ? "—" : store.name).font(.system(size: 26, weight: .bold, design: .serif))
                            Text("за прохождение маршрута по улицам Рябинина, Громова и Гризодубовой").multilineTextAlignment(.center)
                            Text("Очки: \(store.points)").bold().foregroundColor(.acc)
                            Text("Улицы Победы · Максим Шумилов, 7 «П»").font(.caption).foregroundColor(.muted)
                        }.foregroundColor(.ink).padding(22).frame(maxWidth: .infinity).background(Color.paper)
                            .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.acc, lineWidth: 3)).clipShape(RoundedRectangle(cornerRadius: 20))
                        Text("Сделайте скриншот, чтобы сохранить грамоту.").font(.caption).foregroundColor(.paper.opacity(0.6))
                    }
                }.padding(20)
            }
        }.navigationTitle("Награда").navigationBarTitleDisplayMode(.inline)
    }
    func badge(_ ic: String, _ t: String, _ got: Bool) -> some View {
        VStack(spacing: 4) {
            Text(ic).font(.system(size: 24)).opacity(got ? 1 : 0.35)
            Text(t).font(.system(size: 11)).multilineTextAlignment(.center).foregroundColor(.paper.opacity(got ? 1 : 0.4))
        }.padding(8).frame(maxWidth: .infinity).background(got ? Color.acc : Color.paper.opacity(0.06)).clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

/* ======================= Викторина ======================= */
struct QuizView: View {
    @EnvironmentObject var store: QuestStore
    @State private var i = 0
    @State private var score = 0
    @State private var picked: Int?
    @State private var order: [Int] = []
    let c = AppContent.shared
    var body: some View {
        ZStack {
            Backdrop()
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if i < c.quiz.count {
                        let q = c.quiz[i]
                        Text("Вопрос \(i + 1) / \(c.quiz.count) · очки \(score)").font(.system(size: 13, design: .monospaced)).foregroundColor(.paper.opacity(0.7))
                        ProgressView(value: Double(i), total: Double(c.quiz.count)).tint(.accL)
                        PaperCard {
                            Text(q.q).font(.system(size: 22, weight: .bold, design: .serif))
                            ForEach(order, id: \.self) { k in
                                Button {
                                    guard picked == nil else { return }
                                    picked = k; if k == q.a { score += 1 }
                                } label: {
                                    Text(q.o[k]).fontWeight(.semibold).frame(maxWidth: .infinity, alignment: .leading).padding(14)
                                        .background(picked == nil ? Color.ink.opacity(0.03) : k == q.a ? Color(red: 0.86, green: 0.94, blue: 0.886) : k == picked ? Color(red: 0.965, green: 0.863, blue: 0.85) : Color.ink.opacity(0.03))
                                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.ink.opacity(0.2))).clipShape(RoundedRectangle(cornerRadius: 12))
                                }.foregroundColor(.ink)
                            }
                            if let p = picked {
                                Text((p == q.a ? "Верно! " : "Неверно. ") + q.e)
                                Button {
                                    picked = nil; i += 1
                                    if i >= c.quiz.count { store.quizBest = max(store.quizBest, score) } else { order = c.quiz[i].o.indices.shuffled() }
                                } label: { Text(i < c.quiz.count - 1 ? "Дальше →" : "Результат").bold().frame(maxWidth: .infinity).padding().background(Color.acc).foregroundColor(.paper).clipShape(RoundedRectangle(cornerRadius: 14)) }
                            }
                        }
                    } else {
                        VStack(spacing: 12) {
                            Text("\(score) / \(c.quiz.count)").font(.system(size: 72, weight: .bold, design: .serif)).foregroundColor(.accL)
                            Text(score >= 9 ? "Отлично! Вы знаете героев своих улиц." : score >= 6 ? "Хорошо! Кое-что можно повторить." : "Загляните в истории героев и попробуйте ещё раз.")
                                .font(.system(size: 20, weight: .bold, design: .serif)).foregroundColor(.paper).multilineTextAlignment(.center)
                            Button { i = 0; score = 0; order = c.quiz[0].o.indices.shuffled() } label: { Text("Пройти ещё раз").bold().frame(maxWidth: .infinity).padding().background(Color.acc).foregroundColor(.paper).clipShape(RoundedRectangle(cornerRadius: 14)) }
                        }.frame(maxWidth: .infinity)
                    }
                }.padding(20)
            }
        }
        .navigationTitle("Викторина").navigationBarTitleDisplayMode(.inline)
        .onAppear { if order.isEmpty { order = c.quiz[0].o.indices.shuffled() } }
    }
}

/* ======================= О проекте ======================= */
struct AboutView: View {
    @Environment(\.openURL) var openURL
    var body: some View {
        ZStack {
            Backdrop()
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Улицы Победы").font(.system(size: 30, weight: .bold, design: .serif)).foregroundColor(.paper)
                    Group {
                        Text("Проект Максима Шумилова, 7 «П» класс. Я узнал, какие улицы Ленинского и Академического районов Екатеринбурга названы в честь участников Великой Отечественной войны. Их оказалось три: улицы Громова, Гризодубовой и Рябинина.")
                        Text("Найти все улицы вручную невозможно – в городе около тысячи улиц, поэтому я использовал нейросеть Claude, а каждый факт проверил по энциклопедиям.")
                        Text("Источники: Википедия, Большая российская энциклопедия, «Областная газета». Озвучка: ElevenLabs. Карта: Яндекс.")
                    }.foregroundColor(.paper.opacity(0.88))
                    Button { openURL(URL(string: AppContent.shared.site)!) } label: {
                        Text("Открыть сайт проекта").bold().frame(maxWidth: .infinity).padding().background(Color.acc).foregroundColor(.paper).clipShape(RoundedRectangle(cornerRadius: 14))
                    }.padding(.top, 8)
                }.padding(20)
            }
        }.navigationTitle("О проекте").navigationBarTitleDisplayMode(.inline)
    }
}
