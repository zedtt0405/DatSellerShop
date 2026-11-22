package com.DatShop;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsArePublicAndDescribeBearerAuth() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Smart Fashion E-Commerce API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/products'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/products/{slug}'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/categories'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/brands'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/sizes'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/colors'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/products'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/categories'].post").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/products'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/products'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/categories'].post").exists())
                .andExpect(jsonPath("$.paths['/api/admin/products'].post").exists())
                .andExpect(jsonPath("$.paths['/api/profile'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/profile'].put.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/profile/password'].put.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/addresses'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/addresses'].post").exists())
                .andExpect(jsonPath("$.paths['/api/cart'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/cart/items'].post").exists())
                .andExpect(jsonPath("$.paths['/api/cart/items/{itemId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/cart/items/{itemId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/cart/items'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/wishlist'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/wishlist/items'].post").exists())
                .andExpect(jsonPath("$.paths['/api/wishlist/items/{productId}/status'].get").exists())
                .andExpect(jsonPath("$.paths['/api/vouchers/validate'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/vouchers'].post").exists())
                .andExpect(jsonPath("$.paths['/api/admin/vouchers'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/checkout'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/orders'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/orders/{orderNumber}'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/orders/{orderNumber}/cancel'].patch.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/orders'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/orders/{orderNumber}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/orders/{orderNumber}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/orders/{orderNumber}/payment'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/orders/{orderNumber}/payment'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/payments'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/payments/{paymentReference}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/products/{slug}/reviews'].get.security").isEmpty())
                .andExpect(jsonPath("$.paths['/api/reviews'].post.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/reviews/me'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/reviews/{reviewId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/reviews/{reviewId}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/admin/reviews'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/reviews/{reviewId}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/admin/customers'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/customers/{customerId}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/customers/{customerId}/orders'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/customers/{customerId}/status'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/admin/dashboard/summary'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/admin/dashboard/revenue-chart'].get").exists())
                .andExpect(jsonPath("$.paths['/api/admin/dashboard/best-selling-products'].get").exists());
    }

    @Test
    void swaggerUiIsPublic() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
