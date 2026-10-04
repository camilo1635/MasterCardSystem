package com.mastercard.system.accounting;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    @Query("select distinct e from JournalEntry e left join fetch e.lines where e.date between :from and :to order by e.date desc, e.id desc")
    List<JournalEntry> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** El asiento original de un origen (FACTURA, COMPRA...) es el primero creado. */
    @Query("select distinct e from JournalEntry e left join fetch e.lines where e.source = :source and e.sourceId = :sourceId")
    List<JournalEntry> findBySource(@Param("source") String source, @Param("sourceId") Long sourceId);

    /** Totales por cuenta: [code, name, type, sumDebit, sumCredit]. */
    @Query("""
            select a.code, a.name, a.type, coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0)
            from JournalLine l join l.account a join l.entry e
            where e.date between :from and :to
            group by a.code, a.name, a.type
            order by a.code""")
    List<Object[]> totalsByAccount(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
