import Foundation
import CoreLocation
import AVFoundation

/// Прогресс квеста. Хранится в UserDefaults.
final class QuestStore: ObservableObject {
    private let d = UserDefaults.standard
    @Published var stage: Int { didSet { d.set(stage, forKey: "stage") } }
    @Published var done: Set<Int> { didSet { d.set(Array(done), forKey: "done") } }
    @Published var blocks: Set<String> { didSet { d.set(Array(blocks), forKey: "blocks") } }
    @Published var checkins: Set<String> { didSet { d.set(Array(checkins), forKey: "checkins") } }
    @Published var points: Int { didSet { d.set(points, forKey: "points") } }
    @Published var wordSolved: Bool { didSet { d.set(wordSolved, forKey: "word") } }
    @Published var name: String { didSet { d.set(name, forKey: "name") } }
    @Published var quizBest: Int { didSet { d.set(quizBest, forKey: "quizBest") } }

    init() {
        stage = d.integer(forKey: "stage")
        done = Set((d.array(forKey: "done") as? [Int]) ?? [])
        blocks = Set((d.array(forKey: "blocks") as? [String]) ?? [])
        checkins = Set((d.array(forKey: "checkins") as? [String]) ?? [])
        points = d.integer(forKey: "points")
        wordSolved = d.bool(forKey: "word")
        name = d.string(forKey: "name") ?? ""
        quizBest = d.integer(forKey: "quizBest")
    }

    func solve(_ i: Int, firstTry: Bool) { if !done.contains(i) { done.insert(i); points += firstTry ? 10 : 5 } }
    func next(total: Int) { stage = min(stage + 1, total) }
    @discardableResult func addBlock(_ id: String) -> Bool {
        if blocks.contains(id) { return false }
        blocks.insert(id); points += 20; return true
    }
    func checkin(_ id: String) { if !checkins.contains(id) { checkins.insert(id); points += 15 } }
    func solveWord() { if !wordSolved { wordSolved = true; points += 10 } }
    func reset() { stage = 0; done = []; blocks = []; checkins = []; points = 0; wordSolved = false }
}

/// GPS и компас.
final class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {
    static let shared = LocationService()
    private let m = CLLocationManager()
    @Published var location: CLLocation?
    @Published var heading: Double?
    @Published var authorized = false

    override init() {
        super.init()
        m.delegate = self
        m.desiredAccuracy = kCLLocationAccuracyBest
        m.headingFilter = 1
    }
    func start() {
        m.requestWhenInUseAuthorization()
        m.startUpdatingLocation()
        if CLLocationManager.headingAvailable() { m.startUpdatingHeading() }
    }
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorized = manager.authorizationStatus == .authorizedWhenInUse || manager.authorizationStatus == .authorizedAlways
        if authorized { manager.startUpdatingLocation() }
    }
    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        if let l = locations.last, l.horizontalAccuracy >= 0 { location = l }
    }
    func locationManager(_ manager: CLLocationManager, didUpdateHeading newHeading: CLHeading) {
        heading = newHeading.trueHeading >= 0 ? newHeading.trueHeading : newHeading.magneticHeading
    }
}

/// Проигрыватель записей ElevenLabs.
final class AudioPlayer: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published var playing = false
    @Published var progress: Double = 0
    private var player: AVAudioPlayer?
    private var timer: Timer?
    private var file = ""

    func toggle(_ name: String) {
        if file != name { stop(); file = name }
        if player == nil, let url = Assets.audioURL(name) {
            try? AVAudioSession.sharedInstance().setCategory(.playback)
            player = try? AVAudioPlayer(contentsOf: url)
            player?.delegate = self
        }
        guard let p = player else { return }
        if p.isPlaying { p.pause(); playing = false; timer?.invalidate() }
        else {
            p.play(); playing = true
            timer = Timer.scheduledTimer(withTimeInterval: 0.25, repeats: true) { [weak self] _ in
                guard let self, let p = self.player, p.duration > 0 else { return }
                self.progress = p.currentTime / p.duration
            }
        }
    }
    func stop() { player?.stop(); player = nil; playing = false; progress = 0; timer?.invalidate() }
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        playing = false; progress = 1; timer?.invalidate()
    }
}
