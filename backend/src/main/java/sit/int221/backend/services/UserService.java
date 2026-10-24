package sit.int221.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sit.int221.backend.dtos.UserRegisterDto;
import sit.int221.backend.dtos.UserResponseDto;
import sit.int221.backend.entities.SellerProfile;
import sit.int221.backend.entities.User;
import sit.int221.backend.repositories.SellerProfileRepository;
import sit.int221.backend.repositories.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final SellerProfileRepository sellerProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService; // <-- สำคัญ!

    @Transactional
    public UserResponseDto registerUser(UserRegisterDto dto) {

        // unique email
        if (userRepository.existsByEmailIgnoreCase(dto.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already exists");
        }
        // ถ้าต้อง unique nickname ให้เปิดใช้บรรทัดนี้ (ต้องมีเมธอดใน repo ด้วย)
        // if (userRepository.existsByNickNameIgnoreCase(dto.getNickName())) {
        //     throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nickname already exists");
        // }

        String type = Optional.ofNullable(dto.getUserType()).orElse("BUYER").toUpperCase();

        // สร้าง user
        User user = new User();
        user.setNickName(dto.getNickName());
        user.setEmail(dto.getEmail());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setFullName(dto.getFullName());
        user.setUserType(type);
        user.setPhoneNumber("SELLER".equals(type) ? dto.getPhoneNumber() : null);
        user.setIsActive(false); // รอ verify email
        user.setCreatedOn(LocalDateTime.now());
        user.setUpdatedOn(LocalDateTime.now());
        userRepository.save(user);

        // ถ้าเป็นผู้ขาย บันทึกโปรไฟล์
        if ("SELLER".equals(type)) {
            if (dto.getIdCardImageFront() == null || dto.getIdCardImageFront().isEmpty()
                    || dto.getIdCardImageBack() == null || dto.getIdCardImageBack().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seller must upload ID card images");
            }

            SellerProfile sp = new SellerProfile();
            sp.setUser(user);
            sp.setBankAccount(dto.getBankAccount());
            sp.setBankName(dto.getBankName());
            sp.setIdCardNumber(dto.getIdCardNumber());

            // NOTE: ควรอัปโหลดไฟล์จริง แล้วเก็บเป็น path/URL ที่ได้กลับมา
            sp.setIdCardImageFront("uploads/" + dto.getIdCardImageFront().getOriginalFilename());
            sp.setIdCardImageBack("uploads/" + dto.getIdCardImageBack().getOriginalFilename());

            sellerProfileRepository.save(sp);
        }

        // 🔔 ส่งอีเมลยืนยัน (สร้าง token แล้วส่ง)
        try {
            emailVerificationService.createAndSendToken(user);
        } catch (Exception e) {
            // ไม่อยาก fail การสมัครทั้งหมด ให้ log ไว้พอ
            System.out.println("[EMAIL] send verification failed for " + user.getEmail() + " : " + e.getMessage());
        }

        // response
        UserResponseDto res = new UserResponseDto();
        res.setId(user.getId());
        res.setNickName(user.getNickName());
        res.setEmail(user.getEmail());
        res.setFullName(user.getFullName());
        res.setPhoneNumber(user.getPhoneNumber());
        res.setIsActive(user.getIsActive());
        res.setUserType(user.getUserType());
        return res;
    }
}
