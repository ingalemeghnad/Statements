package com.bank.mt.parsing;

import com.bank.mt.domain.MtStatement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MtParserTest {

    private final MtParser parser = new MtParser();

    @Test
    void parseSinglePageMt940() {
        String raw = """
                {1:F01BANKGB22AXXX0000000000}{2:I940CLIENTBICXXXXN}{4:
                :20:REF123
                :25:123456789
                :28C:00001/001
                :60F:C210101EUR1000,
                :61:2101010101DR100,
                :62F:C210101EUR900,
                -}""";

        MtStatement stmt = parser.parse(raw);

        assertEquals("MT940", stmt.getMessageType());
        assertEquals("123456789", stmt.getAccountNumber());
        assertEquals("REF123", stmt.getTransactionReference());
        assertEquals("BANKGB22", stmt.getSenderBic());
        assertEquals("CLIENTBI", stmt.getReceiverBic());
        assertEquals("00001", stmt.getStatementNumber());
        assertEquals(1, stmt.getPageNumber());
        assertFalse(stmt.isMultiPage());
    }

    @Test
    void parseMt942() {
        String raw = """
                {1:F01BANKGB22AXXX0000000000}{2:I942CLIENTBICXXXXN}{4:
                :20:REF789
                :25:987654321
                :28C:00001/001
                :34F:EUR100,
                :13D:2101011200+0100
                :61:2101010101CR500,
                :90D:1EUR100,
                :90C:1EUR500,
                -}""";

        MtStatement stmt = parser.parse(raw);

        assertEquals("MT942", stmt.getMessageType());
        assertEquals("987654321", stmt.getAccountNumber());
        assertEquals("REF789", stmt.getTransactionReference());
    }

    @Test
    void parseMultiPageMt940_page1() {
        String raw = """
                {1:F01BANKGB22AXXX0000000000}{2:I940CLIENTBICXXXXN}{4:
                :20:REF456
                :25:123456789
                :28C:00002/002
                :60F:C210201EUR5000,
                :61:2102010201DR200,
                -}""";

        MtStatement stmt = parser.parse(raw);

        assertEquals("MT940", stmt.getMessageType());
        assertEquals("00002", stmt.getStatementNumber());
        assertEquals(2, stmt.getPageNumber());
    }

    @Test
    void parseNullMessageThrows() {
        assertThrows(MtParseException.class, () -> parser.parse(null));
    }

    @Test
    void parseBlankMessageThrows() {
        assertThrows(MtParseException.class, () -> parser.parse("  "));
    }
}
