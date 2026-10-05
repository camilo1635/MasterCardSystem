package com.mastercard.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mastercard.system.security.AppUser;
import com.mastercard.system.security.AppUserRepository;
import com.mastercard.system.security.Role;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SecurityIT {

    static final String PASS = "Clave-Segura-123";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;

    private void user(String name, Role role) {
        AppUser u = new AppUser();
        u.setUsername(name);
        u.setPasswordHash(encoder.encode(PASS));
        u.setRole(role);
        users.save(u);
    }

    private MvcResult login(String user, String pass) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"" + pass + "\"}")).andReturn();
    }

    private String token(String user, Role role) throws Exception {
        if (users.findByUsername(user).isEmpty()) user(user, role);
        MvcResult r = login(user, user.equals("admin") ? "Admin-Test-12345" : PASS);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        return "Bearer " + body.get("accessToken").asText();
    }

    @Test
    void sinTokenOTokenInvalido401() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
        mvc.perform(get("/api/products").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginCorrectoDevuelveTokenYCookieSegura() throws Exception {
        MvcResult r = login("admin", "Admin-Test-12345");
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = json.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("accessToken").asText()).isNotBlank();
        assertThat(body.get("user").get("role").asText()).isEqualTo("ADMIN");
        assertThat(body.toString()).doesNotContain("password");
        String cookie = r.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).contains("refresh_token=").contains("HttpOnly").contains("SameSite=Strict")
                .contains("Path=/api/auth");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + body.get("accessToken").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void loginFallidoDaMensajeGenerico() throws Exception {
        MvcResult bad = login("admin", "incorrecta-123456");
        MvcResult none = login("no-existe", "incorrecta-123456");
        assertThat(bad.getResponse().getStatus()).isEqualTo(401);
        assertThat(none.getResponse().getStatus()).isEqualTo(401);
        assertThat(bad.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .isEqualTo(none.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void bloqueoTrasCincoIntentosFallidos() throws Exception {
        user("bloqueable", Role.VENDEDOR);
        for (int i = 0; i < 5; i++) {
            assertThat(login("bloqueable", "mala-clave-123").getResponse().getStatus()).isEqualTo(401);
        }
        // Aun con la clave correcta, la cuenta está bloqueada.
        assertThat(login("bloqueable", PASS).getResponse().getStatus()).isEqualTo(423);
    }

    @Test
    void permisosPorRol() throws Exception {
        String admin = token("admin", Role.ADMIN);
        String vendedor = token("vend1", Role.VENDEDOR);
        String contador = token("cont1", Role.CONTADOR);
        String producto = "{\"sku\":\"S-1\",\"name\":\"X\",\"price\":1000}";

        // Lectura: cualquier usuario autenticado.
        mvc.perform(get("/api/products").header("Authorization", vendedor)).andExpect(status().isOk());

        // Vendedor: no administra catálogo, usuarios, contabilidad ni anula facturas.
        mvc.perform(post("/api/products").header("Authorization", vendedor)
                .contentType(MediaType.APPLICATION_JSON).content(producto)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tiene permisos para realizar esta acción"));
        mvc.perform(post("/api/invoices/1/cancel").header("Authorization", vendedor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/accounting/accounts").header("Authorization", vendedor)).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", vendedor)).andExpect(status().isForbidden());
        mvc.perform(post("/api/inventory/adjust").header("Authorization", vendedor)
                .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isForbidden());

        // Contador: ve contabilidad, pero no factura ni gestiona usuarios.
        mvc.perform(get("/api/accounting/accounts").header("Authorization", contador)).andExpect(status().isOk());
        mvc.perform(post("/api/invoices").header("Authorization", contador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paymentType\":\"CONTADO\",\"items\":[{\"productId\":1,\"quantity\":1}]}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/users").header("Authorization", contador)).andExpect(status().isForbidden());

        // Admin: todo.
        mvc.perform(get("/api/users").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/accounting/accounts").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(post("/api/products").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(producto)).andExpect(status().isCreated());
    }

    @Test
    void refreshRotaYDetectaReutilizacion() throws Exception {
        user("rot1", Role.VENDEDOR);
        MvcResult l = login("rot1", PASS);
        Cookie first = new Cookie("refresh_token", l.getResponse().getCookie("refresh_token").getValue());

        MvcResult r = mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isOk()).andReturn();
        Cookie second = new Cookie("refresh_token", r.getResponse().getCookie("refresh_token").getValue());
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Reusar el token ya rotado: rechazado y cierra todas las sesiones del usuario.
        mvc.perform(post("/api/auth/refresh").cookie(first)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevocaElRefreshToken() throws Exception {
        user("out1", Role.VENDEDOR);
        MvcResult l = login("out1", PASS);
        Cookie c = new Cookie("refresh_token", l.getResponse().getCookie("refresh_token").getValue());
        mvc.perform(post("/api/auth/logout").cookie(c)).andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").cookie(c)).andExpect(status().isUnauthorized());
    }

    @Test
    void usuariosSoloAdminYContrasenaMinima() throws Exception {
        String admin = token("admin", Role.ADMIN);
        mvc.perform(post("/api/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nuevo1\",\"password\":\"corta\",\"role\":\"VENDEDOR\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"nuevo1\",\"password\":\"" + PASS + "\",\"role\":\"VENDEDOR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        // Un administrador no puede desactivarse a sí mismo.
        long adminId = users.findByUsername("admin").orElseThrow().getId();
        mvc.perform(put("/api/users/" + adminId).header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\",\"active\":false}"))
                .andExpect(status().isBadRequest());
    }
}
