import Foundation
import ImageIO
import UIKit
import UniformTypeIdentifiers

enum ImageFile {
    static func fileExtension(at url: URL) throws -> String {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, [kCGImageSourceShouldCache: false] as CFDictionary),
              let identifier = CGImageSourceGetType(source),
              let type = UTType(identifier as String), type.conforms(to: .image),
              let ext = type.preferredFilenameExtension,
              ["jpg", "jpeg", "png", "webp", "gif", "avif", "heic", "heif"].contains(ext.lowercased()) else {
            throw DownloadFailure.message("图片格式暂不受 iOS 支持，文件未保存。")
        }
        return ext.lowercased()
    }

    static func thumbnail(at url: URL, maximumPixelSize: Int) throws -> UIImage {
        guard let source = CGImageSourceCreateWithURL(url as CFURL, [kCGImageSourceShouldCache: false] as CFDictionary),
              CGImageSourceGetCount(source) > 0 else {
            throw DownloadFailure.message("图片文件无效，文件未保存。")
        }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maximumPixelSize,
            kCGImageSourceShouldCacheImmediately: true
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            throw DownloadFailure.message("图片格式暂不受 iOS 支持，文件未保存。")
        }
        return UIImage(cgImage: image)
    }
}
