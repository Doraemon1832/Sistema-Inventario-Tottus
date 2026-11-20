package com.tottus.inventario.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProveedorRequestDTOTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rucPeruanoExigeExactamenteOnceDigitos() {
        assertTrue(validator.validate(request("20123456789")).isEmpty());
        assertFalse(validator.validate(request("2012345678")).isEmpty());
        assertFalse(validator.validate(request("2012345678A")).isEmpty());
        assertFalse(validator.validate(request("201234567890")).isEmpty());
    }

    private ProveedorRequestDTO request(String ruc) {
        return ProveedorRequestDTO.builder().documento(ruc).nombre("Proveedor de prueba").build();
    }
}
