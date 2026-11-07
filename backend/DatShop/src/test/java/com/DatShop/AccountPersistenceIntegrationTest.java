package com.DatShop;

import static org.assertj.core.api.Assertions.assertThat;

import com.DatShop.model.Address;
import com.DatShop.model.RoleName;
import com.DatShop.model.User;
import com.DatShop.model.UserStatus;
import com.DatShop.repository.AddressRepository;
import com.DatShop.repository.RoleRepository;
import com.DatShop.repository.UserRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AccountPersistenceIntegrationTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Test
    void rolesAreSeededOnStartup() {
        assertThat(roleRepository.count()).isEqualTo(3);
        assertThat(roleRepository.findByName(RoleName.CUSTOMER)).isPresent();
        assertThat(roleRepository.findByName(RoleName.STAFF)).isPresent();
        assertThat(roleRepository.findByName(RoleName.ADMIN)).isPresent();
    }

    @Test
    void persistsUserWithRoleAndAddress() {
        var customerRole = roleRepository.findByName(RoleName.CUSTOMER).orElseThrow();

        User user = new User();
        user.setUsername("test_customer");
        user.setEmail("test_customer@example.com");
        user.setPasswordHash("$2a$10$placeholder.hash.for.testing.only");
        user.setFullName("Test Customer");
        user.setPhoneNumber("0900000000");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(customerRole));

        User savedUser = userRepository.saveAndFlush(user);
        assertThat(savedUser.getId()).isNotNull();

        Address address = new Address();
        address.setUser(savedUser);
        address.setRecipientName("Test Customer");
        address.setPhoneNumber("0900000000");
        address.setProvince("Ho Chi Minh");
        address.setDistrict("District 1");
        address.setWard("Ben Nghe");
        address.setAddressLine("123 Test Street");
        address.setIsDefault(true);

        Address savedAddress = addressRepository.saveAndFlush(address);
        assertThat(savedAddress.getId()).isNotNull();

        User reloaded = userRepository.findById(savedUser.getId()).orElseThrow();
        assertThat(reloaded.getUsername()).isEqualTo("test_customer");
        assertThat(reloaded.getRoles()).extracting(role -> role.getName()).contains(RoleName.CUSTOMER);

        assertThat(addressRepository.findByUser_Id(savedUser.getId())).hasSize(1);
        assertThat(addressRepository.findByUser_IdAndIsDefaultTrue(savedUser.getId())).hasSize(1);
    }
}
