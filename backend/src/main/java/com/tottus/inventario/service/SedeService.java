package com.tottus.inventario.service;

import com.tottus.inventario.model.Sede;
import com.tottus.inventario.repository.SedeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SedeService {

    private final SedeRepository sedeRepository;

    @Transactional(readOnly = true)
    public List<Sede> listarSedes() {
        return sedeRepository.findByEstadoTrue();
    }
}
