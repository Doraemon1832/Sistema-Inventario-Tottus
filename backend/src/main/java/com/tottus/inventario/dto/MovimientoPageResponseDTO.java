package com.tottus.inventario.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class MovimientoPageResponseDTO {
    List<MovimientoResponseDTO> content;
    int page;
    int size;
    long totalElements;
    int totalPages;
}
