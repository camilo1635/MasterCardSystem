package com.mastercard.system.accounting;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByCode(String code);

    List<Account> findAllByOrderByCode();

    List<Account> findByCodeIn(java.util.Collection<String> codes);
}
