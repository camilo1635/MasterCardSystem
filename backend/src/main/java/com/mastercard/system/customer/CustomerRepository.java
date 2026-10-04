package com.mastercard.system.customer;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.id = :id")
    Optional<Customer> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select c from Customer c
            where lower(c.name) like lower(concat('%', :q, '%')) or lower(c.document) like lower(concat('%', :q, '%'))
            order by c.name""")
    List<Customer> search(@Param("q") String q);
}
