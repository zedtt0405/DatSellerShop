package com.DatShop.service;

import com.DatShop.dto.catalog.SizeCreateRequest;
import com.DatShop.dto.catalog.SizeResponse;
import com.DatShop.dto.catalog.SizeUpdateRequest;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.DuplicateResourceException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.CatalogMapper;
import com.DatShop.model.Size;
import com.DatShop.repository.SizeRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SizeService {

    private final SizeRepository sizeRepository;
    private final RequestValidator requestValidator;

    public SizeService(SizeRepository sizeRepository, RequestValidator requestValidator) {
        this.sizeRepository = sizeRepository;
        this.requestValidator = requestValidator;
    }

    @Transactional
    public SizeResponse create(SizeCreateRequest request) {
        SizeCreateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        assertDisplayOrder(normalized.displayOrder());
        if (sizeRepository.existsByName(normalized.name())) {
            throw new DuplicateResourceException("Size name already exists");
        }
        Size size = new Size();
        apply(size, normalized.name(), normalized.displayOrder(), normalized.active());
        return CatalogMapper.toSizeResponse(sizeRepository.save(size));
    }

    @Transactional
    public SizeResponse update(Long id, SizeUpdateRequest request) {
        SizeUpdateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        assertDisplayOrder(normalized.displayOrder());
        Size size = sizeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Size not found"));
        if (sizeRepository.existsByNameAndIdNot(normalized.name(), id)) {
            throw new DuplicateResourceException("Size name already exists");
        }
        apply(size, normalized.name(), normalized.displayOrder(), normalized.active());
        return CatalogMapper.toSizeResponse(size);
    }

    public List<SizeResponse> listActive() {
        return sizeRepository.findByActiveTrueOrderByDisplayOrderAscNameAsc().stream()
                .map(CatalogMapper::toSizeResponse)
                .toList();
    }

    public List<SizeResponse> listAll() {
        return sizeRepository.findAllByOrderByDisplayOrderAscNameAsc().stream()
                .map(CatalogMapper::toSizeResponse)
                .toList();
    }

    private static void assertDisplayOrder(Integer displayOrder) {
        if (displayOrder != null && displayOrder < 0) {
            throw new BusinessRuleException("displayOrder must not be negative");
        }
    }

    private static void apply(Size size, String name, Integer displayOrder, Boolean active) {
        size.setName(name);
        size.setDisplayOrder(displayOrder);
        size.setActive(active);
    }

    private static SizeCreateRequest normalize(SizeCreateRequest request) {
        if (request == null) {
            return null;
        }
        return new SizeCreateRequest(Text.trim(request.name()), request.displayOrder(), request.active());
    }

    private static SizeUpdateRequest normalize(SizeUpdateRequest request) {
        if (request == null) {
            return null;
        }
        return new SizeUpdateRequest(Text.trim(request.name()), request.displayOrder(), request.active());
    }
}
