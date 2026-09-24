package com.yufesta.common.storage;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Component;

/**
 * 업로드 이미지를 카드용(긴 변 1600px)과 썸네일(400px) JPEG로 만든다(FR-PH-05).
 * EXIF 회전을 반영하고, 원본이 작으면 키우지 않는다. 형식은 jpeg·png만(ImageIO가 webp를 못 읽는다)
 */
@Component
public class ImageProcessor {

    public static final int LARGE_EDGE = 1600;
    public static final int THUMBNAIL_EDGE = 400;
    private static final Set<String> SUPPORTED_TYPES = Set.of("image/jpeg", "image/png");
    private static final float JPEG_QUALITY = 0.85f;

    /**
     * @throws CustomException IMAGE_UNSUPPORTED_TYPE(jpeg·png 아님), IMAGE_INVALID(깨진 파일)
     */
    public ProcessedImage process(byte[] original, String contentType) {
        if (contentType == null || !SUPPORTED_TYPES.contains(contentType.toLowerCase())) {
            throw new CustomException(ErrorCode.IMAGE_UNSUPPORTED_TYPE);
        }
        try {
            // 먼저 EXIF 회전만 적용한 원본을 만든다. 이후 리사이즈는 이 이미지 기준
            BufferedImage upright = Thumbnails.of(new ByteArrayInputStream(original))
                    .scale(1.0)
                    .useExifOrientation(true)
                    .asBufferedImage();
            return new ProcessedImage(resizeToFit(upright, LARGE_EDGE), resizeToFit(upright, THUMBNAIL_EDGE));
        } catch (IOException | IllegalArgumentException exception) {
            // ImageIO는 못 읽는 파일에 IOException 또는 "No suitable ImageReader"(IllegalArgumentException)를 던진다
            throw new CustomException(ErrorCode.IMAGE_INVALID);
        }
    }

    // 긴 변이 edge를 넘으면 비율을 지켜 줄이고, 아니면 그대로 JPEG로만 바꾼다(확대 금지)
    private static byte[] resizeToFit(BufferedImage source, int edge) throws IOException {
        int longest = Math.max(source.getWidth(), source.getHeight());
        double scale = longest > edge ? (double) edge / longest : 1.0;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thumbnails.of(source)
                .scale(scale)
                .imageType(BufferedImage.TYPE_INT_RGB) // PNG 투명 배경을 JPEG로 바꿀 때 알파 채널 제거
                .outputFormat("jpg")
                .outputQuality(JPEG_QUALITY)
                .toOutputStream(out);
        return out.toByteArray();
    }
}
