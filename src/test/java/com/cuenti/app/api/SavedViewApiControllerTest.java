package com.cuenti.app.api;

import com.cuenti.app.service.UserService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "demo")
class SavedViewApiControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;

    private long createView(String name) throws Exception {
        String body = mockMvc.perform(post("/api/saved-views")
                        .with(user("demo"))
                        .contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"params\":\"type=EXPENSE&tag=food\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    @Test
    void createListUpdateDelete() throws Exception {
        long id = createView("Food expenses");

        mockMvc.perform(get("/api/saved-views")
                        .with(user("demo")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].name").value("Food expenses"));

        mockMvc.perform(put("/api/saved-views/" + id)
                        .with(user("demo"))
                        .contentType("application/json")
                        .content("{\"name\":\"Food 2026\",\"params\":\"type=EXPENSE&tag=food&year=2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Food 2026"))
                .andExpect(jsonPath("$.params").value("type=EXPENSE&tag=food&year=2026"));

        mockMvc.perform(delete("/api/saved-views/" + id)
                        .with(user("demo")))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/saved-views/" + id)
                        .with(user("demo")))
                .andExpect(status().isNotFound());
    }

    @Test
    void sameNameUpserts() throws Exception {
        long first = createView("Dupe");
        long second = createView("Dupe");
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);
    }

    @Test
    void foreignViewInvisible() throws Exception {
        long id = createView("Mine");
        userService.registerUser("intruder2", "intruder2@x.com", "password123", "In", "Truder");

        mockMvc.perform(put("/api/saved-views/" + id)
                        .with(user("intruder2"))
                        .contentType("application/json")
                        .content("{\"params\":\"x\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/saved-views/" + id)
                        .with(user("intruder2")))
                .andExpect(status().isNotFound());
    }
}
