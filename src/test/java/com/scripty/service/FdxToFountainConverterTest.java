package com.scripty.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class FdxToFountainConverterTest {

    @Test
    void convertsScreenplayElementsToFountain() throws Exception {
        String fdx = """
                <?xml version="1.0" encoding="UTF-8" standalone="no" ?>
                <FinalDraft DocumentType="Script" Template="No" Version="3">
                  <TitlePage>
                    <Content>
                      <Paragraph Alignment="Center">
                        <Text>MY SCRIPT</Text>
                      </Paragraph>
                      <Paragraph Alignment="Center">
                        <Text>written by</Text>
                      </Paragraph>
                      <Paragraph Alignment="Center">
                        <Text>Jane Doe</Text>
                      </Paragraph>
                      <Paragraph Alignment="Left">
                        <Text>jane@example.com</Text>
                      </Paragraph>
                    </Content>
                  </TitlePage>
                  <Content>
                    <Paragraph Type="Action">
                      <Text>FADE IN:</Text>
                    </Paragraph>
                    <Paragraph Type="Scene Heading">
                      <Text>INT. KITCHEN - DAY</Text>
                    </Paragraph>
                    <Paragraph Type="Action">
                      <Text>Jane enters.</Text>
                    </Paragraph>
                    <Paragraph Type="Character">
                      <Text>JANE</Text>
                    </Paragraph>
                    <Paragraph Type="Parenthetical">
                      <Text>(quietly)</Text>
                    </Paragraph>
                    <Paragraph Type="Dialogue">
                      <Text>Hello there.</Text>
                    </Paragraph>
                    <Paragraph Type="Transition">
                      <Text>CUT TO:</Text>
                    </Paragraph>
                    <Paragraph Type="Shot">
                      <Text>CLOSE ON THE DOOR</Text>
                    </Paragraph>
                    <Paragraph Type="General" Alignment="Center">
                      <Text>THE END</Text>
                    </Paragraph>
                  </Content>
                </FinalDraft>
                """;

        String fountain = FdxToFountainConverter.convert(
                new ByteArrayInputStream(fdx.getBytes(StandardCharsets.UTF_8)));

        assertTrue(fountain.contains("Title: MY SCRIPT"));
        assertTrue(fountain.contains("Credit: written by"));
        assertTrue(fountain.contains("Author: Jane Doe"));
        assertTrue(fountain.contains("Contact: jane@example.com"));
        assertTrue(fountain.contains("INT. KITCHEN - DAY"));
        assertTrue(fountain.contains("@JANE"));
        assertTrue(fountain.contains("(quietly)"));
        assertTrue(fountain.contains("Hello there."));
        assertTrue(fountain.contains("CUT TO:"));
        assertTrue(fountain.contains("CLOSE ON THE DOOR"));
        assertTrue(fountain.contains(">THE END<"));

        int bodyStart = fountain.indexOf("FADE IN:");
        assertTrue(bodyStart > 0);
        String body = fountain.substring(bodyStart);

        FountainImportServiceImpl importer = new FountainImportServiceImpl();
        List<?> parsed = importer.parse(body);
        assertEquals(9, parsed.size());
    }

    @Test
    void convertsDualDialogue() throws Exception {
        String fdx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FinalDraft DocumentType="Script" Version="3">
                  <Content>
                    <Paragraph>
                      <DualDialogue>
                        <Paragraph Type="Character"><Text>ALICE</Text></Paragraph>
                        <Paragraph Type="Dialogue"><Text>Left side.</Text></Paragraph>
                      </DualDialogue>
                      <DualDialogue>
                        <Paragraph Type="Character"><Text>BOB</Text></Paragraph>
                        <Paragraph Type="Dialogue"><Text>Right side.</Text></Paragraph>
                      </DualDialogue>
                    </Paragraph>
                  </Content>
                </FinalDraft>
                """;

        String fountain = FdxToFountainConverter.convert(
                new ByteArrayInputStream(fdx.getBytes(StandardCharsets.UTF_8)));

        assertTrue(fountain.contains("@ALICE"));
        assertTrue(fountain.contains("Left side."));
        assertTrue(fountain.contains("@BOB ^"));
        assertTrue(fountain.contains("Right side."));
    }

    @Test
    void convertPlainSkipsTitlePage() throws Exception {
        String fdx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FinalDraft DocumentType="Script" Version="3">
                  <TitlePage>
                    <Content>
                      <Paragraph Alignment="Center"><Text>TITLE ONLY</Text></Paragraph>
                    </Content>
                  </TitlePage>
                  <Content>
                    <Paragraph Type="Action"><Text>Body line one</Text></Paragraph>
                    <Paragraph Type="Action"><Text>Body line two</Text></Paragraph>
                  </Content>
                </FinalDraft>
                """;

        String plain = FdxToFountainConverter.convertPlain(
                new ByteArrayInputStream(fdx.getBytes(StandardCharsets.UTF_8)));

        assertEquals("Body line one\nBody line two", plain);
    }

    @Test
    void preservesEmphasisStyles() throws Exception {
        String fdx = """
                <?xml version="1.0" encoding="UTF-8"?>
                <FinalDraft DocumentType="Script" Version="3">
                  <Content>
                    <Paragraph Type="Action">
                      <Text Style="Bold">Loud</Text>
                      <Text> and </Text>
                      <Text Style="Italic">soft</Text>
                    </Paragraph>
                  </Content>
                </FinalDraft>
                """;

        String fountain = FdxToFountainConverter.convert(
                new ByteArrayInputStream(fdx.getBytes(StandardCharsets.UTF_8)));

        assertTrue(fountain.contains("**Loud**"));
        assertTrue(fountain.contains("*soft*"));
    }
}
