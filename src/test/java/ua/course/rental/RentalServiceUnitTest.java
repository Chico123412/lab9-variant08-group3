package ua.course.rental;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RentalServiceUnitTest {
    private final RentalService service = createService();

    private static RentalService createService() {
        var dataSource = new DriverManagerDataSource();
        var jdbc = new JdbcTemplate(dataSource);
        return new RentalService(
                new EquipmentRepository(jdbc),
                new RentalRepository(jdbc),
                (equipment, quantity, days) -> true
        );
    }

    @Test
    void calculatesTotal() {
        assertEquals(60_000, service.calculateTotal(10_000, 2, 3));
    }

    @Test
    void calculatesDepositForShortRental() {
        assertEquals(9_000, service.calculateDeposit(10_000, 2, 3, false));
    }

    @Test
    void limitsDepositToMaximum() {
        assertEquals(200_000, service.calculateDeposit(100_000, 2, 14, true));
    }

    @Test
    void rejectsInvalidQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> service.calculateTotal(10_000, 0, 3));
    }
}
