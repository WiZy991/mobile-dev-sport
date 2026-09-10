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

    func testDecodeProductionTrainingPayload() throws {
        let json = Data(
            """
            [{"id":"training-2","name":"Тест","description":"Персональное занятие","type":"personal",
            "trainer":{"id":"trainer-2","name":"Степан","photo_url":null,"specialization":"Тест","rating":5,"description":null},
            "start_time":"2026-06-26T12:45:00","end_time":"2026-06-26T13:46:00",
            "duration_minutes":61,"room":"","max_participants":1,"current_participants":0,
            "is_booked":false,"intensity":"medium","image_url":null}]
            """.utf8
        )
        let items = try AppJSON.decoder().decode([Training].self, from: json)
        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items[0].type, .personal)
        XCTAssertEqual(items[0].durationMinutes, 61)
        XCTAssertEqual(items[0].trainer.name, "Степан")
    }

    func testDecodeProductionBookingPayload() throws {
        let json = Data(
            """
            {"id":"booking-2","status":"confirmed","booked_at":"2026-06-26T03:02:00",
            "training":{"id":"training-2","name":"Тест","description":"Персональное занятие","type":"personal",
            "trainer":{"id":"trainer-2","name":"Степан","photo_url":null,"specialization":"Тест","rating":5},
            "start_time":"2026-06-26T12:45:00","end_time":"2026-06-26T13:46:00",
            "duration_minutes":61,"room":null,"max_participants":1,"current_participants":2,
            "is_booked":true,"intensity":null,"image_url":null}}
            """.utf8
        )
        let b = try AppJSON.decoder().decode(Booking.self, from: json)
        XCTAssertEqual(b.status, "confirmed")
        XCTAssertEqual(b.training.room, "")
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

    func testDecodeLegalRequisitesPayload() throws {
        let json = Data(
            """
            {"title":"Реквизиты","fields":[{"label":"ИНН","value":"254009880989"}]}
            """.utf8
        )
        let doc = try AppJSON.decoder().decode(LegalDocumentResponse.self, from: json)
        XCTAssertEqual(doc.title, "Реквизиты")
        XCTAssertEqual(doc.fields?.count, 1)
        XCTAssertEqual(doc.fields?.first?.value, "254009880989")
    }

    /// Контракт CRM / Android `Trainer` + `TrainerServiceOffer` (`photo_url`, `phone`, `description`, `services`/`price_from`).
    func testDecodeTrainerWithServicesAndContacts() throws {
        let json = Data(
            """
            {"id":"42","name":"Анна Иванова","photo_url":"https://example.com/a.jpg",
            "specialization":"Силовой тренинг","rating":4.5,"phone":"+7 (900) 111-22-33",
            "description":"Тренер с опытом","services":[{"name":"Персональная","price_from":2500},{"name":"","price_from":0}]}
            """.utf8
        )
        let t = try AppJSON.decoder().decode(Trainer.self, from: json)
        XCTAssertEqual(t.id, "42")
        XCTAssertEqual(t.photoUrl, "https://example.com/a.jpg")
        XCTAssertEqual(t.phone, "+7 (900) 111-22-33")
        XCTAssertEqual(t.description, "Тренер с опытом")
        XCTAssertEqual(t.rating, 4.5)
        XCTAssertEqual(t.services.count, 2)
        XCTAssertEqual(t.services[0].name, "Персональная")
        XCTAssertEqual(t.services[0].priceFrom, 2500)
        XCTAssertEqual(t.services[1].name, "")
        XCTAssertEqual(t.services[1].priceFrom, 0)
    }

    func testDecodeTrainerDefaultsWhenOptionalFieldsMissing() throws {
        let json = Data(
            """
            {"id":7,"name":"Без полей"}
            """.utf8
        )
        let t = try AppJSON.decoder().decode(Trainer.self, from: json)
        XCTAssertEqual(t.id, "7")
        XCTAssertEqual(t.rating, 0)
        XCTAssertNil(t.phone)
        XCTAssertNil(t.description)
        XCTAssertTrue(t.services.isEmpty)
    }
}
