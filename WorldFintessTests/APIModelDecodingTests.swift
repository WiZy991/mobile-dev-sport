import XCTest
@testable import WorldFintess

/// Юнит-тесты разбора JSON (контракт как у Gson + `FitnessApi` на Android).
/// В Xcode: File → New → Target → Unit Testing Bundle, добавьте этот файл в тестовый таргет и укажите Host Application = WorldFintess.
final class APIModelDecodingTests: XCTestCase {

    func testDecodeAuthResponse() throws {
        let json = Data(
            """
            {"token":"tok","refresh_token":"ref","user":{"id":"user-1","email":"a@b.ru","phone":"+7","name":"Иван","bonus_points":10}}
            """.utf8
        )
        let r = try AppJSON.decoder().decode(AuthResponse.self, from: json)
        XCTAssertEqual(r.token, "tok")
        XCTAssertEqual(r.user.email, "a@b.ru")
        XCTAssertEqual(r.user.bonusPoints, 10)
    }

    func testDecodeTrainingWithDoubleDuration() throws {
        let json = Data(
            """
            {"id":"training-1","name":"Йога","description":null,"type":"group",
            "trainer":{"id":"trainer-1","name":"Мария","photo_url":null,"specialization":"Йога","rating":4.8},
            "start_time":"2026-04-10T09:00:00","end_time":"2026-04-10T10:00:00",
            "duration_minutes":60.0,"room":"Зал","max_participants":15,"current_participants":5,
            "is_booked":false,"intensity":"low","image_url":null}
            """.utf8
        )
        let t = try AppJSON.decoder().decode(Training.self, from: json)
        XCTAssertEqual(t.durationMinutes, 60)
        XCTAssertEqual(t.type, .group)
    }

    func testDecodeBookingStatusWaiting() throws {
        let json = Data(
            """
            {"id":"booking-1","status":"waiting","booked_at":"2026-04-10T10:00:00",
            "training":{"id":"training-1","name":"Силовая","description":"","type":"group",
            "trainer":{"id":"t1","name":"Пётр","photo_url":null,"specialization":null,"rating":5},
            "start_time":"2026-04-11T11:00:00","end_time":"2026-04-11T12:00:00",
            "duration_minutes":60,"room":"Зал","max_participants":10,"current_participants":10,
            "is_booked":true,"intensity":null,"image_url":null}}
            """.utf8
        )
        let b = try AppJSON.decoder().decode(Booking.self, from: json)
        XCTAssertTrue(b.isUpcomingList)
        XCTAssertEqual(b.status, "waiting")
    }
}
