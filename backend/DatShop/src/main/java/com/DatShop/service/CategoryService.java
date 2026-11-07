package com.DatShop.service;

import com.DatShop.dto.catalog.CategoryCreateRequest;
import com.DatShop.dto.catalog.CategoryResponse;
import com.DatShop.dto.catalog.CategoryUpdateRequest;
import com.DatShop.exception.BusinessRuleException;
import com.DatShop.exception.DuplicateResourceException;
import com.DatShop.exception.ResourceNotFoundException;
import com.DatShop.mapper.CatalogMapper;
import com.DatShop.model.CatalogStatus;
import com.DatShop.model.Category;
import com.DatShop.repository.CategoryRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final RequestValidator requestValidator;

    public CategoryService(CategoryRepository categoryRepository, RequestValidator requestValidator) {
        this.categoryRepository = categoryRepository;
        this.requestValidator = requestValidator;
    }

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        CategoryCreateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        if (categoryRepository.existsBySlug(normalized.slug())) {
            throw new DuplicateResourceException("Category slug already exists");
        }
        Category category = new Category();
        apply(category, normalized.name(), normalized.slug(), normalized.description(), normalized.status());
        assignParent(category, normalized.parentCategoryId());
        return CatalogMapper.toCategoryResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        CategoryUpdateRequest normalized = normalize(request);
        requestValidator.validate(normalized);
        Category category = requireCategory(id);
        if (categoryRepository.existsBySlugAndIdNot(normalized.slug(), id)) {
            throw new DuplicateResourceException("Category slug already exists");
        }
        apply(category, normalized.name(), normalized.slug(), normalized.description(), normalized.status());
        assignParent(category, normalized.parentCategoryId());
        return CatalogMapper.toCategoryResponse(category);
    }

    public CategoryResponse getById(Long id) {
        return CatalogMapper.toCategoryResponse(requireCategory(id));
    }

    public List<CategoryResponse> listActive() {
        return categoryRepository.findByStatusOrderByNameAsc(CatalogStatus.ACTIVE).stream()
                .map(CatalogMapper::toCategoryResponse)
                .toList();
    }

    public List<CategoryResponse> listAll() {
        return categoryRepository.findAllByOrderByNameAsc().stream()
                .map(CatalogMapper::toCategoryResponse)
                .toList();
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));
    }

    private void assignParent(Category category, Long parentId) {
        if (parentId == null) {
            category.setParentCategory(null);
            return;
        }
        if (category.getId() != null && category.getId().equals(parentId)) {
            throw new BusinessRuleException("Category cannot be its own parent");
        }
        Category parent = categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category not found"));
        Category current = parent;
        Set<Long> seen = new HashSet<>();
        while (current != null) {
            if (category.getId() != null && category.getId().equals(current.getId())) {
                throw new BusinessRuleException("Category parent cycle is not allowed");
            }
            if (!seen.add(current.getId())) {
                throw new BusinessRuleException("Category parent cycle is not allowed");
            }
            current = current.getParentCategory();
        }
        category.setParentCategory(parent);
    }

    private static void apply(
            Category category, String name, String slug, String description, CatalogStatus status) {
        category.setName(name);
        category.setSlug(slug);
        category.setDescription(description);
        category.setStatus(status);
    }

    private static CategoryCreateRequest normalize(CategoryCreateRequest request) {
        if (request == null) {
            return null;
        }
        return new CategoryCreateRequest(
                Text.trim(request.name()),
                Text.trim(request.slug()),
                Text.emptyToNull(request.description()),
                request.status(),
                request.parentCategoryId());
    }

    private static CategoryUpdateRequest normalize(CategoryUpdateRequest request) {
        if (request == null) {
            return null;
        }
        return new CategoryUpdateRequest(
                Text.trim(request.name()),
                Text.trim(request.slug()),
                Text.emptyToNull(request.description()),
                request.status(),
                request.parentCategoryId());
    }
}
