package com.platform.integration;

import com.platform.entity.Business;
import com.platform.entity.Employee;
import com.platform.entity.EmployeeLocationServicePrice;
import com.platform.entity.Location;
import com.platform.entity.ProvidedService;
import com.platform.entity.User;
import com.platform.repository.EmployeeLocationServicePriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;

/**
 * Covers {@link EmployeeLocationServicePriceRepository#findByEmployeeIdAndLocationId}
 * - the composite lookup {@code EmployeeLocationServicePriceService} uses to price a
 * booking for a given employee at a given location - plus the DB-level unique
 * constraint on (employee_id, service_id, location_id) that the three-way join
 * table depends on to keep pricing well-defined.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class EmployeeLocationServicePriceRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EmployeeLocationServicePriceRepository priceRepository;

    private Employee employee;
    private Location locationA;
    private Location locationB;
    private ProvidedService haircut;
    private ProvidedService shave;

    @BeforeEach
    void setUp() {
        User owner = entityManager.persistFlushFind(
                TestFixtures.userBuilder("owner-" + UUID.randomUUID() + "@example.com").build());
        Business business = entityManager.persistFlushFind(
                TestFixtures.businessBuilder(owner, "business-" + UUID.randomUUID()).build());
        employee = entityManager.persistFlushFind(TestFixtures.employeeBuilder(business, "Alex").build());
        locationA = entityManager.persistFlushFind(TestFixtures.locationBuilder(business).build());
        locationB = entityManager.persistFlushFind(
                TestFixtures.locationBuilder(business).isDefaultLocation(false).name("Second location").build());
        haircut = entityManager.persistFlushFind(TestFixtures.serviceBuilder(business, "Haircut").build());
        shave = entityManager.persistFlushFind(TestFixtures.serviceBuilder(business, "Shave").build());
    }

    @Test
    void findByEmployeeIdAndLocationId_returnsOnlyPricesAtThatLocation() {
        EmployeeLocationServicePrice haircutAtA = entityManager.persistFlushFind(
                TestFixtures.priceEntryBuilder(employee, haircut, locationA).build());
        entityManager.persistFlushFind(
                TestFixtures.priceEntryBuilder(employee, shave, locationB).price(new BigDecimal("50.00")).build());

        List<EmployeeLocationServicePrice> atLocationA =
                priceRepository.findByEmployeeIdAndLocationId(employee.getId(), locationA.getId());

        assertEquals(List.of(haircutAtA.getId()),
                atLocationA.stream().map(EmployeeLocationServicePrice::getId).toList());
    }

    @Test
    void findByEmployeeIdAndLocationId_returnsEmptyWhenNoPriceExistsAtThatLocation() {
        entityManager.persistFlushFind(TestFixtures.priceEntryBuilder(employee, haircut, locationA).build());

        assertTrue(priceRepository.findByEmployeeIdAndLocationId(employee.getId(), locationB.getId()).isEmpty());
    }

    @Test
    void uniqueConstraintOnEmployeeServiceLocation_rejectsADuplicateTriple() {
        entityManager.persistFlushFind(TestFixtures.priceEntryBuilder(employee, haircut, locationA).build());

        EmployeeLocationServicePrice duplicate =
                TestFixtures.priceEntryBuilder(employee, haircut, locationA)
                        .price(new BigDecimal("999.99"))
                        .build();

        // Go through the repository, not entityManager directly: Spring's exception
        // translation (raw Hibernate ConstraintViolationException ->
        // DataIntegrityViolationException, the one GlobalExceptionHandler maps to 409)
        // is AOP applied to @Repository beans, not to a bare EntityManager.flush().
        assertThrows(DataIntegrityViolationException.class,
                () -> priceRepository.saveAndFlush(duplicate));
    }
}
