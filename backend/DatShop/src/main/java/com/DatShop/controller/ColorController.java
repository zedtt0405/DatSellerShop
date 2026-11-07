package com.DatShop.controller;

import com.DatShop.dto.catalog.ColorResponse;
import com.DatShop.service.ColorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Colors")
@SecurityRequirements
@RestController
@RequestMapping("/api/colors")
public class ColorController {

    private final ColorService colorService;

    public ColorController(ColorService colorService) {
        this.colorService = colorService;
    }

    @Operation(summary = "List active colors")
    @GetMapping
    public List<ColorResponse> listActive() {
        return colorService.listActive();
    }
}
