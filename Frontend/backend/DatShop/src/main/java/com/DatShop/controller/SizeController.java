package com.DatShop.controller;

import com.DatShop.dto.catalog.SizeResponse;
import com.DatShop.service.SizeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sizes")
@SecurityRequirements
@RestController
@RequestMapping("/api/sizes")
public class SizeController {

    private final SizeService sizeService;

    public SizeController(SizeService sizeService) {
        this.sizeService = sizeService;
    }

    @Operation(summary = "List active sizes")
    @GetMapping
    public List<SizeResponse> listActive() {
        return sizeService.listActive();
    }
}
