package sit.int221.backend.config;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableAsync
public class AppConfig {

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    // ✅ PasswordEncoder (ใช้ Argon2 ตามที่เซ็ตไว้)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    // ✅ Global CORS config (ปรับปรุงให้รองรับ IP ปัจจุบันและ Localhost)
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedOrigins(
                                "http://localhost:5173",
                                "http://168.110.9.184"
                        )
                        .allowedMethods("*")
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }

    // ✅ Map /media/** -> file://<baseUploadDir>/
    @Bean
    public WebMvcConfigurer staticResourceConfigurer(
            @Value("${app.upload.base-dir:${APP_UPLOAD_BASE_DIR:/data/uploads}}") String baseUploadDir,
            @Value("${app.upload.public-base-url:${APP_UPLOAD_PUBLIC_BASE_URL:/media}}") String publicBaseUrl
    ) {
        return new WebMvcConfigurer() {
            @Override
            public void addResourceHandlers(ResourceHandlerRegistry registry) {
                String pattern = "/media/**";

                String location = java.nio.file.Path.of(baseUploadDir).toUri().toString();
                if (!location.endsWith("/")) location += "/";

                registry.addResourceHandler(pattern)
                        .addResourceLocations(location)
                        .setCachePeriod(0);
            }
        };
    }
}
