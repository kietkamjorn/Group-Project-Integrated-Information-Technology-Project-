package sit.int221.backend.dtos;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class UserRegisterDto {
    @NotBlank
    @Size(max = 50)
    private String nickName;

    @NotBlank
    @Email
    @Size(max = 100)
    private String email;

    @NotBlank
    @Size(min = 8)
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&/_])[A-Za-z\\d@$!%*?&/_]{8,}$",
            message = "Password must contain uppercase, lowercase, number and special char"
    )
    private String password;

    @NotBlank
    @Size(min = 4, max = 40)
    private String fullName;

    // 🟠 เฉพาะ Seller
    private String phoneNumber;
    private String bankAccount;
    private String bankName;
    private String idCardNumber;

    private MultipartFile idCardImageFront;
    private MultipartFile idCardImageBack;

    @NotBlank
    private String userType; // BUYER หรือ SELLER
}
