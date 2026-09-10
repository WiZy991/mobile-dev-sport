import SwiftUI
import UIKit

/// Рендер QR внутри secure-контейнера UITextField (блокирует скриншот QR без ломания SwiftUI sheets).
struct SecureQRImageView: UIViewRepresentable {
    let image: UIImage

    func makeUIView(context: Context) -> UIView {
        let view = SecureQRHostView()
        view.setImage(image)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        (uiView as? SecureQRHostView)?.setImage(image)
    }
}

fileprivate final class SecureQRHostView: UIView {
    private let secureField = UITextField()
    private let imageView = UIImageView()
    private weak var secureHost: UIView?

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        clipsToBounds = true
        isUserInteractionEnabled = false

        secureField.isSecureTextEntry = true
        secureField.isUserInteractionEnabled = false
        addSubview(secureField)

        imageView.contentMode = .scaleAspectFit
        imageView.clipsToBounds = true
        imageView.isUserInteractionEnabled = false

        let host = secureField.subviews.first ?? secureField
        host.isUserInteractionEnabled = false
        secureHost = host
        addSubview(host)
        host.addSubview(imageView)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { nil }

    func setImage(_ image: UIImage) {
        imageView.image = image
        setNeedsLayout()
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        let bounds = bounds
        secureField.frame = bounds
        secureHost?.frame = bounds
        imageView.frame = secureHost?.bounds ?? bounds
    }
}

extension View {
    /// Полноэкранный layer-trick ломает SwiftUI `.sheet` — используйте `SecureQRImageView` для QR.
    func secureScreen() -> some View {
        self
    }
}
