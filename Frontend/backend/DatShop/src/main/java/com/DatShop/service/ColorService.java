package com.DatShop.service;

import com.DatShop.dto.catalog.ColorCreateRequest;
import com.DatShop.dto.catalog.ColorResponse;
import com.DatShop.dto.catalog.ColorUpdateRequest;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.DuplicateResourceException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.CatalogMapper;
import com.DatShop.model.Color;
import com.DatShop.repository.ColorRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ColorService {

    private static final String HEX = "^#[0-9A-F]{6}$";

    private final ColorRepository colorRepository;
    private final RequestValidator requestValidator;

    public ColorService(ColorRepository colorRepository, RequestValidator requestValidator) {
        this.colorRepository = colorRepository;
        this.requestValidator = requestValidator;
    }

    @Transactional
    public ColorResponse create(ColorCreateRequest request) {
        ColorCreateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        assertHex(normalized.hexCode());
        if (colorRepository.existsByName(normalized.name())) {
            throw new DuplicateResourceException("Color name already exists");
        }
        Color color = new Color();
        apply(color, normalized.name(), normalized.hexCode(), normalized.active());
        return CatalogMapper.toColorResponse(colorRepository.save(color));
    }

    @Transactional
    public ColorResponse update(Long id, ColorUpdateRequest request) {
        ColorUpdateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        assertHex(normalized.hexCode());
        Color color = colorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Color not found"));
        if (colorRepository.existsByNameAndIdNot(normalized.name(), id)) {
            throw new DuplicateResourceException("Color name already exists");
        }
        apply(color, normalized.name(), normalized.hexCode(), normalized.active());
        return CatalogMapper.toColorResponse(color);
    }

    public List<ColorResponse> listActive() {
        return colorRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(CatalogMapper::toColorResponse)
                .toList();
    }

    public List<ColorResponse> listAll() {
        return colorRepository.findAllByOrderByNameAsc().stream()
                .map(CatalogMapper::toColorResponse)
                .toList();
    }

    private static void assertHex(String hexCode) {
        if (hexCode != null && !hexCode.matches(HEX)) {
            throw new BusinessRuleException("hexCode must match #RRGGBB");
        }
    }

    private static void apply(Color color, String name, String hexCode, Boolean active) {
        color.setName(name);
        color.setHexCode(hexCode);
        color.setActive(active);
    }

    private static String normalizeHex(String hexCode) {
        String trimmed = Text.emptyToNull(hexCode);
        if (trimmed == null) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static ColorCreateRequest normalize(ColorCreateRequest request) {
        if (request == null) {
            return null;
        }
        return new ColorCreateRequest(Text.trim(request.name()), normalizeHex(request.hexCode()), request.active());
    }

    private static ColorUpdateRequest normalize(ColorUpdateRequest request) {
        if (request == null) {
            return null;
        }
        return new ColorUpdateRequest(Text.trim(request.name()), normalizeHex(request.hexCode()), request.active());
    }
}
