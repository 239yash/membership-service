package com.work.membership_service.service.user;

import com.work.membership_service.exception.NotFoundException;
import com.work.membership_service.exception.ValidationException;
import com.work.membership_service.model.entity.UserAccount;
import com.work.membership_service.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserAccountRepository userRepository;

    @Transactional
    public UserAccount create(String name, String email, List<String> cohorts) {
        // surface duplicate email cleanly
        userRepository.findByEmail(email).ifPresent(u -> {
            throw new ValidationException("email already in use: " + email);
        });

        String[] cohortArray = cohorts == null
                ? new String[0]
                : cohorts.toArray(new String[0]);

        UserAccount user = UserAccount.builder()
                .name(name)
                .email(email)
                .cohorts(cohortArray)
                .createdAt(Instant.now())
                .build();
        try {
            UserAccount saved = userRepository.save(user);
            log.info("[user_flow] created user id: {}, email: {}", saved.getId(), email);
            return saved;
        } catch (DataIntegrityViolationException e) {
            // race on the unique email index
            throw new ValidationException("email already in use: " + email);
        }
    }

    public UserAccount getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("user not found id: " + id));
    }
}
