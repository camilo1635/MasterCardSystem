package com.mastercard.system.accounting;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);
}
