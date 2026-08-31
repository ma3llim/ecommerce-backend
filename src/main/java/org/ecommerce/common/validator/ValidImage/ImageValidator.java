package org.ecommerce.common.validator.ValidImage;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Set;

public class ImageValidator implements ConstraintValidator<ValidImage, MultipartFile> {
    private static final long MAX_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png");
    private final Tika tika = new Tika();

    @Override
    public boolean isValid(MultipartFile image, ConstraintValidatorContext constraintValidatorContext) {
        if (image == null || image.isEmpty()) return false;
        if (image.getSize() > MAX_SIZE) return false;

        try {
            String detectedType = tika.detect(image.getInputStream());

            if (!ALLOWED_TYPES.contains(detectedType)) return false;

            BufferedImage bufferedImage = ImageIO.read(image.getInputStream());

            return bufferedImage != null;
        } catch (IOException e) {
            return false;
        }
    }
}
