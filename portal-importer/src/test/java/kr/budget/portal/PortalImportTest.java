package kr.budget.portal;

import java.nio.charset.StandardCharsets;
import org.apache.camel.CamelExecutionException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortalImportTest {
    private static final String ID = "61709210-e5a4-44c1-bc03-579cc6f30775";
    private static final String ROW = String.join(",",ID,ID,ID,"2","SETTLEMENT","180000");

    @Test
    void normalizesBothFormatsAndRejectsAmbiguousMessages() throws Exception {
        try (var camel = PortalImport.routes()) {
            camel.start();
            try (var producer = camel.createProducerTemplate()) {
                String csv = ROW.replace(",SETTLEMENT,",",\"SETTLEMENT\",")+"\r\n";
                String normalized = producer.requestBody("direct:csv",csv,String.class);
                assertTrue(normalized.contains("\"amount\":180000"));
                assertEquals(normalized,producer.requestBody("direct:json",normalized.getBytes(StandardCharsets.UTF_8),String.class));
                for (String invalid : new String[]{ROW+"\n"+ROW,ROW+",",ROW.replace("180000","-1"),ROW.replace("2,SETTLEMENT","1,SETTLEMENT")})
                    assertThrows(CamelExecutionException.class,()->producer.requestBody("direct:csv",invalid));
                for (String invalid : new String[]{normalized+" {}",normalized.replace("\"amount\":","\"amount\":0,\"amount\":"),normalized.replace("180000","180000.1")})
                    assertThrows(CamelExecutionException.class,()->producer.requestBody("direct:json",invalid));
            }
        }
    }
}
