import Foundation
import HealthKit
import ComposeApp

/// Writes weigh-ins to Apple Health for the shared Kotlin code.
///
/// Each sample carries a sync identifier made from the weigh-in's id, so saving the same
/// weigh-in again replaces its samples instead of duplicating them, and deleting a weigh-in
/// can find them.
final class HealthKitBridge: NSObject, HealthBridge {
    static let shared = HealthKitBridge()

    private let store = HKHealthStore()

    private let types: [HKQuantityType] = [
        HKQuantityType(.bodyMass),
        HKQuantityType(.bodyMassIndex),
        HKQuantityType(.bodyFatPercentage),
        HKQuantityType(.leanBodyMass),
    ]

    func isAvailable() -> Bool {
        HKHealthStore.isHealthDataAvailable()
    }

    func requestAuthorization(completion: @escaping (KotlinBoolean) -> Void) {
        store.requestAuthorization(toShare: Set(types), read: []) { ok, _ in
            DispatchQueue.main.async { completion(KotlinBoolean(bool: ok)) }
        }
    }

    func saveWeighIn(
        id: Int64,
        epochMillis: Int64,
        weightKg: Double,
        bmi: Double,
        bodyFatFraction: Double,
        leanMassKg: Double,
        completion: @escaping (KotlinBoolean) -> Void
    ) {
        let date = Date(timeIntervalSince1970: Double(epochMillis) / 1000)
        let version = Int(Date().timeIntervalSince1970)
        func sample(_ type: HKQuantityTypeIdentifier, _ unit: HKUnit, _ value: Double, _ tag: String) -> HKQuantitySample? {
            guard !value.isNaN else { return nil }
            return HKQuantitySample(
                type: HKQuantityType(type),
                quantity: HKQuantity(unit: unit, doubleValue: value),
                start: date,
                end: date,
                metadata: [
                    HKMetadataKeySyncIdentifier: Self.syncId(id: id, tag: tag),
                    HKMetadataKeySyncVersion: NSNumber(value: version),
                ]
            )
        }
        let samples = [
            sample(.bodyMass, .gramUnit(with: .kilo), weightKg, "weight"),
            sample(.bodyMassIndex, .count(), bmi, "bmi"),
            sample(.bodyFatPercentage, .percent(), bodyFatFraction, "fat"),
            sample(.leanBodyMass, .gramUnit(with: .kilo), leanMassKg, "lean"),
        ].compactMap { $0 }
        // Only save the types the user allowed; an unauthorized type fails the whole batch.
        let allowed = samples.filter { store.authorizationStatus(for: $0.quantityType) == .sharingAuthorized }
        guard !allowed.isEmpty else {
            DispatchQueue.main.async { completion(KotlinBoolean(bool: false)) }
            return
        }
        store.save(allowed) { ok, _ in
            DispatchQueue.main.async { completion(KotlinBoolean(bool: ok)) }
        }
    }

    func deleteWeighIn(id: Int64, completion: @escaping (KotlinBoolean) -> Void) {
        let ids = ["weight", "bmi", "fat", "lean"].map { Self.syncId(id: id, tag: $0) }
        let predicate = HKQuery.predicateForObjects(withMetadataKey: HKMetadataKeySyncIdentifier, allowedValues: ids)
        let group = DispatchGroup()
        var allOk = true
        for type in types where store.authorizationStatus(for: type) == .sharingAuthorized {
            group.enter()
            store.deleteObjects(of: type, predicate: predicate) { ok, _, _ in
                if !ok { allOk = false }
                group.leave()
            }
        }
        group.notify(queue: .main) { completion(KotlinBoolean(bool: allOk)) }
    }

    private static func syncId(id: Int64, tag: String) -> String {
        "tare-\(id)-\(tag)"
    }
}
