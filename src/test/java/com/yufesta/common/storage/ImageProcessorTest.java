package com.yufesta.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yufesta.common.exception.CustomException;
import com.yufesta.common.exception.error.ErrorCode;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * 리사이즈 규칙(긴 변 1600·400, 확대 금지, JPEG 변환)과 형식 검증을 고정
 */
class ImageProcessorTest {

    private final ImageProcessor processor = new ImageProcessor();

    @Test
    void 큰_이미지는_긴_변_1600과_400으로_비율을_지켜_줄인다() throws IOException {
        ProcessedImage result = processor.process(png(3000, 2000), "image/png");

        BufferedImage large = decode(result.large());
        BufferedImage thumb = decode(result.thumbnail());
        assertThat(large.getWidth()).isEqualTo(1600);
        assertThat(large.getHeight()).isBetween(1066, 1067);
        assertThat(thumb.getWidth()).isEqualTo(400);
        assertThat(thumb.getHeight()).isBetween(266, 267);
    }

    @Test
    void 세로_이미지는_높이가_긴_변이다() throws IOException {
        BufferedImage large = decode(processor.process(png(1000, 2400), "image/png").large());

        assertThat(large.getHeight()).isEqualTo(1600);
        assertThat(large.getWidth()).isBetween(666, 667);
    }

    @Test
    void 작은_이미지는_키우지_않고_JPEG로만_바꾼다() throws IOException {
        ProcessedImage result = processor.process(png(800, 600), "image/png");

        BufferedImage large = decode(result.large());
        assertThat(large.getWidth()).isEqualTo(800);
        assertThat(large.getHeight()).isEqualTo(600);
        assertThat(decode(result.thumbnail()).getWidth()).isEqualTo(400);
        // JPEG 매직 넘버 FF D8
        assertThat(result.large()[0] & 0xFF).isEqualTo(0xFF);
        assertThat(result.large()[1] & 0xFF).isEqualTo(0xD8);
    }

    @Test
    void jpeg_png가_아니면_IMAGE_UNSUPPORTED_TYPE이고_깨진_파일이면_IMAGE_INVALID다() throws IOException {
        byte[] image = png(10, 10);

        assertThatThrownBy(() -> processor.process(image, "image/gif"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_UNSUPPORTED_TYPE);
        assertThatThrownBy(() -> processor.process(new byte[] {1, 2, 3, 4}, "image/png"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.IMAGE_INVALID);
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }
}
