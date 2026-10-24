package sit.int221.backend.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SellerProfileDto {
    private String nickName;
    private String fullName;
    private String email;
    private String bankAccount;     // mask ไว้
    private String bankName;
    private String idCardNumber;    // mask ไว้
    private String idCardImageFront; // return “Provided”
    private String idCardImageBack;  // return “Provided”
}

