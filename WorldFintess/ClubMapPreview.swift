import CoreLocation
import MapKit
import SwiftUI

/// Карта клуба + маршрут / Яндекс / 2ГИС (`ClubMapPreview.kt`).
struct ClubMapPreview: View {
    let latitude: Double
    let longitude: Double
    let address: String

    @StateObject private var location = ClubMapLocationHelper()
    @State private var route: MKRoute?
    @State private var statusText: String?
    @State private var routing = false
    @State private var cameraPosition: MapCameraPosition = .automatic

    private var resolved: (lat: Double, lon: Double) {
        ClubMapCoords.resolve(latitude: latitude, longitude: longitude, address: address)
    }

    private var clubCoordinate: CLLocationCoordinate2D {
        CLLocationCoordinate2D(latitude: resolved.lat, longitude: resolved.lon)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack(alignment: .bottomLeading) {
                Map(position: $cameraPosition) {
                    Annotation(address.isEmpty ? "Клуб" : address, coordinate: clubCoordinate) {
                        Image(systemName: "mappin.circle.fill")
                            .font(.title)
                            .foregroundStyle(Color(red: 0.99, green: 0.25, blue: 0.11))
                            .shadow(radius: 2)
                    }
                    if let route {
                        MapPolyline(route.polyline)
                            .stroke(Theme.accentOrange, lineWidth: 5)
                    }
                    if let user = location.coordinate {
                        Annotation("Вы здесь", coordinate: user) {
                            Image(systemName: "location.circle.fill")
                                .font(.title2)
                                .foregroundStyle(Theme.accentBlue)
                        }
                    }
                }
                .mapStyle(.standard(elevation: .flat))
                .frame(height: 300)
                .clipShape(Rectangle())

                HStack(spacing: 8) {
                    Image(systemName: "mappin.circle.fill")
                        .foregroundStyle(Theme.primary)
                    Text(address.isEmpty ? "Клуб на карте" : address)
                        .font(FCTypography.labelMedium())
                        .fontWeight(.semibold)
                        .foregroundStyle(Color(red: 0.12, green: 0.16, blue: 0.22))
                        .lineLimit(2)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(.white.opacity(0.95))
                .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
                .shadow(color: .black.opacity(0.12), radius: 4, y: 2)
                .padding(12)
            }
            .onAppear {
                cameraPosition = .region(
                    MKCoordinateRegion(
                        center: clubCoordinate,
                        span: MKCoordinateSpan(latitudeDelta: 0.02, longitudeDelta: 0.02)
                    )
                )
            }

            if let statusText {
                Text(statusText)
                    .font(FCTypography.bodySmall())
                    .foregroundStyle(Theme.onSurfaceVariant)
                    .padding(.horizontal, 16)
            }

            VStack(spacing: 8) {
                Button {
                    Task { await buildInAppRoute() }
                } label: {
                    HStack(spacing: 8) {
                        if routing {
                            ProgressView().tint(.white)
                            Text("Строим…")
                        } else {
                            Image(systemName: "arrow.triangle.turn.up.right.diamond.fill")
                            Text("Маршрут на карте")
                                .fontWeight(.semibold)
                        }
                    }
                    .font(FCTypography.titleSmall())
                    .foregroundStyle(Theme.onPrimary)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Theme.primary)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
                }
                .buttonStyle(.plain)
                .disabled(routing)

                HStack(spacing: 8) {
                    mapExternalButton(title: "Яндекс") {
                        ClubMapExternal.openYandex(lat: resolved.lat, lon: resolved.lon, address: address)
                    }
                    mapExternalButton(title: "2ГИС") {
                        ClubMapExternal.openDgis(lat: resolved.lat, lon: resolved.lon, address: address)
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
    }

    private func mapExternalButton(title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: "map")
                Text(title)
                    .lineLimit(1)
            }
            .font(FCTypography.labelLarge())
            .foregroundStyle(Theme.onSurface)
            .frame(maxWidth: .infinity)
            .frame(height: 44)
            .background(Theme.surfaceVariant)
            .clipShape(RoundedRectangle(cornerRadius: Theme.radius12, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    @MainActor
    private func buildInAppRoute() async {
        routing = true
        statusText = "Определяем ваше местоположение…"
        defer { routing = false }

        let granted = await location.requestPermissionAndLocation()
        guard granted, let from = location.coordinate else {
            statusText = "Без геолокации маршрут недоступен"
            return
        }

        let fromLoc = CLLocation(latitude: from.latitude, longitude: from.longitude)
        let clubLoc = CLLocation(latitude: clubCoordinate.latitude, longitude: clubCoordinate.longitude)
        let km = fromLoc.distance(from: clubLoc) / 1000
        if km > ClubMapCoords.maxRouteKm {
            statusText = "Геолокация далеко от клуба (~\(Int(km)) км)."
            return
        }

        statusText = "Строим маршрут…"
        let request = MKDirections.Request()
        request.source = MKMapItem(placemark: MKPlacemark(coordinate: from))
        request.destination = MKMapItem(placemark: MKPlacemark(coordinate: clubCoordinate))
        request.transportType = .automobile

        do {
            let response = try await MKDirections(request: request).calculate()
            guard let first = response.routes.first else {
                statusText = "Маршрут не найден"
                return
            }
            route = first
            let minutes = max(1, Int(first.expectedTravelTime / 60))
            let distKm = first.distance / 1000
            statusText = String(format: "Маршрут: ~%.1f км, ~%d мин", distKm, minutes)
            cameraPosition = .region(first.polyline.boundingMapRect.fcCoordinateRegion(paddedBy: 1.35))
        } catch {
            statusText = error.localizedDescription
        }
    }
}

// MARK: - Coords / external maps

enum ClubMapCoords {
    static let deFriesLat = 43.313906
    static let deFriesLon = 131.999418
    static let sedankaLat = 43.212592
    static let sedankaLon = 131.95021
    static let moscowLat = 55.7558
    static let moscowLon = 37.6173
    static let maxRouteKm = 250.0

    static func resolve(latitude: Double, longitude: Double, address: String) -> (lat: Double, lon: Double) {
        let a = address.lowercased()
        let looksSedanka = a.contains("седанка") || a.contains("полетаева")
        let looksDeFriz =
            a.contains("де фриз")
            || a.contains("де-фриз")
            || a.contains("купера")
            || a.contains("надеждин")
        let looksVladivostok = a.contains("владивосток") || looksSedanka || looksDeFriz
        let isMoscowPlaceholder =
            abs(latitude - moscowLat) < 0.05 && abs(longitude - moscowLon) < 0.05
        let hasRealCoords =
            (abs(latitude) > 0.01 || abs(longitude) > 0.01) && !isMoscowPlaceholder

        if looksSedanka {
            return (sedankaLat, sedankaLon)
        }
        if looksDeFriz {
            return (deFriesLat, deFriesLon)
        }
        if looksVladivostok && (!hasRealCoords || longitude < 100) {
            return (deFriesLat, deFriesLon)
        }
        if hasRealCoords { return (latitude, longitude) }
        return (moscowLat, moscowLon)
    }
}

enum ClubMapExternal {
    static func openYandex(lat: Double, lon: Double, address: String) {
        let deep = URL(string: "yandexmaps://maps.yandex.ru/?rtext=~\(lat),\(lon)&rtt=auto")
        let web = URL(string: "https://yandex.ru/maps/?rtext=~\(lat),\(lon)&rtt=auto")
        let search = address.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)
            .flatMap { URL(string: "https://yandex.ru/maps/?text=\($0)") }
        openFirstAvailable([deep, web, search].compactMap { $0 })
    }

    static func openDgis(lat: Double, lon: Double, address: String) {
        let deep = URL(string: "dgis://2gis.ru/routeSearch/to/\(lon),\(lat)/go")
        let web = URL(string: "https://2gis.ru/routeSearch/to/\(lon),\(lat)/go")
        let search = address.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)
            .flatMap { URL(string: "https://2gis.ru/search/\($0)") }
        openFirstAvailable([deep, web, search].compactMap { $0 })
    }

    private static func openFirstAvailable(_ urls: [URL]) {
        for url in urls {
            if UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
                return
            }
        }
        if let last = urls.last {
            UIApplication.shared.open(last)
        }
    }
}

@MainActor
final class ClubMapLocationHelper: NSObject, ObservableObject, CLLocationManagerDelegate {
    @Published var coordinate: CLLocationCoordinate2D?
    private let manager = CLLocationManager()
    private var continuation: CheckedContinuation<Bool, Never>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    func requestPermissionAndLocation() async -> Bool {
        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            manager.requestLocation()
            return await withCheckedContinuation { cont in
                continuation = cont
            }
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
            return await withCheckedContinuation { cont in
                continuation = cont
            }
        default:
            return false
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        Task { @MainActor in
            switch manager.authorizationStatus {
            case .authorizedAlways, .authorizedWhenInUse:
                manager.requestLocation()
            case .denied, .restricted:
                finish(false)
            default:
                break
            }
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        Task { @MainActor in
            coordinate = locations.last?.coordinate
            finish(coordinate != nil)
        }
    }

    nonisolated func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        Task { @MainActor in finish(false) }
    }

    private func finish(_ ok: Bool) {
        continuation?.resume(returning: ok)
        continuation = nil
    }
}

private extension MKMapRect {
    func fcCoordinateRegion(paddedBy factor: Double) -> MKCoordinateRegion {
        var rect = self
        let w = rect.size.width * (factor - 1) / 2
        let h = rect.size.height * (factor - 1) / 2
        rect = rect.insetBy(dx: -w, dy: -h)
        return MKCoordinateRegion(rect)
    }
}
