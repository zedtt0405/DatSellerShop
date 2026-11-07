package com.DatShop.service;

import com.DatShop.dto.catalog.BrandCreateRequest;
import com.DatShop.dto.catalog.BrandResponse;
import com.DatShop.dto.catalog.BrandUpdateRequest;
import com.DatShop.exception.DuplicateResourceException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.CatalogMapper;
import com.DatShop.model.Brand;
import com.DatShop.model.CatalogStatus;
import com.DatShop.repository.BrandRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BrandService {

    private final BrandRepository brandRepository;
    private final RequestValidator requestValidator;

    public BrandService(BrandRepository brandRepository, RequestValidator requestValidator) {
        this.brandRepository = brandRepository;
        this.requestValidator = requestValidator;
    }

    @Transactional
    public BrandResponse create(BrandCreateRequest request) {
        BrandCreateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        assertUnique(normalized.name(), normalized.slug(), null);
        Brand brand = new Brand();
        apply(brand, normalized.name(), normalized.slug(), normalized.description(), normalized.logoUrl(), normalized.status());
        return CatalogMapper.toBrandResponse(brandRepository.save(brand));
    }

    @Transactional
    public BrandResponse update(Long id, BrandUpdateRequest request) {
        BrandUpdateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found"));
        assertUnique(normalized.name(), normalized.slug(), id);
        apply(brand, normalized.name(), normalized.slug(), normalized.description(), normalized.logoUrl(), normalized.status());
        return CatalogMapper.toBrandResponse(brand);
    }

    public BrandResponse getById(Long id) {
        return CatalogMapper.toBrandResponse(brandRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Brand not found")));
    }

    public List<BrandResponse> listActive() {
        return brandRepository.findByStatusOrderByNameAsc(CatalogStatus.ACTIVE).stream()
                .map(CatalogMapper::toBrandResponse)
                .toList();
    }

    public List<BrandResponse> listAll() {
        return brandRepository.findAllByOrderByNameAsc().stream()
                .map(CatalogMapper::toBrandResponse)
                .toList();
    }

    private void assertUnique(String name, String slug, Long excludeId) {
        boolean nameTaken = excludeId == null
                ? brandRepository.existsByName(name)
                : brandRepository.existsByNameAndIdNot(name, excludeId);
        if (nameTaken) {
            throw new DuplicateResourceException("Brand name already exists");
        }
        boolean slugTaken = excludeId == null
                ? brandRepository.existsBySlug(slug)
                : brandRepository.existsBySlugAndIdNot(slug, excludeId);
        if (slugTaken) {
            throw new DuplicateResourceException("Brand slug already exists");
        }
    }

    private static void apply(
            Brand brand, String name, String slug, String description, String logoUrl, CatalogStatus status) {
        brand.setName(name);
        brand.setSlug(slug);
        brand.setDescription(description);
        brand.setLogoUrl(logoUrl);
        brand.setStatus(status);
    }

    private static BrandCreateRequest normalize(BrandCreateRequest request) {
        if (request == null) {
            return null;
        }
        return new BrandCreateRequest(
                Text.trim(request.name()),
                Text.trim(request.slug()),
                Text.emptyToNull(request.description()),
                Text.emptyToNull(request.logoUrl()),
                request.status());
    }

    private static BrandUpdateRequest normalize(BrandUpdateRequest request) {
        if (request == null) {
            return null;
        }
        return new BrandUpdateRequest(
                Text.trim(request.name()),
                Text.trim(request.slug()),
                Text.emptyToNull(request.description()),
                Text.emptyToNull(request.logoUrl()),
                request.status());
    }
}
