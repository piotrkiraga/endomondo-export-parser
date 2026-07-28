package pl.kiraga.endomondoexportparser.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class LocalizationTest {

    private static final String LOCALE_COOKIE = "org.springframework.web.servlet.i18n.CookieLocaleResolver.LOCALE";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void polishPageRendersCorrectDiacritics() throws Exception {
        mockMvc.perform(get("/home").param("lang", "pl"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Strona główna")))
                .andExpect(content().string(containsString("Odczytuje Twój eksport aktywności")))
                .andExpect(content().string(not(containsString("Å"))));
    }

    @Test
    void uploadFormTranslatesCompletely() throws Exception {
        mockMvc.perform(get("/upload").param("lang", "pl"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Prześlij pojedynczy plik eksportu aktywności")))
                .andExpect(content().string(containsString("Plik zawierający eksport aktywności Endomondo w formacie JSON:")))
                .andExpect(content().string(containsString("Prześlij")))
                .andExpect(content().string(not(containsString("Submit"))));
    }

    @Test
    void localePersistsViaCookieWithoutLangParameter() throws Exception {
        mockMvc.perform(get("/home").cookie(new Cookie(LOCALE_COOKIE, "pl")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Strona główna")));
    }

    @Test
    void defaultLocaleIsEnglish() throws Exception {
        mockMvc.perform(get("/home"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Home")))
                .andExpect(content().string(containsString("Reads your Endomondo activity export")));
    }

}
