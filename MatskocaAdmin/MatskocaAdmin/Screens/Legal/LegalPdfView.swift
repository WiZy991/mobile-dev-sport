import SwiftUI
import PDFKit

struct LegalPdfView: View {
    let doc: StaffLegalPdf
    var onBack: (() -> Void)? = nil

    var body: some View {
        Group {
            if let url = doc.bundleURL {
                PDFKitRepresentedView(url: url)
            } else {
                StaffEmptyState(message: "Документ не найден в приложении")
                    .padding()
            }
        }
        .navigationTitle(doc.title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if let onBack {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Назад", action: onBack)
                }
            }
        }
        .staffToolbarStyle()
    }
}

private struct PDFKitRepresentedView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        view.document = PDFDocument(url: url)
        return view
    }

    func updateUIView(_ uiView: PDFView, context: Context) {}
}
