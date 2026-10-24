package sit.int221.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import sit.int221.backend.entities.User;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {
    // ✅ แนะนำให้เช็คแบบไม่สนตัวพิมพ์
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByNickNameIgnoreCase(String nickName);

    // ✅ สำหรับใช้ตอน login โหลดผู้ใช้จากอีเมล
    Optional<User> findByEmailIgnoreCase(String email);
}
