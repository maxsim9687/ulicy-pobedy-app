import Foundation
import SwiftUI
import UIKit
import CoreLocation

struct Hero: Codable, Identifiable {
    let id, name, fullName, years, street, district, role, image, lifeImage, lifeCaption, audio, story: String
    let life: [[String]]
    let facts: [String]
}

struct Plaque: Codable, Identifiable {
    let id, hero: String
    let lat, lon: Double
    let `where`, text, hint: String
    var coordinate: CLLocationCoordinate2D { .init(latitude: lat, longitude: lon) }
}

struct Stage: Codable, Identifiable {
    let id, title, `where`: String
    let lat, lon: Double
    let hero: String?
    let letter, kind, intro: String
    let how: [String]
    let q: String
    let opts: [String]
    let a: Int
}

struct QuizItem: Codable {
    let q: String
    let o: [String]
    let a: Int
    let e: String
}

struct AppContent: Codable {
    let title, author, site, word: String
    let heroes: [Hero]
    let plaques: [Plaque]
    let stages: [Stage]
    let quiz: [QuizItem]

    static let shared: AppContent = {
        let url = Bundle.main.url(forResource: "content", withExtension: "json", subdirectory: "shared")!
        let data = try! Data(contentsOf: url)
        return try! JSONDecoder().decode(AppContent.self, from: data)
    }()

    static var stagesJSON: String {
        let url = Bundle.main.url(forResource: "content", withExtension: "json", subdirectory: "shared")!
        if let data = try? Data(contentsOf: url),
           let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let st = obj["stages"],
           let out = try? JSONSerialization.data(withJSONObject: st),
           let s = String(data: out, encoding: .utf8) { return s }
        return "[]"
    }

    func hero(_ id: String?) -> Hero? { heroes.first { $0.id == id } }
}

enum Assets {
    private static var cache: [String: UIImage] = [:]
    static func image(_ name: String) -> UIImage {
        if let i = cache[name] { return i }
        let url = Bundle.main.url(forResource: name, withExtension: nil, subdirectory: "shared/images")
        let img = url.flatMap { UIImage(contentsOfFile: $0.path) } ?? UIImage()
        cache[name] = img
        return img
    }
    static func audioURL(_ name: String) -> URL? {
        Bundle.main.url(forResource: name, withExtension: nil, subdirectory: "shared/audio")
    }
    static var mapHTML: String {
        guard let url = Bundle.main.url(forResource: "map", withExtension: "html", subdirectory: "shared"),
              let s = try? String(contentsOf: url, encoding: .utf8) else { return "" }
        return s
    }
}

enum Geo {
    static func distance(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Double {
        CLLocation(latitude: a.latitude, longitude: a.longitude).distance(from: CLLocation(latitude: b.latitude, longitude: b.longitude))
    }
    /// направление на точку: градусы от севера по часовой
    static func bearing(_ a: CLLocationCoordinate2D, _ b: CLLocationCoordinate2D) -> Double {
        let p1 = a.latitude * .pi / 180, p2 = b.latitude * .pi / 180, dl = (b.longitude - a.longitude) * .pi / 180
        let y = sin(dl) * cos(p2), x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return (atan2(y, x) * 180 / .pi + 360).truncatingRemainder(dividingBy: 360)
    }
    static func human(_ m: Double) -> String {
        m < 1000 ? "\(Int(m / 10) * 10) м" : String(format: "%.1f км", m / 1000).replacingOccurrences(of: ".", with: ",")
    }
}

/* ---------- цвета ---------- */
extension Color {
    static let ink = Color(red: 0.11, green: 0.06, blue: 0.055)
    static let paper = Color(red: 0.957, green: 0.937, blue: 0.906)
    static let acc = Color(red: 0.62, green: 0.165, blue: 0.17)
    static let accL = Color(red: 0.925, green: 0.604, blue: 0.588)
    static let gold = Color(red: 0.85, green: 0.71, blue: 0.416)
    static let okGreen = Color(red: 0.247, green: 0.541, blue: 0.361)
    static let muted = Color(red: 0.54, green: 0.48, blue: 0.46)
}
