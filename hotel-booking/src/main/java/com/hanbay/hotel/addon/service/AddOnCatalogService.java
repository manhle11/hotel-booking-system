package com.hanbay.hotel.addon.service;

import com.hanbay.hotel.addon.dto.response.AddOnCatalogResponse;
import com.hanbay.hotel.addon.mapper.AddOnMapper;
import com.hanbay.hotel.addon.repository.AddOnServiceRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddOnCatalogService {

    private final AddOnServiceRepository addOnServiceRepository;
    private final AddOnMapper addOnMapper;

    @Transactional(readOnly = true)
    public List<AddOnCatalogResponse> getActiveAddOns() {
        return addOnServiceRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .map(addOnMapper::toCatalogResponse)
                .toList();
    }
}
