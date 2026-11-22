package com.DatShop.controller;

import com.DatShop.dto.account.AddressCreateRequest;
import com.DatShop.dto.account.AddressResponse;
import com.DatShop.dto.account.AddressUpdateRequest;
import com.DatShop.exception.ApiError;
import com.DatShop.service.AddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Addresses")
@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @Operation(summary = "List the authenticated user's shipping addresses")
    @GetMapping
    public List<AddressResponse> list(Authentication authentication) {
        return addressService.listMine(authentication);
    }

    @Operation(summary = "Create a shipping address")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid token",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping
    public ResponseEntity<AddressResponse> create(
            Authentication authentication,
            @Valid @RequestBody AddressCreateRequest request) {
        AddressResponse created = addressService.create(authentication, request);
        return ResponseEntity.created(URI.create("/api/addresses/" + created.id())).body(created);
    }

    @Operation(summary = "Update a shipping address owned by the current user")
    @PutMapping("/{addressId}")
    public AddressResponse update(
            Authentication authentication,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressUpdateRequest request) {
        return addressService.update(authentication, addressId, request);
    }

    @Operation(summary = "Delete a shipping address owned by the current user")
    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication authentication, @PathVariable Long addressId) {
        addressService.delete(authentication, addressId);
    }

    @Operation(summary = "Mark an address as the default shipping address")
    @PatchMapping("/{addressId}/default")
    public AddressResponse setDefault(Authentication authentication, @PathVariable Long addressId) {
        return addressService.setDefault(authentication, addressId);
    }
}
