import XCTest
@testable import WorldFintess

final class QRCodeGeneratorTests: XCTestCase {

    func testEncodeTimestampBase62Length() {
        let encoded = QRCodeGenerator.encodeTimestampBase62(ms: 1_779_176_143_000)
        XCTAssertEqual(encoded.count, 7)
        XCTAssertTrue(encoded.allSatisfy { $0.isNumber || $0.isLetter })
    }

    func testWiegandPayloadSevenDigitsFitsWiegand26() {
        let payload = QRCodeGenerator.wiegandEntryPayload(userId: "user-5133", timestampMillis: 1_700_000_000_000)
        XCTAssertEqual(payload.count, 7)
        XCTAssertTrue(payload.allSatisfy(\.isNumber))
        XCTAssertLessThanOrEqual(Int(payload) ?? Int.max, 0xFFFFFF)
        XCTAssertTrue(QRCodeGenerator.usesWiegandNumeric(clubId: "11"))
        XCTAssertFalse(QRCodeGenerator.usesWiegandNumeric(clubId: "2"))
        let clubPayload = QRCodeGenerator.entryPayload(userId: "user-5133", clubId: "11", timestampMillis: 1_700_000_000_000)
        XCTAssertEqual(clubPayload, payload)
    }

    func testEntryPayloadStripsUserPrefixAndUsesCompactTime() {
        let payload = QRCodeGenerator.entryPayload(userId: "user-42", timestampMillis: 1_700_000_000_000)
        XCTAssertTrue(payload.hasPrefix("FITNESSCLUB:ENTRY:42:"))
        let parts = payload.split(separator: ":")
        XCTAssertEqual(parts.count, 4)
        XCTAssertEqual(parts[2], "42")
        XCTAssertEqual(parts[3].count, 7)
        XCTAssertLessThanOrEqual(payload.count, 32)
    }

    func testEntryPayloadWithoutUserPrefix() {
        let payload = QRCodeGenerator.entryPayload(userId: "99", timestampMillis: 1000)
        XCTAssertEqual(payload, "FITNESSCLUB:ENTRY:99:\(QRCodeGenerator.encodeTimestampBase62(ms: 1000))")
    }

    func testRoundTripKnownBase62Segment() {
        // 0 ms → "0000000"
        XCTAssertEqual(QRCodeGenerator.encodeTimestampBase62(ms: 0), "0000000")
        // 61 → last char of alphabet at ones place
        XCTAssertEqual(QRCodeGenerator.encodeTimestampBase62(ms: 61), "00000z")
    }
}
